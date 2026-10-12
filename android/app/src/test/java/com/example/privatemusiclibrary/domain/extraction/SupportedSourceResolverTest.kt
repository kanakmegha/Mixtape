package com.example.privatemusiclibrary.domain.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedSourceResolverTest {

    @Test
    fun resolve_instagramReel_returnsSupportedWithCanonicalUrlAndMediaId() {
        val url = "https://www.instagram.com/reel/DCqQkQ3I123/"
        val result = SupportedSourceResolver.resolve(url)

        assertTrue(result is SourceResolutionResult.Supported)
        val supported = result as SourceResolutionResult.Supported
        assertEquals(MediaPlatform.INSTAGRAM, supported.platform)
        assertEquals("Instagram", supported.platformDisplayName)
        assertEquals("https://www.instagram.com/reel/DCqQkQ3I123/", supported.canonicalUrl)
        assertEquals("DCqQkQ3I123", supported.mediaId)
    }

    @Test
    fun resolve_instagramReelWithTrackingParameters_stripsTrackingParameters() {
        val url = "https://www.instagram.com/reel/DCqQkQ3I123/?igsh=MW55ZGFuM291Ym1nbQ==&utm_source=ig_web_copy_link"
        val result = SupportedSourceResolver.resolve(url)

        assertTrue(result is SourceResolutionResult.Supported)
        val supported = result as SourceResolutionResult.Supported
        assertEquals("https://www.instagram.com/reel/DCqQkQ3I123/", supported.canonicalUrl)
        assertEquals("DCqQkQ3I123", supported.mediaId)
    }

    @Test
    fun resolve_instagramReelsPluralVariant_normalizesToReelCanonicalUrl() {
        val url = "https://instagram.com/reels/DCqQkQ3I123"
        val result = SupportedSourceResolver.resolve(url)

        assertTrue(result is SourceResolutionResult.Supported)
        val supported = result as SourceResolutionResult.Supported
        assertEquals("https://www.instagram.com/reel/DCqQkQ3I123/", supported.canonicalUrl)
        assertEquals("DCqQkQ3I123", supported.mediaId)
    }

    @Test
    fun resolve_instagramPost_returnsSupportedWithPostCanonicalUrl() {
        val url = "https://www.instagram.com/p/DCqQkQ3I123/?utm_medium=share_sheet"
        val result = SupportedSourceResolver.resolve(url)

        assertTrue(result is SourceResolutionResult.Supported)
        val supported = result as SourceResolutionResult.Supported
        assertEquals(MediaPlatform.INSTAGRAM, supported.platform)
        assertEquals("https://www.instagram.com/p/DCqQkQ3I123/", supported.canonicalUrl)
        assertEquals("DCqQkQ3I123", supported.mediaId)
    }

    @Test
    fun resolve_instagramTv_returnsSupportedWithTvCanonicalUrl() {
        val url = "https://www.instagram.com/tv/DCqQkQ3I123/"
        val result = SupportedSourceResolver.resolve(url)

        assertTrue(result is SourceResolutionResult.Supported)
        val supported = result as SourceResolutionResult.Supported
        assertEquals("https://www.instagram.com/tv/DCqQkQ3I123/", supported.canonicalUrl)
        assertEquals("DCqQkQ3I123", supported.mediaId)
    }

    @Test
    fun resolve_instagramHostVariants_areSupported() {
        assertTrue(SupportedSourceResolver.isSupported("https://m.instagram.com/reel/DCqQkQ3I123/"))
        assertTrue(SupportedSourceResolver.isSupported("https://instagr.am/p/DCqQkQ3I123/"))
        assertTrue(SupportedSourceResolver.isSupported("https://ddinstagram.com/reel/DCqQkQ3I123/"))
        assertTrue(SupportedSourceResolver.isSupported("http://instagram.com/reel/DCqQkQ3I123/"))
    }

    @Test
    fun resolve_maliciousLookalikeDomains_areRejected() {
        assertFalse(SupportedSourceResolver.isSupported("https://evilinstagram.com/reel/DCqQkQ3I123/"))
        assertFalse(SupportedSourceResolver.isSupported("https://notinstagram.com/p/DCqQkQ3I123/"))
        assertFalse(SupportedSourceResolver.isSupported("https://instagram.com.attacker.com/reel/123/"))
        assertFalse(SupportedSourceResolver.isSupported("https://fakeyoutube.com/watch?v=123"))
    }

    @Test
    fun resolve_instagramProfilePage_isRejectedWithInformativeReason() {
        val result = SupportedSourceResolver.resolve("https://www.instagram.com/creators/")

        assertTrue(result is SourceResolutionResult.Unsupported)
        val unsupported = result as SourceResolutionResult.Unsupported
        assertEquals(MediaPlatform.INSTAGRAM, unsupported.platform)
        assertTrue(unsupported.reason.contains("profile"))
    }

    @Test
    fun resolve_instagramStories_isRejectedWithInformativeReason() {
        val result = SupportedSourceResolver.resolve("https://www.instagram.com/stories/someuser/123456789/")

        assertTrue(result is SourceResolutionResult.Unsupported)
        val unsupported = result as SourceResolutionResult.Unsupported
        assertEquals(MediaPlatform.INSTAGRAM, unsupported.platform)
        assertTrue(unsupported.reason.contains("Stories"))
    }

    @Test
    fun resolve_instagramExplore_isRejectedWithInformativeReason() {
        val result = SupportedSourceResolver.resolve("https://www.instagram.com/explore/tags/music/")

        assertTrue(result is SourceResolutionResult.Unsupported)
        val unsupported = result as SourceResolutionResult.Unsupported
        assertEquals(MediaPlatform.INSTAGRAM, unsupported.platform)
        assertTrue(unsupported.reason.contains("Explore"))
    }

    @Test
    fun resolve_urlWithTrailingPunctuation_cleansAndResolves() {
        val url = "https://www.instagram.com/reel/DCqQkQ3I123/."
        val result = SupportedSourceResolver.resolve(url)

        assertTrue(result is SourceResolutionResult.Supported)
        assertEquals("https://www.instagram.com/reel/DCqQkQ3I123/", (result as SourceResolutionResult.Supported).canonicalUrl)
    }

    @Test
    fun resolve_youTubeUrls_areSupportedAndStripPlaylist() {
        val standard = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL12345"
        val resultStandard = SupportedSourceResolver.resolve(standard)
        assertTrue(resultStandard is SourceResolutionResult.Supported)
        val supportedStandard = resultStandard as SourceResolutionResult.Supported
        assertEquals(MediaPlatform.YOUTUBE, supportedStandard.platform)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", supportedStandard.canonicalUrl)

        val shortUrl = "https://youtu.be/dQw4w9WgXcQ"
        val resultShort = SupportedSourceResolver.resolve(shortUrl)
        assertTrue(resultShort is SourceResolutionResult.Supported)
    }

    @Test
    fun resolve_soundCloudUrls_areSupported() {
        val result = SupportedSourceResolver.resolve("https://soundcloud.com/artist/track-name")
        assertTrue(result is SourceResolutionResult.Supported)
        assertEquals(MediaPlatform.SOUNDCLOUD, (result as SourceResolutionResult.Supported).platform)
    }

    @Test
    fun resolve_directAudioUrls_areSupported() {
        val resultMp3 = SupportedSourceResolver.resolve("https://example.com/audio/song.mp3")
        assertTrue(resultMp3 is SourceResolutionResult.Supported)
        assertEquals(MediaPlatform.DIRECT_AUDIO, (resultMp3 as SourceResolutionResult.Supported).platform)

        val resultFlac = SupportedSourceResolver.resolve("https://example.com/audio/track.flac")
        assertTrue(resultFlac is SourceResolutionResult.Supported)
        assertEquals(MediaPlatform.DIRECT_AUDIO, (resultFlac as SourceResolutionResult.Supported).platform)
    }

    @Test
    fun resolve_unsupportedOrInvalidUrls_returnUnsupported() {
        assertFalse(SupportedSourceResolver.isSupported(""))
        assertFalse(SupportedSourceResolver.isSupported("   "))
        assertFalse(SupportedSourceResolver.isSupported("not a url"))
        assertFalse(SupportedSourceResolver.isSupported("file:///storage/emulated/0/track.mp3"))
        assertFalse(SupportedSourceResolver.isSupported("https://example.com/blog/article"))
    }
}
