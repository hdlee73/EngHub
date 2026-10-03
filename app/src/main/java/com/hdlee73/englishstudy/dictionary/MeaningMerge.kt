package com.hdlee73.englishstudy.dictionary

/**
 * Completes a Korean sense list with senses from another dictionary and orders it like the English
 * entry. Each source only lists the senses it has a Korean gloss for, so one list can lack a whole
 * part of speech (palm showed only the verb) or the sense an example uses.
 */
internal object MeaningMerge {
    private val partNames = mapOf("noun" to "명사", "verb" to "동사", "adjective" to "형용사", "adverb" to "부사",
        "pronoun" to "대명사", "preposition" to "전치사", "conjunction" to "접속사", "interjection" to "감탄사")
    private val numbered = Regex("^\\s*\\d+[.)]\\s*(.*)$")
    private val partLabel = Regex("^\\[([^\\]]+)]\\s*(.*)$", RegexOption.DOT_MATCHES_ALL)

    /** Korean part-of-speech label for an English one (noun → 명사), null for the rare ones. */
    fun koreanPart(english: String): String? = partNames[english.trim().lowercase()]

    fun normalize(label: String): String = when {
        "대명사" in label -> "대명사"
        "명사" in label -> "명사"
        "동사" in label -> "동사"
        "형용사" in label || "관형사" in label -> "형용사"
        else -> label.trim()
    }

    private class Parsed(val preface: List<String>, val senses: List<String>)

    /** Unnumbered lines before the first sense (notes) are kept apart; continuations stay attached. */
    private fun parse(korean: String): Parsed {
        val preface = mutableListOf<String>()
        val senses = mutableListOf<String>()
        korean.lines().filter { it.isNotBlank() }.forEach { line ->
            val match = numbered.find(line)
            when {
                match != null -> senses += match.groupValues[1].trim()
                senses.isEmpty() -> preface += line.trim()
                else -> senses[senses.lastIndex] = senses.last() + "\n" + line.trim()
            }
        }
        return Parsed(preface, senses)
    }

    fun partOf(sense: String): String = partLabel.find(sense.trim())?.let { normalize(it.groupValues[1]) }.orEmpty()

    fun partsOf(korean: String): Set<String> = parse(korean).senses.map { partOf(it) }.filter { it.isNotEmpty() }.toSet()

    /** Parts of speech the English entry has but the Korean list does not (only when it names parts at all). */
    fun missingParts(korean: String, englishParts: List<String>): Set<String> {
        val have = partsOf(korean)
        if (have.isEmpty()) return emptySet()
        return englishParts.mapNotNull { koreanPart(it) }.filter { it !in have }.toSet()
    }

    /**
     * Senses from [source] whose Korean words are not already in [existing], reduced to the new
     * words only ("[명사] 야자나무"). [parts] restricts the parts of speech taken, null takes any.
     */
    fun extras(source: String, parts: Set<String>?, existing: String, limit: Int,
               maxWords: Int = Int.MAX_VALUE, topWords: Int = Int.MAX_VALUE): List<String> {
        val known = StringBuilder(existing)
        val out = mutableListOf<String>()
        for (sense in parse(source).senses) {
            if (out.size == limit) break
            val pos = partOf(sense)
            if (parts != null && pos !in parts) continue
            val match = partLabel.find(sense.trim())
            val body = match?.groupValues?.get(2) ?: sense
            val words = body.split(',', ';').map { it.trim().trimEnd('.') }.take(topWords)
                .filter { word -> word.any { it in '가'..'힣' } && !known.contains(word) }.distinct().take(maxWords)
            if (words.isEmpty()) continue
            out += (if (match != null) "[${match.groupValues[1]}] " else "") + words.joinToString(", ")
            known.append(' ').append(words.joinToString(" "))
        }
        return out
    }

    /** [korean] plus [extras], ordered by the English entry's parts of speech, renumbered, capped. */
    fun combine(korean: String, extras: List<String>, englishParts: List<String>): String {
        val parsed = parse(korean)
        val all = parsed.senses + extras
        val order = englishParts.mapNotNull { koreanPart(it) }.distinct()
        val ordered = if (order.size >= 2 && all.map { partOf(it) }.filter { it.isNotEmpty() }.distinct().size >= 2) {
            // Unlabeled senses stay behind the labeled sense they follow.
            var last = 0
            all.map { sense ->
                val pos = partOf(sense)
                if (pos.isNotEmpty()) last = order.indexOf(pos).let { if (it < 0) order.size else it }
                last to sense
            }.sortedBy { it.first }.map { it.second }
        } else all
        return (parsed.preface + ordered.take(StudyMeanings.MAX_SENSES).mapIndexed { i, sense -> "${i + 1}. $sense" })
            .joinToString("\n")
    }
}
