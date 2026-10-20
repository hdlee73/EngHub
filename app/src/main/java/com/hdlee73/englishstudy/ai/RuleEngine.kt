package com.hdlee73.englishstudy.ai

import java.util.Locale

/**
 * Always-available offline rule engine: basic proofreading heuristics that need no model.
 * Used as the fallback layer when on-device Gemini Nano is unavailable, and merged on top of
 * Gemini Nano results for deterministic catch-all fixes.
 */
object RuleEngine {

    /** Common surface-level fixes (capitalization, punctuation, spacing, frequent confusions). */
    private val simpleFixes: List<Pair<Regex, String>> = listOf(
        Regex("""\bi\b""", RegexOption.IGNORE_CASE) to "I",
        // "a apple" -> "an apple", but keep "a university", "a one-way", "a euro"
        Regex("""\b([Aa]) (?!(?i:uni|use|usu|uti|one|eu))([aeiouAEIOU])""") to "$1n $2",
        // "an book" -> "a book", but keep the silent-h words ("an hour", "an honest ...")
        Regex("""\b([Aa])n (?!(?i:hour|honest|hono|heir))([b-df-hj-np-tv-zB-DF-HJ-NP-TV-Z])""") to "$1 $2",
        Regex("""\bhe have\b""", RegexOption.IGNORE_CASE) to "he has",
        Regex("""\bshe have\b""", RegexOption.IGNORE_CASE) to "she has",
        Regex("""\bhe don't\b""", RegexOption.IGNORE_CASE) to "he doesn't",
        Regex("""\bshe don't\b""", RegexOption.IGNORE_CASE) to "she doesn't",
        Regex("""\bI has\b""") to "I have",
        Regex("""\bI is\b""") to "I am",
        Regex("""\bpeople is\b""", RegexOption.IGNORE_CASE) to "people are",
        Regex("""\bpeoples\b""", RegexOption.IGNORE_CASE) to "people",
        Regex("""\bdid you went\b""", RegexOption.IGNORE_CASE) to "did you go",
        Regex("""\bdiscuss about\b""", RegexOption.IGNORE_CASE) to "discuss",
        Regex("""\bmention about\b""", RegexOption.IGNORE_CASE) to "mention",
        Regex("""\bwant to to\b""", RegexOption.IGNORE_CASE) to "want to",
        Regex("""\bto to\b""", RegexOption.IGNORE_CASE) to "to",
        Regex("""\bgonna be gonna\b""", RegexOption.IGNORE_CASE) to "gonna",
        Regex("""\s+([,.!?])""") to "$1",
        Regex("""([,.!?])(?=[A-Za-z])""") to "$1 ",
    )

    fun fix(input: String): String {
        var s = input.replace("\n", " ").trim()
        if (s.isEmpty()) return s
        for ((pattern, replacement) in simpleFixes) {
            s = pattern.replace(s, replacement)
        }
        // collapse duplicated words ("the the")
        s = Regex("""\b(\w+) \1\b""", RegexOption.IGNORE_CASE).replace(s) { it.groupValues[1] }
        // duplicate spaces
        s = s.replace(Regex("""\s{2,}"""), " ")
        // capitalize sentence starts
        s = s.replace(Regex("""(^|[.!?] )([a-z])""")) { m ->
            m.groupValues[1] + m.groupValues[2].uppercase(Locale.ROOT)
        }
        // ensure ending punctuation
        if (s.isNotEmpty() && s.last().isLetterOrDigit()) s += "."
        return s
    }

    data class Correction(val from: String, val to: String, val kind: String)

    /** Finds word-level diffs between the original input and the corrected output. */
    fun findCorrections(before: String, after: String): List<Correction> {
        val a = before.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val b = after.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        // compare words without case or surrounding punctuation so "i" -> "I." is not listed as a change
        fun norm(w: String) = w.lowercase(Locale.ROOT).trim { !it.isLetterOrDigit() && it != '\'' }
        val al = a.map(::norm)
        val bl = b.map(::norm)
        val n = al.size
        val m = bl.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i2 in n - 1 downTo 0) for (j2 in m - 1 downTo 0) {
            var v = dp[i2 + 1][j2 + 1] + (if (al[i2] == bl[j2]) 1 else 0)
            if (v < dp[i2 + 1][j2]) v = dp[i2 + 1][j2]
            if (v < dp[i2][j2 + 1]) v = dp[i2][j2 + 1]
            dp[i2][j2] = v
        }
        var i = 0; var j = 0
        val out = ArrayList<Correction>()
        while (i < n && j < m) {
            when {
                al[i] == bl[j] -> { i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { out += Correction(a[i], "", "삭제"); i++ }
                else -> { out += Correction("", b[j], "추가"); j++ }
            }
        }
        while (i < n) { out += Correction(a[i], "", "삭제"); i++ }
        while (j < m) { out += Correction("", b[j], "추가"); j++ }
        return out.take(12)
    }

    /**
     * Produces 3 natural paraphrases (casual / neutral / formal) with offline templates.
     * "x" gets upgraded automatically when the on-device rewriting model is available.
     */
    fun paraphrase(input: String): List<String> {
        val s = input.trim()
        if (s.isEmpty()) return emptyList()
        val casual = s
            .replace("I am going to", "I'm gonna", ignoreCase = true)
            .replace("I want to", "I wanna", ignoreCase = true)
            .replace("What is", "What's", ignoreCase = true)
            .replace("Do not", "Don't", ignoreCase = true)
            .replace("Cannot", "Can't", ignoreCase = true)
        val formal = s
            .replace("gonna", "going to", ignoreCase = true)
            .replace("wanna", "want to", ignoreCase = true)
            .replace("What's", "What is")
            .replace("Don't", "Do not", ignoreCase = true)
            .replace("Can't", "Cannot", ignoreCase = true)
        val polite = "I was wondering if " + formal
            .removePrefix("Can you ").removePrefix("Could you ")
            .removePrefix("can you ").removePrefix("could you ")
        return listOf(casual, formal, polite).filter { it.isNotEmpty() }.distinct().take(3)
    }
}
