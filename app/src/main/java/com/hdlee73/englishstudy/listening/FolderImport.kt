package com.hdlee73.englishstudy.listening

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Reads a folder chosen with the system folder picker (and every folder inside it) as a tree of
 * directories with their audio files. The document ids let the playlist folders stay linked to
 * the device folders they came from, whatever they are renamed to.
 */
object FolderImport {
    /** One directory: [path] is relative to the chosen folder's name, e.g. "Podcasts/Week 1". */
    class Node(
        val docId: String,
        val name: String,
        val path: String,
        val files: List<TrackStore.Entry>,
        val children: List<Node>
    ) {
        val hasAudio: Boolean get() = files.isNotEmpty() || children.any { it.hasAudio }
    }

    private val AUDIO_EXT = setOf(
        "mp3", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "3gp", "wma", "mka", "aif", "aiff", "mp2", "weba"
    )

    /** True when the file name has an audio extension (the system does not always file m4a, opus, amr ... as audio). */
    fun isAudioName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in AUDIO_EXT
    private const val MAX_DEPTH = 8
    /** A single file bigger than this (bytes) is skipped: it is not a listening track and would not fit memory anyway. */
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024
    /** A picked tree may contribute at most this many files; further files are ignored. */
    private const val MAX_TOTAL_FILES = 50_000
    /** A picked tree may contribute at most this many bytes (sizes reported by the provider are summed). */
    private const val MAX_TOTAL_BYTES = 16L * 1024 * 1024 * 1024

    /** Running totals for one [scan] call, shared down the recursion to bound a hostile or huge tree. */
    private class Budget {
        var files = 0
        var bytes = 0L
        val exhausted: Boolean get() = files >= MAX_TOTAL_FILES || bytes >= MAX_TOTAL_BYTES
    }

    /** Must run off the main thread. */
    fun scan(context: Context, tree: Uri): Node {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootName = nameOf(context, tree, rootId) ?: rootId.substringAfterLast(':').substringAfterLast('/').ifBlank { "폴더" }
        return walk(context, tree, rootId, rootName, rootName, 0, Budget())
    }

    private fun walk(context: Context, tree: Uri, docId: String, name: String, path: String, depth: Int, budget: Budget): Node {
        val files = mutableListOf<TrackStore.Entry>()
        val dirs = mutableListOf<Pair<String, String>>()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        runCatching {
            context.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val fileName = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    val size = if (c.isNull(3)) -1L else c.getLong(3)
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> dirs += id to fileName
                        mime.startsWith("audio/") || fileName.substringAfterLast('.', "").lowercase() in AUDIO_EXT -> {
                            if (budget.exhausted) continue
                            if (size > MAX_FILE_BYTES) continue
                            budget.files++
                            if (size > 0) budget.bytes += size
                            files += TrackStore.Entry(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), fileName)
                        }
                    }
                }
            }
        }
        val subs = if (depth < MAX_DEPTH && !budget.exhausted) {
            dirs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
                .map { (id, n) -> walk(context, tree, id, n, "$path/$n", depth + 1, budget) }
        } else emptyList()
        return Node(docId, name, path, files.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }), subs)
    }

    private fun nameOf(context: Context, tree: Uri, docId: String): String? = runCatching {
        context.contentResolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, docId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * The tree uri and source directory of a folder whose files all came from one directory of a picked tree
     * (folders imported before v1.22 were not linked), or null.
     */
    fun inferSource(entries: List<TrackStore.Entry>): Pair<String, String>? {
        if (entries.isEmpty()) return null
        var tree: String? = null
        var parent: String? = null
        for (e in entries) {
            val uri = Uri.parse(e.uri)
            val (t, p) = runCatching {
                val doc = DocumentsContract.getDocumentId(uri)
                val treeId = DocumentsContract.getTreeDocumentId(uri)
                val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority, treeId).toString()
                val dir = if (doc.contains('/')) doc.substringBeforeLast('/') else doc.substringBefore(':') + ":"
                treeUri to dir
            }.getOrNull() ?: return null
            if (tree == null) { tree = t; parent = p } else if (tree != t || parent != p) return null
        }
        return tree!! to parent!!
    }
}
