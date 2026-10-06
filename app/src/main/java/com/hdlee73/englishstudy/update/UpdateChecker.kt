package com.hdlee73.englishstudy.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Looks at the latest GitHub release of EngHub and tells the user when it is newer than the installed app: a
 * notification (once per new version) and a notice in the app info dialog. Runs when the app opens and once a day.
 */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/hdlee73/EngHub/releases/latest"
    const val RELEASES_URL = "https://github.com/hdlee73/EngHub/releases"
    private const val WORK = "update_check"
    private const val PREFS = "update_check"
    private const val KEY_VERSION = "latest_version"
    private const val KEY_URL = "latest_url"
    private const val KEY_NOTIFIED = "notified_version"
    private const val CHANNEL = "app_update"

    data class Release(val version: String, val url: String)

    private val client by lazy { OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** "v1.19.1" -> [1, 19, 1]; anything unparsable counts as 0. */
    fun parts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(latest: String, installed: String): Boolean {
        val a = parts(latest)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The newer release found by the last check, or null when the app is up to date (or nothing was checked yet). */
    fun available(context: Context): Release? {
        val p = prefs(context)
        val version = p.getString(KEY_VERSION, null) ?: return null
        if (!isNewer(version, installedVersion(context))) return null
        return Release(version, p.getString(KEY_URL, null) ?: RELEASES_URL)
    }

    /** Asks GitHub for the latest release and remembers it. Returns the newer release, or null if up to date or offline. */
    suspend fun check(context: Context): Release? = withContext(Dispatchers.IO) {
        val json = runCatching {
            client.newCall(Request.Builder().url(API).header("Accept", "application/vnd.github+json").build()).execute().use { response ->
                if (!response.isSuccessful) null else JSONObject(response.body?.string().orEmpty())
            }
        }.getOrNull() ?: return@withContext available(context)
        val tag = json.optString("tag_name")
        if (tag.isBlank() || json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext available(context)
        prefs(context).edit().putString(KEY_VERSION, tag).putString(KEY_URL, json.optString("html_url").ifBlank { RELEASES_URL }).apply()
        available(context)
    }

    /** Checks now and posts a notification the first time a given newer version is seen. */
    suspend fun checkAndNotify(context: Context) {
        val release = check(context) ?: return
        val p = prefs(context)
        if (p.getString(KEY_NOTIFIED, null) == release.version) return
        if (notify(context, release)) p.edit().putString(KEY_NOTIFIED, release.version).apply()
    }

    private fun notify(context: Context, release: Release): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "앱 업데이트 알림", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(Intent.ACTION_VIEW, Uri.parse(release.url)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("EngHub 업데이트가 있어요")
            .setContentText("새 버전 ${release.version.removePrefix("v")}이(가) 나왔습니다. 눌러서 받아 보세요.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return runCatching { manager.notify(3301, notification) }.isSuccess && manager.areNotificationsEnabled()
    }

    /** Runs the check once a day while the phone is online. */
    fun schedule(context: Context) {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS).setConstraints(online).build()
        )
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            checkAndNotify(applicationContext)
            return Result.success()
        }
    }
}
