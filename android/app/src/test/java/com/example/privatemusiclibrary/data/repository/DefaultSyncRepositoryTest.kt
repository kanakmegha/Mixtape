package com.example.privatemusiclibrary.data.repository

import com.example.privatemusiclibrary.data.auth.AuthTokenStore
import com.example.privatemusiclibrary.data.network.*
import com.example.privatemusiclibrary.database.*
import com.example.privatemusiclibrary.domain.model.SyncStatus
import com.example.privatemusiclibrary.domain.repository.SyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class FakeTrackDao : TrackDao {
    val tracks = mutableMapOf<String, TrackEntity>()

    override suspend fun insert(track: TrackEntity): Long {
        tracks[track.id] = track
        return 1L
    }

    override suspend fun update(track: TrackEntity): Int {
        tracks[track.id] = track
        return 1
    }

    override suspend fun delete(track: TrackEntity): Int {
        tracks.remove(track.id)
        return 1
    }

    override suspend fun getById(id: String): TrackEntity? = tracks[id]
    override suspend fun getBySourceId(sourceId: String): TrackEntity? = tracks.values.find { it.sourceId == sourceId }
    override suspend fun getByLocalUri(localUri: String): TrackEntity? = tracks.values.find { it.localUri == localUri }
    override fun observeAll(): Flow<List<TrackEntity>> = flowOf(tracks.values.toList())
    override fun observeFavorites(): Flow<List<TrackEntity>> = flowOf(tracks.values.filter { it.isFavorite })
    override fun observeRecentlyAdded(limit: Int): Flow<List<TrackEntity>> = flowOf(tracks.values.toList())
    override fun observeRecentlyPlayed(limit: Int): Flow<List<TrackEntity>> = flowOf(tracks.values.toList())
    override suspend fun search(query: String): List<TrackEntity> = tracks.values.filter { it.title.contains(query) }
    override suspend fun getAll(): List<TrackEntity> = tracks.values.toList()

    override suspend fun getByTrackKey(trackKey: String): TrackEntity? = tracks.values.find { it.trackKey == trackKey }
    override suspend fun getByTrackKeys(trackKeys: List<String>): List<TrackEntity> = tracks.values.filter { it.trackKey in trackKeys }
    override suspend fun getFavorites(): List<TrackEntity> = tracks.values.filter { it.isFavorite }

    override suspend fun updateFavoriteByTrackKey(trackKey: String, isFavorite: Boolean, updatedAt: Long): Int {
        val t = getByTrackKey(trackKey) ?: return 0
        tracks[t.id] = t.copy(isFavorite = isFavorite, updatedAt = updatedAt)
        return 1
    }

    override suspend fun updateTrackKey(id: String, trackKey: String): Int {
        val t = tracks[id] ?: return 0
        tracks[id] = t.copy(trackKey = trackKey)
        return 1
    }
}

class FakePlaylistDao : PlaylistDao {
    val playlists = mutableMapOf<String, PlaylistEntity>()

    override suspend fun insert(playlist: PlaylistEntity): Long {
        playlists[playlist.id] = playlist
        return 1L
    }

    override suspend fun update(playlist: PlaylistEntity): Int {
        playlists[playlist.id] = playlist
        return 1
    }

    override suspend fun delete(playlist: PlaylistEntity): Int {
        playlists.remove(playlist.id)
        return 1
    }

    override suspend fun getById(id: String): PlaylistEntity? = playlists[id]
    override fun observeById(id: String): Flow<PlaylistEntity?> = flowOf(playlists[id])
    override fun observeAll(): Flow<List<PlaylistEntity>> = flowOf(playlists.values.filter { !it.isDeleted })
    override suspend fun getAll(): List<PlaylistEntity> = playlists.values.toList()

    override suspend fun markDeleted(id: String, updatedAt: Long): Int {
        val p = playlists[id] ?: return 0
        playlists[id] = p.copy(isDeleted = true, updatedAt = updatedAt)
        return 1
    }
}

class FakePlaylistTrackDao : PlaylistTrackDao {
    val items = mutableListOf<PlaylistTrackEntity>()
    var trackDao: FakeTrackDao? = null

    override suspend fun insert(playlistTrack: PlaylistTrackEntity): Long {
        items.removeIf { it.playlistId == playlistTrack.playlistId && it.trackId == playlistTrack.trackId }
        items.add(playlistTrack)
        return 1L
    }

    override suspend fun delete(playlistTrack: PlaylistTrackEntity): Int {
        return if (items.removeIf { it.playlistId == playlistTrack.playlistId && it.trackId == playlistTrack.trackId }) 1 else 0
    }

    override suspend fun removeTrack(playlistId: String, trackId: String): Int {
        return if (items.removeIf { it.playlistId == playlistId && it.trackId == trackId }) 1 else 0
    }

    override suspend fun removeAllForPlaylist(playlistId: String): Int {
        val before = items.size
        items.removeIf { it.playlistId == playlistId }
        return before - items.size
    }

    override suspend fun updatePosition(playlistId: String, trackId: String, position: Int): Int {
        val idx = items.indexOfFirst { it.playlistId == playlistId && it.trackId == trackId }
        if (idx != -1) {
            items[idx] = items[idx].copy(position = position)
            return 1
        }
        return 0
    }

    override suspend fun getTrackCount(playlistId: String): Int = items.count { it.playlistId == playlistId }
    override fun observeTrackCount(playlistId: String): Flow<Int> = flowOf(items.count { it.playlistId == playlistId })
    override fun observePlaylistsForTrack(trackId: String): Flow<List<String>> =
        flowOf(items.filter { it.trackId == trackId }.map { it.playlistId })

    override fun observeTracksForPlaylist(playlistId: String): Flow<List<TrackEntity>> {
        val tracksForP = items.filter { it.playlistId == playlistId }
            .sortedBy { it.position }
            .mapNotNull { trackDao?.tracks?.get(it.trackId) }
        return flowOf(tracksForP)
    }
}

class FakeSyncTombstoneDao : SyncTombstoneDao {
    val tombstones = mutableListOf<SyncTombstoneEntity>()

    override suspend fun insert(tombstone: SyncTombstoneEntity): Long {
        tombstones.removeIf { it.entityType == tombstone.entityType && it.entityId == tombstone.entityId }
        tombstones.add(tombstone)
        return 1L
    }

    override suspend fun getAll(): List<SyncTombstoneEntity> = tombstones.toList()

    override suspend fun delete(type: String, id: String): Int {
        return if (tombstones.removeIf { it.entityType == type && it.entityId == id }) 1 else 0
    }

    override suspend fun clearAll(): Int {
        val count = tombstones.size
        tombstones.clear()
        return count
    }
}

class FakeMixtapeApi : MixtapeApi {
    var syncResponse: Response<SyncResponse>? = null
    var shouldThrowIoException = false
    var lastSyncRequest: SyncRequest? = null

    override suspend fun sync(request: SyncRequest): Response<SyncResponse> {
        lastSyncRequest = request
        if (shouldThrowIoException) throw IOException("Simulated network timeout")
        return syncResponse ?: Response.success(SyncResponse(serverTime = 12345L))
    }

    override suspend fun register(request: RegisterRequest): Response<Unit> = Response.success(Unit)
    override suspend fun login(request: LoginRequest): Response<TokenResponse> = Response.success(TokenResponse("access", "refresh"))
    override suspend fun createInvitation(request: CreateInvitationRequest): Response<InvitationResponse> = Response.success(InvitationResponse("id", "tok"))
    override suspend fun refresh(request: RefreshRequest): Response<TokenResponse> = Response.success(TokenResponse("access", "refresh"))
    override suspend fun logout(request: RefreshRequest): Response<Unit> = Response.success(Unit)
    override suspend fun getSyncState(since: Long?): Response<SyncResponse> = Response.success(SyncResponse(serverTime = 12345L))
    override suspend fun syncTracks(tracks: List<TrackSyncPayload>): Response<List<TrackSyncPayload>> = Response.success(tracks)
    override suspend fun syncPlaylists(playlists: List<PlaylistSyncPayload>): Response<Unit> = Response.success(Unit)
}

class FakeAuthTokenStore : AuthTokenStore(null) {
    var token: String? = "mock_jwt_token"
    override fun getAccessToken(): String? = token
}

class DefaultSyncRepositoryTest {

    private lateinit var trackDao: FakeTrackDao
    private lateinit var playlistDao: FakePlaylistDao
    private lateinit var playlistTrackDao: FakePlaylistTrackDao
    private lateinit var tombstoneDao: FakeSyncTombstoneDao
    private lateinit var api: FakeMixtapeApi
    private lateinit var tokenStore: FakeAuthTokenStore
    private lateinit var repository: DefaultSyncRepository

    @Before
    fun setup() {
        trackDao = FakeTrackDao()
        playlistDao = FakePlaylistDao()
        playlistTrackDao = FakePlaylistTrackDao().apply { this.trackDao = this@DefaultSyncRepositoryTest.trackDao }
        tombstoneDao = FakeSyncTombstoneDao()
        api = FakeMixtapeApi()
        tokenStore = FakeAuthTokenStore()

        repository = DefaultSyncRepository(
            api = api,
            trackDao = trackDao,
            playlistDao = playlistDao,
            playlistTrackDao = playlistTrackDao,
            syncTombstoneDao = tombstoneDao,
            tokenStore = tokenStore,
            context = null
        )
    }

    @Test
    fun unauthenticatedUser_returnsAuthErrorAndSetsIdle() = runBlocking {
        tokenStore.token = null

        val result = repository.sync()
        assertTrue(result is SyncResult.AuthError)
        assertEquals(SyncStatus.Idle, repository.syncStatus.value)
        assertNull(api.lastSyncRequest)
    }

    @Test
    fun networkFailure_returnsNetworkErrorAndSetsWaitingForNetwork() = runBlocking {
        api.shouldThrowIoException = true

        val result = repository.sync()
        assertTrue(result is SyncResult.NetworkError)
        assertEquals(SyncStatus.WaitingForNetwork, repository.syncStatus.value)
    }

    @Test
    fun serverError_returnsServerErrorAndSetsError() = runBlocking {
        api.syncResponse = Response.error(500, "Internal Server Error".toResponseBody("text/plain".toMediaTypeOrNull()))

        val result = repository.sync()
        assertTrue(result is SyncResult.ServerError)
        assertTrue(repository.syncStatus.value is SyncStatus.Error)
    }

    @Test
    fun successfulSync_sendsLocalPlaylistsAndTombstones_andAppliesRemoteSnapshot() = runBlocking {
        // Setup local playlist
        val p1 = PlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = "Morning Coffee",
            description = "Chill tunes",
            artworkUri = null,
            createdAt = 1000L,
            updatedAt = 2000L,
            isDeleted = false
        )
        playlistDao.insert(p1)

        val t1 = TrackEntity(
            id = "track_1",
            title = "Chill Beats",
            artist = "Lofi Artist",
            creator = null,
            album = "Vol 1",
            thumbnailUri = null,
            sourceUrl = "https://www.youtube.com/watch?v=chill123",
            sourcePlatform = "youtube",
            sourceId = "chill123",
            localUri = "file:///storage/chill.mp3",
            mimeType = "audio/mpeg",
            durationMs = 120000L,
            fileSizeBytes = 5000L,
            dateAdded = 1000L,
            lastPlayedAt = null,
            playbackPositionMs = 0L,
            isFavorite = true,
            folderId = null,
            createdAt = 1000L,
            updatedAt = 2000L,
            trackKey = "youtube:chill123",
            isLocalAvailable = true
        )
        trackDao.insert(t1)
        playlistTrackDao.insert(PlaylistTrackEntity(p1.id, t1.id, 0))

        // Record a tombstone for a deleted favorite
        tombstoneDao.insert(SyncTombstoneEntity("favorite", "youtube:deleted_song", 3000L))

        // Mock remote response containing a remote playlist with an unavailable track
        val remoteTrackKey = "youtube:unavailable_song"
        api.syncResponse = Response.success(
            SyncResponse(
                playlists = listOf(
                    PlaylistSyncPayload(
                        id = UUID.randomUUID().toString(),
                        name = "Remote Playlist",
                        description = "Synced from other device",
                        deleted = false,
                        updatedAt = 5000L,
                        tracks = listOf(
                            PlaylistTrackSyncPayload(
                                trackKey = remoteTrackKey,
                                position = 0,
                                title = "Unavailable Song",
                                artist = "Remote Artist"
                            )
                        )
                    )
                ),
                favorites = listOf(
                    FavoriteSyncPayload(
                        trackKey = "youtube:new_remote_fav",
                        title = "Remote Fav Song",
                        artist = "Fav Artist",
                        isFavorite = true,
                        updatedAt = 5000L
                    )
                ),
                serverTime = 5555L
            )
        )

        val result = repository.sync()
        assertEquals(SyncResult.Success, result)
        assertEquals(SyncStatus.Synced, repository.syncStatus.value)
        assertEquals(5555L, repository.lastSyncTime.value)

        // Verify sent request included the local playlist, favorite, and tombstone
        val sentReq = api.lastSyncRequest!!
        assertEquals(1, sentReq.playlists.size)
        assertEquals(p1.id, sentReq.playlists[0].id)
        assertEquals("youtube:chill123", sentReq.playlists[0].tracks[0].trackKey)

        // Tombstones should be cleared after acknowledgement
        assertEquals(0, tombstoneDao.getAll().size)

        // Remote playlist should be inserted locally
        val allPlaylists = playlistDao.getAll()
        assertEquals(2, allPlaylists.size)
        assertTrue(allPlaylists.any { it.name == "Remote Playlist" })

        // The unavailable track should have been created as a placeholder with isLocalAvailable = false
        val placeholderTrack = trackDao.getByTrackKey(remoteTrackKey)
        assertNotNull(placeholderTrack)
        assertFalse("Placeholder track must have isLocalAvailable = false", placeholderTrack!!.isLocalAvailable)
        assertEquals("", placeholderTrack.localUri)
        assertEquals("Unavailable Song", placeholderTrack.title)

        // The remote favorite track should also exist as an unavailable placeholder
        val remoteFavTrack = trackDao.getByTrackKey("youtube:new_remote_fav")
        assertNotNull(remoteFavTrack)
        assertTrue(remoteFavTrack!!.isFavorite)
        assertFalse(remoteFavTrack.isLocalAvailable)
    }
}
