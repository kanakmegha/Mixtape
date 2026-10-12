package com.example.privatemusiclibrary.util

import com.example.privatemusiclibrary.domain.extraction.SupportedSourceResolver
import java.net.URI

object SharedUrlExtractor {
    private val URL_REGEX = "(https?://[^\\s<>\"'{}|\\\\^`]+)".toRegex()

    /**
     * Extracts a valid HTTP/HTTPS URL from text shared from other applications.
     * Trims common surrounding punctuation such as periods, commas, quotes, and brackets.
     * If multiple URLs are present, prioritizes URLs supported by media extractors.
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null

        val candidates = URL_REGEX.findAll(text).mapNotNull { match ->
            cleanCandidate(match.value)
        }.toList()

        if (candidates.isEmpty()) return null

        // 1. Prioritize a URL recognized as a supported source (e.g. Instagram reel, YouTube)
        val supportedMatch = candidates.firstOrNull { SupportedSourceResolver.isSupported(it) }
        if (supportedMatch != null) {
            return supportedMatch
        }

        // 2. Otherwise return the first valid HTTP/HTTPS URL candidate
        return candidates.firstOrNull()
    }

    private fun cleanCandidate(raw: String): String? {
        var candidate = raw.trim()

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

        if (candidate.isBlank()) return null

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
            val lower = candidate.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                candidate.ifBlank { null }
            } else {
                null
            }
        }
    }
}
