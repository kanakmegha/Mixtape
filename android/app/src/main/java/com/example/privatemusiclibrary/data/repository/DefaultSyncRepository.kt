package com.example.privatemusiclibrary.data.repository

import android.content.Context
import android.util.Log
import com.example.privatemusiclibrary.data.auth.AuthTokenStore
import com.example.privatemusiclibrary.data.network.*
import com.example.privatemusiclibrary.database.*
import com.example.privatemusiclibrary.domain.identity.TrackIdentityResolver
import com.example.privatemusiclibrary.domain.model.SyncStatus
import com.example.privatemusiclibrary.domain.repository.SyncRepository
import com.example.privatemusiclibrary.domain.repository.SyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSyncRepository @Inject constructor(
    private val api: MixtapeApi,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val playlistTrackDao: PlaylistTrackDao,
    private val syncTombstoneDao: SyncTombstoneDao,
    private val tokenStore: AuthTokenStore,
    @ApplicationContext private val context: Context?
) : SyncRepository {

    private val prefs by lazy {
        try {
            context?.getSharedPreferences("mixtape_sync_prefs", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    private var inMemoryLastSyncTime: Long = 0L

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(prefs?.getLong("last_sync_time", 0L) ?: 0L)
    override val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val syncMutex = Mutex()

    override suspend fun recordPlaylistDeletion(playlistId: String) {
        val now = System.currentTimeMillis()
        syncTombstoneDao.insert(SyncTombstoneEntity("playlist", playlistId, now))
    }

    override suspend fun recordFavoriteDeletion(trackKey: String) {
        val now = System.currentTimeMillis()
        syncTombstoneDao.insert(SyncTombstoneEntity("favorite", trackKey, now))
    }

    override suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            return@withContext SyncResult.NetworkError("Sync already in progress")
        }

        try {
            val token = tokenStore.getAccessToken()
            if (token.isNullOrBlank()) {
                _syncStatus.value = SyncStatus.Idle
                return@withContext SyncResult.AuthError("User not authenticated")
            }

            _syncStatus.value = SyncStatus.Syncing

            // 1. Ensure all local tracks have trackKeys
            val localTracks = trackDao.getAll()
            for (track in localTracks) {
                if (track.trackKey.isBlank()) {
                    val domainTrack = track.toDomain()
                    val resolvedKey = TrackIdentityResolver.resolveKey(domainTrack, context)
                    trackDao.updateTrackKey(track.id, resolvedKey)
                }
            }

            // Reload local tracks after updating keys
            val updatedTracks = trackDao.getAll()
            val trackMapByTrackKey = updatedTracks.associateBy { it.trackKey }

            // 2. Prepare playlists payload
            val localPlaylists = playlistDao.getAll()
            val playlistPayloads = mutableListOf<PlaylistSyncPayload>()

            for (p in localPlaylists) {
                val tracksInPlaylist = playlistTrackDao.observeTracksForPlaylist(p.id).first()
                val trackPayloads = tracksInPlaylist.mapIndexed { index, t ->
                    val stableKey = t.trackKey.ifBlank { TrackIdentityResolver.fallbackLegacyKey(t.id) }
                    PlaylistTrackSyncPayload(
                        trackKey = stableKey,
                        trackId = stableKey,
                        position = index,
                        title = t.title,
                        artist = t.artist,
                        album = t.album,
                        duration = t.durationMs?.toInt()?.div(1000),
                        deleted = false,
                        updatedAt = p.updatedAt
                    )
                }

                playlistPayloads.add(
                    PlaylistSyncPayload(
                        id = p.id,
                        name = p.name,
                        description = p.description,
                        deleted = p.isDeleted,
                        updatedAt = p.updatedAt,
                        tracks = trackPayloads
                    )
                )
            }

            // 3. Prepare tombstones
            val tombstones = syncTombstoneDao.getAll()
            for (t in tombstones) {
                if (t.entityType == "playlist") {
                    if (playlistPayloads.none { it.id == t.entityId }) {
                        playlistPayloads.add(
                            PlaylistSyncPayload(
                                id = t.entityId,
                                name = "",
                                deleted = true,
                                updatedAt = t.deletedAt
                            )
                        )
                    }
                }
            }

            // 4. Prepare favorites payload
            val favoritePayloads = mutableListOf<FavoriteSyncPayload>()
            val favoriteTracks = trackDao.getFavorites()
            for (fav in favoriteTracks) {
                val stableKey = fav.trackKey.ifBlank { TrackIdentityResolver.fallbackLegacyKey(fav.id) }
                favoritePayloads.add(
                    FavoriteSyncPayload(
                        trackKey = stableKey,
                        title = fav.title,
                        artist = fav.artist,
                        album = fav.album,
                        duration = fav.durationMs?.toInt()?.div(1000),
                        source = fav.sourcePlatform,
                        isFavorite = true,
                        updatedAt = fav.updatedAt,
                        deleted = false
                    )
                )
            }

            for (t in tombstones) {
                if (t.entityType == "favorite") {
                    favoritePayloads.add(
                        FavoriteSyncPayload(
                            trackKey = t.entityId,
                            title = "",
                            isFavorite = false,
                            updatedAt = t.deletedAt,
                            deleted = true
                        )
                    )
                }
            }

            val request = SyncRequest(
                playlists = playlistPayloads,
                favorites = favoritePayloads,
                since = _lastSyncTime.value
            )

            // 5. Send to backend
            val response = api.sync(request)

            if (response.code() == 401 || response.code() == 403) {
                _syncStatus.value = SyncStatus.Error("Authentication required")
                return@withContext SyncResult.AuthError("Session expired")
            }

            if (!response.isSuccessful) {
                val errorMsg = "Sync failed (${response.code()})"
                _syncStatus.value = SyncStatus.Error(errorMsg)
                return@withContext SyncResult.ServerError(response.code(), errorMsg)
            }

            val syncResponse = response.body() ?: return@withContext SyncResult.ServerError(500, "Empty sync response")

            // 6. Apply remote playlists to local Room
            for (remotePlaylist in syncResponse.playlists) {
                if (remotePlaylist.deleted) {
                    playlistTrackDao.removeAllForPlaylist(remotePlaylist.id)
                    playlistDao.delete(
                        PlaylistEntity(
                            id = remotePlaylist.id,
                            name = remotePlaylist.name,
                            description = remotePlaylist.description,
                            artworkUri = null,
                            createdAt = remotePlaylist.updatedAt,
                            updatedAt = remotePlaylist.updatedAt,
                            isDeleted = true
                        )
                    )
                } else {
                    playlistDao.insert(
                        PlaylistEntity(
                            id = remotePlaylist.id,
                            name = remotePlaylist.name,
                            description = remotePlaylist.description,
                            artworkUri = null,
                            createdAt = remotePlaylist.updatedAt,
                            updatedAt = remotePlaylist.updatedAt,
                            isDeleted = false
                        )
                    )

                    playlistTrackDao.removeAllForPlaylist(remotePlaylist.id)

                    for (item in remotePlaylist.tracks) {
                        val logicalKey = item.getLogicalKey()
                        if (logicalKey.isBlank()) continue

                        var localTrack = trackDao.getByTrackKey(logicalKey)
                        if (localTrack == null) {
                            // Track is unavailable locally on this device:
                            // Create placeholder track with isLocalAvailable = false
                            val placeholderId = UUID.randomUUID().toString()
                            val placeholder = TrackEntity(
                                id = placeholderId,
                                title = item.title.ifBlank { "Unknown Title" },
                                artist = item.artist,
                                creator = item.artist,
                                album = item.album,
                                thumbnailUri = null,
                                sourceUrl = null,
                                sourcePlatform = null,
                                sourceId = null,
                                localUri = "",
                                mimeType = "audio/mpeg",
                                durationMs = item.duration?.toLong()?.times(1000L),
                                fileSizeBytes = null,
                                dateAdded = System.currentTimeMillis(),
                                lastPlayedAt = null,
                                playbackPositionMs = 0L,
                                isFavorite = false,
                                folderId = null,
                                createdAt = remotePlaylist.updatedAt,
                                updatedAt = remotePlaylist.updatedAt,
                                trackKey = logicalKey,
                                isLocalAvailable = false
                            )
                            trackDao.insert(placeholder)
                            localTrack = placeholder
                        }

                        playlistTrackDao.insert(
                            PlaylistTrackEntity(
                                playlistId = remotePlaylist.id,
                                trackId = localTrack.id,
                                position = item.position
                            )
                        )
                    }
                }
            }

            // 7. Apply remote favorites to local Room
            for (remoteFav in syncResponse.favorites) {
                val localTrack = trackDao.getByTrackKey(remoteFav.trackKey)
                if (remoteFav.deleted || !remoteFav.isFavorite) {
                    if (localTrack != null) {
                        trackDao.updateFavoriteByTrackKey(remoteFav.trackKey, false, remoteFav.updatedAt)
                    }
                } else {
                    if (localTrack != null) {
                        trackDao.updateFavoriteByTrackKey(remoteFav.trackKey, true, remoteFav.updatedAt)
                    } else {
                        // Create placeholder favorite track (unavailable on this device)
                        val placeholder = TrackEntity(
                            id = UUID.randomUUID().toString(),
                            title = remoteFav.title.ifBlank { "Unknown Title" },
                            artist = remoteFav.artist,
                            creator = remoteFav.artist,
                            album = remoteFav.album,
                            thumbnailUri = null,
                            sourceUrl = null,
                            sourcePlatform = remoteFav.source,
                            sourceId = null,
                            localUri = "",
                            mimeType = "audio/mpeg",
                            durationMs = remoteFav.duration?.toLong()?.times(1000L),
                            fileSizeBytes = null,
                            dateAdded = System.currentTimeMillis(),
                            lastPlayedAt = null,
                            playbackPositionMs = 0L,
                            isFavorite = true,
                            folderId = null,
                            createdAt = remoteFav.updatedAt,
                            updatedAt = remoteFav.updatedAt,
                            trackKey = remoteFav.trackKey,
                            isLocalAvailable = false
                        )
                        trackDao.insert(placeholder)
                    }
                }
            }

            // 8. Clear acknowledged tombstones
            syncTombstoneDao.clearAll()

            // 9. Update last sync time
            val serverTime = syncResponse.serverTime
            _lastSyncTime.value = serverTime
            inMemoryLastSyncTime = serverTime
            prefs?.edit()?.putLong("last_sync_time", serverTime)?.apply()

            _syncStatus.value = SyncStatus.Synced
            Log.d("DefaultSyncRepository", "Sync successful at $serverTime")
            SyncResult.Success

        } catch (e: com.example.privatemusiclibrary.di.UnconfiguredBackendException) {
            val msg = e.message ?: "Staging backend URL is not configured"
            _syncStatus.value = SyncStatus.Error(msg)
            SyncResult.ServerError(503, msg)
        } catch (e: IOException) {
            Log.w("DefaultSyncRepository", "Sync network error: ${e.message}")
            _syncStatus.value = SyncStatus.WaitingForNetwork
            SyncResult.NetworkError(e.message ?: "Network error")
        } catch (e: Exception) {
            Log.e("DefaultSyncRepository", "Sync exception: ${e.message}", e)
            _syncStatus.value = SyncStatus.Error(e.message ?: "Sync error")
            SyncResult.NetworkError(e.message ?: "Sync error")
        } finally {
            syncMutex.unlock()
        }
    }
}
