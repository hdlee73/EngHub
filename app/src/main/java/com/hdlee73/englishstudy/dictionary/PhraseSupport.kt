package com.hdlee73.englishstudy.dictionary

import java.util.Locale

/**
 * Keeps the Korean meanings short and word-like. Some dictionary rows carry a whole
 * explanation after the Korean word ("[동사] 비하다: 어떤 것을 기준으로 판단해 볼 때 그보다."); only
 * the word is kept so the list reads like a bilingual dictionary.
 */
internal object MeaningQuality {
    private val explained = Regex("""^(\s*\d+[.)]\s*)?(\[[^\]\n]+\]\s*)?([^:\n]{1,40}):\s+(.{10,})$""")
    private val numbering = Regex("""^\s*\d+[.)]\s*""")

    fun compact(korean: String): String = korean.lines().joinToString("\n") { line ->
        val m = explained.matchEntire(line)
        if (m == null) line else (m.groupValues[1] + m.groupValues[2] + m.groupValues[3]).trimEnd()
    }

    /** Merges numbered meaning lists (automatic translation first), dropping repeats and renumbering. */
    fun merge(first: String?, second: String?, max: Int = 4): String {
        val seen = mutableSetOf<String>()
        val lines = listOfNotNull(first, second).flatMap { it.lines() }
            .map { it.replace(numbering, "").trim() }
            .filter { it.isNotBlank() && seen.add(it.lowercase(Locale.ROOT)) }
            .take(max)
        return lines.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
    }
}

/** Finds expressions that resemble an idiom the dictionary has no exact entry for ("be compared to" → "compare to"). */
internal object PhraseSimilarity {
    private val skip = setOf("be", "to", "the", "a", "an", "one's", "ones", "someone", "somebody", "something", "oneself", "s")
    private val word = Regex("[a-z']+")

    private fun words(text: String) = word.findAll(text.lowercase(Locale.ROOT)).map { it.value }.toList()

    /** Content words of an expression, reduced to base forms by [lemma]. */
    fun tokens(text: String, lemma: (String) -> String): List<String> =
        words(text).filter { it !in skip }.map(lemma).filter { it.length >= 2 }.distinct()

    /** A full-text query matching any of the tokens as a prefix; null when nothing is searchable. */
    fun ftsQuery(tokens: List<String>): String? {
        val parts = tokens.map { it.replace("'", "") }.filter { it.length >= 2 && it.all { c -> c.isLetter() } }.take(4)
        return if (parts.isEmpty()) null else parts.joinToString(" OR ") { "$it*" }
    }

    /** Multi-word candidates sharing the most content words with [query], closest first. */
    fun rank(query: String, candidates: List<String>, lemma: (String) -> String, limit: Int = 6): List<String> {
        val queryTokens = tokens(query, lemma).toSet()
        if (queryTokens.isEmpty()) return emptyList()
        val queryWords = words(query).toSet()
        val needed = minOf(2, queryTokens.size)
        return candidates.asSequence().distinct()
            .filter { it.contains(' ') && !it.equals(query.trim(), true) }
            .map { candidate ->
                val overlap = tokens(candidate, lemma).count { it in queryTokens }
                val raw = words(candidate).count { it in queryWords }
                Triple(candidate, overlap, raw)
            }
            .filter { it.second >= needed }
            .sortedWith(compareBy({ -it.second }, { -it.third }, { kotlin.math.abs(it.first.length - query.length) }, { it.first }))
            .map { it.first }.take(limit).toList()
    }
}
