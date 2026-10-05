package com.hdlee73.englishstudy.docvoice

import com.hdlee73.englishstudy.R

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.hdlee73.englishstudy.docvoice.core.AudioDecoder
import com.hdlee73.englishstudy.docvoice.core.EdgeTts
import com.hdlee73.englishstudy.docvoice.core.ExtractException
import com.hdlee73.englishstudy.docvoice.core.Exporter
import com.hdlee73.englishstudy.docvoice.core.ModelException
import com.hdlee73.englishstudy.docvoice.core.ModelStore
import com.hdlee73.englishstudy.docvoice.core.PdfExporter
import com.hdlee73.englishstudy.docvoice.core.PdfText
import com.hdlee73.englishstudy.docvoice.core.Segmenter
import com.hdlee73.englishstudy.docvoice.core.Storage
import com.hdlee73.englishstudy.docvoice.core.Stt
import com.hdlee73.englishstudy.docvoice.core.TtsEngine
import com.hdlee73.englishstudy.docvoice.core.TtsException
import com.hdlee73.englishstudy.docvoice.core.TtsPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 변환 작업을 포그라운드 서비스에서 실행해 화면을 꺼도/앱을 나가도 계속되게 한다. */
class JobService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wake: PowerManager.WakeLock? = null
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            JobHub.job?.cancel()
            if (JobHub.job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        val req = JobHub.pending
        if (req == null || JobHub.job?.isActive == true) {
            if (req == null && JobHub.job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        JobHub.pending = null
        ensureChannel()
        val title = when (req) { is JobRequest.Tts -> "문서 → 음성"; is JobRequest.Stt -> "음성 → 문서"; is JobRequest.RecExport -> "녹음 → 문서"; is JobRequest.ModelDownload -> "모델 내려받기" }
        startForeground(NOTIF_ID, buildNotification(title, "준비 중", null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DocVoice:job").apply { acquire(3 * 60 * 60 * 1000L) }

        JobHub.state.value = JobState.Running(title, "준비 중", null)
        JobHub.job = scope.launch {
            try {
                when (req) {
                    is JobRequest.Tts -> runTts(req, title)
                    is JobRequest.Stt -> runStt(req, title)
                    is JobRequest.RecExport -> runRecExport(req, title)
                    is JobRequest.ModelDownload -> runModelDownload(req, title)
                }
            } catch (e: CancellationException) {
                JobHub.state.value = JobState.Failed("작업을 취소했습니다.")
            } catch (e: ExtractException) {
                JobHub.state.value = JobState.Failed(e.message ?: "문서를 읽지 못했습니다.")
            } catch (e: TtsException) {
                JobHub.state.value = JobState.Failed(e.message ?: "음성 합성에 실패했습니다.")
            } catch (e: ModelException) {
                JobHub.state.value = JobState.Failed(e.message ?: "모델을 준비하지 못했습니다.")
            } catch (e: AudioDecoder.DecodeException) {
                JobHub.state.value = JobState.Failed(e.message ?: "오디오를 읽지 못했습니다.")
            } catch (e: OutOfMemoryError) {
                JobHub.state.value = JobState.Failed("메모리가 부족합니다. 더 짧은 파일로 나눠 시도하세요.")
            } catch (e: Throwable) {
                JobHub.state.value = JobState.Failed("오류가 발생했습니다: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                wake?.let { if (it.isHeld) it.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifyFinished()
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

    // ---- 문서 → 음성 ----------------------------------------------------------

    private suspend fun runTts(req: JobRequest.Tts, title: String) {
        progress(title, "문서 읽는 중", null)
        val text = withContext(Dispatchers.IO) {
            val bytes = Storage.readBytes(this@JobService, req.doc.uri)
            com.hdlee73.englishstudy.docvoice.core.Extractors.extract(req.doc.name, bytes, PdfText.reader(this@JobService))
        }
        val chunks = TtsPlanner.buildChunks(text)
        if (chunks.isEmpty()) throw ExtractException("읽을 텍스트가 없습니다.")
        progress(title, "음성 만드는 중 (0/${chunks.size})", 0f)
        val mp3 = TtsEngine.synthesizeAll(chunks, req.koVoice, req.enVoice, req.speed, EdgeTts()) { done, total ->
            progress(title, "음성 만드는 중 ($done/$total)", done.toFloat() / total)
        }
        progress(title, "저장하는 중", null)
        val name = Storage.baseName(req.doc.name) + ".mp3"
        val uri = withContext(Dispatchers.IO) { Storage.saveToDownloads(this@JobService, name, mp3) }
        JobHub.state.value = JobState.Done(
            "MP3 변환 완료  (${text.length}자)",
            listOf(OutFile(name, uri, "audio/mpeg")),
            null,
        )
    }

    // ---- 음성 → 문서 ----------------------------------------------------------

    private suspend fun runStt(req: JobRequest.Stt, title: String) {
        val job = currentCoroutineContext()[Job]!!
        val active = { job.isActive }
        progress(title, "오디오 읽는 중", 0f)
        val pcm = withContext(Dispatchers.IO) {
            AudioDecoder.decode(this@JobService, req.audio.uri, active) { progress(title, "오디오 읽는 중", it) }
        }
        val store = ModelStore(this)
        val result = withContext(Dispatchers.Default) {
            Stt.transcribe(store, pcm, req.opts, active) { stage, f -> progress(title, stage, f) }
        }
        if (result.segments.isEmpty()) throw ExtractException("음성에서 인식된 말이 없습니다.")
        val bytes = finishDocument(title, result, req.format, Storage.baseName(req.audio.name), req.gap, req.includeTime, req.showSpeaker)
        // An SRT made from an audio file becomes that file's subtitle in the Listening player.
        if (req.format == "srt") runCatching {
            com.hdlee73.englishstudy.listening.SubtitleLinks.remember(this, req.audio.uri.toString(), req.audio.name, bytes)
        }
    }

    private suspend fun runModelDownload(req: JobRequest.ModelDownload, title: String) {
        val job = currentCoroutineContext()[Job]!!
        progress(title, "${req.lang.label} 모델 내려받는 중", 0f)
        withContext(Dispatchers.IO) {
            ModelStore(this@JobService).ensureLive(req.lang, { job.isActive }) { m, f -> progress(title, "${req.lang.label} · $m", f) }
        }
        JobHub.state.value = JobState.Done("${req.lang.label} 모델 준비 완료", emptyList(), null)
    }

    private suspend fun runRecExport(req: JobRequest.RecExport, title: String) {
        val pcm = RecorderHub.pcm ?: throw ExtractException("녹음 데이터가 없습니다.")
        var segs = RecorderHub.state.value.segments
        val store = ModelStore(this)
        if (req.refine) {
            val job0 = currentCoroutineContext()[Job]!!
            val r = withContext(Dispatchers.Default) {
                Stt.transcribe(store, pcm, com.hdlee73.englishstudy.docvoice.core.SttOptions(req.lang, req.size, false), { job0.isActive }) { m, f -> progress(title, m, f) }
            }
            if (r.segments.isNotEmpty()) segs = r.segments
        }
        if (segs.isEmpty()) throw ExtractException("인식된 말이 없습니다.")
        var turns: List<Triple<Double, Double, Int>> = emptyList()
        var note: String? = null
        if (req.diarize) {
            if (pcm.seconds > Stt.MAX_DIARIZE_SECONDS) {
                note = "녹음이 ${Stt.MAX_DIARIZE_SECONDS / 60}분보다 길어 화자 구분은 건너뛰었습니다."
            } else {
                val job = currentCoroutineContext()[Job]!!
                try {
                    withContext(Dispatchers.Default) {
                        store.ensureDiar({ job.isActive }) { m, f -> progress(title, m, f) }
                        progress(title, "화자 구분 중", null)
                        turns = Stt.diarize(store, pcm, Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    note = "화자 구분에 실패해 화자 정보 없이 저장합니다. (${e.message})"
                }
            }
        }
        finishDocument(title, com.hdlee73.englishstudy.docvoice.core.SttResult(segs, turns, note), req.format, req.title, req.gap, req.includeTime, req.showSpeaker)
    }

    private suspend fun finishDocument(
        title: String, result: com.hdlee73.englishstudy.docvoice.core.SttResult, format: String, docTitle: String,
        gap: Double, includeTime: Boolean, showSpeakerReq: Boolean,
    ): ByteArray {
        progress(title, "문서로 정리하는 중", null)
        val (sentences, paragraphs) = Stt.toDocument(result, gap)
        val speakers = showSpeakerReq && result.turns.isNotEmpty()
        val bytes = withContext(Dispatchers.IO) {
            when (format) {
                "xlsx" -> Exporter.xlsx(sentences, includeTime, speakers)
                "docx" -> Exporter.docx(paragraphs, docTitle, includeTime, speakers)
                "srt" -> Exporter.srt(sentences, speakers)
                "pdf" -> PdfExporter.export(this@JobService, paragraphs, docTitle, includeTime, speakers)
                else -> Exporter.txt(paragraphs, includeTime, speakers)
            }
        }
        val name = "$docTitle.$format"
        val uri = withContext(Dispatchers.IO) { Storage.saveToDownloads(this@JobService, name, bytes) }
        val n = result.turns.map { it.third }.distinct().size
        val info = buildString {
            append("${sentences.size}문장")
            if (format != "xlsx" && format != "srt") append(" · ${paragraphs.size}문단")
            if (n > 0) append(" · 화자 ${n}명")
        }
        JobHub.state.value = JobState.Done(
            "문서 변환 완료  ($info)",
            listOf(OutFile(name, uri, Storage.mimeFor(name))),
            result.diarizationNote,
        )
        return bytes
    }

    // ---- 알림 -----------------------------------------------------------------

    private fun progress(title: String, stage: String, fraction: Float?) {
        JobHub.state.value = JobState.Running(title, stage, fraction)
        val now = System.currentTimeMillis()
        if (now - lastNotify > 700) {
            lastNotify = now
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(title, stage, fraction))
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "변환 진행", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "변환 완료", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, com.hdlee73.englishstudy.MainActivity::class.java).putExtra(DocVoiceLink.EXTRA_OPEN, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun buildNotification(title: String, stage: String, fraction: Float?): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, JobService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.dv_ic_notif)
            .setContentTitle("DocVoice · $title")
            .setContentText(stage)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
            .addAction(0, "취소", cancel)
            .build()
    }

    private fun notifyFinished() {
        val s = JobHub.state.value
        val text = when (s) {
            is JobState.Done -> s.message
            is JobState.Failed -> s.message
            else -> return
        }
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.dv_ic_notif)
            .setContentTitle(if (s is JobState.Done) "변환이 끝났어요" else "변환하지 못했어요")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_DONE_ID, n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.hdlee73.englishstudy.docvoice.CANCEL"
        private const val CHANNEL = "job"
        private const val CHANNEL_DONE = "job_done"
        private const val NOTIF_ID = 1
        private const val NOTIF_DONE_ID = 2
    }
}
