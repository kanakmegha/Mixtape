package com.example.privatemusiclibrary.domain.sync

import com.example.privatemusiclibrary.data.auth.AuthTokenStore
import com.example.privatemusiclibrary.database.PlaylistDao
import com.example.privatemusiclibrary.database.TrackDao
import com.example.privatemusiclibrary.database.PlaylistTrackDao
import com.example.privatemusiclibrary.data.network.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SyncUseCase @Inject constructor(
    private val api: MixtapeApi,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val playlistTrackDao: PlaylistTrackDao,
    private val tokenStore: AuthTokenStore
) {
    suspend operator fun invoke(): Boolean {
        if (tokenStore.getAccessToken() == null) return false

        return try {
            val localTracks = trackDao.getAll()
            
            val trackPayloads = localTracks.map { track ->
                TrackSyncPayload(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = track.durationMs?.toInt(),
                    source = track.sourcePlatform,
                    sourceIdentifier = track.localUri,
                    deleted = false
                )
            }

            val tracksRes = api.syncTracks(trackPayloads)
            if (tracksRes.isSuccessful && tracksRes.body() != null) {
                // Synced tracks down. Do nothing for now in local-first since we don't have audio
            }

            // 2. Playlists
            val localPlaylists = playlistDao.observeAll().first()
            val playlistPayloads = localPlaylists.map { p ->
                val localTracksInPlaylist = playlistTrackDao.observeTracksForPlaylist(p.id).first()
                PlaylistSyncPayload(
                    id = p.id,
                    name = p.name,
                    deleted = false,
                    tracks = localTracksInPlaylist.mapIndexed { index, track -> 
                        PlaylistTrackSyncPayload(trackId = track.id, position = index) 
                    }
                )
            }

            api.syncPlaylists(playlistPayloads)
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
