package com.hdlee73.englishstudy.listening

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Reads a folder chosen with the system folder picker (and every folder inside it) and returns
 * its audio files per folder, named by their path relative to the chosen folder.
 */
object FolderImport {
    /** One playlist folder: [path] is e.g. "Podcasts" or "Podcasts/Week 1". */
    class Found(val path: String, val entries: List<TrackStore.Entry>)

    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "3gp", "wma")
    private const val MAX_DEPTH = 8

    /** Folders without any audio are left out. Must run off the main thread. */
    fun scan(context: Context, tree: Uri): List<Found> {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootName = nameOf(context, tree, rootId) ?: rootId.substringAfterLast(':').substringAfterLast('/').ifBlank { "폴더" }
        val out = mutableListOf<Found>()
        walk(context, tree, rootId, rootName, 0, out)
        return out
    }

    private fun walk(context: Context, tree: Uri, docId: String, path: String, depth: Int, out: MutableList<Found>) {
        val files = mutableListOf<TrackStore.Entry>()
        val dirs = mutableListOf<Pair<String, String>>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        runCatching {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> dirs += id to name
                        mime.startsWith("audio/") || name.substringAfterLast('.', "").lowercase() in AUDIO_EXT ->
                            files += TrackStore.Entry(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), name)
                    }
                }
            }
        }
        if (files.isNotEmpty()) {
            out += Found(path, files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }))
        }
        if (depth < MAX_DEPTH) {
            dirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
                .forEach { (id, name) -> walk(context, tree, id, "$path/$name", depth + 1, out) }
        }
    }

    private fun nameOf(context: Context, tree: Uri, docId: String): String? = runCatching {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, docId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
