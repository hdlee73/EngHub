package com.hdlee73.englishstudy.listening

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * BBC Learning English "6 Minute English": a new episode comes out every week. A background job looks at the
 * programme page once a day, downloads episodes it has not fetched yet into Downloads/EngHub/6min and queues them
 * for the Listening tab's "6min" playlist folder (added the next time the player is open).
 */
object SixMinuteEnglish {
    const val GROUP_NAME = "6min"
    const val INDEX_URL = "https://www.bbc.co.uk/learningenglish/english/features/6-minute-english"
    private const val PREFS = "six_minute_english"
    private const val KEY_DONE = "done"
    private const val KEY_PENDING = "pending"
    private const val WORK = "six_minute_english"
    private const val WORK_NOW = "six_minute_english_now"

    data class Episode(val id: String, val url: String)

    private val episodePath = Regex("""/learningenglish/english/features/6-minute-english[\w-]*/ep-(\d{6})""")
    private val mp3Url = Regex("""https?://downloads\.bbc\.co\.uk/learningenglish/features/6min/[^"'\s<>\\]+?\.mp3""")
    private val idPrefix = Regex("""/(\d{6})_[^/]*$""")

    /** Episode pages linked from the programme page, newest first. */
    fun episodePages(html: String): List<Episode> =
        episodePath.findAll(html).map { Episode(it.groupValues[1], "https://www.bbc.co.uk" + it.value) }
            .distinctBy { it.id }.sortedByDescending { it.id }.toList()

    /** MP3 links found in [html] (an episode page or the programme page), newest first, preferring the "download" files. */
    fun mp3Links(html: String): List<Episode> =
        mp3Url.findAll(html).map { it.value.replaceFirst("http://", "https://") }.distinct()
            .mapNotNull { url -> idPrefix.find(url)?.groupValues?.get(1)?.let { Episode(it, url) } }
            .sortedWith(compareByDescending<Episode> { it.id }.thenByDescending { it.url.endsWith("_download.mp3") })
            .distinctBy { it.id }.toList()

    /** "261001_6_minute_english_why_does_heartbreak_hurt_so_much_download.mp3" → "6min 261001 Why does heartbreak hurt so much". */
    fun titleOf(url: String): String {
        val file = url.substringAfterLast('/').removeSuffix(".mp3")
        val id = file.substringBefore('_')
        val words = file.substringAfter('_').removeSuffix("_download")
            .replace(Regex("^6_minute_english_?"), "").replace('_', ' ').trim()
        val topic = words.replaceFirstChar { it.uppercase() }
        return if (topic.isEmpty()) "6 Minute English $id" else "6min $id $topic"
    }

    /** Runs the check once a day while the phone is online, and once right away. */
    fun schedule(context: Context) {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = WorkManager.getInstance(context)
        work.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS).setConstraints(online).build()
        )
    }

    /** Checks for a new episode now (from the playlist menu). */
    fun checkNow(context: Context) {
        val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<Worker>().setConstraints(online).setInputData(androidx.work.workDataOf("manual" to true)).build()
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Downloaded episodes not yet added to the playlist. */
    fun takePending(context: Context): List<TrackStore.Entry> {
        val p = prefs(context)
        val raw = p.getString(KEY_PENDING, null) ?: return emptyList()
        val list = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getJSONObject(it) }.map { TrackStore.Entry(it.getString("u"), it.getString("n")) }
        }.getOrDefault(emptyList())
        p.edit().remove(KEY_PENDING).apply()
        return list
    }

    fun hasPending(context: Context): Boolean = prefs(context).contains(KEY_PENDING)

    @Synchronized
    private fun addPending(context: Context, entry: TrackStore.Entry, id: String) {
        val p = prefs(context)
        val array = runCatching { JSONArray(p.getString(KEY_PENDING, "[]")) }.getOrDefault(JSONArray())
        array.put(JSONObject().put("u", entry.uri).put("n", entry.name))
        val done = p.getStringSet(KEY_DONE, emptySet()).orEmpty() + id
        p.edit().putString(KEY_PENDING, array.toString()).putStringSet(KEY_DONE, done).apply()
    }

    class Worker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
        private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            try {
                val got = fetchNew(applicationContext)
                if (got.isNotEmpty()) notifyDownloaded(got)
                else if (inputData.getBoolean("manual", false)) notify("새로 올라온 회차가 없어요.")
                Result.success()
            } catch (e: Exception) {
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }

        private fun get(url: String): String = http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) EngHub").build())
            .execute().use { r -> if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}"); r.body!!.string() }

        private fun fetchNew(context: Context): List<String> {
            val done = prefs(context).getStringSet(KEY_DONE, emptySet()).orEmpty()
            val index = get(INDEX_URL)
            // The newest episodes: the programme page links each episode page, and each episode page has the MP3 download link.
            val candidates = linkedMapOf<String, String>()
            mp3Links(index).forEach { candidates[it.id] = it.url }
            for (page in episodePages(index).take(3)) {
                if (page.id in done || page.id in candidates) continue
                val mp3 = runCatching { mp3Links(get(page.url)).firstOrNull() }.getOrNull() ?: continue
                candidates[page.id] = mp3.url
            }
            val newest = candidates.keys.sortedDescending()
            // The first time only the latest episode is fetched, not the whole archive; afterwards everything newer than what we have.
            val lastDone = done.maxOrNull()
            val wanted = newest.filter { it !in done && (lastDone == null || it > lastDone) }.let { if (lastDone == null) it.take(1) else it.take(3) }
            val fetched = mutableListOf<String>()
            for (id in wanted.sorted()) {
                val url = candidates.getValue(id)
                val title = titleOf(url)
                val uri = download(context, url) ?: continue
                addPending(context, TrackStore.Entry(uri, title), id)
                fetched += title
            }
            return fetched
        }

        /** Saves the MP3 to Downloads/EngHub/6min and returns its address. */
        private fun download(context: Context, url: String): String? {
            val name = url.substringAfterLast('/')
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EngHub/6min")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            try {
                http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) EngHub").build()).execute().use { r ->
                    if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
                    resolver.openOutputStream(uri)?.use { out -> r.body!!.byteStream().copyTo(out) } ?: throw java.io.IOException("저장할 수 없습니다.")
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri.toString()
        }

        private fun notifyDownloaded(titles: List<String>) =
            notify(if (titles.size == 1) "‘${titles[0]}’을(를) 6min 재생목록에 받았어요." else "새 회차 ${titles.size}개를 6min 재생목록에 받았어요.")

        private fun notify(message: String) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "six_minute_english"
            manager.createNotificationChannel(NotificationChannel(channelId, "6 Minute English 자동 다운로드", NotificationManager.IMPORTANCE_LOW))
            val notification = android.app.Notification.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("6 Minute English")
                .setContentText(message)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(3201, notification) }
        }
    }
}
