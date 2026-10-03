package com.hdlee73.englishstudy.dictionary

import java.util.Locale

internal data class InflectedMeaning(val base: String, val form: String, val partOfSpeech: String) {
    fun korean(baseGloss: String?): String = buildString {
        if (partOfSpeech.isNotBlank()) append("[$partOfSpeech] ")
        append(base).append("의 $form")
        if (!baseGloss.isNullOrBlank()) append("\n원형 뜻\n").append(StudyMeanings.limit(baseGloss))
    }
}

/**
 * Use explicit dictionary "form of" definitions (inflections, misspellings, variant
 * spellings); never guess a base by stripping suffixes here.
 */
internal object InflectedForms {
    private val patterns = listOf(
        Triple("(?:(?:simple )?past(?: tense)? and past participle|past tense and past participle)", "과거형·과거분사", "동사"),
        Triple("(?:simple )?past(?: tense)?", "과거형", "동사"),
        Triple("past participle", "과거분사", "동사"),
        Triple("present participle(?: and gerund)?|gerund(?: and present participle)?", "현재분사·동명사", "동사"),
        Triple("third[- ]person singular(?: simple)? present(?: indicative)?(?: tense)?", "3인칭 단수 현재형", "동사"),
        Triple("(?:plural|plural form)", "복수형", "명사"),
        Triple("comparative(?: form)?", "비교급", "변형"),
        Triple("superlative(?: form)?", "최상급", "변형"),
        Triple("(?:common |frequent )?misspelling", "잘못된 철자", ""),
        Triple("(?:obsolete|archaic|dated|rare|nonstandard|informal|eye dialect|alternative)(?: alternative)? (?:spelling|form)", "다른 표기", ""),
        Triple("(?:british|american|uk|us|commonwealth) (?:english )?(?:standard )?spelling", "영·미 철자 차이", "")
    )
    fun find(definitions: String): InflectedMeaning? {
        for (line in definitions.lines()) {
            val definition = line.replace(Regex("^\\s*\\d+[.)]\\s*"), "").trim()
            for ((pattern, label, pos) in patterns) {
                val match = Regex("^(?:$pattern) of ([a-z]+(?:[-'][a-z]+)*)(?:[. ,;:]|$)", RegexOption.IGNORE_CASE)
                    .find(definition) ?: continue
                return InflectedMeaning(match.groupValues[1].lowercase(Locale.ROOT), label, pos)
            }
        }
        return null
    }
}
