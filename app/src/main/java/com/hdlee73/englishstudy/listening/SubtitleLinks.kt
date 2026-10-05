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

    private fun displayName(context: Context, uri: String): String? = runCatching {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == "file") parsed.lastPathSegment
        else context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
