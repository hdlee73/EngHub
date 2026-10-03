package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class ReviewedSearchTest {
    @Test fun catchUpHasRealBilingualSentencesWithoutTranslationDownload() {
        val entry = ReviewedEntries.lookup(" CATCH   UP ")!!
        assertTrue(entry.korean.contains("[구동사]"))
        assertTrue(entry.korean.contains("따라잡다"))
        assertEquals(4, entry.english.lines().size)
        entry.examples.lines().forEach { line ->
            val parts = line.split('\t')
            assertEquals(2, parts.size)
            assertTrue(SentenceExamples.isSentence(parts[0]))
            assertTrue(parts[1].any { it in '가'..'힣' })
            assertFalse(parts[0].contains("I heard"))
        }
    }

    @Test fun preliminaryKeepsItsAdjectiveAndNounPartsOfSpeech() {
        val entry = ReviewedEntries.lookup("preliminary")!!
        assertFalse(entry.korean.contains("[구동사]"))
        assertTrue(entry.korean.contains("[형용사]"))
        assertTrue(entry.korean.contains("[명사]"))
    }
}
