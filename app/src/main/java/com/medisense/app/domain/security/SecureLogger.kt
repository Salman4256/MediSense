package com.medisense.app.domain.security

import com.medisense.app.BuildConfig
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Centralized, privacy-safe logger for MediSense.
 * Enforces sanitization of sensitive medical and authentication information,
 * and restricts debug outputs to DEBUG builds.
 */
object SecureLogger {

    private val SENSITIVE_PATTERNS = listOf(
        Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*[^\\s,;]+"),
        Pattern.compile("(?i)(bearer\\s+[a-zA-Z0-9._~+/-]+)"),
        Pattern.compile("(?i)(api[_-]?key|secret|token|anon[_-]?key)\\s*[=:]\\s*[^\\s,;]+"),
        Pattern.compile("(?i)(sbp_[a-zA-Z0-9]+|eyJ[a-zA-Z0-9._-]+)")
    )

    /**
     * Sanitizes a log string by redacting potential credentials or tokens.
     */
    fun sanitize(message: String?): String {
        if (message == null) return ""
        var sanitized = message
        for (pattern in SENSITIVE_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("[REDACTED_SECRET]")
        }
        return sanitized
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
