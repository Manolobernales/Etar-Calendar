/*
 * Copyright (C) 2026 The Etar Project contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.android.calendar.icalendar

import android.content.Context
import org.json.JSONArray

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

    fun remove(context: Context, url: String): Boolean =
        save(context, getAll(context).filterNot { it == url })

    private fun save(context: Context, urls: List<String>): Boolean {
        val json = JSONArray()
        urls.forEach(json::put)
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URLS, json.toString()).commit()
    }
}
