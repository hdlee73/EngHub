package com.hdlee73.englishstudy.reading

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate

data class ReadingUiState(
    val loaded: Boolean = false,
    val dateLabel: String = "",
    /** The three texts of the day. */
    val today: List<ReadingArticle> = emptyList(),
    /** Ids of today's texts that have been opened. */
    val readIds: Set<String> = emptySet(),
    val openId: String? = null,
    val showTranslation: Boolean = false,
    val translating: Boolean = false,
    /** Korean text per paragraph of the open article; a missing index is not translated yet. */
    val translations: Map<Int, String> = emptyMap(),
    val message: String? = null,
    /** The translation of the words the learner selected in the open article. */
    val snippet: Snippet? = null
) {
    val open: ReadingArticle? get() = today.firstOrNull { it.id == openId }
}

/** A selected passage and its Korean translation ([korean] is null while loading or when it failed). */
data class Snippet(val text: String, val korean: String?, val loading: Boolean)

class ReadingViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("reading", Context.MODE_PRIVATE)
    private val translator = ReadingTranslator()
    private val snippetTranslator = com.hdlee73.englishstudy.translate.OnlineTranslator()
    private var snippetJob: Job? = null
    private val _state = MutableStateFlow(ReadingUiState())
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()

    private var library: List<ReadingArticle> = emptyList()
    private var day = LocalDate.now().toEpochDay()
    private var translateJob: Job? = null

    init { refresh() }

    /** Loads the library once and picks the texts for today; call again when the tab is shown (the date may have changed). */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            if (library.isEmpty()) {
                library = runCatching {
                    getApplication<Application>().assets.open("reading_articles.txt").bufferedReader(Charsets.UTF_8).use { ReadingLibrary.parse(it.readText()) }
                }.getOrDefault(emptyList())
            }
            val date = LocalDate.now()
            day = date.toEpochDay()
            val today = DailyReading.pick(day, library)
            val read = prefs.getStringSet("read_$day", emptySet()).orEmpty()
            _state.update {
                // Keep an article open across a refresh unless the day has moved on.
                val stillOpen = it.openId?.takeIf { id -> today.any { a -> a.id == id } }
                it.copy(
                    loaded = true, today = today, readIds = read.filter { id -> today.any { a -> a.id == id } }.toSet(),
                    dateLabel = "${date.monthValue}월 ${date.dayOfMonth}일", openId = stillOpen
                )
            }
            // Drop the read marks of earlier days.
            prefs.all.keys.filter { it.startsWith("read_") && it != "read_$day" }.forEach { prefs.edit().remove(it).apply() }
        }
    }

    fun open(id: String) {
        val read = (prefs.getStringSet("read_$day", emptySet()).orEmpty() + id).toSet()
        prefs.edit().putStringSet("read_$day", read).apply()
        translateJob?.cancel()
        _state.update { it.copy(openId = id, readIds = it.readIds + id, showTranslation = false, translating = false, translations = cached(id), snippet = null) }
    }

    fun close() {
        translateJob?.cancel()
        snippetJob?.cancel()
        _state.update { it.copy(openId = null, showTranslation = false, translating = false, translations = emptyMap(), snippet = null) }
    }

    /** Translates the selected passage into Korean. */
    fun translateSnippet(text: String) {
        snippetJob?.cancel()
        _state.update { it.copy(snippet = Snippet(text, null, true)) }
        snippetJob = viewModelScope.launch(Dispatchers.IO) {
            val korean = snippetTranslator.translate(text, com.hdlee73.englishstudy.translate.Direction.EN_KO)
            _state.update {
                if (it.snippet?.text != text) it
                else it.copy(snippet = Snippet(text, korean, false), message = if (korean == null) "번역을 가져오지 못했습니다. 인터넷 연결을 확인해 주세요." else it.message)
            }
        }
    }

    fun clearSnippet() {
        snippetJob?.cancel()
        _state.update { if (it.snippet == null) it else it.copy(snippet = null) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Shows or hides the Korean translation; the first time, every paragraph is translated online and kept for next time. */
    fun toggleTranslation() {
        val article = _state.value.open ?: return
        if (_state.value.showTranslation) {
            translateJob?.cancel()
            _state.update { it.copy(showTranslation = false, translating = false) }
            return
        }
        _state.update { it.copy(showTranslation = true) }
        if (_state.value.translations.size >= article.paragraphs.size) return
        translateJob?.cancel()
        _state.update { it.copy(translating = true) }
        translateJob = viewModelScope.launch(Dispatchers.IO) {
            var failed = false
            for ((index, paragraph) in article.paragraphs.withIndex()) {
                if (_state.value.translations.containsKey(index)) continue
                val korean = translator.translate(paragraph)
                if (korean == null) { failed = true; break }
                _state.update { if (it.openId == article.id) it.copy(translations = it.translations + (index to korean)) else it }
            }
            val done = _state.value.translations
            if (_state.value.openId == article.id && done.size >= article.paragraphs.size) store(article.id, article.paragraphs.size, done)
            _state.update {
                it.copy(
                    translating = false,
                    message = if (failed) "번역을 가져오지 못했습니다. 인터넷 연결을 확인하고 다시 눌러 주세요." else it.message
                )
            }
        }
    }

    private fun cached(id: String): Map<Int, String> = runCatching {
        val array = JSONArray(prefs.getString("tr_$id", null) ?: return emptyMap())
        (0 until array.length()).associateWith { array.getString(it) }
    }.getOrDefault(emptyMap())

    private fun store(id: String, size: Int, translations: Map<Int, String>) {
        val array = JSONArray()
        for (i in 0 until size) array.put(translations[i].orEmpty())
        prefs.edit().putString("tr_$id", array.toString()).apply()
    }
}
