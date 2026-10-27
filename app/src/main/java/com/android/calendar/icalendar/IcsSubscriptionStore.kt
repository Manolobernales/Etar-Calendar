/*
 * Copyright (C) 2026 The Etar Project contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.android.calendar.icalendar

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

/** Small persistent registry for remote ICS URLs. Network synchronization is handled separately. */
object IcsSubscriptionStore {
    private const val PREFS = "ics_url_subscriptions"
    private const val KEY_URLS = "urls"

    fun getAll(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URLS, "[]")
            ?: "[]"
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).mapNotNull { json.optString(it).takeIf(String::isNotBlank) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, url: String): Boolean {
        val urls = getAll(context)
        if (url in urls) return false
        return save(context, urls + url)
    }

    fun remove(context: Context, url: String): Boolean {
        val removed = save(context, getAll(context).filterNot { it == url })
        if (removed) snapshotFile(context, url).delete()
        return removed
    }

    fun saveLastDownload(context: Context, url: String, body: String) {
        snapshotFile(context, url).writeText(body, Charsets.UTF_8)
    }

    fun getLastDownload(context: Context, url: String): String? =
        snapshotFile(context, url).takeIf { it.isFile }?.readText(Charsets.UTF_8)

    private fun snapshotFile(context: Context, url: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(context.filesDir, "ics_feed_$digest.ics")
    }

    private fun save(context: Context, urls: List<String>): Boolean {
        val json = JSONArray()
        urls.forEach(json::put)
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URLS, json.toString()).commit()
    }
}
