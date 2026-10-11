package com.example.privatemusiclibrary.data.extraction

/**
 * Classifies yt-dlp, network, and extraction errors into distinct, actionable
 * user-facing messages without misattributing public video failures to private or
 * login restrictions.
 */
object YtDlpErrorClassifier {

    /**
     * Sanitizes error messages by scrubbing cookies, tokens, credentials, and query secrets
     * before logging or reporting.
     */
    fun sanitizeMessage(message: String?): String {
        if (message.isNullOrBlank()) return "Unknown error"
        return message
            .replace(Regex("(?i)(cookie[s]?|authorization|token|bearer|key|password|client_secret)[=: ]+[^;\\s,]+"), "$1=REDACTED")
            .replace(Regex("https?://[^\\s]+"), "[URL]")
    }

    /**
     * Determines whether an error genuinely indicates private, members-only, or account-restricted content.
     */
    fun isTrulyPrivateOrLoginRequired(raw: String): Boolean {
        // "Sign in to confirm you’re not a bot" is a bot challenge, NOT a private video.
        if (raw.contains("not a bot") || raw.contains("confirm you’re not a bot") || raw.contains("confirm you're not a bot")) {
            return false
        }

        return raw.contains("private video") ||
                raw.contains("this video is private") ||
                raw.contains("members-only") ||
                raw.contains("only available to channel members") ||
                raw.contains("sign in if you've been granted access") ||
                raw.contains("sign in to confirm your age") ||
                raw.contains("permission to view this") ||
                (raw.contains("login required") && !raw.contains("bot"))
    }

    /**
     * Maps an extraction exception and its cause chain to an accurate, actionable message.
     */
    fun classifyError(e: Throwable): String {
        val messages = mutableListOf<String>()
        var curr: Throwable? = e
        while (curr != null) {
            curr.message?.let { messages.add(it) }
            curr = curr.cause
        }
        val raw = messages.joinToString(" ").lowercase()

        return when {
            // 1. Engine initialization failure
            raw.contains("not initialized") || raw.contains("cannot find bin") ||
            (raw.contains("no such file") && raw.contains("yt-dlp")) ->
                "Audio extraction engine is not initialized. Please restart the app or reinstall."

            // 2. Network / DNS / Connection issues
            raw.contains("errno 7") || raw.contains("no address associated") ||
            raw.contains("unable to resolve host") || raw.contains("unknownhost") ||
            raw.contains("network is unreachable") || raw.contains("connection refused") ||
            raw.contains("timed out") || raw.contains("sockettimeout") ||
            raw.contains("failed to connect") || raw.contains("temporary failure in name resolution") ->
                "Network error: unable to reach the source. Please check your internet connection."

            // 3. HTTP 403 Forbidden / SABR streaming restrictions (NOT private content)
            raw.contains("http error 403") || raw.contains("403: forbidden") ||
            raw.contains("sabr streaming") || raw.contains("missing a url") ->
                "Access forbidden (HTTP 403) by media provider. The extraction engine may be outdated or restricted."

            // 4. Rate limiting / HTTP 429
            raw.contains("rate-limit") || raw.contains("too many requests") ||
            raw.contains("http error 429") || raw.contains("429: too many") ->
                "The media source is temporarily limiting requests (HTTP 429). Please try again in a few minutes."

            // 5. Bot verification / anti-scraping challenge
            raw.contains("not a bot") || raw.contains("bot verification") ->
                "YouTube is requiring bot verification for this request. Please try again shortly or check for app updates."

            // 5. Genuine private / restricted / account-required content
            isTrulyPrivateOrLoginRequired(raw) ->
                "This content is private, age-restricted, or requires account authentication."

            // 6. Content unavailable, removed, or geo-restricted
            raw.contains("video unavailable") || raw.contains("this video has been removed") ||
            raw.contains("does not exist") || raw.contains("not found") || raw.contains("http error 404") ||
            raw.contains("not made this video available in your country") || raw.contains("geo-restricted") ||
            raw.contains("blocked in your country") ->
                "This media is unavailable, removed, or geo-restricted in your region."

            // 7. Metadata / JSON parsing failure (e.g. Jackson or extractor format changes)
            raw.contains("unable to parse video information") || raw.contains("failed to fetch video information") ->
                "Unable to parse media metadata from this link. The media format may have changed."

            // 8. Unsupported URL or format
            raw.contains("unsupported url") || raw.contains("no suitable format") ||
            raw.contains("requested format not available") || raw.contains("no video formats found") ->
                "This URL or format is not supported for audio extraction."

            // 9. Generic extraction fallback
            else -> {
                val snippet = e.message?.lines()?.firstOrNull()?.trim()?.take(100)
                if (!snippet.isNullOrBlank() && !snippet.equals("null", ignoreCase = true)) {
                    "Audio extraction failed: $snippet"
                } else {
                    "Audio extraction failed. Please check the link and try again."
                }
            }
        }
    }
}
