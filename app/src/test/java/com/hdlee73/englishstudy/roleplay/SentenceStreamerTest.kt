package com.hdlee73.englishstudy.roleplay

import org.junit.Assert.assertEquals
import org.junit.Test

class SentenceStreamerTest {
    private fun run(vararg chunks: String): List<String> {
        val out = ArrayList<String>()
        val streamer = SentenceStreamer { out.add(it) }
        chunks.forEach(streamer::feed)
        streamer.finish()
        return out
    }

    @Test
    fun sentencesAreHandedOverAsSoonAsTheyAreComplete() {
        val out = ArrayList<String>()
        val streamer = SentenceStreamer { out.add(it) }
        streamer.feed("Hello the")
        streamer.feed("re! How are")
        assertEquals(listOf("Hello there!"), out)
        streamer.feed(" you today? I")
        streamer.feed(" am fine.")
        assertEquals(listOf("Hello there!", "How are you today?"), out)
        streamer.finish()
        assertEquals(listOf("Hello there!", "How are you today?", "I am fine."), out)
    }

    @Test
    fun roleLabelAndInventedLearnerLinesAreDropped() {
        assertEquals(listOf("Nice to meet you!", "What do you do?"), run("Partner: Nice to meet you! What do you do?\nLearner: I am a teacher."))
    }

    @Test
    fun aShortAbbreviationDoesNotCutASentence() {
        assertEquals(listOf("I met Mr. Kim at the station yesterday."), run("I met Mr. Kim at the station yesterday."))
        assertEquals(listOf("I met Mr. Kim at the station.", "He was late."), run("I met Mr. Kim at the station. He was late."))
    }

    @Test
    fun textWithoutAnyEndingStillComesOutAtTheEnd() {
        assertEquals(listOf("Sounds great"), run("Sounds ", "great"))
    }
}
