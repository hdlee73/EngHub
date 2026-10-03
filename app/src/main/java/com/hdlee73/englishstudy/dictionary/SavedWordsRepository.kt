package com.hdlee73.englishstudy.dictionary

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The saved words, shared by the dictionary, flashcard, quiz and speaking tabs. */
class SavedWordsRepository private constructor(context: Context) {
    private val db = EntryDb(context.applicationContext)
    private val _words = MutableStateFlow<List<WordEntry>>(emptyList())
    val words: StateFlow<List<WordEntry>> = _words.asStateFlow()

    init { reload() }

    /** Re-reads the saved words from the database. */
    @Synchronized fun reload() { _words.value = db.all().map { it.studyVersion() } }

    @Synchronized fun save(entry: WordEntry): Boolean = db.save(entry).also { if (it) reload() }

    @Synchronized fun delete(id: Long) { db.delete(id); reload() }

    companion object {
        @Volatile private var instance: SavedWordsRepository? = null

        fun get(context: Context): SavedWordsRepository =
            instance ?: synchronized(this) { instance ?: SavedWordsRepository(context).also { instance = it } }
    }
}
