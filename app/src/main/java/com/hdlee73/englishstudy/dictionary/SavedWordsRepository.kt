package com.hdlee73.englishstudy.dictionary

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The saved words, shared by the dictionary, flashcard, quiz and speaking tabs. */
class SavedWordsRepository private constructor(context: Context) {
    private val db = EntryDb(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _words = MutableStateFlow<List<WordEntry>>(emptyList())
    val words: StateFlow<List<WordEntry>> = _words.asStateFlow()

    // The first read happens off the main thread: constructing the repository (from a ViewModel) must not block the UI.
    init { scope.launch { reload() } }

    /** Re-reads the saved words from the database. Call from a background thread. */
    @Synchronized fun reload() { _words.value = db.all().map { it.studyVersion() } }

    /** Saves [entry]. Call from a background thread: it writes to SQLite and reloads the whole list. */
    @Synchronized fun save(entry: WordEntry): Boolean = db.save(entry).also { if (it) reload() }

    @Synchronized fun delete(id: Long) { db.delete(id); reload() }

    companion object {
        @Volatile private var instance: SavedWordsRepository? = null

        fun get(context: Context): SavedWordsRepository =
            instance ?: synchronized(this) { instance ?: SavedWordsRepository(context).also { instance = it } }
    }
}
