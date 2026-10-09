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
    enum class Outcome { SUCCESS, RETRYABLE_FAILURE, PERMANENT_FAILURE }

    private fun classify(code: Int): Outcome = when {
        code == 401 || code == 403 -> Outcome.PERMANENT_FAILURE
        code == 408 || code == 429 || code >= 500 -> Outcome.RETRYABLE_FAILURE
        else -> Outcome.PERMANENT_FAILURE
    }

    // Push-only and idempotent: payloads are keyed by stable local IDs, and this
    // method never deletes or modifies local Room data.
    suspend operator fun invoke(): Outcome {
        if (tokenStore.getAccessToken() == null) return Outcome.PERMANENT_FAILURE

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
            if (!tracksRes.isSuccessful) return classify(tracksRes.code())

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

            val playlistsRes = api.syncPlaylists(playlistPayloads)
            if (!playlistsRes.isSuccessful) return classify(playlistsRes.code())

            Outcome.SUCCESS
        } catch (e: java.io.IOException) {
            Outcome.RETRYABLE_FAILURE
        } catch (e: Exception) {
            Outcome.PERMANENT_FAILURE
        }
    }
}
