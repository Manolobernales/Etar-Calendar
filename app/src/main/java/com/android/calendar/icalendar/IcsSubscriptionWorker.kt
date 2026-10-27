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
                val body = downloadFeed(safeUrl)
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

    /**
     * Follow a small number of HTTPS redirects explicitly. Redirect targets are
     * revalidated before connecting so a feed cannot downgrade to HTTP or embed
     * credentials in a URL. Response bodies remain size-bounded.
     */
    private fun downloadFeed(initialUrl: String): String {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "text/calendar, text/plain;q=0.9, */*;q=0.1")
            }
            try {
                connection.connect()
                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    if (redirectCount >= MAX_REDIRECTS) {
                        throw IllegalStateException("Too many redirects")
                    }
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Redirect without Location")
                    val target = URL(URL(currentUrl), location).toString()
                    currentUrl = IcsSubscriptionUrl.normalizeAndValidate(target)
                        ?: throw IllegalStateException("Redirect target must be a valid HTTPS URL")
                    return@repeat
                }
                if (status !in 200..299) throw IllegalStateException("HTTP $status")
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_FEED_BYTES) {
                            throw IllegalStateException("ICS feed too large")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                return bytes.toString(Charsets.UTF_8)
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("Unable to download feed")
    }

    companion object {
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val MAX_REDIRECTS = 3
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
