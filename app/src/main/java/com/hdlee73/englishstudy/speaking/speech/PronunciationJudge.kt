package com.hdlee73.englishstudy.speaking.speech

import android.content.Context
import com.hdlee73.englishstudy.ai.GenAi
import com.hdlee73.englishstudy.dictionary.MiniJson

/**
 * Speech recognition often writes a similar-sounding word for a learner's accent ("sheep" for "ship"), and the read-along
 * then marks the word as missed. With Gemini Nano on the device, the expected sentence tells which word was meant: a missed
 * word is forgiven only when Nano says it was most likely said AND the recognized text really contains something that
 * sounds like it (a letter-level similarity check), so a skipped or clearly different word is never accepted.
 */
internal object PronunciationJudge {
    private const val MIN_LETTER_SIMILARITY = 50

    fun buildPrompt(expected: String, heard: String, missing: List<String>): String = buildString {
        append("A Korean adult learner read an English sentence aloud, and a speech recognizer wrote down what it heard. ")
        append("Because of the learner's accent, the recognizer sometimes writes a different, similar-sounding word.\n")
        append("Sentence the learner was reading: \"").append(expected.trim()).append("\"\n")
        append("What the recognizer wrote: \"").append(heard.trim()).append("\"\n")
        append("Words of the sentence that did not appear exactly in the recognized text: ").append(missing.joinToString(", ") { "\"$it\"" }).append("\n\n")
        append("Which of those words did the learner most likely say, but the recognizer wrote as a similar-sounding word? ")
        append("Do not include a word that was skipped or replaced by a word that sounds clearly different. ")
        append("Reply with ONLY a JSON object like {\"said\":[\"word\"]}, with an empty list when none.")
    }

    private fun clean(word: String) = word.lowercase().filter { it.isLetterOrDigit() || it == '\'' }

    /** Letter-level similarity (0-100) of two words. */
    internal fun similarity(a: String, b: String): Int {
        if (a.isEmpty() && b.isEmpty()) return 100
        var previous = IntArray(b.length + 1) { it }
        a.forEachIndexed { i, ac ->
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            b.forEachIndexed { j, bc -> current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (ac == bc) 0 else 1) }
            previous = current
        }
        return (100 - previous[b.length] * 100 / maxOf(a.length, b.length)).coerceAtLeast(0)
    }

    /** The indexes (into the displayed words of [expected]) that [raw] forgives; [missing] are the displayed-word indexes still unmatched. */
    fun parse(expected: String, heard: String, missing: List<Int>, raw: String): Set<Int> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptySet()
        val map = (try { MiniJson.parse(raw.substring(start, end + 1)) } catch (_: IllegalArgumentException) { null }) as? Map<*, *> ?: return emptySet()
        val said = (map["said"] as? List<*>).orEmpty().mapNotNull { (it as? String)?.let(::clean) }.filter { it.isNotEmpty() }.toSet()
        val words = SpeechScorer.displayWords(expected)
        val heardWords = heard.split(Regex("\\s+")).map(::clean).filter { it.isNotEmpty() }
        return missing.filter { index ->
            val word = clean(words.getOrElse(index) { "" })
            word in said && heardWords.any { similarity(word, it) >= MIN_LETTER_SIMILARITY }
        }.toSet()
    }

    suspend fun forgive(context: Context, expected: String, heard: String, missing: List<Int>): Set<Int> {
        if (heard.isBlank() || missing.isEmpty()) return emptySet()
        // Checks Gemini Nano the first time this is needed (and starts its one-time download).
        GenAi.initialize(context)
        if (!GenAi.canChat()) return emptySet()
        val words = SpeechScorer.displayWords(expected)
        val raw = GenAi.generate(buildPrompt(expected, heard, missing.map { words.getOrElse(it) { "" } })) ?: return emptySet()
        return parse(expected, heard, missing, raw)
    }
}
