package com.hdlee73.englishstudy.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {
    @Test
    fun capitalizesI() {
        assertTrue(RuleEngine.fix("i like it").contains("I like"))
    }

    @Test
    fun subjectVerbAgreementIsIdempotent() {
        val once = RuleEngine.fix("he don't know")
        assertTrue(once.contains("doesn't"))
        assertTrue(RuleEngine.fix(once).contains("doesn't"))
    }

    @Test
    fun articleFixAndExceptions() {
        assertTrue(RuleEngine.fix("a apple").lowercase().contains("an apple"))
        assertTrue(RuleEngine.fix("I waited an hour").contains("an hour"))
        assertTrue(RuleEngine.fix("she is a university student").contains("a university"))
        assertTrue(RuleEngine.fix("an book").lowercase().startsWith("a book"))
    }

    @Test
    fun sentenceStartCapitalizedAndPeriodAdded() {
        val fixed = RuleEngine.fix("hello there")
        assertTrue(fixed.startsWith("Hello"))
        assertTrue(fixed.endsWith("."))
    }

    @Test
    fun doubledToKeepsVerb() {
        assertEquals("I need to go home.", RuleEngine.fix("I need to to go home"))
    }

    @Test
    fun wordDiffFindsTheChange() {
        val diffs = RuleEngine.findCorrections("he have", "he has")
        assertTrue(diffs.any { it.to == "has" || it.from == "have" })
    }

    @Test
    fun paraphraseGivesThreeVersions() {
        val list = RuleEngine.paraphrase("I am going to the market")
        assertEquals(3, list.size)
        assertTrue(list[0].contains("gonna"))
        assertTrue(list[1].contains("going to"))
        assertTrue(list[2].startsWith("I was wondering"))
    }
}
