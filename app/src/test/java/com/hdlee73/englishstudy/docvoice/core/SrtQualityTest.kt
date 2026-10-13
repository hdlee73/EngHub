package com.hdlee73.englishstudy.docvoice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtQualityTest {
    @Test fun oneSentenceIsOneCue() {
        val long = "This is a really long sentence that goes on and on, so the subtitle has to wrap onto more than two lines of text for sure."
        val out = String(Exporter.srt(listOf(Sentence(1.0, 9.0, long), Sentence(9.5, 11.0, "Next one here."))), Charsets.UTF_8)
        val cues = out.trim().split("\n\n")
        assertEquals(2, cues.size)
        assertTrue(cues[0].startsWith("1\n00:00:01,000 --> 00:00:09,000\n"))
        assertTrue(cues[0].replace("\n", " ").contains(long))
    }

    @Test fun pauseInsideASentenceDoesNotSplitIt() {
        val s = Segmenter.buildSentences(listOf(
            Segment(0.0, 3.0, "When we got to the station"),
            Segment(4.2, 7.0, "the last train had already left."),
        ))
        assertEquals(1, s.size)
        assertEquals(0.0, s[0].start, 0.001)
        assertEquals(7.0, s[0].end, 0.001)
    }

    @Test fun falseFullStopBeforeLowerCaseContinues() {
        val s = Segmenter.buildSentences(listOf(
            Segment(0.0, 3.0, "We talked about the plan for the trip."),
            Segment(3.2, 6.0, "and then decided to leave early today."),
        ))
        assertEquals(1, s.size)
    }

    @Test fun realSentenceEndStillSplits() {
        val s = Segmenter.buildSentences(listOf(
            Segment(0.0, 3.0, "We talked about the plan for the trip."),
            Segment(4.0, 7.0, "Then we decided to leave early today."),
        ))
        assertEquals(2, s.size)
    }

    @Test fun cleansRecognizerNoise() {
        assertNull(Segmenter.cleanAsr("[Music]", 3.0))
        assertNull(Segmenter.cleanAsr("Thank you.", 1.5))
        assertEquals("Thank you.", Segmenter.cleanAsr("Thank you.", 6.0))
        assertEquals("Hello there", Segmenter.cleanAsr("(applause) Hello there", 3.0))
        assertEquals("Okay", Segmenter.cleanAsr("Okay, okay, okay, okay, okay", 5.0))
    }
}
