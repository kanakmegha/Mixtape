package com.example.privatemusiclibrary.util

import java.net.URI

object SharedUrlExtractor {
    private val URL_REGEX = "(https?://[^\\s<>\"'{}|\\\\^`]+)".toRegex()

    /**
     * Extracts a valid HTTP/HTTPS URL from text shared from other applications.
     * Trims common surrounding punctuation such as periods, commas, quotes, and brackets.
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = URL_REGEX.find(text) ?: return null
        var candidate = match.value

        // Trim trailing punctuation commonly attached by natural language or punctuation
        while (candidate.isNotEmpty() && candidate.last() in ".,;!?:)]}>\"'") {
            // Keep closing parenthesis if balanced with opening parentheses in URL
            if (candidate.last() == ')' && candidate.count { it == '(' } >= candidate.count { it == ')' }) {
                break
            }
            if (candidate.last() == ']' && candidate.count { it == '[' } >= candidate.count { it == ']' }) {
                break
            }
            candidate = candidate.dropLast(1)
        }

        // Validate basic URI structure
        return try {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                candidate.ifBlank { null }
            } else {
                null
            }
        } catch (_: Exception) {
            // If URI parsing fails, verify scheme prefix as fallback
            val lower = candidate.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                candidate.ifBlank { null }
            } else {
                null
            }
        }
    }
}
