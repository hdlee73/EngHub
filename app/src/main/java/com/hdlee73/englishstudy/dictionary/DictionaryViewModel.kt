package com.hdlee73.englishstudy.dictionary

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DictionaryUiState(
    val query: String = "",
    val status: String = "영어 단어나 숙어를 입력하세요.",
    val searching: Boolean = false,
    val entry: WordEntry? = null,
    val canSave: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val suggestionTitle: String = "",
    val showSaved: Boolean = false,
    val sort: SavedSort = SavedSort.ALPHABETICAL,
    val message: String? = null
)

class DictionaryViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val repository = SavedWordsRepository.get(application)
    private val _state = MutableStateFlow(DictionaryUiState(sort = SavedSort.of(settings.getString("savedSort", null))))
    val state: StateFlow<DictionaryUiState> = _state.asStateFlow()
    /** The saved words in the order chosen for the list (the repository itself is unsorted-by-choice). */
    val savedWords: StateFlow<List<WordEntry>> = repository.words

    private val engine = DictionaryEngine(application) { update ->
        _state.update {
            it.copy(
                entry = update.entry, status = update.status, canSave = update.canSave,
                suggestions = update.suggestions, suggestionTitle = update.suggestionTitle, searching = update.searching
            )
        }
    }
    private var debounce: Job? = null
    private var lastClipboard = ""

    fun onQueryChange(text: String) {
        _state.update { it.copy(query = text, showSaved = false) }
        debounce?.cancel()
        val q = text.trim()
        if (q.isEmpty()) {
            engine.cancel()
            _state.update { it.copy(entry = null, suggestions = emptyList(), canSave = false, searching = false, status = "영어 단어나 숙어를 입력하세요.") }
            return
        }
        debounce = viewModelScope.launch { delay(300); engine.lookup(q) }
    }

    fun searchNow() {
        debounce?.cancel()
        val q = _state.value.query.trim()
        if (q.isNotEmpty()) { _state.update { it.copy(showSaved = false) }; engine.lookup(q) }
    }

    fun pickSuggestion(word: String) {
        debounce?.cancel()
        _state.update { it.copy(query = word, showSaved = false) }
        engine.lookup(word)
    }

    fun saveCurrent() {
        val entry = _state.value.entry ?: return
        val ok = repository.save(entry)
        _state.update { it.copy(message = if (ok) "‘${entry.word}’ 단어를 저장했습니다." else "저장하지 못했습니다.") }
    }

    fun saveEntry(entry: WordEntry) {
        val ok = repository.save(entry)
        _state.update { it.copy(message = if (ok) "‘${entry.word}’ 단어를 저장했습니다." else "저장하지 못했습니다.") }
    }

    fun deleteSaved(entry: WordEntry) {
        repository.delete(entry.id)
        _state.update { it.copy(message = "‘${entry.word}’ 단어를 삭제했습니다.") }
    }

    fun setShowSaved(show: Boolean) = _state.update { it.copy(showSaved = show) }

    fun setSort(sort: SavedSort) {
        settings.edit().putString("savedSort", sort.name).apply()
        _state.update { it.copy(sort = sort) }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Called with the clipboard text when the window gains focus; a copied word is searched once. */
    fun onClipboardText(text: String) {
        val t = text.trim()
        if (t == lastClipboard || !t.matches(Regex("[A-Za-z][A-Za-z '\\-]{0,79}"))) return
        if (t.split(Regex("\\s+")).size > 6) return
        lastClipboard = t
        debounce?.cancel()
        _state.update { it.copy(query = t, showSaved = false) }
        engine.lookup(t)
    }

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }
}
