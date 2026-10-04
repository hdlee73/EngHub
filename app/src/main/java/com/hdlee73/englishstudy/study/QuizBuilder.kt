package com.hdlee73.englishstudy.study

import com.hdlee73.englishstudy.dictionary.WordEntry
import com.hdlee73.englishstudy.dictionary.examplePairs
import kotlin.math.abs
import kotlin.random.Random

/**
 * One multiple-choice question: an example sentence of a saved word with that word blanked out, its
 * Korean translation as the clue, and four candidate words.
 */
data class QuizQuestion(
    val wordId: Long,
    /** The correct answer, as saved. */
    val word: String,
    /** The example sentence with the word replaced by blanks. */
    val blankedSentence: String,
    /** The complete example sentence. */
    val sentence: String,
    /** Korean translation of the sentence; empty when the example has none. */
    val translation: String,
    /** First Korean meaning of the word, offered as the clue when [translation] is empty. */
    val meaningHint: String,
    val choices: List<String>,
    val answerIndex: Int,
    /** false for questions made from a speaking dataset: they are not tied to a saved word's progress. */
    val trackProgress: Boolean = true
) {
    fun isCorrect(choice: Int): Boolean = choice == answerIndex

    /** The same question with the choices in a new order. */
    fun reshuffled(random: Random = Random.Default): QuizQuestion {
        val shuffled = choices.shuffled(random)
        return copy(choices = shuffled, answerIndex = shuffled.indexOf(choices[answerIndex]))
    }
}

internal object QuizBuilder {
    const val CHOICE_COUNT = 4

    private class Candidate(val sentence: String, val translation: String, val blanked: String)

    private fun candidates(entry: WordEntry): List<Candidate> =
        examplePairs(entry.examples).mapNotNull { (english, korean) ->
            Blanker.blank(english, entry.word)?.let { Candidate(english, korean, it) }
        }

    /** Saved words that have at least one example sentence in which the word can be blanked out. */
    fun eligible(words: List<WordEntry>): List<WordEntry> = words.filter { candidates(it).isNotEmpty() }

    /**
     * Up to [count] questions, one per word, in random order. Wrong choices are other saved words;
     * when fewer than three exist, common words from [FallbackWords] fill the gap.
     */
    fun build(words: List<WordEntry>, count: Int, random: Random = Random.Default): List<QuizQuestion> {
        val saved = words.map { it.word.trim() }.filter { it.isNotEmpty() }
        return eligible(words).shuffled(random).take(count.coerceAtLeast(0)).map { entry ->
            val example = candidates(entry).random(random)
            val answer = entry.word.trim()
            val wrong = pickWrongChoices(answer, example.sentence, saved, random)
            val choices = (wrong + answer).shuffled(random)
            QuizQuestion(
                wordId = entry.id, word = answer, blankedSentence = example.blanked, sentence = example.sentence,
                translation = example.translation, meaningHint = firstMeaning(entry.korean),
                choices = choices, answerIndex = choices.indexOf(answer)
            )
        }
    }

    private fun pickWrongChoices(answer: String, sentence: String, saved: List<String>, random: Random): List<String> {
        val chosen = ArrayList<String>()
        for (pool in listOf(saved, FallbackWords.ALL)) {
            if (chosen.size >= CHOICE_COUNT - 1) break
            val taken = chosen.map { it.lowercase() }.toSet()
            chosen += rank(answer, sentence, pool.filter { it.lowercase() !in taken }, random).take(CHOICE_COUNT - 1 - chosen.size)
        }
        return chosen
    }

    /** Candidates that look like the answer (single word or phrase, similar length) and cannot also be right. */
    private fun rank(answer: String, sentence: String, pool: List<String>, random: Random): List<String> {
        val a = answer.trim()
        fun shape(word: String) = if (word.contains(' ')) 1 else 0
        return pool.map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(a, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            // A candidate must not appear in the sentence, nor be an inflected form of the answer (or the reverse).
            .filter { Blanker.find(sentence, it).isEmpty() && Blanker.find(it, a).isEmpty() && Blanker.find(a, it).isEmpty() }
            .shuffled(random)
            .sortedWith(compareBy({ abs(shape(it) - shape(a)) }, { abs(it.length - a.length) / 3 }))
    }

    /** The first Korean sense of an entry without its numbering or part-of-speech tag. */
    fun firstMeaning(korean: String): String {
        val tagOnly = Regex("^\\[[^\\]]*]\\s*$")
        val line = korean.lines().map { it.trim() }
            .firstOrNull { it.any { c -> c in '가'..'힣' } && !tagOnly.matches(it) }
            ?: return ""
        return line.replace(Regex("^\\d+[.)]\\s*"), "").take(60)
    }
}

/** Everyday words used only when the learner has too few saved words to make four choices. */
internal object FallbackWords {
    val ALL: List<String> = (
        "improve decide believe prepare explain remember suggest consider develop produce receive support " +
        "describe discuss express reduce increase protect achieve require provide include follow continue " +
        "create connect compare collect choose carry borrow arrive agree accept account affect attend avoid " +
        "behave belong break build cancel celebrate challenge change check clean climb complain complete " +
        "concern control cook cover cross damage deliver depend design destroy discover divide earn enjoy " +
        "enter escape examine exist fail fill finish gather grow guess handle hurry imagine invite join " +
        "judge manage measure mention notice offer order organize owe pack paint perform plan pour practice " +
        "promise raise reach recognize relax repair repeat replace report respect return reveal share shout " +
        "solve spend stretch succeed suffer supply survive teach thank trust visit waste wonder worry"
        ).split(" ") + listOf(
        "give up", "put off", "take off", "look after", "run out of", "figure out", "get along", "break down",
        "turn down", "set up", "carry on", "call off", "hold on", "pick up", "show up", "work out"
    )
}
