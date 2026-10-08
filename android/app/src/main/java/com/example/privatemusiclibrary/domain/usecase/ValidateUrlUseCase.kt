package com.example.privatemusiclibrary.domain.usecase

import com.example.privatemusiclibrary.domain.extraction.AudioExtractor
import javax.inject.Inject

class ValidateUrlUseCase @Inject constructor(
    private val extractors: Set<@JvmSuppressWildcards AudioExtractor>
) {
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    operator fun invoke(url: String): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult.Invalid("URL cannot be empty")
        }

        try {
            java.net.URL(url).toURI()
        } catch (e: Exception) {
            return ValidationResult.Invalid("Invalid URL syntax")
        }

        val lowerUrl = url.lowercase()
        if (lowerUrl.startsWith("file://") || 
            lowerUrl.startsWith("javascript:") || 
            lowerUrl.startsWith("data:")) {
            return ValidationResult.Invalid("URL scheme not supported")
        }
        
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            return ValidationResult.Invalid("Only HTTP and HTTPS URLs are supported")
        }

        val supported = extractors.any { it.supports(url) }
        if (!supported) {
            return ValidationResult.Invalid("Only supported URLs and content you are authorized to save can be imported. Supported types: Direct audio links (.mp3, .wav, etc)")
        }

        return ValidationResult.Valid
    }
}
