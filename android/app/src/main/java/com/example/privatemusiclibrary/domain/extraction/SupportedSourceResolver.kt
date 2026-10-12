package com.example.privatemusiclibrary.domain.extraction

import java.net.URI

enum class MediaPlatform {
    YOUTUBE,
    INSTAGRAM,
    SOUNDCLOUD,
    DIRECT_AUDIO,
    UNKNOWN
}

sealed class SourceResolutionResult {
    data class Supported(
        val platform: MediaPlatform,
        val platformDisplayName: String,
        val canonicalUrl: String,
        val mediaId: String? = null
    ) : SourceResolutionResult()

    data class Unsupported(
        val reason: String,
        val platform: MediaPlatform = MediaPlatform.UNKNOWN
    ) : SourceResolutionResult()
}

/**
 * Maintainable and extensible URL resolver for media sources.
 *
 * Responsibilities:
 * 1. Safe host matching using URI parser (preventing lookalike or malicious domains).
 * 2. Path analysis for Instagram Reels, Posts, and IGTV.
 * 3. Distinguishing extractable media from non-media pages (e.g. profile, stories, explore).
 * 4. Cleaning tracking parameters while preserving media identifiers.
 * 5. Formatting canonical URLs for downstream extraction engines.
 */
object SupportedSourceResolver {

    private val INSTAGRAM_REEL_PATH_REGEX = "^/(?:reel|reels)/([A-Za-z0-9_-]+)/?.*$".toRegex()
    private val INSTAGRAM_POST_PATH_REGEX = "^/p/([A-Za-z0-9_-]+)/?.*$".toRegex()
    private val INSTAGRAM_TV_PATH_REGEX = "^/tv/([A-Za-z0-9_-]+)/?.*$".toRegex()

    private val DIRECT_AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus"
    )

    fun resolve(rawUrl: String): SourceResolutionResult {
        val trimmed = trimTrailingPunctuation(rawUrl)
        if (trimmed.isBlank()) {
            return SourceResolutionResult.Unsupported("URL cannot be empty")
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return SourceResolutionResult.Unsupported("Invalid URL syntax")
        }

        val scheme = uri.scheme?.lowercase() ?: return SourceResolutionResult.Unsupported("Only HTTP and HTTPS URLs are supported")
        if (scheme != "http" && scheme != "https") {
            return SourceResolutionResult.Unsupported("URL scheme '$scheme' is not supported")
        }

        val host = uri.host?.lowercase() ?: return SourceResolutionResult.Unsupported("URL host is missing")

        // 1. YouTube
        if (isYouTubeHost(host)) {
            return resolveYouTube(uri, trimmed)
        }

        // 2. Instagram
        if (isInstagramHost(host)) {
            return resolveInstagram(uri, trimmed)
        }

        // 3. SoundCloud
        if (isSoundCloudHost(host)) {
            return SourceResolutionResult.Supported(
                platform = MediaPlatform.SOUNDCLOUD,
                platformDisplayName = "SoundCloud",
                canonicalUrl = trimmed
            )
        }

        // 4. Direct Audio File
        val path = uri.path?.lowercase() ?: ""
        val extension = path.substringAfterLast('.', "")
        if (DIRECT_AUDIO_EXTENSIONS.contains(extension)) {
            return SourceResolutionResult.Supported(
                platform = MediaPlatform.DIRECT_AUDIO,
                platformDisplayName = "Direct Audio",
                canonicalUrl = trimmed
            )
        }

        return SourceResolutionResult.Unsupported("This source isn't supported yet.")
    }

    fun isSupported(url: String): Boolean {
        return resolve(url) is SourceResolutionResult.Supported
    }

    private fun trimTrailingPunctuation(raw: String): String {
        var candidate = raw.trim()
        while (candidate.isNotEmpty() && candidate.last() in ".,;!?:)]}>\"'") {
            if (candidate.last() == ')' && candidate.count { it == '(' } >= candidate.count { it == ')' }) {
                break
            }
            if (candidate.last() == ']' && candidate.count { it == '[' } >= candidate.count { it == ']' }) {
                break
            }
            candidate = candidate.dropLast(1)
        }
        return candidate
    }

    private fun isInstagramHost(host: String): Boolean {
        return host == "instagram.com" || host.endsWith(".instagram.com") ||
               host == "instagr.am" || host.endsWith(".instagr.am") ||
               host == "ddinstagram.com"
    }

    private fun isYouTubeHost(host: String): Boolean {
        return host == "youtube.com" || host.endsWith(".youtube.com") ||
               host == "youtu.be" || host.endsWith(".youtu.be")
    }

    private fun isSoundCloudHost(host: String): Boolean {
        return host == "soundcloud.com" || host.endsWith(".soundcloud.com")
    }

    private fun resolveInstagram(uri: URI, originalUrl: String): SourceResolutionResult {
        val path = uri.path ?: ""

        val reelMatch = INSTAGRAM_REEL_PATH_REGEX.matchEntire(path)
        if (reelMatch != null) {
            val id = reelMatch.groupValues[1]
            return SourceResolutionResult.Supported(
                platform = MediaPlatform.INSTAGRAM,
                platformDisplayName = "Instagram",
                canonicalUrl = "https://www.instagram.com/reel/$id/",
                mediaId = id
            )
        }

        val postMatch = INSTAGRAM_POST_PATH_REGEX.matchEntire(path)
        if (postMatch != null) {
            val id = postMatch.groupValues[1]
            return SourceResolutionResult.Supported(
                platform = MediaPlatform.INSTAGRAM,
                platformDisplayName = "Instagram",
                canonicalUrl = "https://www.instagram.com/p/$id/",
                mediaId = id
            )
        }

        val tvMatch = INSTAGRAM_TV_PATH_REGEX.matchEntire(path)
        if (tvMatch != null) {
            val id = tvMatch.groupValues[1]
            return SourceResolutionResult.Supported(
                platform = MediaPlatform.INSTAGRAM,
                platformDisplayName = "Instagram",
                canonicalUrl = "https://www.instagram.com/tv/$id/",
                mediaId = id
            )
        }

        // Non-media paths
        val cleanPath = path.trim('/').lowercase()
        return when {
            cleanPath.startsWith("stories") -> SourceResolutionResult.Unsupported(
                "Instagram Stories cannot be extracted. Please share a public Reel or video post.",
                platform = MediaPlatform.INSTAGRAM
            )
            cleanPath.startsWith("explore") -> SourceResolutionResult.Unsupported(
                "Instagram Explore links cannot be extracted. Please share a specific Reel or video post.",
                platform = MediaPlatform.INSTAGRAM
            )
            cleanPath.startsWith("accounts") -> SourceResolutionResult.Unsupported(
                "Instagram account links cannot be extracted.",
                platform = MediaPlatform.INSTAGRAM
            )
            cleanPath.isNotBlank() && !cleanPath.contains('/') -> SourceResolutionResult.Unsupported(
                "Instagram profile pages do not contain extractable audio. Please share a specific Reel or video post.",
                platform = MediaPlatform.INSTAGRAM
            )
            else -> SourceResolutionResult.Unsupported(
                "This Instagram link does not point to a supported Reel or video post.",
                platform = MediaPlatform.INSTAGRAM
            )
        }
    }

    private fun resolveYouTube(uri: URI, originalUrl: String): SourceResolutionResult {
        var cleanUrl = originalUrl
        if (cleanUrl.contains("youtube.com/watch") || cleanUrl.contains("youtu.be/")) {
            cleanUrl = cleanUrl.substringBefore("&list=").substringBefore("?list=")
        }

        val videoId = extractYouTubeId(uri)

        return SourceResolutionResult.Supported(
            platform = MediaPlatform.YOUTUBE,
            platformDisplayName = "YouTube",
            canonicalUrl = cleanUrl,
            mediaId = videoId
        )
    }

    private fun extractYouTubeId(uri: URI): String? {
        val host = uri.host?.lowercase() ?: ""
        val path = uri.path ?: ""
        if (host == "youtu.be" || host.endsWith(".youtu.be")) {
            val id = path.trim('/').substringBefore('/')
            return if (id.isNotBlank()) id else null
        }
        if (path.startsWith("/shorts/")) {
            val id = path.removePrefix("/shorts/").trim('/').substringBefore('/')
            return if (id.isNotBlank()) id else null
        }
        if (path.startsWith("/embed/")) {
            val id = path.removePrefix("/embed/").trim('/').substringBefore('/')
            return if (id.isNotBlank()) id else null
        }
        if (path.startsWith("/v/")) {
            val id = path.removePrefix("/v/").trim('/').substringBefore('/')
            return if (id.isNotBlank()) id else null
        }
        val query = uri.query
        if (!query.isNullOrBlank()) {
            val params = query.split('&')
            for (param in params) {
                if (param.startsWith("v=")) {
                    val v = param.substringAfter("v=")
                    if (v.isNotBlank()) return v
                }
            }
        }
        return null
    }
}
