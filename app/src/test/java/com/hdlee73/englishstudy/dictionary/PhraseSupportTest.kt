package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseSupportTest {
    @Test fun compactKeepsOnlyTheKoreanWordOfExplainedRows() {
        val raw = "1. [동사] 비하다: 어떤 것을 기준으로 판단해 볼 때 그보다.\n2. [동사] 비유되다: 어떤 것이 효과적으로 표현되다."
        assertEquals("1. [동사] 비하다\n2. [동사] 비유되다", MeaningQuality.compact(raw))
    }

    @Test fun compactLeavesShortGlossesAlone() {
        val raw = "1. [명사] 사과: 과일\n2. [동사] 먹다"
        assertEquals(raw, MeaningQuality.compact(raw))
    }

    @Test fun mergeDropsRepeatsAndRenumbers() {
        val merged = MeaningQuality.merge("1. 비교되다\n2. 비유되다", "1. [동사] 비하다\n2. 비교되다")
        assertEquals("1. 비교되다\n2. 비유되다\n3. [동사] 비하다", merged)
    }

    @Test fun mergeLimitsTheNumberOfLines() {
        assertEquals(2, MeaningQuality.merge("1. a\n2. b\n3. c", null, 2).lines().size)
    }

    private val lemma = { w: String -> when (w) { "compared" -> "compare"; "comparing" -> "compare"; else -> w } }

    @Test fun tokensIgnoreBeToAndPlaceholders() {
        assertEquals(listOf("compare"), PhraseSimilarity.tokens("be compared to", lemma))
        assertEquals(listOf("look", "forward"), PhraseSimilarity.tokens("look forward to someone", lemma))
    }

    @Test fun ftsQueryMatchesAnyTokenAsPrefix() {
        assertEquals("compare* OR notes*", PhraseSimilarity.ftsQuery(listOf("compare", "notes")))
        assertEquals(null, PhraseSimilarity.ftsQuery(emptyList()))
    }

    @Test fun rankPutsTheClosestExpressionsFirst() {
        val found = listOf("compare notes", "compare to", "compared to", "compare with", "compare", "be compared to")
        val ranked = PhraseSimilarity.rank("be compared to", found, lemma)
        assertTrue("compare" !in ranked)
        assertTrue("be compared to" !in ranked)
        assertEquals("compared to", ranked.first())
        assertTrue("compare to" in ranked)
    }

    @Test fun rankNeedsTwoSharedWordsWhenTheQueryHasTwo() {
        val ranked = PhraseSimilarity.rank("look forward to", listOf("look up", "look forward", "forward looking"), lemma)
        assertEquals(listOf("look forward"), ranked.take(1))
        assertTrue("look up" !in ranked)
    }
}
