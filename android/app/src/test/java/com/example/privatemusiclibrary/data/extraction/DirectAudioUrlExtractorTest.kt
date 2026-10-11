package com.example.privatemusiclibrary.data.extraction

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DirectAudioUrlExtractorTest {

    private lateinit var extractor: DirectAudioUrlExtractor

    @Before
    fun setUp() {
        extractor = DirectAudioUrlExtractor()
    }

    @Test
    fun supports_plainAudioUrls_returnsTrue() {
        assertTrue(extractor.supports("https://example.com/audio.mp3"))
        assertTrue(extractor.supports("https://example.com/audio.wav"))
        assertTrue(extractor.supports("https://example.com/audio.ogg"))
        assertTrue(extractor.supports("https://example.com/audio.m4a"))
        assertTrue(extractor.supports("https://example.com/audio.flac"))
        assertTrue(extractor.supports("https://example.com/audio.aac"))
        assertTrue(extractor.supports("https://example.com/audio.opus"))
        assertTrue(extractor.supports("http://insecure.example.com/audio.mp3"))
    }

    @Test
    fun supports_audioUrlsWithQueryParams_returnsTrue() {
        assertTrue(extractor.supports("https://cdn.example.com/audio.mp3?token=secret123&expires=1700000000"))
        assertTrue(extractor.supports("https://example.com/sound.wav?track_id=45&format=pcm"))
        assertTrue(extractor.supports("https://media.server/stream.ogg?key=abc&session=xyz"))
        assertTrue(extractor.supports("https://storage.cloud.com/files/track.m4a?dl=1"))
    }

    @Test
    fun supports_audioUrlsWithFragments_returnsTrue() {
        assertTrue(extractor.supports("https://example.com/audio.mp3#t=30"))
        assertTrue(extractor.supports("https://example.com/audio.flac?auth=token#section-2"))
    }

    @Test
    fun supports_caseInsensitiveExtensions_returnsTrue() {
        assertTrue(extractor.supports("https://example.com/audio.MP3"))
        assertTrue(extractor.supports("https://example.com/audio.M4A?source=web"))
        assertTrue(extractor.supports("https://example.com/audio.FLAC"))
    }

    @Test
    fun supports_nonAudioExtensions_returnsFalse() {
        assertFalse(extractor.supports("https://example.com/video.mp4"))
        assertFalse(extractor.supports("https://example.com/document.pdf"))
        assertFalse(extractor.supports("https://example.com/image.png"))
        assertFalse(extractor.supports("https://example.com/archive.zip"))
        assertFalse(extractor.supports("https://example.com/index.html"))
    }

    @Test
    fun supports_urlWithAudioExtensionOnlyInQuery_returnsFalse() {
        // Query param value contains .mp3, but file path is .html or endpoint
        assertFalse(extractor.supports("https://example.com/api/player.html?file=song.mp3"))
        assertFalse(extractor.supports("https://example.com/download?format=mp3"))
    }

    @Test
    fun supports_socialPlatformsAndVideoSites_returnsFalse() {
        assertFalse(extractor.supports("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(extractor.supports("https://youtu.be/dQw4w9WgXcQ"))
        assertFalse(extractor.supports("https://www.instagram.com/reel/C12345/"))
        assertFalse(extractor.supports("https://tiktok.com/@user/video/123"))
    }

    @Test
    fun supports_invalidOrBlankUrl_returnsFalse() {
        assertFalse(extractor.supports(""))
        assertFalse(extractor.supports("   "))
        assertFalse(extractor.supports("not a valid url"))
        assertFalse(extractor.supports("ftp://example.com/audio.mp3"))
        assertFalse(extractor.supports("file:///sdcard/audio.mp3"))
    }
}
