/*
 * Copyright (C) 2026 The Etar Project contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.android.calendar.icalendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Imports basic VEVENT fields into a dedicated local calendar for each feed.
 * Existing events are matched by CUSTOM_APP_URI so repeated refreshes update rather
 * than duplicate them. Unsupported or malformed events are skipped safely.
 */
object IcsSubscriptionImporter {
    fun import(context: Context, feedUrl: String, text: String): Int {
        val resolver = context.contentResolver
        val digest = sha256(feedUrl).take(24)
        val accountName = "etar_ics_$digest"
        val calendarId = findOrCreateCalendar(context, accountName, "ICS: ${hostName(feedUrl)}")
            ?: return 0

        val lines = unfold(text)
        val events = mutableListOf<Map<String, Pair<String, String>>>()
        var current: MutableMap<String, Pair<String, String>>? = null
        for (line in lines) {
            if (line.equals("BEGIN:VEVENT", true)) {
                current = linkedMapOf()
            } else if (line.equals("END:VEVENT", true)) {
                current?.let(events::add)
                current = null
            } else if (current != null) {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val left = line.substring(0, colon)
                    val name = left.substringBefore(';').uppercase(Locale.ROOT)
                    val params = left.substringAfter(';', "")
                    current[name] = line.substring(colon + 1) to params
                }
            }
        }

        var imported = 0
        for (event in events) {
            val uid = event["UID"]?.first?.takeIf { it.isNotBlank() } ?: continue
            val startValue = event["DTSTART"] ?: continue
            val start = parseDate(startValue.first, startValue.second) ?: continue
            val endValue = event["DTEND"]
            val allDay = startValue.second.contains("VALUE=DATE", true) ||
                Regex("^\\d{8}$").matches(startValue.first)
            val end = endValue?.let { parseDate(it.first, it.second) } ?: (start + if (allDay) 86_400_000L else 3_600_000L)
            if (end < start) continue

            val stableUri = "etar-ics://$digest/${sha256(uid)}"
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, unescape(event["SUMMARY"]?.first).ifBlank { "(Sin título)" })
                put(CalendarContract.Events.DESCRIPTION, unescape(event["DESCRIPTION"]?.first))
                put(CalendarContract.Events.EVENT_LOCATION, unescape(event["LOCATION"]?.first))
                put(CalendarContract.Events.DTSTART, start)
                put(CalendarContract.Events.DTEND, end)
                put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
                put(CalendarContract.Events.CUSTOM_APP_URI, stableUri)
                put(CalendarContract.Events.RRULE, event["RRULE"]?.first)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            }
            val existing = resolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.CUSTOM_APP_URI}=?",
                arrayOf(calendarId.toString(), stableUri),
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

            if (existing == null) {
                if (resolver.insert(CalendarContract.Events.CONTENT_URI, values) != null) imported++
            } else {
                resolver.update(
                    CalendarContract.Events.CONTENT_URI,
                    values,
                    "${CalendarContract.Events._ID}=?",
                    arrayOf(existing.toString())
                )
                imported++
            }
        }
        return imported
    }

    private fun findOrCreateCalendar(context: Context, accountName: String, displayName: String): Long? {
        val resolver = context.contentResolver
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME}=? AND ${CalendarContract.Calendars.ACCOUNT_TYPE}=?",
            arrayOf(accountName, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null
        )?.use { if (it.moveToFirst()) return it.getLong(0) }

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
            put(CalendarContract.Calendars.NAME, accountName)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4285F4.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_ROOT)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CAN_MODIFY_TIME_ZONE, 1)
            put(CalendarContract.Calendars.ALLOWED_REMINDERS, CalendarContract.Reminders.METHOD_ALERT.toString())
            put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES, CalendarContract.Attendees.TYPE_NONE.toString())
        }
        return resolver.insert(CalendarContract.Calendars.CONTENT_URI, values)?.lastPathSegment?.toLongOrNull()
    }

    private fun parseDate(value: String, params: String): Long? {
        val patterns = if (value.endsWith("Z")) listOf("yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmm'Z'")
            else if (value.length == 8) listOf("yyyyMMdd")
            else listOf("yyyyMMdd'T'HHmmss", "yyyyMMdd'T'HHmm")
        for (pattern in patterns) try {
            val format = SimpleDateFormat(pattern, Locale.ROOT).apply {
                isLenient = false
                timeZone = if (value.endsWith("Z")) TimeZone.getTimeZone("UTC")
                    else TimeZone.getDefault()
            }
            return format.parse(value)?.time
        } catch (_: Exception) { }
        return null
    }

    private fun unfold(text: String): List<String> {
        val result = mutableListOf<String>()
        for (line in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.drop(1)
            } else result.add(line)
        }
        return result
    }

    private fun unescape(value: String?): String =
        value.orEmpty().replace("\\n", "\n").replace("\\N", "\n")
            .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun hostName(url: String): String = try { URI(url).host ?: "remoto" } catch (_: Exception) { "remoto" }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
