package com.hdlee73.englishstudy.dictionary

/** Keep dictionary sense order; synonyms within one sense stay together. */
internal object StudyMeanings {
    const val MAX_SENSES = 5
    fun limit(text: String): String {
        val out = mutableListOf<String>()
        var senses = 0
        var keep = true
        text.lines().forEach { line ->
            if (Regex("^\\s*\\d+[.)]\\s*").containsMatchIn(line)) {
                senses++
                keep = senses <= MAX_SENSES
            }
            if (keep) out += line
        }
        return out.joinToString("\n").trim()
    }
}

internal fun WordEntry.studyVersion(): WordEntry =
    copy(korean = StudyMeanings.limit(korean), english = StudyMeanings.limit(english), examples = SentenceExamples.clean(examples))
