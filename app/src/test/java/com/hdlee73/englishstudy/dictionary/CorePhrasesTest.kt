package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.assertTrue
import org.junit.Test

class CorePhrasesTest {
    @Test fun keepAwayHasNaturalKoreanSensesAndBilingualExamples() {
        val entry = CorePhrases.lookup("  KEEP   AWAY ")!!
        assertTrue(entry.korean.contains("[구동사]"))
        assertTrue(entry.korean.contains("가까이 오지 않게 하다"))
        assertTrue(entry.examples.lines().all { line ->
            val pieces = line.split('\t')
            pieces.size == 2 && SentenceExamples.isSentence(pieces[0]) &&
                pieces[1].any { it in '가'..'힣' }
        })
    }

    @Test fun allCorePhrasesHaveAtMostFourSenses() {
        listOf("keep up", "make up", "get through", "go through").forEach { word ->
            val meanings = CorePhrases.lookup(word)!!.korean.lines()
                .filter { it.matches(Regex("\\d+\\..*")) }
            assertTrue("${word}: ${meanings.size}", meanings.size in 1..4)
        }
    }
}
