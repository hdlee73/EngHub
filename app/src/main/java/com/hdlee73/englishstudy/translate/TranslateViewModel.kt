package com.hdlee73.englishstudy.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    fun setInput(text: String) = _state.update { it.copy(input = text.take(TextTranslation.MAX_CHARS)) }

    fun clear() {
        job?.cancel()
        _state.update { it.copy(input = "", result = null, resultFor = "", translating = false) }
    }

    fun swap() {
        job?.cancel()
        _state.update {
            // Swapping also moves the last result into the input, like a translator's swap button.
            val moved = if (it.resultVisible) it.result.orEmpty() else it.input
            it.copy(direction = it.direction.swapped(), input = moved, result = null, resultFor = "", translating = false)
        }
    }

    fun setDirection(direction: Direction) = _state.update { it.copy(direction = direction, result = null, resultFor = "") }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun translate() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isEmpty() || current.translating) return
        // Korean-only text is translated into English and English-only text into Korean, whatever the buttons say.
        val direction = TextTranslation.detect(text, current.direction)
        _state.update { it.copy(direction = direction, translating = true, result = null) }
        job = viewModelScope.launch(Dispatchers.IO) {
            val out = translator.translate(text, direction)
            _state.update {
                if (out == null) it.copy(translating = false, message = "번역을 가져오지 못했습니다. 인터넷 연결을 확인하고 다시 눌러 주세요.")
                else it.copy(translating = false, result = out, resultFor = text, resultDirection = direction)
            }
        }
    }
}
