package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class ReviewedEntriesTest {
    @Test fun payOffSeparatesRepaymentResultsAndBribery() {
        val e = ReviewedEntries.lookup("  PAY   OFF ")!!
        assertTrue(e.korean.contains("전액 갚다"))
        assertTrue(e.korean.contains("성과를 내다"))
        assertTrue(e.korean.contains("매수하다"))
        assertFalse(e.korean.contains("페이 오프"))
        assertTrue(e.examples.contains("이미 뇌물을 주고 매수한"))
        assertEquals("", e.ipa)
    }
    @Test fun reviewedEntriesHaveEnglishAndKoreanExamples() {
        listOf("pay off", "give up", "put off", "take off", "look up", "look after",
            "look forward to", "run out of", "figure out", "get along", "break down", "stuck").forEach { word ->
            val e = ReviewedEntries.lookup(word)!!
            assertTrue(e.korean.isNotBlank())
            assertTrue(e.english.isNotBlank())
            assertTrue(e.examples.isNotBlank())
            e.examples.lines().forEach { line ->
                val pair = line.split('\t')
                assertEquals(2, pair.size)
                assertTrue(pair[0].isNotBlank())
                assertTrue(pair[1].any { it in '가'..'힣' })
            }
        }
    }
    @Test fun unknownExpressionsAreNotInvented() {
        assertNull(ReviewedEntries.lookup("unknown phrase 123"))
    }
}
