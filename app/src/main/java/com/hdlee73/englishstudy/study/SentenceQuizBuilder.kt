package com.hdlee73.englishstudy.study

import com.hdlee73.englishstudy.speaking.model.SentencePair
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/**
 * Quiz questions made from the sentences of a speaking dataset. Only sentences that come with a Korean
 * translation are used (it is the clue); the blanked part is chosen by [KeyWordPicker], and the three wrong
 * choices are key words of other sentences in the same dataset that look like the answer.
 */
internal object SentenceQuizBuilder {
    private class Prepared(val pair: SentencePair, val answer: String, val blanked: String)

    private fun prepare(pair: SentencePair): Prepared? {
        if (pair.korean.isBlank() || pair.english.isBlank()) return null
        val picked = KeyWordPicker.pick(pair.english) ?: return null
        val blanked = blank(pair.english, picked) ?: return null
        return Prepared(pair, normalize(pair.english, picked), blanked)
    }

    /** A capital letter only because the word starts the sentence is dropped, so it gives nothing away. */
    private fun normalize(sentence: String, picked: String): String {
        val atStart = sentence.trimStart().startsWith(picked)
        val plainCapital = picked[0].isUpperCase() && picked.drop(1).none { it.isUpperCase() }
        return if (atStart && plainCapital) picked.replaceFirstChar { it.lowercase(Locale.ROOT) } else picked
    }

    /** [sentence] with every occurrence of [text] (matched as whole words) replaced by [Blanker.BLANK]. */
    fun blank(sentence: String, text: String): String? {
        val pattern = text.trim().split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
        val regex = Regex("(?<![A-Za-z])$pattern(?![A-Za-z])", RegexOption.IGNORE_CASE)
        if (!regex.containsMatchIn(sentence)) return null
        return regex.replace(sentence, Blanker.BLANK)
    }

    /** Sentences that can be asked: Korean translation present and a key word found. */
    fun eligible(pairs: List<SentencePair>): List<SentencePair> = pairs.filter { prepare(it) != null }

    fun build(pairs: List<SentencePair>, count: Int, random: Random = Random.Default): List<QuizQuestion> {
        val prepared = pairs.distinctBy { it.english.trim().lowercase(Locale.ROOT) }.mapNotNull { prepare(it) }
        val pool = prepared.map { it.answer }.distinctBy { it.lowercase(Locale.ROOT) }
        return prepared.shuffled(random).take(count.coerceAtLeast(0)).mapIndexed { index, item ->
            val wrong = wrongChoices(item, pool, random)
            val choices = (wrong + item.answer).shuffled(random)
            QuizQuestion(
                wordId = -(index + 1).toLong(), word = item.answer, blankedSentence = item.blanked,
                sentence = item.pair.english.trim(), translation = item.pair.korean.trim(), meaningHint = "",
                choices = choices, answerIndex = choices.indexOf(item.answer), trackProgress = false
            )
        }
    }

    private fun wrongChoices(item: Prepared, pool: List<String>, random: Random): List<String> {
        val chosen = ArrayList<String>()
        for (source in listOf(pool, FallbackWords.ALL)) {
            if (chosen.size >= QuizBuilder.CHOICE_COUNT - 1) break
            val taken = chosen.map { it.lowercase(Locale.ROOT) }.toSet()
            val candidates = source.filter { it.lowercase(Locale.ROOT) !in taken && usable(it, item) }
            chosen += rank(item.answer, candidates, random).take(QuizBuilder.CHOICE_COUNT - 1 - chosen.size)
        }
        return chosen
    }

    /** A wrong choice must not be the answer, appear in the sentence, or be a form of the answer. */
    private fun usable(candidate: String, item: Prepared): Boolean =
        !candidate.equals(item.answer, ignoreCase = true) &&
            Blanker.find(item.pair.english, candidate).isEmpty() &&
            Blanker.find(candidate, item.answer).isEmpty() && Blanker.find(item.answer, candidate).isEmpty()

    private fun ending(word: String): Int {
        val w = word.substringBefore(' ').lowercase(Locale.ROOT)
        return when {
            w.endsWith("ing") -> 1
            w.endsWith("ed") -> 2
            w.endsWith("ly") -> 3
            w.endsWith("s") && !w.endsWith("ss") -> 4
            else -> 0
        }
    }

    /** Most similar first: same kind (phrase / word), same ending (-ing, -ed, -ly, -s), similar length. */
    private fun rank(answer: String, candidates: List<String>, random: Random): List<String> {
        fun kind(w: String) = if (w.contains(' ')) 1 else 0
        return candidates.shuffled(random).sortedWith(
            compareBy({ abs(kind(it) - kind(answer)) }, { if (ending(it) == ending(answer)) 0 else 1 }, { abs(it.length - answer.length) / 2 })
        )
    }
}
