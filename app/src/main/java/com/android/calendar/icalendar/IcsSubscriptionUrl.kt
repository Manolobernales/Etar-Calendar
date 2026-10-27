package com.android.calendar.icalendar

import android.net.Uri

/**
 * Validation helpers for remote iCalendar subscriptions.
 *
 * URLs are treated as untrusted input. Only HTTPS is accepted by default.
 * Fetching and synchronizing subscription contents is implemented separately.
 */
object IcsSubscriptionUrl {
    fun normalizeAndValidate(rawUrl: String): String? {
        val candidate = rawUrl.trim()
        if (candidate.isEmpty() || candidate.length > MAX_URL_LENGTH) return null

        val uri = try {
            Uri.parse(candidate)
        } catch (_: Exception) {
            return null
        }

        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        if (uri.fragment != null) return null

        return uri.normalizeScheme().toString()
    }

    private const val MAX_URL_LENGTH = 4096
}
