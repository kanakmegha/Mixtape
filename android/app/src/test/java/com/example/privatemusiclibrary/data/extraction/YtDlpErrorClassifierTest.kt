package com.example.privatemusiclibrary.data.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpErrorClassifierTest {

    @Test
    fun isTrulyPrivateOrLoginRequired_doesNotMisclassifyBotChallenge() {
        val botMessage1 = "ERROR: [youtube] dQw4w9WgXcQ: Sign in to confirm you’re not a bot. This helps protect our community."
        val botMessage2 = "ERROR: [youtube] abc: Sign in to confirm you're not a bot"

        assertFalse(YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired(botMessage1.lowercase()))
        assertFalse(YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired(botMessage2.lowercase()))
    }

    @Test
    fun isTrulyPrivateOrLoginRequired_doesNotMisclassifyGenericOrHttpErrors() {
        assertFalse(YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired("unable to parse video information"))
        assertFalse(YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired("http error 403: forbidden"))
        assertFalse(YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired("http error 429: too many requests"))
        assertFalse(YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired("video unavailable"))
    }

    @Test
    fun isTrulyPrivateOrLoginRequired_correctlyIdentifiesGenuinePrivateContent() {
        assertTrue(
            YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired(
                "ERROR: [youtube] 123: Private video. Sign in if you've been granted access to this video".lowercase()
            )
        )
        assertTrue(
            YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired(
                "Join this channel to get access to members-only content".lowercase()
            )
        )
        assertTrue(
            YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired(
                "Sign in to confirm your age. This video may be inappropriate for some users.".lowercase()
            )
        )
    }

    @Test
    fun classifyError_botVerification_reportsBotChallengeNotPrivate() {
        val exception = Exception("ERROR: [youtube] dQw4w9WgXcQ: Sign in to confirm you’re not a bot. This helps protect our community.")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("bot verification"))
        assertFalse(result.contains("private", ignoreCase = true))
    }

    @Test
    fun classifyError_networkFailure_reportsNetworkError() {
        val exception = Exception("Unable to download webpage: <urlopen error [Errno 7] No address associated with hostname>")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("Network error"))
        assertTrue(result.contains("internet connection"))
    }

    @Test
    fun classifyError_rateLimit_reports429() {
        val exception = Exception("HTTP Error 429: Too Many Requests")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("429"))
        assertTrue(result.contains("limiting requests"))
    }

    @Test
    fun classifyError_http403_reportsForbiddenNotPrivate() {
        val exception = Exception("ERROR: unable to download video data: HTTP Error 403: Forbidden")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("HTTP 403"))
        assertFalse(result.contains("private", ignoreCase = true))
    }

    @Test
    fun classifyError_sabrStreaming_reportsForbiddenOrOutdatedNotPrivate() {
        val exception = Exception("Some web client https formats have been skipped as they are missing a url. YouTube is forcing SABR streaming for this client.")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("HTTP 403"))
        assertFalse(result.contains("private", ignoreCase = true))
    }

    @Test
    fun classifyError_metadataParsingFailure_reportsFormatChangedNotPrivate() {
        val exception = Exception("Unable to parse video information")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("Unable to parse media metadata"))
        assertFalse(result.contains("private", ignoreCase = true))
    }

    @Test
    fun classifyError_removedVideo_reportsUnavailable() {
        val exception = Exception("ERROR: [youtube] 123: Video unavailable. This video has been removed by the uploader")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("unavailable, removed, or geo-restricted"))
    }

    @Test
    fun classifyError_engineNotInitialized_reportsEngineIssue() {
        val exception = IllegalStateException("youtube-dl is not initialized")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("engine is not initialized"))
    }

    @Test
    fun sanitizeMessage_scrubsSensitiveTokensAndUrls() {
        val raw = "Failed request: cookie=session_12345; user=tester with token=secret_jwt_abc to https://backend.app/api"
        val sanitized = YtDlpErrorClassifier.sanitizeMessage(raw)

        assertFalse(sanitized.contains("session_12345"))
        assertFalse(sanitized.contains("secret_jwt_abc"))
        assertFalse(sanitized.contains("https://backend.app/api"))
        assertTrue(sanitized.contains("REDACTED"))
        assertTrue(sanitized.contains("[URL]"))
    }

    @Test
    fun classifyError_instagramPrivateAccount_reportsPrivateActionableMessage() {
        val exception = Exception("ERROR: [Instagram] DCqQkQ3I123: This account is private. Login required.")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("private and cannot be accessed without login"))
    }

    @Test
    fun classifyError_instagramPhotoOnlyPost_reportsPhotoOnlyMessage() {
        val exception = Exception("ERROR: [Instagram] DCqQkQ3I123: Post does not contain a video")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("contains only photos or images without an extractable audio stream"))
    }

    @Test
    fun classifyError_instagramDeletedPost_reportsUnavailableMessage() {
        val exception = Exception("ERROR: [Instagram] DCqQkQ3I123: This post may have been deleted")
        val result = YtDlpErrorClassifier.classifyError(exception)

        assertTrue(result.contains("unavailable, removed, or geo-restricted"))
    }
}
