/*
 * Copyright (C) 2026 The Etar Project contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.android.calendar.icalendar

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Downloads registered feeds over HTTPS and keeps a bounded local copy.
 * Event reconciliation into CalendarContract is intentionally separate so a failed
 * download can never delete or corrupt events already shown by the calendar.
 */
class IcsSubscriptionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val urls = IcsSubscriptionStore.getAll(applicationContext)
        if (urls.isEmpty()) return Result.success()

        var hadFailure = false
        for (url in urls) {
            try {
                val safeUrl = IcsSubscriptionUrl.normalizeAndValidate(url) ?: continue
                val connection = (URL(safeUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "text/calendar, text/plain;q=0.9, */*;q=0.1")
                }
                val body = try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP ${connection.responseCode}")
                    val stream = connection.inputStream
                    val bytes = stream.use { it.readNBytes(MAX_FEED_BYTES + 1) }
                    if (bytes.size > MAX_FEED_BYTES) throw IllegalStateException("ICS feed too large")
                    bytes.toString(Charsets.UTF_8)
                } finally {
                    connection.disconnect()
                }
                if (!body.contains("BEGIN:VCALENDAR") || !body.contains("END:VCALENDAR")) {
                    throw IllegalStateException("Response is not an iCalendar feed")
                }
                IcsSubscriptionStore.saveLastDownload(applicationContext, safeUrl, body)
                IcsSubscriptionImporter.import(applicationContext, safeUrl, body)
            } catch (_: Exception) {
                hadFailure = true
            }
        }
        return if (hadFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val MAX_FEED_BYTES = 2 * 1024 * 1024
        private const val UNIQUE_WORK = "ics_subscription_refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<IcsSubscriptionWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun refreshNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<IcsSubscriptionWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
