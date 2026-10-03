package com.hdlee73.englishstudy.study

import java.util.Locale

/** Key under which a word's progress is stored: case and surrounding spaces do not matter. */
internal fun progressKey(word: String): String = word.trim().lowercase(Locale.ROOT)

/** What the learner has done with one saved word. [box] 0 = new or just missed … [Leitner.MASTERED_BOX] = memorized. */
data class WordProgress(
    val box: Int = 0,
    val seen: Int = 0,
    val known: Int = 0,
    val missed: Int = 0,
    val quizCorrect: Int = 0,
    val quizWrong: Int = 0,
    /** Outcome of the latest flashcard or quiz answer; null = never answered. */
    val lastResult: Boolean? = null,
    val lastStudied: Long = 0L
) {
    val mastered: Boolean get() = box >= Leitner.MASTERED_BOX
}

/** Simple Leitner boxes: a known card moves up one box, a missed card (or wrong quiz answer) falls back to box 0. */
internal object Leitner {
    const val MASTERED_BOX = 3

    fun afterCard(progress: WordProgress, known: Boolean, now: Long): WordProgress =
        if (known) progress.copy(
            box = minOf(progress.box + 1, MASTERED_BOX), seen = progress.seen + 1, known = progress.known + 1,
            lastResult = true, lastStudied = now
        ) else progress.copy(
            box = 0, seen = progress.seen + 1, missed = progress.missed + 1,
            lastResult = false, lastStudied = now
        )

    /** A right quiz answer proves recall without moving the box; a wrong one sends the word back to box 0. */
    fun afterQuiz(progress: WordProgress, correct: Boolean, now: Long): WordProgress =
        if (correct) progress.copy(quizCorrect = progress.quizCorrect + 1, lastResult = true, lastStudied = now)
        else progress.copy(box = 0, quizWrong = progress.quizWrong + 1, lastResult = false, lastStudied = now)
}

/** Storage for [WordProgress]; the app uses SharedPreferences, tests use memory. */
internal interface ProgressStore {
    fun all(): Map<String, WordProgress>
    fun update(word: String, change: (WordProgress) -> WordProgress): WordProgress
    fun remove(word: String)
}

internal class MemoryProgressStore : ProgressStore {
    private val map = LinkedHashMap<String, WordProgress>()
    override fun all(): Map<String, WordProgress> = LinkedHashMap(map)
    override fun update(word: String, change: (WordProgress) -> WordProgress): WordProgress {
        val key = progressKey(word)
        return change(map[key] ?: WordProgress()).also { map[key] = it }
    }
    override fun remove(word: String) { map.remove(progressKey(word)) }
}

/** One word per line, tab-separated fields. Malformed lines are skipped so a damaged value never blocks study. */
internal object ProgressCodec {
    fun encode(map: Map<String, WordProgress>): String = map.entries.joinToString("\n") { (key, p) ->
        listOf(
            key, p.box, p.seen, p.known, p.missed, p.quizCorrect, p.quizWrong,
            when (p.lastResult) { true -> "1"; false -> "0"; null -> "-" }, p.lastStudied
        ).joinToString("\t")
    }

    fun decode(text: String): Map<String, WordProgress> {
        val out = LinkedHashMap<String, WordProgress>()
        text.lines().forEach { line ->
            val f = line.split('\t')
            if (f.size < 9 || f[0].isBlank()) return@forEach
            val numbers = f.subList(1, 7).map { it.toIntOrNull() ?: return@forEach }
            val last = when (f[7]) { "1" -> true; "0" -> false; else -> null }
            out[f[0]] = WordProgress(
                box = numbers[0].coerceIn(0, Leitner.MASTERED_BOX), seen = numbers[1], known = numbers[2],
                missed = numbers[3], quizCorrect = numbers[4], quizWrong = numbers[5],
                lastResult = last, lastStudied = f[8].toLongOrNull() ?: 0L
            )
        }
        return out
    }
}
