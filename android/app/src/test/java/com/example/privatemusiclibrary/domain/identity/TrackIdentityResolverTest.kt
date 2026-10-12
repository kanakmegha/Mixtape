package com.example.privatemusiclibrary.domain.identity

import com.example.privatemusiclibrary.domain.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class TrackIdentityResolverTest {

    private fun createTrack(
        id: String = UUID.randomUUID().toString(),
        title: String = "Test Song",
        artist: String? = "Test Artist",
        sourcePlatform: String? = null,
        sourceId: String? = null,
        sourceUrl: String? = null,
        localUri: String = "",
        trackKey: String = ""
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        creator = artist,
        album = null,
        thumbnailUri = null,
        sourceUrl = sourceUrl,
        sourcePlatform = sourcePlatform,
        sourceId = sourceId,
        localUri = localUri,
        mimeType = "audio/mpeg",
        durationMs = 180000L,
        fileSizeBytes = 1000L,
        dateAdded = 1000L,
        lastPlayedAt = null,
        playbackPositionMs = 0L,
        isFavorite = false,
        folderId = null,
        createdAt = 1000L,
        updatedAt = 1000L,
        trackKey = trackKey,
        isLocalAvailable = true
    )

    @Test
    fun existingTrackKey_isPreserved() = runBlocking {
        val track = createTrack(trackKey = "youtube:existing123")
        val key = TrackIdentityResolver.resolveKey(track, null)
        assertEquals("youtube:existing123", key)
    }

    @Test
    fun explicitPlatformAndSourceId_resolvesProviderKey() = runBlocking {
        val ytTrack = createTrack(sourcePlatform = "YouTube", sourceId = "dQw4w9WgXcQ")
        val igTrack = createTrack(sourcePlatform = "Instagram", sourceId = "Cx7890")
        val scTrack = createTrack(sourcePlatform = "SoundCloud", sourceId = "123456")

        assertEquals("youtube:dQw4w9WgXcQ", TrackIdentityResolver.resolveKey(ytTrack, null))
        assertEquals("instagram:Cx7890", TrackIdentityResolver.resolveKey(igTrack, null))
        assertEquals("soundcloud:123456", TrackIdentityResolver.resolveKey(scTrack, null))
    }

    @Test
    fun sourceUrl_resolvesNormalizedProviderKey() = runBlocking {
        val ytTrack = createTrack(sourceUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10s")
        val igTrack = createTrack(sourceUrl = "https://www.instagram.com/reel/C_reel123/?igsh=abc")

        assertEquals("youtube:dQw4w9WgXcQ", TrackIdentityResolver.resolveKey(ytTrack, null))
        assertEquals("instagram:C_reel123", TrackIdentityResolver.resolveKey(igTrack, null))
    }

    @Test
    fun directAudioUrl_resolvesDirectSha256Key() = runBlocking {
        val directTrack = createTrack(sourceUrl = "https://example.com/audio/song.mp3")
        val key = TrackIdentityResolver.resolveKey(directTrack, null)

        assertTrue("Key should start with direct:", key.startsWith("direct:"))
        assertEquals(key, TrackIdentityResolver.resolveKey(directTrack, null))
    }

    @Test
    fun legacyTrack_withoutProviderOrFile_fallsBackToLegacyId() = runBlocking {
        val id = UUID.randomUUID().toString()
        val legacyTrack = createTrack(id = id, sourcePlatform = null, sourceId = null, sourceUrl = null, localUri = "")

        val key = TrackIdentityResolver.resolveKey(legacyTrack, null)
        assertEquals("legacy:$id", key)
    }

    @Test
    fun sameTitleAndArtistDifferentId_doesNotFalselyMerge() = runBlocking {
        val id1 = UUID.randomUUID().toString()
        val id2 = UUID.randomUUID().toString()

        val track1 = createTrack(id = id1, title = "Hallelujah", artist = "Leonard Cohen")
        val track2 = createTrack(id = id2, title = "Hallelujah", artist = "Leonard Cohen")

        val key1 = TrackIdentityResolver.resolveKey(track1, null)
        val key2 = TrackIdentityResolver.resolveKey(track2, null)

        assertNotEquals("Different recordings sharing same title/artist must not merge", key1, key2)
    }
}
