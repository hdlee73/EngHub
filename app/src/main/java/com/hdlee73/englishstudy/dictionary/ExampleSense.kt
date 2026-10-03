package com.hdlee73.englishstudy.dictionary

/**
 * Chooses the example sentences that go with the Korean meanings on screen. A corpus lookup returns
 * sentences for every sense of a word (palm: the tree, the hand, the verb) in length order, so the
 * shortest ones can illustrate a sense the meaning list never mentions. Candidates whose Korean
 * translation contains a word of the first-listed (most common) sense come first.
 */
internal object ExampleSense {
    private val numbered = Regex("^\\s*\\d+[.)]\\s*(.*)$")
    private val label = Regex("^\\[[^\\]]*]\\s*")

    /** Korean search stem of a dictionary word: "감추다" → "감추", "~을 하다" kept short, spaces removed. */
    fun stem(word: String): String {
        var w = word.replace(Regex("\\([^)]*\\)"), "").replace(Regex("[~\\s]"), "").trim().trimEnd('.')
        if (w.length > 3 && w.endsWith("하다")) w = w.dropLast(2)
        else if (w.length >= 3 && w.endsWith("다")) w = w.dropLast(1)
        return w
    }

    /** Stems of the Korean words of each numbered sense, in list order (one-letter words are too loose). */
    fun senseWords(korean: String): List<List<String>> = korean.lines().mapNotNull { line ->
        val body = numbered.find(line)?.groupValues?.get(1)?.replace(label, "") ?: return@mapNotNull null
        body.split(',', ';').map { stem(it) }.filter { w -> w.length >= 2 && w.any { it in '가'..'힣' } }
    }

    fun pick(candidates: List<BilingualSentence>, korean: String, limit: Int = 2): List<BilingualSentence> {
        if (candidates.size <= 1) return candidates.take(limit)
        val senses = senseWords(korean)
        val none = Int.MAX_VALUE
        val ranked = candidates.mapIndexed { i, pair ->
            val text = pair.korean.replace(Regex("\\s+"), "")
            val sense = senses.indexOfFirst { words -> words.any { text.contains(it) } }
            Triple(i, pair, if (sense < 0) none else sense)
        }.sortedWith(compareBy({ it.third }, { it.first }))
        val first = ranked.first()
        if (first.third == none) return candidates.take(limit)
        val chosen = mutableListOf(first.second)
        val rest = ranked.drop(1)
        // The second example prefers another listed sense, then the same sense, then anything.
        while (chosen.size < limit && rest.size >= chosen.size) {
            val next = rest.firstOrNull { it.second !in chosen && it.third != first.third && it.third != none }
                ?: rest.firstOrNull { it.second !in chosen } ?: break
            chosen += next.second
        }
        return chosen
    }

    /**
     * Senses used by the shown examples that the meaning list lacks, found through a compound the
     * example contains: "Tom is planting a palm tree" → the dictionary's "palm tree" (야자나무),
     * kept only when that Korean word appears in the example's Korean translation.
     */
    fun compoundSenses(examples: List<BilingualSentence>, query: String, existing: String,
                       lookup: (String) -> String?): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty() || q.contains(' ')) return emptyList()
        val known = StringBuilder(existing)
        val out = mutableListOf<String>()
        for (example in examples) {
            val tokens = Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(example.english).map { it.value.lowercase() }.toList()
            val at = tokens.indexOf(q)
            if (at < 0) continue
            val text = example.korean.replace(Regex("\\s+"), "")
            val compounds = listOfNotNull(tokens.getOrNull(at + 1)?.let { "$q $it" }, tokens.getOrNull(at - 1)?.let { "$it $q" })
            for (compound in compounds) {
                val first = (lookup(compound) ?: continue).lines().firstNotNullOfOrNull { numbered.find(it)?.groupValues?.get(1) } ?: continue
                val part = label.find(first)?.value?.trim()
                val words = first.replace(label, "").replace(Regex("\\([^)]*\\)"), "").split(',', ';')
                    .map { it.trim().trimEnd('.') }
                    .filter { w -> w.any { it in '가'..'힣' } && stem(w).length >= 2 && text.contains(stem(w)) && !known.contains(w) }
                if (words.isEmpty()) continue
                out += (if (part != null) "$part " else "") + words.joinToString(", ")
                known.append(' ').append(words.joinToString(" "))
            }
        }
        return out
    }
}
