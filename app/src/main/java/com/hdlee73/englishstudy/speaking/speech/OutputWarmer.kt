package com.hdlee73.englishstudy.speaking.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import kotlin.concurrent.thread

/**
 * Keeps the media output path awake by streaming silence on an AudioTrack.
 *
 * When nothing has played for a moment the speaker amplifier (or a Bluetooth A2DP link)
 * goes to standby, and the first 100–300 ms of the next sound is lost while it powers up.
 * TTS "silent utterances" do not help because the engine only waits, it writes no audio.
 * A real track writing zeros keeps the path open, so speech and the result chime start
 * at their first sample.
 */
class OutputWarmer {
    private var track: AudioTrack? = null
    @Volatile private var running = false
    @Volatile private var startedAt = 0L

    val isRunning: Boolean get() = running

    /** Milliseconds the path has been held open (0 when stopped). */
    fun warmMillis(): Long = if (running) SystemClock.elapsedRealtime() - startedAt else 0L

    fun start() {
        if (running) return
        val rate = 16_000
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) return
        val created = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()
                )
                .setBufferSizeInBytes(maxOf(min, 3_200))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return
        if (created.state != AudioTrack.STATE_INITIALIZED) { created.release(); return }
        track = created
        running = true
        startedAt = SystemClock.elapsedRealtime()
        thread(name = "EngHub-output-warmer", isDaemon = true) {
            val zeros = ByteArray(1_600) // 50 ms
            runCatching {
                created.play()
                while (running) {
                    if (created.write(zeros, 0, zeros.size) < 0) break
                }
            }
            runCatching { created.stop() }
            created.release()
        }
    }

    fun stop() {
        running = false
        track = null
    }
}
