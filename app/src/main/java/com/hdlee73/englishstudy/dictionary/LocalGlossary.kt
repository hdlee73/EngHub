package com.hdlee73.englishstudy.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.util.Locale

internal data class LocalMeaning(val korean: String, val english: String, val ipa: String, val source: String)

internal class LocalGlossary(private val context: Context) {
    private var database: SQLiteDatabase? = null
    // The saved vocabulary database (sajeon.db) is separate and never touched here.
    @Synchronized private fun open(): SQLiteDatabase? =
        database ?: AssetDatabase.open(context, "word_dictionary.sqlite", "meaning_dictionary")?.also { database = it }
    fun prewarm() { open() }
    fun contains(word: String): Boolean = lookup(word) != null
    /** Dictionary headwords one edit away from a misspelled query, common entries first. */
    fun spellingCandidates(word: String): List<String> {
        val edits = Spelling.edits1(word).toList()
        if (edits.isEmpty()) return emptyList()
        val db = open() ?: return emptyList()
        val found = mutableListOf<Pair<String, String>>()
        return try {
            edits.chunked(400).forEach { chunk ->
                val marks = chunk.joinToString(",") { "?" }
                db.rawQuery("SELECT word, source FROM words WHERE word IN ($marks)", chunk.toTypedArray()).use { c ->
                    while (c.moveToNext()) found += c.getString(0) to c.getString(1).orEmpty()
                }
            }
            Spelling.rank(word, found).take(5)
        } catch (_: Exception) { emptyList() }
    }
    fun lookup(word: String): LocalMeaning? {
        val db = open() ?: return null
        return try {
            db.rawQuery("SELECT meaning_ko, meaning_en, ipa, source FROM words WHERE word = ? COLLATE NOCASE LIMIT 1", arrayOf(word.trim())).use { c ->
                if (c.moveToFirst()) LocalMeaning(c.getString(0).orEmpty(), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty()) else null
            }
        } catch (_: Exception) { null }
    }
    fun suggest(query: String): List<String> {
        val terms = Regex("[a-z]+").findAll(query.lowercase(Locale.ROOT)).map { it.value + "*" }.toList()
        if (terms.isEmpty()) return emptyList()
        val db = open() ?: return emptyList()
        return try {
            db.rawQuery(
                "SELECT w.word FROM words_fts f JOIN words w ON w.rowid=f.docid WHERE words_fts MATCH ? ORDER BY length(w.word), w.word LIMIT 6",
                arrayOf(terms.joinToString(" "))
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        } catch (_: Exception) { emptyList() }
    }
}
