package com.hdlee73.englishstudy.roleplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionFixTest {
    @Test
    fun aSingleMisheardWordIsReplaced() {
        assertEquals("I want to take the ship", RecognitionFix.accept("I want to take the sheep", "I want to take the ship"))
        assertEquals("I want to take the ship", RecognitionFix.accept("I want to take the sheep", "\"I want to take the ship\"\nExplanation: ..."))
    }

    @Test
    fun unchangedKoreanOrRewrittenAnswersAreRejected() {
        assertNull(RecognitionFix.accept("I like coffee", "I like coffee."))
        assertNull(RecognitionFix.accept("I like coffee", "저는 커피를 좋아해요"))
        assertNull(RecognitionFix.accept("I like coffee", ""))
        assertNull(RecognitionFix.accept("I like coffee", "Certainly, I would absolutely adore a cup of fresh tea"))
        assertNull(RecognitionFix.accept("me go store yesterday", "I went to the store yesterday"))
    }

    @Test
    fun promptKeepsTheLearnersGrammarAndListsOtherReadings() {
        val prompt = RecognitionFix.buildPrompt(RoleplayThemes.FREE, emptyList(), "I want to take the sheep", listOf("I want to take the ship", "I want to take the sheep"))
        assertTrue(prompt.contains("\"I want to take the sheep\""))
        assertTrue(prompt.contains("\"I want to take the ship\""))
        assertTrue(prompt.contains("do not improve or rephrase"))
    }
}
