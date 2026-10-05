package com.hdlee73.englishstudy.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens files through Samsung's My Files app when it is installed, and through the system picker otherwise.
 * My Files only hands out access for the current session, so callers read the file right away
 * (word lists and datasets are copied in on import).
 */
object FilePick {
    const val MY_FILES = "com.sec.android.app.myfiles"

    fun intent(context: Context, mimeTypes: Array<String>, multiple: Boolean): Intent {
        val types = mimeTypes.ifEmpty { arrayOf("*/*") }
        val myFiles = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setPackage(MY_FILES)
            // My Files filters by the main type better than by a list, so pass one type when there is one.
            type = if (types.size == 1) types[0] else "*/*"
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (handles(context, myFiles)) return myFiles
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (types.size == 1) types[0] else "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, types)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    private fun handles(context: Context, intent: Intent): Boolean =
        runCatching {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        }.getOrDefault(false)

    fun uris(resultCode: Int, data: Intent?): List<Uri> {
        if (resultCode != Activity.RESULT_OK || data == null) return emptyList()
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) } }
        data.data?.let { if (it !in uris) uris.add(it) }
        return uris
    }

    /** Picks several files; returns an empty list when the user backs out. */
    class Multiple : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun createIntent(context: Context, input: Array<String>) = intent(context, input, multiple = true)
        override fun parseResult(resultCode: Int, intent: Intent?) = uris(resultCode, intent)
    }

    /** Picks one file; returns null when the user backs out. */
    class Single : ActivityResultContract<Array<String>, Uri?>() {
        override fun createIntent(context: Context, input: Array<String>) = intent(context, input, multiple = false)
        override fun parseResult(resultCode: Int, intent: Intent?) = uris(resultCode, intent).firstOrNull()
    }
}
