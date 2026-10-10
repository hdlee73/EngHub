package com.hdlee73.englishstudy.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Downloads the APK of a newer release inside the app and opens the system installer on it.
 * Android never lets an app install silently, so the user still taps "Install" once.
 */
object UpdateInstaller {
    private val client by lazy { OkHttpClient.Builder().callTimeout(10, TimeUnit.MINUTES).readTimeout(60, TimeUnit.SECONDS).build() }

    private fun dir(context: Context) = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Downloads the release APK; [onProgress] gets 0..1 (or -1 when the size is unknown). Throws on failure. */
    suspend fun download(context: Context, release: UpdateChecker.Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: error("이 릴리스에는 APK 파일이 없어요")
        val folder = dir(context)
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "EngHub-${release.version}.apk")
        val partial = File(folder, "download.part")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("다운로드에 실패했어요 (${response.code})")
            val body = response.body ?: error("다운로드에 실패했어요")
            val total = body.contentLength()
            var done = 0L
            body.byteStream().use { input ->
                partial.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        onProgress(if (total > 0) done.toFloat() / total else -1f)
                    }
                }
            }
            if (total > 0 && done != total) error("다운로드가 중간에 끊겼어요")
        }
        if (!partial.renameTo(target)) error("파일을 저장하지 못했어요")
        target
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system settings page where the user allows EngHub to install apps (first time only). */
    fun openInstallPermissionSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Opens the system installer on the downloaded APK. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
