package com.hdlee73.englishstudy.translate

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hdlee73.englishstudy.ai.GenAi
import com.hdlee73.englishstudy.ai.Tutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the Translate tab does with the text: translate it, or correct (proofread) an English sentence. */
enum class TranslateMode { TRANSLATE, CORRECT }

data class TranslateUiState(
    val mode: TranslateMode = TranslateMode.TRANSLATE,
    val input: String = "",
    val direction: Direction = Direction.EN_KO,
    /** The text the shown result belongs to; the result is hidden once the input differs. */
    val resultFor: String = "",
    val resultDirection: Direction = Direction.EN_KO,
    val result: String? = null,
    /** Which engine made the shown translation (blank for the usual online one). */
    val resultNote: String = "",
    val translating: Boolean = false,
    val correcting: Boolean = false,
    val correction: Tutor.CorrectionResult? = null,
    /** The text the shown correction belongs to. */
    val correctionFor: String = "",
    val message: String? = null
) {
    val resultVisible: Boolean get() = result != null && resultFor == input.trim()
    val correctionVisible: Boolean get() = correction != null && correctionFor == input.trim()
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
        _state.update { it.copy(input = "", result = null, resultFor = "", translating = false, correcting = false, correction = null, correctionFor = "") }
    }

    fun setMode(mode: TranslateMode) {
        if (_state.value.mode == mode) return
        job?.cancel()
        generation++
        _state.update { it.copy(mode = mode, translating = false, correcting = false) }
    }

    /** Checks whether Gemini Nano is available (and starts its one-time download); nothing happens before the correction mode is used. */
    fun prepareAi(context: Context) {
        val app = context.applicationContext
        viewModelScope.launch(Dispatchers.IO) { GenAi.initialize(app) }
    }

    /** Puts the (English) translation result into the input and corrects it. */
    fun correctResult(context: Context) {
        val current = _state.value
        val text = current.result?.takeIf { current.resultVisible && current.resultDirection == Direction.KO_EN } ?: return
        _state.update { it.copy(mode = TranslateMode.CORRECT, input = text.take(TextTranslation.MAX_CHARS), direction = Direction.EN_KO, correction = null, correctionFor = "") }
        correct(context)
    }

    fun correct(context: Context) {
        val current = _state.value
        val text = current.input.trim()
        if (text.isEmpty() || current.correcting) return
        if (TextTranslation.detect(text, Direction.EN_KO) == Direction.KO_EN) {
            _state.update { it.copy(message = "교정은 영어 문장만 할 수 있어요. 한국어는 번역한 뒤 영어 결과를 ‘교정’해 보세요.") }
            return
        }
        prepareAi(context)
        _state.update { it.copy(correcting = true, correction = null) }
        val run = ++generation
        job = viewModelScope.launch(Dispatchers.IO) {
            // Gemini Nano may still be checking or downloading its model: wait briefly so the first answer can already use it.
            kotlinx.coroutines.withTimeoutOrNull(4_000L) {
                while (GenAi.status.value == GenAi.Status.CHECKING) kotlinx.coroutines.delay(150)
            }
            val result = Tutor.correct(text)
            if (!isActive || run != generation) return@launch
            _state.update { it.copy(correcting = false, correction = result, correctionFor = text) }
        }
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
            var out = translator.translate(text, direction)
            var note = ""
            if (out == null) {
                // The online service did not answer (offline or blocked): fall back to the translation on the device.
                out = MlKitTranslator.translate(text, direction)
                if (out != null) note = MlKitTranslator.CREDIT
            }
            // A clear/swap or a newer translate cancels this job, but a blocking translate may already have returned:
            // only write when this is still the current run.
            if (!isActive || run != generation) return@launch
            _state.update {
                if (out == null) it.copy(translating = false, message = "번역을 가져오지 못했습니다. 인터넷에 연결해 다시 눌러 주세요. (기기 내 번역 모델은 처음 한 번 내려받아야 해요.)")
                else it.copy(translating = false, result = out, resultFor = text, resultDirection = direction, resultNote = note)
            }
        }
    }
}
