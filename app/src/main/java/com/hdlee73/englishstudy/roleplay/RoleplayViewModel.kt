package com.hdlee73.englishstudy.roleplay

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hdlee73.englishstudy.ai.FreeTalk
import com.hdlee73.englishstudy.ai.GenAi
import com.hdlee73.englishstudy.docvoice.core.ModelStore
import kotlinx.coroutines.channels.Channel
import com.hdlee73.englishstudy.translate.Direction
import com.hdlee73.englishstudy.translate.MlKitTranslator
import com.hdlee73.englishstudy.translate.OnlineTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One line of the conversation. [tip] is a more natural way to say what the learner said (set after the partner has answered). */
data class Turn(
    val id: Int,
    val fromUser: Boolean,
    val text: String,
    val korean: String? = null,
    val showKorean: Boolean = false,
    val tip: String? = null,
    /** What the recognizer wrote, when a misheard word was fixed from the conversation ([text] is the fixed sentence). */
    val heard: String? = null
)

/** The on-device speech recognition model (Whisper base.en, about 160 MB, downloaded once when the learner asks for it). */
enum class SttModel { MISSING, DOWNLOADING, READY, FAILED }

data class RoleplayUiState(
    val theme: RoleplayTheme? = null,
    val turns: List<Turn> = emptyList(),
    val voice: RoleplayVoice.State = RoleplayVoice.State.IDLE,
    /** What is being recognised right now. */
    val partial: String = "",
    /** The partner is thinking of an answer, or the theme is being prepared. */
    val busy: Boolean = false,
    /** A scripted scene has reached its last line. */
    val finished: Boolean = false,
    val summary: Boolean = false,
    val message: String? = null,
    val sttModel: SttModel = SttModel.MISSING,
    val sttProgress: Float = 0f,
    /** The learner's choice: recognize on the phone (when the model is there) instead of with the Google speech service. */
    val deviceStt: Boolean = true
)

/** The 롤플레잉 part of the Speaking tab: 프리토킹 and the themes, spoken instead of typed. */
class RoleplayViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val models = ModelStore(application)
    private val _state = MutableStateFlow(
        RoleplayUiState(
            sttModel = if (models.speakingReady()) SttModel.READY else SttModel.MISSING,
            deviceStt = settings.getBoolean("rpDeviceStt", true)
        )
    )
    val state: StateFlow<RoleplayUiState> = _state.asStateFlow()
    private var voice: RoleplayVoice? = null
    private var nextId = 1
    private val online = OnlineTranslator()
    private var replyJob: Job? = null

    private fun voice(): RoleplayVoice = voice ?: RoleplayVoice(
        getApplication<Application>(),
        onState = { s -> _state.update { it.copy(voice = s) } },
        onPartial = { p -> _state.update { it.copy(partial = p) } },
        onSaid = { text, alts -> onSaid(text, alts) },
        onProblem = { m -> _state.update { it.copy(message = m) } }
    ).also { voice = it }

    private fun deviceSttOn() = _state.value.deviceStt && _state.value.sttModel == SttModel.READY

    /** Downloads the on-device recognition model (once; it stays on the phone afterwards). */
    fun downloadSttModel() {
        if (_state.value.sttModel == SttModel.DOWNLOADING) return
        _state.update { it.copy(sttModel = SttModel.DOWNLOADING, sttProgress = 0f, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                models.ensureSpeaking({ true }) { p -> _state.update { it.copy(sttProgress = p) } }
                true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "음성 인식 모델을 내려받지 못했어요.") }
                false
            }
            _state.update { it.copy(sttModel = if (ok) SttModel.READY else SttModel.FAILED, sttProgress = if (ok) 1f else 0f) }
        }
    }

    /** Chooses between the on-device recognition and the Google speech service (the latter stays as the spare). */
    fun setDeviceStt(on: Boolean) {
        settings.edit().putBoolean("rpDeviceStt", on).apply()
        _state.update { it.copy(deviceStt = on) }
    }

    /** Starts [theme]: checks Gemini Nano first (the first time this also starts its one-time download), then the partner speaks. */
    fun start(theme: RoleplayTheme) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            GenAi.initialize(getApplication<Application>())
            if (!RoleplayChat.available(theme)) {
                val why = when (GenAi.status.value) {
                    GenAi.Status.DOWNLOADING -> "Gemini Nano를 내려받는 중이에요. 끝나면 이 상황도 연습할 수 있어요. 지금은 프리토킹과 카페·공항·면접·소개팅·호텔을 해 보세요."
                    else -> "이 상황은 Gemini Nano가 필요해요. 이 기기에서는 프리토킹과 카페·공항·면접·소개팅·호텔 상황을 연습할 수 있어요."
                }
                _state.update { it.copy(busy = false, message = why) }
                return@launch
            }
            voice().useDeviceStt = deviceSttOn()
            if (deviceSttOn()) voice().warmUp()
            val opener = RoleplayChat.opener(theme)
            nextId = 1
            _state.update { it.copy(theme = theme, turns = listOf(Turn(nextId++, false, opener)), busy = false, finished = false, summary = false, partial = "") }
            voice().speak(opener)
        }
    }

    /** Starts the same scene again. */
    fun restart() {
        val theme = _state.value.theme ?: return
        replyJob?.cancel()
        voice?.stop()
        _state.update { it.copy(theme = null, busy = false, partial = "") }
        start(theme)
    }

    /** Takes back the learner's last answer (and the partner's reply to it) and opens the microphone for it again. */
    fun retryLast() {
        val current = _state.value
        val lastUser = current.turns.indexOfLast { it.fromUser }
        if (lastUser < 0) return
        replyJob?.cancel()
        voice?.stop()
        _state.update { it.copy(turns = it.turns.take(lastUser), busy = false, finished = false, partial = "", message = null) }
        voice().startListening()
    }

    /** Back to the list of scenes. */
    fun end() {
        voice?.stop()
        voice?.releaseModel()
        _state.update { RoleplayUiState() }
    }

    /** Stops the voice (the learner left the tab); the conversation stays where it was. */
    fun stop() {
        voice?.stop()
    }

    /** The one big button: skip the partner's line, start listening, or hand over what was said. */
    fun micTap() {
        val v = voice ?: return
        when (_state.value.voice) {
            RoleplayVoice.State.RECOGNIZING -> Unit
            RoleplayVoice.State.LISTENING -> v.finishListening()
            else -> if (!_state.value.busy && !_state.value.finished) { v.useDeviceStt = deviceSttOn(); v.startListening() }
        }
    }

    private fun onSaid(text: String, alternatives: List<String>) {
        val current = _state.value
        val theme = current.theme ?: return
        val userId = nextId++
        val turns = current.turns + Turn(userId, true, text)
        _state.update { it.copy(turns = turns, busy = true, partial = "") }
        replyJob = viewModelScope.launch {
            // A word the recognizer mistook for a similar-sounding one is put right from the conversation (Gemini Nano only).
            val fixed = withContext(Dispatchers.IO) {
                RecognitionFix.fix(theme, turns.dropLast(1).map { FreeTalk.Line(it.fromUser, it.text) }, text, alternatives)
            }
            val said = fixed ?: text
            if (fixed != null) _state.update { s -> s.copy(turns = s.turns.map { if (it.id == userId) it.copy(text = fixed, heard = text) else it }) }
            val history = turns.dropLast(1).map { FreeTalk.Line(it.fromUser, it.text) } + FreeTalk.Line(true, said)
            val userTurns = turns.count { it.fromUser }
            // The partner starts speaking with the first finished sentence while Gemini Nano is still writing the rest.
            val v = voice()
            v.useDeviceStt = deviceSttOn()
            v.beginStream()
            val sentences = Channel<String>(Channel.UNLIMITED)
            var partnerId = -1
            val spoken = StringBuilder()
            val consumer = launch {
                for (sentence in sentences) {
                    v.speakPart(sentence)
                    if (spoken.isNotEmpty()) spoken.append(' ')
                    spoken.append(sentence)
                    val line = spoken.toString()
                    if (partnerId < 0) {
                        partnerId = nextId++
                        val id = partnerId
                        _state.update { it.copy(turns = it.turns + Turn(id, false, line), busy = false) }
                    } else {
                        val id = partnerId
                        _state.update { s -> s.copy(turns = s.turns.map { if (it.id == id) it.copy(text = line) else it }) }
                    }
                }
            }
            val reply = withContext(Dispatchers.IO) { RoleplayChat.reply(theme, history, userTurns) { sentences.trySend(it) } }
            sentences.close()
            consumer.join()
            if (partnerId < 0) {
                partnerId = nextId++
                val id = partnerId
                _state.update { it.copy(turns = it.turns + Turn(id, false, reply.text), busy = false, finished = reply.finished) }
            } else {
                val id = partnerId
                _state.update { s -> s.copy(turns = s.turns.map { if (it.id == id) it.copy(text = reply.text) else it }, busy = false, finished = reply.finished) }
            }
            if (!v.streamAbandoned()) {
                if (v.streamStarted()) v.endStream(listenAfter = !reply.finished) else v.speak(reply.text, listenAfter = !reply.finished)
            }
            val tip = runCatching { FreeTalk.suggestion(said) }.getOrNull()
            if (tip != null) _state.update { s -> s.copy(turns = s.turns.map { if (it.id == userId) it.copy(tip = tip) else it }) }
        }
    }

    /** Shows or hides the Korean translation of one of the partner's lines (made the first time it is asked for). */
    fun toggleKorean(id: Int) {
        val turn = _state.value.turns.firstOrNull { it.id == id } ?: return
        if (turn.korean != null) {
            _state.update { s -> s.copy(turns = s.turns.map { if (it.id == id) it.copy(showKorean = !it.showKorean) else it }) }
            return
        }
        viewModelScope.launch {
            val korean = withContext(Dispatchers.IO) {
                online.translate(turn.text, Direction.EN_KO) ?: MlKitTranslator.translate(turn.text, Direction.EN_KO)
            }
            _state.update { s ->
                if (korean == null) s.copy(message = "번역을 가져오지 못했어요. 인터넷에 연결해 다시 눌러 주세요.")
                else s.copy(turns = s.turns.map { if (it.id == id) it.copy(korean = korean, showKorean = true) else it })
            }
        }
    }

    fun showSummary(show: Boolean) {
        voice?.stop()
        _state.update { it.copy(summary = show) }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        voice?.shutdown()
        voice = null
        super.onCleared()
    }
}
