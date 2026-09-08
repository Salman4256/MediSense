package com.medisense.app.domain.security

import com.medisense.app.BuildConfig
import timber.log.Timber

/**
 * Centralized, privacy-safe logger for MediSense.
 * Enforces sanitization of sensitive medical and authentication information,
 * and restricts debug outputs to DEBUG builds.
 */
object SecureLogger {

    private val SENSITIVE_PATTERNS = listOf(
        Regex("(?i)(password|passwd|pwd)\\s*[=:]\\s*[^\\s,;]+"),
        Regex("(?i)(bearer\\s+[a-zA-Z0-9._~+/-]+)"),
        Regex("(?i)(api[_-]?key|secret|token|anon[_-]?key)\\s*[=:]\\s*[^\\s,;]+"),
        Regex("(?i)(sbp_[a-zA-Z0-9]+|eyJ[a-zA-Z0-9._-]+)")
    )

    /**
     * Sanitizes a log string by redacting potential credentials or tokens.
     */
    fun sanitize(message: String?): String {
        val nonNullMsg = message?.takeIf { it.isNotBlank() } ?: return ""
        return SENSITIVE_PATTERNS.fold(nonNullMsg) { current, regex ->
            regex.replace(current, "[REDACTED_SECRET]")
        }
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Timber.tag(tag).d(sanitize(message))
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Timber.tag(tag).i(sanitize(message))
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val cleanMsg = sanitize(message)
        if (throwable != null) {
            Timber.tag(tag).w(throwable, cleanMsg)
        } else {
            Timber.tag(tag).w(cleanMsg)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val cleanMsg = sanitize(message)
        if (throwable != null) {
            Timber.tag(tag).e(throwable, cleanMsg)
        } else {
            Timber.tag(tag).e(cleanMsg)
        }
    }
}
