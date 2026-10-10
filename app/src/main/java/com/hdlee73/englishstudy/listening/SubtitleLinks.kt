package com.hdlee73.englishstudy.listening

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Subtitles made elsewhere in the app (DocVoice "MP3 → 문서" saved as SRT), remembered per audio file so the Listening
 * player picks them up by itself. An audio file is recognised by its address or, since the two tabs may reach the same file
 * through different addresses, by its file name.
 */
object SubtitleLinks {
    private const val PREFS = "subtitle_links"
    private val names = mutableMapOf<String, String>()

    /** Goes up whenever a new link is made, so the player knows to look again. */
    @Volatile var version = 0
        private set

    fun key(name: String): String = name.substringAfterLast('/').substringBeforeLast('.').trim().lowercase()

    /** Keeps a private copy of [srt] and links it to the audio file at [audioUri] named [audioName]. */
    fun remember(context: Context, audioUri: String, audioName: String, srt: ByteArray) {
        val dir = File(context.filesDir, "subtitles").apply { mkdirs() }
        val file = File(dir, key(audioName).replace(Regex("[^\\w.-]+"), "_").take(80).ifBlank { "audio" } + "_" + System.currentTimeMillis() + ".srt")
        file.writeBytes(srt)
        val link = Uri.fromFile(file).toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("u:$audioUri", link)
            .putString("n:${key(audioName)}", link)
            .putString("t:$link", audioName)
            .apply()
        version++
    }

    /** The subtitle linked to the track at [uri] titled [title], if any. */
    fun find(context: Context, uri: String, title: String?): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("u:$uri", null)?.let { return it }
        if (title != null) prefs.getString("n:${key(title)}", null)?.let { return it }
        val display = names.getOrPut(uri) { displayName(context, uri) ?: "" }
        return if (display.isNotEmpty()) prefs.getString("n:${key(display)}", null) else null
    }

    /** One subtitle file the app holds a private copy of. */
    class Item(val link: String, val audioName: String, val sizeBytes: Long, val savedAt: Long)

    /** Every subtitle file made or linked through the app (newest first). */
    fun list(context: Context): List<Item> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        val links = all.filterKeys { it.startsWith("u:") || it.startsWith("n:") }.values.filterIsInstance<String>().toSet()
        return links.mapNotNull { link ->
            val file = runCatching { File(Uri.parse(link).path ?: return@mapNotNull null) }.getOrNull() ?: return@mapNotNull null
            if (!file.exists()) return@mapNotNull null
            val name = (all["t:$link"] as? String)
                ?: all.entries.firstOrNull { it.value == link && it.key.startsWith("n:") }?.key?.removePrefix("n:")
                ?: file.nameWithoutExtension
            Item(link, name, file.length(), file.lastModified())
        }.sortedByDescending { it.savedAt }
    }

    /** Deletes the private copy and every link to it. */
    fun remove(context: Context, link: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.forEach { (k, v) -> if (v == link && (k.startsWith("u:") || k.startsWith("n:"))) editor.remove(k) }
        editor.remove("t:$link").apply()
        runCatching { Uri.parse(link).path?.let { File(it).delete() } }
        version++
    }

    private fun displayName(context: Context, uri: String): String? = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") parsed.lastPathSegment
        else context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
