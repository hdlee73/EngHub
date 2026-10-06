package com.hdlee73.englishstudy.docvoice

import android.net.Uri
import com.hdlee73.englishstudy.docvoice.core.SttOptions
import com.hdlee73.englishstudy.docvoice.core.Storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

data class OutFile(val name: String, val uri: Uri, val mime: String)

sealed interface JobState {
    data object Idle : JobState
    data class Running(val title: String, val stage: String, val fraction: Float?) : JobState
    data class Done(val message: String, val files: List<OutFile>, val note: String?) : JobState
    data class Failed(val message: String) : JobState
}

sealed interface JobRequest {
    data class Tts(
        val doc: Storage.Picked,
        val koVoice: String,
        val enVoice: String,
        val speed: Double,
    ) : JobRequest

    data class Stt(
        val audio: Storage.Picked,
        val format: String,
        val opts: SttOptions,
        val gap: Double,
        val includeTime: Boolean,
        val showSpeaker: Boolean,
    ) : JobRequest

    /** 유튜브 영상의 소리를 mp3 로 */
    data class Yt(val url: String) : JobRequest

    /** 실시간 인식 모델 미리 내려받기 */
    data class ModelDownload(val lang: com.hdlee73.englishstudy.docvoice.core.LiveLang) : JobRequest

    /** 녹음(실시간 받아쓰기) 결과를 문서로 내보내기 */
    data class RecExport(
        val title: String,
        val format: String,
        val refine: Boolean,
        val lang: String,
        val size: com.hdlee73.englishstudy.docvoice.core.WhisperSize,
        val diarize: Boolean,
        val gap: Double,
        val includeTime: Boolean,
        val showSpeaker: Boolean,
    ) : JobRequest
}

/** 서비스(작업 실행)와 화면(상태 표시)이 공유하는 프로세스 단위 상태. */
object JobHub {
    val state = MutableStateFlow<JobState>(JobState.Idle)

    @Volatile var pending: JobRequest? = null
    @Volatile var job: Job? = null

    fun isRunning() = state.value is JobState.Running
}
