package com.hdlee73.englishstudy.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Facade of the on-device AI features. It composes Gemini Nano (ML Kit GenAI proofreading/rewriting) when the
 * device supports it with the always-offline [RuleEngine], so every caller gets an answer on every device.
 */
object Tutor {

    /** Badge text for the current engine; follows [GenAi.status] so it updates after a model download. */
    fun engineLabel(status: GenAi.Status = GenAi.status.value): String = when (status) {
        GenAi.Status.AVAILABLE -> "Gemini Nano · 기기 내 AI"
        GenAi.Status.DOWNLOADING -> "Gemini Nano 받는 중 · 기본 엔진 사용"
        GenAi.Status.UNAVAILABLE -> "기본 엔진 · 이 기기는 Gemini Nano를 쓸 수 없어요"
        GenAi.Status.CHECKING -> "AI 엔진 확인 중..."
    }

    data class CorrectionResult(
        val original: String,
        val corrected: String,
        val diffs: List<RuleEngine.Correction>,
        val paraphrases: List<String>,
        val usedModel: Boolean,
    )

    suspend fun correct(input: String): CorrectionResult = withContext(Dispatchers.IO) {
        var base = RuleEngine.fix(input)
        var usedModel = false
        GenAi.proofread(base)?.let { nano ->
            if (nano.isNotBlank()) { base = nano.trim(); usedModel = true }
        }
        val paraphrases = GenAi.rephrase(base).ifEmpty { RuleEngine.paraphrase(base) }
        val diffs = RuleEngine.findCorrections(input, base)
        CorrectionResult(input, base, diffs, paraphrases, usedModel)
    }
}
