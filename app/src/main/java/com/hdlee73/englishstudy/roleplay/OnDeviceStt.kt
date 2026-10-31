package com.hdlee73.englishstudy.roleplay

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.hdlee73.englishstudy.docvoice.core.ModelStore
import com.hdlee73.englishstudy.docvoice.core.Segmenter
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlin.math.abs

/**
 * Speech recognition that runs on the phone (Whisper base.en through sherpa-onnx, the library of the DocVoice tab): no internet
 * and no Google speech service. It records while the learner talks and recognizes everything in one go when the learner taps,
 * so the answer comes a moment after the tap instead of word by word. Callbacks run on the thread that calls [finish]'s caller
 * through the supplied [post] function (the main thread).
 */
class OnDeviceStt(context: Context) {
    private val store = ModelStore(context.applicationContext)
    private val lock = Any()
    private var recognizer: OfflineRecognizer? = null
    @Volatile private var recording: AudioRecord? = null
    private var samples = FloatArray(SAMPLE_RATE * 10)
    private var count = 0
    @Volatile private var session = 0

    fun modelReady(): Boolean = store.speakingReady()

    /** Loads the model in the background so the first answer does not wait for it. */
    fun warmUp() {
        if (!modelReady()) return
        Thread { runCatching { recognizerOrCreate() } }.start()
    }

    fun release() {
        cancel()
        synchronized(lock) {
            runCatching { recognizer?.release() }
            recognizer = null
        }
    }

    private fun recognizerOrCreate(): OfflineRecognizer = synchronized(lock) {
        recognizer ?: run {
            val f = store.speakingWhisper()
            OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = f.encoder.path, decoder = f.decoder.path, language = "", task = "transcribe", tailPaddings = 1000
                        ),
                        tokens = f.tokens.path,
                        numThreads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
                        provider = "cpu"
                    )
                )
            ).also { recognizer = it }
        }
    }

    /** Starts recording; false when the microphone could not be opened. [onLimit] is called (on the recording thread) when the longest recording is reached. */
    @SuppressLint("MissingPermission")
    fun start(onLimit: () -> Unit, tee: ((ByteArray, Int) -> Unit)? = null): Boolean {
        cancel()
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return false
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, min * 4)
        } catch (_: Exception) { return false }
        if (record.state != AudioRecord.STATE_INITIALIZED) { record.release(); return false }
        try { record.startRecording() } catch (_: Exception) { record.release(); return false }
        val mine = ++session
        count = 0
        heardVoice = false
        silentMs = 0
        noiseFloor = 1f
        recording = record
        Thread {
            val buffer = ShortArray(SAMPLE_RATE / 10)
            while (mine == session && recording === record) {
                val n = record.read(buffer, 0, buffer.size)
                if (n <= 0) break
                if (tee != null) {
                    val bytes = ByteArray(n * 2)
                    for (i in 0 until n) { bytes[i * 2] = (buffer[i].toInt() and 0xff).toByte(); bytes[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte() }
                    runCatching { tee(bytes, bytes.size) }
                }
                trackVoice(buffer, n)
                synchronized(lock) {
                    if (mine != session) return@Thread
                    if (count + n > samples.size) samples = samples.copyOf(maxOf(samples.size * 2, count + n))
                    for (i in 0 until n) samples[count + i] = buffer[i] / 32768f
                    count += n
                }
                if (count >= MAX_SECONDS * SAMPLE_RATE) { onLimit(); break }
            }
        }.start()
        return true
    }

    @Volatile private var heardVoice = false
    @Volatile private var silentMs = 0
    private var noiseFloor = 1f
    private val decodeLock = Any()

    /** Tracks whether the learner has spoken yet and for how long it has been quiet since (energy based, adapts to room noise). */
    private fun trackVoice(buffer: ShortArray, n: Int) {
        var sum = 0.0
        for (i in 0 until n) { val v = buffer[i] / 32768.0; sum += v * v }
        val rms = Math.sqrt(sum / n).toFloat()
        noiseFloor = minOf(noiseFloor, maxOf(rms, 0.003f))
        if (rms > maxOf(0.02f, noiseFloor * 4f)) { heardVoice = true; silentMs = 0 }
        else silentMs += n * 1000 / SAMPLE_RATE
    }

    /** True when the learner has said something and has now been quiet for [quietMs]. */
    fun endpoint(quietMs: Int): Boolean = heardVoice && silentMs >= quietMs

    fun heardSpeech(): Boolean = heardVoice

    /** Recognizes what has been recorded so far without stopping the recording (for the live text). [onResult] gets null on failure. */
    fun peek(post: (Runnable) -> Unit, onResult: (String?) -> Unit) {
        val mine = session
        val pcm: FloatArray
        synchronized(lock) { pcm = samples.copyOf(count) }
        Thread {
            val text = try { recognize(pcm) } catch (_: Throwable) { null }
            post(Runnable { if (mine == session) onResult(text) })
        }.start()
    }

    /** Throws the recording away. */
    fun cancel() {
        session++
        val r = recording
        recording = null
        if (r != null) {
            runCatching { r.stop() }
            runCatching { r.release() }
        }
    }

    /**
     * Stops recording and recognizes what was said. [onResult] gets the text (empty when nothing was said) or null when the
     * model could not run; it is called on a background thread, so [post] hands it to the caller's thread.
     */
    fun finish(post: (Runnable) -> Unit, onResult: (String?) -> Unit) {
        val mine = session
        val r = recording
        recording = null
        if (r != null) {
            runCatching { r.stop() }
            runCatching { r.release() }
        }
        val pcm: FloatArray
        synchronized(lock) { pcm = samples.copyOf(count); count = 0 }
        Thread {
            val text: String? = try {
                recognize(pcm)
            } catch (_: Throwable) {
                null
            }
            post(Runnable { if (mine == session) onResult(text) })
        }.start()
    }

    private fun recognize(pcm: FloatArray): String {
        if (pcm.size < SAMPLE_RATE / 4) return ""
        val peak = pcm.maxOf { abs(it) }
        // Nothing but room noise: Whisper would invent a phrase over it.
        if (peak < 0.02f) return ""
        return synchronized(decodeLock) { decode(pcm) }
    }

    private fun decode(pcm: FloatArray): String {
        val rec = recognizerOrCreate()
        val out = ArrayList<String>()
        var from = 0
        while (from < pcm.size) {
            val to = minOf(pcm.size, from + CHUNK)
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(pcm.copyOfRange(from, to), SAMPLE_RATE)
                rec.decode(stream)
                val seconds = (to - from).toDouble() / SAMPLE_RATE
                Segmenter.cleanAsr(rec.getResult(stream).text, seconds)?.let { out.add(it) }
            } finally {
                stream.release()
            }
            from = to
        }
        return out.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val MAX_SECONDS = 60
        /** Whisper takes at most 30 seconds at a time. */
        const val CHUNK = 28 * SAMPLE_RATE
    }
}
