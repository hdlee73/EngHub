package com.hdlee73.englishstudy.docvoice

import com.hdlee73.englishstudy.R

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hdlee73.englishstudy.docvoice.core.AudioDecoder
import com.hdlee73.englishstudy.docvoice.core.ModelStore
import com.hdlee73.englishstudy.docvoice.core.PcmBuffer
import com.hdlee73.englishstudy.docvoice.core.Segment
import com.hdlee73.englishstudy.docvoice.core.Storage
import com.hdlee73.englishstudy.docvoice.core.Stt
import com.hdlee73.englishstudy.docvoice.core.LiveLang
import com.hdlee73.englishstudy.docvoice.core.LiveText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** 마이크 녹음 + 실시간 받아쓰기 (Silero VAD 로 말 구간을 잘라 Whisper 로 바로 인식). */
class RecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wake: PowerManager.WakeLock? = null

    private class Speech(val samples: FloatArray, val start: Int)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { RecorderHub.stopRequested.set(true); return START_NOT_STICKY }
            ACTION_PAUSE -> { RecorderHub.paused.set(!RecorderHub.paused.get()); return START_NOT_STICKY }
            ACTION_START -> {}
            else -> return START_NOT_STICKY
        }
        if (RecorderHub.job?.isActive == true) return START_NOT_STICKY
        ensureChannel()
        startForeground(NOTIF_ID, notification("준비 중"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DocVoice:rec").apply { acquire(4 * 60 * 60 * 1000L) }
        RecorderHub.stopRequested.set(false)
        RecorderHub.paused.set(false)
        RecorderHub.pcm = null
        RecorderHub.state.value = RecState(phase = RecPhase.Preparing, message = "준비 중")

        RecorderHub.job = scope.launch {
            try {
                record()
            } catch (e: CancellationException) {
                RecorderHub.upd { copy(phase = RecPhase.Failed, message = "녹음을 취소했습니다.") }
            } catch (e: SecurityException) {
                RecorderHub.state.value = RecState(phase = RecPhase.Failed, message = "마이크 권한이 필요합니다.")
            } catch (e: Throwable) {
                RecorderHub.upd { copy(
                    phase = RecPhase.Failed, message = "녹음 중 오류가 발생했습니다: ${e.message ?: e.javaClass.simpleName}",
                ) }
            } finally {
                wake?.let { if (it.isHeld) it.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private suspend fun record() {
        val sr = AudioDecoder.SAMPLE_RATE
        val store = ModelStore(this)
        val lang = LiveLang.of(RecorderHub.language)
        val alive = { scope.isActive && !RecorderHub.stopRequested.get() }

        withContext(Dispatchers.IO) {
            store.ensureLive(lang, alive) { m, f -> prep(m, f) }
        }
        if (RecorderHub.stopRequested.get()) { RecorderHub.reset(); return }
        prep("음성 인식 준비 중", null)
        // 한·영 혼합: 두 언어 인식기를 동시에 돌려 문장마다 확신도가 높은 쪽을 채택
        class Eng(val lang: LiveLang, val rec: com.k2fsa.sherpa.onnx.OnlineRecognizer) { val stream = rec.createStream() }
        val engs = lang.members.map { Eng(it, Stt.newLive(store, it)) }

        val pcm = PcmBuffer(sr * 60 * 5)
        RecorderHub.pcm = pcm
        val queue = Channel<FloatArray>(Channel.UNLIMITED)
        val segments = ArrayList<Segment>()
        // 한·영 혼합: 문장이 끝날 때마다 Whisper(다국어)로 다시 인식해 글자를 교체한다 (화면엔 먼저 빠른 결과가 보임)
        val mix = lang == LiveLang.MIX
        val refineQ = Channel<Triple<Int, Double, Double>>(Channel.UNLIMITED)
        val refineJob = if (!mix) null else scope.launch(Dispatchers.Default) {
            try {
                val wr = Stt.newRecognizer(store, com.hdlee73.englishstudy.docvoice.core.SttOptions("", com.hdlee73.englishstudy.docvoice.core.WhisperSize.SMALL, false), 2)
                try {
                    for ((idx, a, b) in refineQ) {
                        val from = ((a - 0.3).coerceAtLeast(0.0) * sr).toInt()
                        val to = minOf(((b + 0.3) * sr).toInt(), pcm.size)
                        if (to - from < sr / 2) continue
                        val seg = Stt.decodeSegment(wr, pcm.toFloats(from, to), from) ?: continue
                        synchronized(segments) {
                            if (idx < segments.size) segments[idx] = segments[idx].copy(text = LiveText.format(seg.text, "ko", true))
                            RecorderHub.upd { copy(segments = segments.toList()) }
                        }
                    }
                } finally { wr.release() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {}
        }

        // 인식 워커: 녹음 스레드와 분리해 오디오가 끊기지 않게 한다.
        var workerError: String? = null
        val worker = scope.launch(Dispatchers.Default) {
          try {
            var fed = 0L
            var segStart = 0.0
            var lastEnd = 0.0
            var active = false
            fun decodeAll() { for (e in engs) while (e.rec.isReady(e.stream)) e.rec.decode(e.stream) }
            // 가장 그럴듯한 후보 (텍스트, 언어키). 확신도(평균 로그확률)가 가장 높은 쪽.
            fun best(): Pair<String, String> {
                var bt = ""; var bl = engs[0].lang.key; var bs = Double.NEGATIVE_INFINITY
                for (e in engs) {
                    val r = e.rec.getResult(e.stream)
                    if (r.text.isBlank()) continue
                    val sc = if (r.ysProbs.isNotEmpty()) r.ysProbs.average() else -r.text.length * 0.01
                    if (sc > bs || bt.isEmpty()) { bs = sc; bt = r.text; bl = e.lang.key }
                }
                return bt to bl
            }
            fun finalizeSegment(now: Double) {
                val (raw, lk) = best()
                val text = LiveText.format(raw, lk, true)
                if (text.isNotEmpty()) {
                    val idx = synchronized(segments) { segments.add(Segment(segStart, maxOf(now, segStart + 0.3), text)); segments.size - 1 }
                    lastEnd = now
                    if (mix) refineQ.trySend(Triple(idx, segStart, maxOf(now, segStart + 0.3)))
                }
                for (e in engs) e.rec.reset(e.stream)
                active = false
                synchronized(segments) { RecorderHub.upd { copy(segments = segments.toList(), partial = "", speaking = false) } }
            }
            for (chunk in queue) {
                for (e in engs) e.stream.acceptWaveform(chunk, sr)
                fed += chunk.size
                decodeAll()
                val now = fed / sr.toDouble()
                val (raw, lk) = best()
                if (raw.isNotBlank() && !active) { active = true; segStart = maxOf(lastEnd, now - 0.5) }
                if (engs.any { it.rec.isEndpoint(it.stream) }) {
                    finalizeSegment(now)
                } else {
                    val part = LiveText.format(raw, lk, false)
                    RecorderHub.upd { copy(partial = part, speaking = part.isNotEmpty()) }
                }
            }
            // 마무리: 남은 꼬리 처리
            for (e in engs) { e.stream.acceptWaveform(FloatArray(sr / 2), sr); e.stream.inputFinished() }
            decodeAll()
            finalizeSegment(fed / sr.toDouble())
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            workerError = "인식 오류: ${e.message ?: e.javaClass.simpleName}"
            for (ignored in queue) { }
          }
        }

        val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val route = routeInput(RecorderHub.bluetooth)
        val rec = AudioRecord(
            if (route.bt) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, sr * 4),
        )
        route.device?.let { rec.preferredDevice = it }
        RecorderHub.upd { copy(source = route.name) }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); queue.close(); worker.cancel(); refineQ.close(); refineJob?.cancel(); engs.forEach { it.stream.release(); it.rec.release() }; route.cleanup()
            throw IllegalStateException("마이크를 시작할 수 없습니다.")
        }
        try {
            rec.startRecording()
            RecorderHub.upd { copy(phase = RecPhase.Recording, message = null, fraction = null) }
            val buf = ShortArray(1600) // 0.1초
            var lastNotify = 0L
            var peak = 0f
            withContext(Dispatchers.IO) {
                while (!RecorderHub.stopRequested.get() && scope.isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    if (RecorderHub.paused.get()) {
                        RecorderHub.upd { copy(phase = RecPhase.Paused, level = 0f) }
                        continue
                    }
                    var sum = 0.0
                    val f = FloatArray(n)
                    for (i in 0 until n) {
                        pcm.add(buf[i])
                        f[i] = buf[i] / 32768f
                        sum += f[i] * f[i]
                        val a = kotlin.math.abs(f[i])
                        if (a > peak) peak = a
                    }
                    queue.trySend(f)
                    val rms = sqrt(sum / n).toFloat()
                    RecorderHub.upd { copy(phase = RecPhase.Recording, seconds = pcm.seconds, level = (rms * 6f).coerceIn(0f, 1f), peak = peak) }
                    val now = System.currentTimeMillis()
                    if (now - lastNotify > 1000) {
                        lastNotify = now
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(fmt(pcm.seconds)))
                    }
                }
            }
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
            route.cleanup()
        }

        RecorderHub.upd { copy(phase = RecPhase.Finishing, message = null, level = 0f) }
        queue.close()
        worker.join()
        refineQ.close()
        refineJob?.join()
        engs.forEach { it.stream.release(); it.rec.release() }

        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        RecorderHub.upd { copy(phase = RecPhase.Stopped, seconds = pcm.seconds, segments = segments.toList(), wav = null, message = workerError, title = "녹음_$stamp") }
    }

    private class Route(val bt: Boolean, val name: String, val device: android.media.AudioDeviceInfo?, val cleanup: () -> Unit)

    /** 블루투스 마이크가 있으면(설정에 따라) 통화용 경로로 연결하고, 없으면 내장 마이크를 쓴다. */
    private suspend fun routeInput(useBt: Boolean): Route {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val inputs = try { am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS).toList() } catch (_: Exception) { emptyList() }
        val builtin = inputs.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC }
        val builtinRoute = Route(false, "내장 마이크", builtin) {}
        if (!useBt) return builtinRoute
        val bt = inputs.firstOrNull {
            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (android.os.Build.VERSION.SDK_INT >= 31 && it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
        } ?: return builtinRoute
        val label = "블루투스 · " + (bt.productName?.toString()?.takeIf { it.isNotBlank() } ?: "마이크")
        return try {
            am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val dev = am.availableCommunicationDevices.firstOrNull { it.id == bt.id }
                    ?: am.availableCommunicationDevices.firstOrNull { it.type == bt.type }
                if (dev == null || !am.setCommunicationDevice(dev)) { am.mode = android.media.AudioManager.MODE_NORMAL; return builtinRoute }
                kotlinx.coroutines.delay(600)
                Route(true, label, bt) { try { am.clearCommunicationDevice(); am.mode = android.media.AudioManager.MODE_NORMAL } catch (_: Exception) {} }
            } else {
                @Suppress("DEPRECATION") am.startBluetoothSco()
                @Suppress("DEPRECATION") am.isBluetoothScoOn = true
                kotlinx.coroutines.delay(1500)
                Route(true, label, bt) {
                    @Suppress("DEPRECATION") try { am.stopBluetoothSco(); am.isBluetoothScoOn = false; am.mode = android.media.AudioManager.MODE_NORMAL } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            try { am.mode = android.media.AudioManager.MODE_NORMAL } catch (_: Exception) {}
            builtinRoute
        }
    }

    private fun prep(msg: String, f: Float?) {
        RecorderHub.upd { copy(phase = RecPhase.Preparing, message = msg, fraction = f) }
    }

    private fun fmt(sec: Double): String {
        val s = sec.toInt()
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "녹음", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, com.hdlee73.englishstudy.MainActivity::class.java).putExtra(DocVoiceLink.EXTRA_OPEN, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.dv_ic_notif)
            .setContentTitle("녹음 중")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "중지", stop)
            .build()
    }

    companion object {
        const val ACTION_START = "com.hdlee73.englishstudy.docvoice.REC_START"
        const val ACTION_STOP = "com.hdlee73.englishstudy.docvoice.REC_STOP"
        const val ACTION_PAUSE = "com.hdlee73.englishstudy.docvoice.REC_PAUSE"
        private const val CHANNEL = "rec"
        private const val NOTIF_ID = 3
    }
}
