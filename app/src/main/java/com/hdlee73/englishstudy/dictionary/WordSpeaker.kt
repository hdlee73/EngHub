package com.hdlee73.englishstudy.dictionary

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.AudioManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Own TTS lifecycle and queue a tap made before asynchronous initialization finishes. */
internal class WordSpeaker(private val activity: Activity, private val notice: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var initializing = false
    private var closed = false
    private var pending: String? = null
    private var generation = 0
    private var sequence = 0
    private var errorDialog: AlertDialog? = null

    init { initialize() }

    private fun initialize() {
        if (closed || initializing) return
        engine?.shutdown()
        ready = false
        initializing = true
        val current = ++generation
        engine = TextToSpeech(activity.applicationContext) { result ->
            // Post even on the main thread: the constructor must first assign engine.
            main.post {
                if (closed || current != generation) return@post
                initializing = false
                val speech = engine ?: return@post
                if (result != TextToSpeech.SUCCESS) {
                    if (pending != null) showProblem("기기의 음성 엔진을 시작하지 못했습니다.")
                    pending = null
                    return@post
                }
                val supported = listOf(Locale.US, Locale.UK, Locale.ENGLISH)
                    .firstOrNull { speech.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
                if (supported == null) {
                    if (pending != null) showProblem("영어 음성 데이터가 없습니다. 음성 설정에서 영어를 설치해 주세요.")
                    pending = null
                    return@post
                }
                val installed = speech.voices.orEmpty().filter {
                    it.locale.language == "en" && !it.isNetworkConnectionRequired &&
                        TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
                }.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == "US" }
                    .thenByDescending { it.quality })
                installed.firstOrNull()?.let { speech.setVoice(it) }
                speech.setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                speech.setSpeechRate(0.9f)
                speech.setPitch(1f)
                speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    @Deprecated("Required by platform listener")
                    override fun onError(utteranceId: String?) = reportError(current)
                    override fun onError(utteranceId: String?, errorCode: Int) = reportError(current)
                })
                ready = true
                pending?.let { text -> pending = null; speak(text) }
            }
        }
    }

    private fun reportError(current: Int) {
        main.post {
            if (!closed && current == generation)
                showProblem("영어 음성을 재생하지 못했습니다. 미디어 음량과 영어 음성 데이터를 확인해 주세요.")
        }
    }

    fun speak(raw: String) {
        // Never read pronunciation symbols aloud ("remnant [rémnənt]" is spoken as "remnant").
        val text = com.hdlee73.englishstudy.study.WordIpa.speakable(raw)
        if (closed || text.isBlank()) return
        if (!ready) {
            pending = text
            initialize()
            notice("영어 음성을 준비하고 있습니다.")
            return
        }
        val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
            notice("미디어 음량을 올린 뒤 다시 눌러 주세요.")
            return
        }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
        if (engine?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "word-" + ++sequence) != TextToSpeech.SUCCESS) {
            ready = false
            showProblem("음성 재생을 시작하지 못했습니다. 음성 설정을 확인한 뒤 다시 눌러 주세요.")
        }
    }

    private fun showProblem(message: String) {
        if (closed || activity.isFinishing || activity.isDestroyed || errorDialog?.isShowing == true) return
        errorDialog = AlertDialog.Builder(activity).setTitle("듣기").setMessage(message)
            .setPositiveButton("음성 설정") { _, _ ->
                ready = false
                try { activity.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                catch (_: Exception) {
                    try { activity.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
                    catch (_: Exception) { notice("기기 설정에서 ‘텍스트 읽어주기’를 열어 주세요.") }
                }
            }.setNegativeButton("닫기", null).show()
    }

    /** Stops a word that is being read out, e.g. when the learner leaves the screen. */
    fun stop() {
        pending = null
        if (!closed && ready) engine?.stop()
    }

    fun close() {
        closed = true
        generation++
        pending = null
        main.removeCallbacksAndMessages(null)
        errorDialog?.dismiss()
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
