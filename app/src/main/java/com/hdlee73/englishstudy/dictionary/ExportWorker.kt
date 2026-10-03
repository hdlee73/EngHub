package com.hdlee73.englishstudy.dictionary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExportWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uriText = inputData.getString(KEY_URI) ?: return@withContext Result.failure()
        val format = inputData.getInt(KEY_FORMAT, 1)
        try {
            val bytes = ExportWorkbook.make(
                SavedSort.of(applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("savedSort", null))
                    .apply(EntryDb(applicationContext).all(), { it.word }, { it.id }), format)
            applicationContext.contentResolver.openOutputStream(Uri.parse(uriText))?.use { it.write(bytes) }
                ?: return@withContext Result.retry()
            notifyComplete("단어장 파일을 저장했습니다.")
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notifyComplete(message: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "exports"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "단어장 파일 저장", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(applicationContext, channelId)
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(applicationContext)
        }.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("영어단어장")
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify(3101, notification)
    }

    companion object {
        const val KEY_URI = "uri"
        const val KEY_FORMAT = "format"
    }
}
