package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class UsageMatcherTest {
    @Test fun matchesWholeWordsAndPhrases() {
        assertTrue(UsageMatcher.contains("They decided to take off early.", "take off"))
        assertTrue(UsageMatcher.contains("That was a SPONTANEOUS decision.", "spontaneous"))
        assertFalse(UsageMatcher.contains("The carpet was red.", "car"))
        assertFalse(UsageMatcher.contains("Take your shoes and turn the light off.", "take off"))
        assertFalse(UsageMatcher.contains("hello", ""))
    }
    @Test fun spontaneousExamplesExpressRealUsage() {
        val entry = ReviewedEntries.lookup("spontaneous")!!
        assertTrue(entry.korean.contains("즉흥적인"))
        assertTrue(entry.korean.contains("자발적인"))
        assertFalse(entry.korean.contains("자적"))
        entry.examples.lines().forEach {
            val pair = it.split('\t')
            assertEquals(2, pair.size)
            assertTrue(UsageMatcher.contains(pair[0], "spontaneous"))
            assertTrue(pair[1].any { ch -> ch in '가'..'힣' })
            assertFalse(pair[0].contains("I heard"))
        }
    }
}
