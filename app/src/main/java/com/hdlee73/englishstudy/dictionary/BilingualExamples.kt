package com.hdlee73.englishstudy.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.util.Locale

internal data class BilingualSentence(val english: String, val korean: String, val credit: String)

internal object UsageMatcher {
    fun contains(sentence: String, query: String): Boolean {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        val pattern = words.joinToString("\\s+") { Regex.escape(it) }
        return Regex("(?<![A-Za-z])$pattern(?![A-Za-z])", RegexOption.IGNORE_CASE).containsMatchIn(sentence)
    }
}

internal class BilingualExamples(private val activity: Context) {
    private var database: SQLiteDatabase? = null
    /** Up to [pool] sentences for the word, shortest first; ExampleSense picks the ones that fit the meanings. */
    @Synchronized fun lookup(query: String, pool: Int = 30): List<BilingualSentence> {
        return try {
            val db = database ?: AssetDatabase.open(activity, "bilingual_examples.sqlite", "bilingual_examples")
                ?.also { database = it } ?: return emptyList()
            val token = Regex("[a-z]+(?:'[a-z]+)?").find(query.lowercase(Locale.ROOT))?.value ?: return emptyList()
            val matches = mutableListOf<BilingualSentence>()
            db.rawQuery("SELECT e.english,e.korean,e.credit FROM examples e JOIN tokens t ON t.example_id=e.id WHERE t.token=? AND length(e.english)>=18 ORDER BY length(e.english),e.id LIMIT 800", arrayOf(token)).use { c ->
                while (c.moveToNext() && matches.size < pool) {
                    val en = c.getString(0)
                    if (SentenceExamples.isSimple(en) && UsageMatcher.contains(en, query) && matches.none { it.english == en }) {
                        matches += BilingualSentence(en, c.getString(1), c.getString(2))
                    }
                }
            }
            matches
        } catch (_: Exception) { emptyList() }
    }
}
