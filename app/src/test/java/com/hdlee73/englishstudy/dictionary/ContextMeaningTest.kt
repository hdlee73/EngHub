package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMeaningTest {
    private val answer = """{"ko":"운영하다","en":"to manage or be in charge of a business","pos":"verb","nuance":"사업이나 가게를 맡아 꾸려 간다는 뜻이에요.","examples":["He runs a small shop.","They run the school together.","bad"]}"""

    @Test
    fun parsesAJsonAnswerEvenInsideACodeFence() {
        val parsed = ContextMeanings.parse("```json\n$answer\n```")
        assertNotNull(parsed)
        assertEquals("운영하다", parsed!!.korean)
        assertEquals("verb", parsed.pos)
        // Only complete sentences stay as examples.
        assertEquals(listOf("He runs a small shop.", "They run the school together."), parsed.examples)
    }

    @Test
    fun anAnswerWithoutKoreanMeaningIsRejected() {
        assertNull(ContextMeanings.parse("""{"ko":"to manage","en":"manage"}"""))
        assertNull(ContextMeanings.parse("I cannot help with that."))
        assertNull(ContextMeanings.parse("{broken json"))
    }

    @Test
    fun promptCarriesWordSentenceAndDictionaryHint() {
        val entry = WordEntry(word = "run", ipa = "", korean = "1. 달리다\n2. 운영하다", english = "1. to move fast", examples = "")
        val prompt = ContextMeanings.buildPrompt("run", "She runs a small bakery.", entry)
        assertTrue(prompt.contains("Word: \"run\""))
        assertTrue(prompt.contains("She runs a small bakery."))
        assertTrue(prompt.contains("달리다 / to move fast"))
    }

    @Test
    fun savedCopyPutsTheSentenceMeaningOnTopOfTheNumberedSenses() {
        val entry = WordEntry(word = "run", ipa = "", korean = "1. 달리다\n2. 운영하다", english = "1. to move fast", examples = "I run daily.\t나는 매일 달린다.")
        val meaning = ContextMeaning("run", "She runs a bakery.", "운영하다", "to manage a business", "verb", "", "그녀는 빵집을 운영한다.", listOf("He runs a shop." to "그는 가게를 운영한다."), usedModel = true)
        val merged = ContextMeanings.mergeInto(entry, meaning)
        assertEquals("[이 문장에서] 운영하다\n1. 달리다\n2. 운영하다", merged.korean)
        assertTrue(merged.english.startsWith("[in this sentence] to manage a business"))
        val lines = merged.examples.lines()
        assertEquals("She runs a bakery.\t그녀는 빵집을 운영한다.", lines[0])
        assertEquals("I run daily.\t나는 매일 달린다.", lines.last())
    }
}
