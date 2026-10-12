package com.example.privatemusiclibrary.domain.identity

import android.content.Context
import android.net.Uri
import com.example.privatemusiclibrary.domain.extraction.SupportedSourceResolver
import com.example.privatemusiclibrary.domain.extraction.SourceResolutionResult
import com.example.privatemusiclibrary.domain.extraction.MediaPlatform
import com.example.privatemusiclibrary.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object TrackIdentityResolver {

    /**
     * Extracts a stable logical track key for a track.
     * Priority:
     * 1. Normalized provider identifier (e.g. youtube:<id>, instagram:<id>, soundcloud:<id>).
     * 2. SHA-256 audio content hash computed off the main thread for local files without a provider ID.
     * 3. Fallback stable legacy key: legacy:<id>.
     */
    suspend fun resolveKey(track: Track, context: Context?): String {
        if (track.trackKey.isNotBlank()) {
            return track.trackKey
        }

        // 1. Try provider key
        val providerKey = resolveProviderKey(track.sourcePlatform, track.sourceId, track.sourceUrl)
        if (!providerKey.isNullOrBlank()) {
            return providerKey
        }

        // 2. Try content hash if local audio exists and context is provided
        if (context != null && track.localUri.isNotBlank()) {
            val contentHash = computeFileHash(context, track.localUri)
            if (!contentHash.isNullOrBlank()) {
                return contentHash
            }
        }

        // 3. Fallback legacy key
        return fallbackLegacyKey(track.id)
    }

    fun resolveProviderKey(
        sourcePlatform: String?,
        sourceId: String?,
        sourceUrl: String?
    ): String? {
        val platform = sourcePlatform?.lowercase()?.trim()
        val id = sourceId?.trim()

        if (!platform.isNullOrEmpty() && !id.isNullOrEmpty()) {
            return "$platform:$id"
        }

        if (!sourceUrl.isNullOrBlank()) {
            when (val res = SupportedSourceResolver.resolve(sourceUrl.trim())) {
                is SourceResolutionResult.Supported -> {
                    if (res.platform == MediaPlatform.DIRECT_AUDIO) {
                        return "direct:${sha256Hex(res.canonicalUrl)}"
                    }
                    val pName = res.platform.name.lowercase()
                    if (!res.mediaId.isNullOrBlank()) {
                        return "$pName:${res.mediaId}"
                    }
                    return "$pName:${sha256Hex(res.canonicalUrl)}"
                }
                else -> {
                    // Check direct URL sha256
                    return "direct:${sha256Hex(sourceUrl.trim())}"
                }
            }
        }

        return null
    }

    suspend fun computeFileHash(context: Context, localUri: String): String? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(localUri)
            val inputStream = if (uri.scheme == "file") {
                val path = uri.path ?: return@withContext null
                val file = File(path)
                if (!file.exists() || !file.canRead()) return@withContext null
                FileInputStream(file)
            } else {
                context.contentResolver.openInputStream(uri)
            } ?: return@withContext null

            inputStream.use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                val hashBytes = digest.digest()
                "hash:" + hashBytes.joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun fallbackLegacyKey(id: String): String {
        return "legacy:${id.trim()}"
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
