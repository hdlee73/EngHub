package com.hdlee73.englishstudy.roleplay

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * The voice of a role-play: the partner's line is read aloud, and when it is finished the microphone opens by itself.
 * The learner talks as long as needed and taps once when done ([finishListening]); the recognizer is restarted whenever
 * it ends on its own (a pause), so what was said is put together and handed over only at the tap.
 * All calls and callbacks run on the main thread.
 */
class RoleplayVoice(
    context: Context,
    private val onState: (State) -> Unit,
    private val onPartial: (String) -> Unit,
    /** What was said, and the recognizer's other readings of it (empty when it was heard in several parts). */
    private val onSaid: (String, List<String>) -> Unit,
    private val onProblem: (String) -> Unit
) {
    enum class State { IDLE, SPEAKING, LISTENING, RECOGNIZING }

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: Pair<String, Boolean>? = null
    /** Bumped whenever something new starts or everything stops, so callbacks of an older run are ignored. */
    private var generation = 0
    private var recognizer: SpeechRecognizer? = null
    private val said = StringBuilder()
    private var lastPartial = ""
    private var userDone = false
    private var listening = false
    private var silentRestarts = 0
    private var failures = 0
    private val stt = OnDeviceStt(context)
    /** Recognize on the phone (Whisper) instead of with the Google speech service; the caller sets it from the learner's choice and the model being present. */
    var useDeviceStt = false
    private var deviceMode = false
    private var streamGen = -1
    private var streamBegun = -1
    private var streamQueued = 0
    private var streamEnded = false
    private var streamListenAfter = true
    private var streamParts = 0
    private var streamPending = ArrayList<String>()
    private var segments = 0
    private var alternatives: List<String> = emptyList()

    init {
        tts = TextToSpeech(app) { status -> main.post { onTtsReady(status) } }
    }

    private fun onTtsReady(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            onProblem("기기의 음성 엔진을 시작하지 못했어요.")
            return
        }
        val language = listOf(Locale.US, Locale.UK, Locale.ENGLISH).firstOrNull { engine.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
        if (language == null) {
            onProblem("영어 음성 데이터가 없어요. 기기의 음성 설정에서 영어를 설치해 주세요.")
            return
        }
        engine.setSpeechRate(0.95f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                streamPartId(utteranceId)?.let { g -> main.post { onStreamPartSpoken(g) }; return }
                val parsed = parseId(utteranceId) ?: return
                main.post { if (parsed.first == generation) { if (parsed.second) startListening() else onState(State.IDLE) } }
            }
            @Deprecated("Required by platform listener")
            override fun onError(utteranceId: String?) = spokenWithError(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = spokenWithError(utteranceId)
        })
        ttsReady = true
        pending?.let { (text, listenAfter) -> pending = null; speak(text, listenAfter) }
        if (streamPending.isNotEmpty()) { val parts = streamPending; streamPending = ArrayList(); parts.forEach { speakPart(it) } }
    }

    /** The utterance id is "rp-<generation>-<1 when the microphone opens afterwards>". */
    private fun parseId(id: String?): Pair<Int, Boolean>? {
        if (id == null || !id.startsWith("rp-")) return null
        val parts = id.removePrefix("rp-").split('-')
        if (parts.size != 2) return null
        val g = parts[0].toIntOrNull() ?: return null
        return g to (parts[1] == "1")
    }

    private fun spokenWithError(utteranceId: String?) {
        streamPartId(utteranceId)?.let { g -> main.post { onStreamPartSpoken(g) }; return }
        val parsed = parseId(utteranceId) ?: return
        main.post {
            if (parsed.first == generation) {
                onState(State.IDLE)
                onProblem("상대의 말을 읽어 주지 못했어요. 마이크 버튼으로 이어서 말해 보세요.")
            }
        }
    }

    /** The utterance id of a streamed part is "rps-<generation>-<n>"; returns the generation, or null for any other id. */
    private fun streamPartId(id: String?): Int? =
        if (id != null && id.startsWith("rps-")) id.removePrefix("rps-").substringBefore('-').toIntOrNull() else null

    private fun onStreamPartSpoken(g: Int) {
        if (g != streamGen || g != generation) return
        streamQueued--
        checkStreamEnd()
    }

    private fun checkStreamEnd() {
        if (!streamEnded || streamQueued > 0 || streamGen != generation) return
        streamGen = -1
        if (streamListenAfter) startListening() else onState(State.IDLE)
    }

    /**
     * A spoken answer that is still being written: [beginStream], then [speakPart] for every finished sentence, then
     * [endStream]. The first sentence is read while the later ones are being made.
     */
    fun beginStream() {
        cancelRecognizer()
        stt.cancel()
        deviceMode = false
        listening = false
        val g = ++generation
        streamGen = g
        streamBegun = g
        streamQueued = 0
        streamParts = 0
        streamEnded = false
        streamPending = ArrayList()
        tts?.stop()
        onState(State.SPEAKING)
    }

    fun speakPart(text: String) {
        if (streamGen != generation) return
        if (!ttsReady) { streamPending.add(text); return }
        streamQueued++
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "rps-$streamGen-${streamParts++}")
    }

    /** No more sentences are coming; the microphone opens once the last one has been read (unless [listenAfter] is false). */
    fun endStream(listenAfter: Boolean = true) {
        if (streamGen != generation) return
        streamEnded = true
        streamListenAfter = listenAfter
        checkStreamEnd()
    }

    /** True when the learner (or a restart) took over after [beginStream], so the rest of that answer must not be read. */
    fun streamAbandoned(): Boolean = streamBegun != generation

    /** True while a streamed answer is being read (so the answer has begun). */
    fun streamStarted(): Boolean = streamGen == generation && streamParts > 0

    /** Reads [text] aloud; when it is finished the microphone opens (unless [listenAfter] is false). */
    fun speak(text: String, listenAfter: Boolean = true) {
        cancelRecognizer()
        stt.cancel()
        deviceMode = false
        streamGen = -1
        val g = ++generation
        onState(State.SPEAKING)
        if (!ttsReady) { pending = text to listenAfter; return }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rp-$g-${if (listenAfter) 1 else 0}")
    }

    /** Opens the microphone now (also skips the rest of the partner's line). */
    fun startListening() {
        tts?.stop()
        pending = null
        generation++
        said.setLength(0)
        lastPartial = ""
        segments = 0
        alternatives = emptyList()
        userDone = false
        silentRestarts = 0
        failures = 0
        streamGen = -1
        if (useDeviceStt && stt.modelReady() && startDevice()) return
        beginRecognition()
    }

    private fun startDevice(): Boolean {
        val g = generation
        if (!stt.start { main.post { if (g == generation && deviceMode && listening) finishListening() } }) return false
        deviceMode = true
        listening = true
        onState(State.LISTENING)
        return true
    }

    /** Hands the recording to the on-device recognizer; the answer comes a moment later. */
    private fun finishDevice() {
        listening = false
        deviceMode = false
        val g = generation
        onState(State.RECOGNIZING)
        stt.finish(post = { r -> main.post(r) }) { text ->
            if (g != generation) return@finish
            onState(State.IDLE)
            when {
                text == null -> {
                    useDeviceStt = false
                    onProblem("기기 안 음성 인식을 쓰지 못했어요. 이번에는 구글 인식으로 바꿨으니 마이크 버튼을 눌러 다시 말해 보세요.")
                }
                text.isBlank() -> onProblem("말씀이 들리지 않았어요. 마이크 버튼을 눌러 다시 말해 보세요.")
                else -> onSaid(text, emptyList())
            }
        }
    }

    /** Loads the on-device model ahead of time. */
    fun warmUp() = stt.warmUp()

    /** Frees the on-device model (it takes a few hundred MB while loaded). */
    fun releaseModel() = stt.release()

    /** The learner is done talking: hands over everything said since the microphone opened. */
    fun finishListening() {
        if (deviceMode) { if (listening) finishDevice(); return }
        if (!listening) { deliver(); return }
        userDone = true
        runCatching { recognizer?.stopListening() }
        val g = generation
        // The recognizer normally answers at once; this only guards against one that never does.
        main.postDelayed({ if (g == generation && userDone && listening) deliver() }, 2500)
    }

    fun stop() {
        generation++
        pending = null
        streamGen = -1
        stt.cancel()
        deviceMode = false
        tts?.stop()
        cancelRecognizer()
        listening = false
        onState(State.IDLE)
    }

    fun shutdown() {
        stop()
        stt.release()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
    }

    private fun beginRecognition() {
        cancelRecognizer()
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            listening = false
            onState(State.IDLE)
            onProblem("이 기기에서 음성 인식을 사용할 수 없어요. Google 앱(음성 서비스)이 켜져 있는지 확인해 주세요.")
            return
        }
        val r = try { SpeechRecognizer.createSpeechRecognizer(app) } catch (_: Exception) { null }
        if (r == null) {
            listening = false
            onState(State.IDLE)
            onProblem("음성 인식을 시작하지 못했어요. 잠시 뒤 마이크 버튼을 다시 눌러 주세요.")
            return
        }
        recognizer = r
        r.setRecognitionListener(Listener(generation))
        listening = true
        onState(State.LISTENING)
        try {
            r.startListening(intent())
        } catch (_: Exception) {
            retryOrGiveUp(-1)
        }
    }

    private fun cancelRecognizer() {
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
    }

    private fun deliver() {
        val text = (said.toString() + " " + lastPartial).trim().replace(Regex("\\s+"), " ")
        cancelRecognizer()
        listening = false
        userDone = false
        generation++
        onState(State.IDLE)
        onPartial("")
        // The other readings only belong to the text when it came in one piece.
        val alts = if (segments == 1 && lastPartial.isBlank()) alternatives else emptyList()
        if (text.isBlank()) onProblem("말씀이 들리지 않았어요. 마이크 버튼을 눌러 다시 말해 보세요.") else onSaid(text, alts)
    }

    /** The recognizer ended without an answer: try again a few times, then stop and say why. */
    private fun retryOrGiveUp(code: Int) {
        failures++
        if (failures <= MAX_RETRIES) {
            val g = generation
            // A fresh recognizer a moment later: a recognizer created right after the last one was destroyed often fails.
            main.postDelayed({ if (g == generation && listening && !userDone) beginRecognition() }, 600L * failures)
            return
        }
        val partial = (said.toString() + " " + lastPartial).trim()
        if (userDone && partial.isNotBlank()) { deliver(); return }
        cancelRecognizer()
        listening = false
        generation++
        onState(State.IDLE)
        val why = if (isOnline()) "음성 인식 서비스에 연결하지 못했어요. 잠시 뒤 마이크 버튼을 다시 눌러 주세요." else "인터넷에 연결되어 있지 않아요. 연결한 뒤 마이크 버튼을 눌러 주세요."
        onProblem("$why (오류 코드 $code)")
    }

    @Suppress("DEPRECATION")
    private fun isOnline(): Boolean = try {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = manager.getNetworkCapabilities(manager.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true
    }

    private inner class Listener(private val gen: Int) : RecognitionListener {
        private fun current() = gen == generation && listening
        override fun onReadyForSpeech(params: Bundle?) { if (current()) failures = 0 }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            if (!current()) return
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
            lastPartial = text
            onPartial((said.toString() + " " + text).trim())
        }

        override fun onResults(results: Bundle?) {
            if (!current()) return
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = list.firstOrNull().orEmpty().trim()
            lastPartial = ""
            if (text.isNotEmpty()) {
                segments++
                alternatives = list.drop(1)
                if (said.isNotEmpty()) said.append(' ')
                said.append(text)
                silentRestarts = 0
                onPartial(said.toString())
            }
            if (userDone) { deliver(); return }
            // The recognizer closes after a pause; the learner has not tapped yet, so listen on.
            silentRestarts++
            if (said.isEmpty() && silentRestarts > MAX_SILENT_RESTARTS) { giveUpSilently(); return }
            val g = gen
            main.postDelayed({ if (g == generation && listening && !userDone) beginRecognition() }, 150L)
        }

        override fun onError(error: Int) {
            if (!current()) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    cancelRecognizer(); listening = false; generation++
                    onState(State.IDLE)
                    onProblem("마이크 권한이 필요해요. 설정에서 마이크를 허용해 주세요.")
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (userDone) { deliver(); return }
                    silentRestarts++
                    if (said.isEmpty() && silentRestarts > MAX_SILENT_RESTARTS) { giveUpSilently(); return }
                    val g = gen
                    main.postDelayed({ if (g == generation && listening && !userDone) beginRecognition() }, 200L)
                }
                else -> {
                    // Network, server, busy and client errors (codes 1, 2, 4, 5, 8, 11) are often short-lived and say nothing
                    // about the internet connection, so they are retried before the learner is told.
                    if (userDone && (said.isNotEmpty() || lastPartial.isNotEmpty())) { deliver(); return }
                    retryOrGiveUp(error)
                }
            }
        }
    }

    private fun giveUpSilently() {
        cancelRecognizer()
        listening = false
        generation++
        onState(State.IDLE)
        onProblem("말씀이 들리지 않았어요. 준비되면 마이크 버튼을 눌러 말해 보세요.")
    }

    private companion object {
        const val LANGUAGE = "en-US"
        const val MAX_RETRIES = 2
        const val MAX_SILENT_RESTARTS = 6
    }
}
