package com.hdlee73.englishstudy.docvoice.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns

object Storage {
    class Picked(val uri: Uri, val name: String, val size: Long)

    fun describe(context: Context, uri: Uri): Picked {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        return Picked(uri, name, size)
    }

    fun readBytes(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ExtractException("파일을 열 수 없습니다.")

    fun mimeFor(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "mp3" -> "audio/mpeg"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "pdf" -> "application/pdf"
        "srt" -> "application/x-subrip"
        else -> "text/plain"
    }

    /** 다운로드/DocVoice 폴더에 저장하고 Uri 를 돌려준다 (이름이 겹치면 시스템이 번호를 붙임). */
    fun saveToDownloads(context: Context, displayName: String, bytes: ByteArray): Uri =
        saveToDownloads(context, displayName) { it.write(bytes) }

    /** Same, but the caller streams the content into the file (for results too big to hold in memory). */
    fun saveToDownloads(context: Context, displayName: String, write: (java.io.OutputStream) -> Unit): Uri {
        val tree = SaveFolder.get(context)
        if (tree != null) {
            try {
                return saveToTree(context, tree, displayName, write)
            } catch (e: SecurityException) {
                // The folder is gone or its permission was revoked: fall back to Downloads/DocVoice.
                SaveFolder.set(context, null)
            } catch (e: java.io.FileNotFoundException) {
                SaveFolder.set(context, null)
            }
        }
        return saveToMediaStore(context, displayName, write)
    }

    private fun saveToTree(context: Context, tree: Uri, displayName: String, write: (java.io.OutputStream) -> Unit): Uri {
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        // A generic type keeps the provider from appending its own extension; it numbers a name that already exists.
        val uri = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", displayName)
            ?: throw java.io.IOException("선택한 폴더에 저장할 수 없습니다.")
        try {
            resolver.openOutputStream(uri)?.use(write) ?: throw java.io.IOException("저장할 수 없습니다.")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            throw e
        }
        return uri
    }

    private fun saveToMediaStore(context: Context, displayName: String, write: (java.io.OutputStream) -> Unit): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(displayName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DocVoice")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw java.io.IOException("저장 위치를 만들 수 없습니다.")
        try {
            resolver.openOutputStream(uri)?.use(write) ?: throw java.io.IOException("저장할 수 없습니다.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** A file name the user typed, made safe to save: no path characters, no extension the app adds itself. */
    fun cleanName(typed: String, ext: String): String {
        var base = typed.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), " ").replace(Regex("\\s+"), " ").trim().trim('.')
        if (base.endsWith(".$ext", ignoreCase = true)) base = base.dropLast(ext.length + 1).trim()
        return base.take(100)
    }

    fun baseName(name: String) = name.substringBeforeLast('.', name).ifBlank { "DocVoice" }
}

/** Where files the app creates (subtitles, MP3, documents) are saved: a folder the user picked, or Downloads/DocVoice. */
object SaveFolder {
    private const val PREFS = "save_folder"

    fun get(context: Context): Uri? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tree", null)?.let(Uri::parse)

    /** Remembers [tree] (a folder picked with the system folder picker); null goes back to Downloads/DocVoice. */
    fun set(context: Context, tree: Uri?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = get(context)
        if (old != null && old != tree) runCatching {
            context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        if (tree != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            prefs.edit().putString("tree", tree.toString()).apply()
        } else prefs.edit().remove("tree").apply()
        version.value++
    }

    /** Changes whenever the folder changes, so screens showing it refresh. */
    val version = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun label(context: Context): String {
        val tree = get(context) ?: return DEFAULT_LABEL
        return runCatching {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: DEFAULT_LABEL
    }

    const val DEFAULT_LABEL = "다운로드/DocVoice (기본)"
}
