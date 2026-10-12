package com.example.privatemusiclibrary.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedUrlExtractorTest {

    @Test
    fun extractUrl_fromExactUrl_returnsUrl() {
        val input = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            SharedUrlExtractor.extractUrl(input)
        )
    }

    @Test
    fun extractUrl_fromUrlWithQueryParamsAndFragment_preservesParameters() {
        val input = "https://example.com/audio.mp3?token=xyz123&user=45#part1"
        assertEquals(
            "https://example.com/audio.mp3?token=xyz123&user=45#part1",
            SharedUrlExtractor.extractUrl(input)
        )
    }

    @Test
    fun extractUrl_fromSentence_extractsUrl() {
        val input = "Hey check out this track: https://youtu.be/dQw4w9WgXcQ it's really good!"
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            SharedUrlExtractor.extractUrl(input)
        )
    }

    @Test
    fun extractUrl_withTrailingPunctuation_stripsTrailingPunctuation() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            SharedUrlExtractor.extractUrl("Listen to this: https://youtu.be/dQw4w9WgXcQ.")
        )
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            SharedUrlExtractor.extractUrl("Is this the right link: https://youtu.be/dQw4w9WgXcQ?")
        )
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            SharedUrlExtractor.extractUrl("Amazing sound! (https://youtu.be/dQw4w9WgXcQ)")
        )
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            SharedUrlExtractor.extractUrl("Check this out: 'https://youtu.be/dQw4w9WgXcQ', amazing!")
        )
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            SharedUrlExtractor.extractUrl("[Music Link](https://youtu.be/dQw4w9WgXcQ)")
        )
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            SharedUrlExtractor.extractUrl("Quote \"https://youtu.be/dQw4w9WgXcQ\" quote")
        )
    }

    @Test
    fun extractUrl_withLeadingAndTrailingWhitespace_trimsCorrectly() {
        val input = "   \n  https://soundcloud.com/artist/track \t \n"
        assertEquals(
            "https://soundcloud.com/artist/track",
            SharedUrlExtractor.extractUrl(input)
        )
    }

    @Test
    fun extractUrl_withMultipleUrls_returnsFirstValidUrl() {
        val input = "Here is track 1 https://youtu.be/first and track 2 https://youtu.be/second"
        assertEquals(
            "https://youtu.be/first",
            SharedUrlExtractor.extractUrl(input)
        )
    }

    @Test
    fun extractUrl_withNullOrEmptyOrBlank_returnsNull() {
        assertNull(SharedUrlExtractor.extractUrl(null))
        assertNull(SharedUrlExtractor.extractUrl(""))
        assertNull(SharedUrlExtractor.extractUrl("    \n\t"))
    }

    @Test
    fun extractUrl_fromInstagramSharedTextWithCaption_extractsReelUrl() {
        val input = "Watch this reel by @creator on Instagram https://www.instagram.com/reel/DCqQkQ3I123/?igsh=MW55ZGFuM291Ym1nbQ=="
        assertEquals(
            "https://www.instagram.com/reel/DCqQkQ3I123/?igsh=MW55ZGFuM291Ym1nbQ==",
            SharedUrlExtractor.extractUrl(input)
        )
    }

    @Test
    fun extractUrl_fromInstagramReelWithTrailingPunctuation_cleansPunctuation() {
        val input = "Check this out: https://www.instagram.com/reel/DCqQkQ3I123/."
        assertEquals(
            "https://www.instagram.com/reel/DCqQkQ3I123/",
            SharedUrlExtractor.extractUrl(input)
        )
    }

    @Test
    fun extractUrl_prioritizesSupportedMediaUrlOverGenericLink() {
        val input = "Shared via https://instagram.com/about. Watch reel: https://www.instagram.com/reel/DCqQkQ3I123/"
        assertEquals(
            "https://www.instagram.com/reel/DCqQkQ3I123/",
            SharedUrlExtractor.extractUrl(input)
        )
    }

    @Test
    fun extractUrl_withNoHttpUrl_returnsNull() {
        assertNull(SharedUrlExtractor.extractUrl("Just a friendly message between users."))
        assertNull(SharedUrlExtractor.extractUrl("ftp://files.example.com/audio.mp3"))
        assertNull(SharedUrlExtractor.extractUrl("file:///storage/emulated/0/Music/track.mp3"))
        assertNull(SharedUrlExtractor.extractUrl("content://media/external/audio/media/123"))
    }
}
