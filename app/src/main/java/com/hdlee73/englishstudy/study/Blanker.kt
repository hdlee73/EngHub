package com.hdlee73.englishstudy.study

import com.hdlee73.englishstudy.dictionary.WordForms
import java.util.Locale

/**
 * Finds a saved word (or an inflected form of it) inside an example sentence and blanks it out for
 * the quiz. "go" is found in "She went home" and "run" in "He was running"; a phrase such as
 * "look forward to" is found as "looking forward to" because only its first word is inflected.
 */
internal object Blanker {
    const val BLANK = "_____"

    private val vowels = "aeiou"

    /** Character ranges of [word], or an inflected form of it, inside [sentence], in reading order. */
    fun find(sentence: String, word: String): List<IntRange> {
        val tokens = word.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty() || sentence.isBlank()) return emptyList()
        val head = variants(tokens.first()).sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val rest = tokens.drop(1).joinToString("") { "\\s+" + Regex.escape(it) }
        val regex = Regex("(?<![A-Za-z])(?:$head)$rest(?![A-Za-z])", RegexOption.IGNORE_CASE)
        return regex.findAll(sentence).map { it.range }.toList()
    }

    /** The sentence with every occurrence of the word replaced by [BLANK]; null when the word is not in it. */
    fun blank(sentence: String, word: String): String? {
        val spans = find(sentence, word)
        if (spans.isEmpty()) return null
        val out = StringBuilder()
        var cursor = 0
        for (span in spans) {
            out.append(sentence, cursor, span.first)
            out.append(BLANK)
            cursor = span.last + 1
        }
        out.append(sentence, cursor, sentence.length)
        return out.toString()
    }

    private fun isConsonant(c: Char) = c in 'a'..'z' && c !in vowels

    /** The word itself plus the regular and irregular inflections it can take in a sentence. */
    internal fun variants(token: String): Set<String> {
        val t = token.lowercase(Locale.ROOT)
        val out = linkedSetOf(t)
        // Contractions, hyphenated words and one-letter words are matched exactly.
        if (t.length < 2 || !t.all { it in 'a'..'z' }) return out
        out += WordForms.irregularFormsOf(t)
        when {
            t.endsWith("ie") -> { out += t + "s"; out += t + "d"; out += t.dropLast(2) + "ying" }
            t.endsWith("e") -> { out += t + "s"; out += t + "d"; out += t.dropLast(1) + "ing" }
            t.endsWith("y") && isConsonant(t[t.length - 2]) -> {
                out += t.dropLast(1) + "ies"; out += t.dropLast(1) + "ied"; out += t + "ing"
            }
            else -> {
                out += t + "s"; out += t + "ed"; out += t + "ing"
                if (t.endsWith("s") || t.endsWith("x") || t.endsWith("z") || t.endsWith("ch") || t.endsWith("sh") || t.endsWith("o")) out += t + "es"
                // stop → stopped / stopping, run → running: a final consonant after one vowel doubles.
                val last = t.last()
                if (t.length >= 3 && isConsonant(last) && last !in "wxy" && t[t.length - 2] in vowels && isConsonant(t[t.length - 3])) {
                    out += t + last + "ed"; out += t + last + "ing"
                }
            }
        }
        return out
    }
}
