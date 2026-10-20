package com.hdlee73.englishstudy.speaking.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PronunciationJudgeTest {
    private val expected = "I want to take the ship."

    @Test
    fun aMisheardSimilarSoundingWordIsForgiven() {
        // "ship" is displayed word 5 and was written as "sheep".
        val forgiven = PronunciationJudge.parse(expected, "I want to take the sheep", listOf(5), """{"said":["ship"]}""")
        assertEquals(setOf(5), forgiven)
    }

    @Test
    fun aWordNanoNamesButNothingSoundsLikeIsNotForgiven() {
        assertEquals(emptySet<Int>(), PronunciationJudge.parse(expected, "I want to take the", listOf(5), """{"said":["ship"]}"""))
        assertEquals(emptySet<Int>(), PronunciationJudge.parse(expected, "I want to take the car", listOf(5), """{"said":["ship"]}"""))
    }

    @Test
    fun onlyMissedWordsNanoNamesAreForgivenAndBadAnswersAreIgnored() {
        assertEquals(emptySet<Int>(), PronunciationJudge.parse(expected, "I want to take the sheep", listOf(5), """{"said":[]}"""))
        assertEquals(emptySet<Int>(), PronunciationJudge.parse(expected, "I want to take the sheep", listOf(5), "no idea"))
        // "take" was matched already (not missing), so naming it changes nothing.
        assertEquals(emptySet<Int>(), PronunciationJudge.parse(expected, "I want to take the sheep", listOf(5), """{"said":["take"]}"""))
    }

    @Test
    fun promptListsTheMissedWordsAndForbidsForgivingSkippedOnes() {
        val prompt = PronunciationJudge.buildPrompt(expected, "I want to take the sheep", listOf("ship"))
        assertTrue(prompt.contains("\"ship\""))
        assertTrue(prompt.contains("skipped"))
    }

    @Test
    fun similarityIsHighForCloseSoundsAndLowForDifferentWords() {
        assertTrue(PronunciationJudge.similarity("ship", "sheep") >= 50)
        assertTrue(PronunciationJudge.similarity("ship", "car") < 50)
    }
}
