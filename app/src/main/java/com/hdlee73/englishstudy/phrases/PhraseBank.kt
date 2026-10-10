package com.hdlee73.englishstudy.phrases

import java.util.Locale

/** One common pattern or expression: [region] is 미국, 영국 or 공통 and [category] one of [PhraseBank.CATEGORIES]. */
data class Phrase(
    val phrase: String,
    val region: String,
    val category: String,
    val korean: String,
    val note: String,
    val example: String,
    val exampleKo: String
)

/**
 * The bundled collection of patterns and expressions used in the US and the UK (assets/phrases_us_uk.txt, one entry per line,
 * fields separated by "|"), and the search over it.
 */
object PhraseBank {
    val REGIONS = listOf("미국", "영국", "공통")
    val CATEGORIES = listOf("패턴", "일상 표현", "관용구", "슬랭", "미영 차이")

    fun parse(text: String): List<Phrase> = text.lineSequence().mapNotNull { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
        val f = line.split('|')
        if (f.size < 7 || f[0].isBlank()) return@mapNotNull null
        Phrase(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim(), f[4].trim(), f[5].trim(), f[6].trim())
    }.toList()

    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * The entries matching [query] (English words, a piece of a pattern or Korean), best matches first. Every word of the query has to
     * appear in the expression, its meaning, its note or its example; an empty query lists everything in file order.
     * [region] and [category] narrow the list when not null.
     */
    fun search(all: List<Phrase>, query: String, region: String? = null, category: String? = null): List<Phrase> {
        val pool = all.filter { (region == null || it.region == region) && (category == null || it.category == category) }
        val q = norm(query)
        if (q.isEmpty()) return pool
        val words = q.split(' ')
        return pool.mapNotNull { p ->
            val head = norm(p.phrase)
            val korean = norm(p.korean)
            val rest = norm(p.note + " " + p.example + " " + p.exampleKo)
            val all3 = "$head $korean $rest"
            if (!words.all { all3.contains(it) }) return@mapNotNull null
            val score = when {
                head == q -> 0
                head.startsWith(q) -> 1
                head.contains(q) -> 2
                words.all { head.contains(it) } -> 3
                korean.contains(q) -> 4
                words.all { head.contains(it) || korean.contains(it) } -> 5
                else -> 6
            }
            score to p
        }.sortedBy { it.first }.map { it.second }
    }
}
