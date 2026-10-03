package com.hdlee73.englishstudy.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankerTest {
    @Test fun blanksTheExactWord() {
        assertEquals("I like _____.", Blanker.blank("I like apples.", "apples"))
    }

    @Test fun blanksRegularInflections() {
        assertEquals("He is _____ fast.", Blanker.blank("He is running fast.", "run"))
        assertEquals("She _____ hard.", Blanker.blank("She studies hard.", "study"))
        assertEquals("He _____ the car.", Blanker.blank("He stopped the car.", "stop"))
        assertEquals("She _____ him.", Blanker.blank("She loves him.", "love"))
        assertEquals("The _____ are big.", Blanker.blank("The cities are big.", "city"))
        assertEquals("He is _____ now.", Blanker.blank("He is dying now.", "die"))
    }

    @Test fun blanksIrregularInflections() {
        assertEquals("She _____ home.", Blanker.blank("She went home.", "go"))
        assertEquals("I have _____ it.", Blanker.blank("I have eaten it.", "eat"))
    }

    @Test fun blanksAPhraseThroughItsFirstWord() {
        assertEquals("We are _____ the trip.", Blanker.blank("We are looking forward to the trip.", "look forward to"))
        assertEquals("Please _____ your shoes.", Blanker.blank("Please take off your shoes.", "take off"))
    }

    @Test fun ignoresCaseAndKeepsPunctuation() {
        assertEquals("_____ fast!", Blanker.blank("Run fast!", "run"))
    }

    @Test fun blanksEveryOccurrence() {
        assertEquals("You _____, then I _____.", Blanker.blank("You run, then I run.", "run"))
    }

    @Test fun doesNotMatchInsideAnotherWord() {
        assertNull(Blanker.blank("The category is large.", "cat"))
        assertNull(Blanker.blank("She is a runner.", "run"))
    }

    @Test fun returnsNullWhenTheWordIsAbsent() {
        assertNull(Blanker.blank("Hello world.", "go"))
        assertNull(Blanker.blank("", "go"))
        assertNull(Blanker.blank("Hello world.", "  "))
    }

    @Test fun comparativesDoNotBlankTheirPositiveWord() {
        assertTrue(Blanker.find("He has more apples.", "many").isEmpty())
    }

    @Test fun reportsCharacterRanges() {
        assertEquals(listOf(4..7), Blanker.find("She went home.", "go"))
    }
}
