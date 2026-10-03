package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class KoreanLexicalMeaningsTest {
    @Test fun usesActualDictionaryTranslationsAndPos() {
        val result = KoreanLexicalMeanings.format(listOf(
            KoreanDictionarySense("adjective", listOf("예비의", "준비의", "예비의")),
            KoreanDictionarySense("noun", listOf("예선"))
        ))
        assertEquals("1. [형용사] 예비의, 준비의\n2. [명사] 예선", result)
    }
    @Test fun ignoresAbsentKoreanRatherThanTranslatingDefinitions() {
        assertEquals("", KoreanLexicalMeanings.format(listOf(KoreanDictionarySense("noun", listOf("abc", "ㄱㄴ", "")))))
    }
    @Test fun capsSensesAtTheDisplayLimit() {
        val result = KoreanLexicalMeanings.format((1..9).map { KoreanDictionarySense("noun", listOf("뜻 " + it)) })
        assertEquals(StudyMeanings.MAX_SENSES, result.lines().size)
    }
    @Test fun preliminaryHasCompleteBilingualExamples() {
        val entry = ReviewedEntries.lookup("preliminary")!!
        assertTrue(entry.korean.contains("예비적인"))
        assertFalse(entry.korean.contains("없습니다"))
        val pairs = entry.examples.lines().map { it.split('\t') }
        assertTrue(pairs.all { SentenceExamples.isSentence(it[0]) && it[1].any { ch -> ch in '가'..'힣' } })
    }
}
