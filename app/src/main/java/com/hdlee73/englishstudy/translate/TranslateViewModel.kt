package com.hdlee73.englishstudy.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TranslateUiState(
    val input: String = "",
    val direction: Direction = Direction.EN_KO,
    /** The text the shown result belongs to; the result is hidden once the input differs. */
    val resultFor: String = "",
    val resultDirection: Direction = Direction.EN_KO,
    val result: String? = null,
    val translating: Boolean = false,
    val message: String? = null
) {
    val resultVisible: Boolean get() = result != null && resultFor == input.trim()
}

class TranslateViewModel : ViewModel() {
    private val translator = OnlineTranslator()
    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()
    private var job: Job? = null
    /** Bumped whenever a translation is started or invalidated, so a stale result can never be written. */
    private var generation = 0

    fun setInput(text: String) = _state.update { it.copy(input = text.take(TextTranslation.MAX_CHARS)) }

    fun clear() {
        job?.cancel()
        generation++
        _state.update { it.copy(input = "", result = null, resultFor = "", translating = false) }
    }

    fun swap() {
        job?.cancel()
        generation++
        _state.update {
            // Swapping also moves the last result into the input, like a translator's swap button.
            val moved = if (it.resultVisible) it.result.orEmpty() else it.input
            it.copy(direction = it.direction.swapped(), input = moved, result = null, resultFor = "", translating = false)
        }
    }

    fun setDirection(direction: Direction) = _state.update { it.copy(direction = direction, result = null, resultFor = "") }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun translate() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isEmpty() || current.translating) return
        // Korean-only text is translated into English and English-only text into Korean, whatever the buttons say.
        val direction = TextTranslation.detect(text, current.direction)
        _state.update { it.copy(direction = direction, translating = true, result = null) }
        val run = ++generation
        job = viewModelScope.launch(Dispatchers.IO) {
            val out = translator.translate(text, direction)
            // A clear/swap or a newer translate cancels this job, but a blocking translate may already have returned:
            // only write when this is still the current run.
            if (!isActive || run != generation) return@launch
            _state.update {
                if (out == null) it.copy(translating = false, message = "번역을 가져오지 못했습니다. 인터넷 연결을 확인하고 다시 눌러 주세요.")
                else it.copy(translating = false, result = out, resultFor = text, resultDirection = direction)
            }
        }
    }
}
