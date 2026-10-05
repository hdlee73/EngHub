package com.hdlee73.englishstudy.docvoice

import com.hdlee73.englishstudy.docvoice.core.PcmBuffer
import com.hdlee73.englishstudy.docvoice.core.Segment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean

enum class RecPhase { Idle, Preparing, Recording, Paused, Finishing, Stopped, Failed }

data class RecState(
    val phase: RecPhase = RecPhase.Idle,
    val seconds: Double = 0.0,
    val level: Float = 0f,
    val segments: List<Segment> = emptyList(),
    val speaking: Boolean = false,
    val partial: String = "",
    val message: String? = null,
    val fraction: Float? = null,
    val wav: OutFile? = null,
    val peak: Float = 0f,
    val title: String = "",
    val source: String = "",
)

/** 녹음 서비스와 화면이 공유하는 상태. 녹음이 끝나면 pcm/segments 가 내보내기에 쓰인다. */
object RecorderHub {
    val state = MutableStateFlow(RecState())

    @Volatile var pcm: PcmBuffer? = null
    @Volatile var job: Job? = null
    @Volatile var language: String = "en"
    @Volatile var bluetooth: Boolean = true
    val stopRequested = AtomicBoolean(false)
    val paused = AtomicBoolean(false)

    fun upd(block: RecState.() -> RecState) = state.update { it.block() }

    fun isActive() = state.value.phase in setOf(RecPhase.Preparing, RecPhase.Recording, RecPhase.Paused, RecPhase.Finishing)

    fun reset() {
        pcm = null
        state.value = RecState()
    }
}
