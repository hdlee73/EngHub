package com.hdlee73.englishstudy.listening

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.hdlee73.englishstudy.docvoice.JobHub
import com.hdlee73.englishstudy.docvoice.JobRequest
import com.hdlee73.englishstudy.docvoice.JobService
import com.hdlee73.englishstudy.docvoice.JobState
import com.hdlee73.englishstudy.docvoice.core.Storage
import com.hdlee73.englishstudy.docvoice.core.SttOptions
import com.hdlee73.englishstudy.docvoice.core.WhisperSize

/**
 * Makes SRT subtitles straight from playlist tracks: the DocVoice speech-to-text job runs on the tracks in the background
 * and links each SRT to its track, so the Listening player shows it as that track's subtitles (and DocVoice keeps a copy in Downloads).
 */
object SrtMaker {
    /** Starts the job for [entries]; tracks that already have a subtitle are skipped. Returns how many tracks were queued. */
    fun start(context: Context, entries: List<TrackStore.Entry>, language: String = "en"): Int {
        val app = context.applicationContext
        if (JobHub.isRunning()) {
            Toast.makeText(app, "다른 변환이 진행 중이에요. 끝난 뒤 다시 눌러 주세요. (진행 상황은 DocVoice 탭)", Toast.LENGTH_LONG).show()
            return 0
        }
        val todo = entries.distinctBy { it.uri }.filter { SubtitleLinks.find(app, it.uri, it.name) == null }
        if (todo.isEmpty()) {
            Toast.makeText(app, "이미 모두 자막이 있어요.", Toast.LENGTH_SHORT).show()
            return 0
        }
        val audios = todo.map { Storage.Picked(Uri.parse(it.uri), it.name, -1L) }
        JobHub.pending = JobRequest.SttBatch(audios, SttOptions(language, WhisperSize.SMALL, false))
        JobHub.state.value = JobState.Running("준비 중", "시작하는 중", null)
        ContextCompat.startForegroundService(app, Intent(app, JobService::class.java))
        Toast.makeText(app, "자막 ${audios.size}개를 만들기 시작했어요. 알림이나 DocVoice 탭에서 진행 상황을 볼 수 있어요.", Toast.LENGTH_LONG).show()
        return audios.size
    }
}
