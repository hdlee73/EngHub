package com.hdlee73.englishstudy.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSessionTest {
    private fun question(word: String, answerIndex: Int = 0) = QuizQuestion(
        wordId = 1, word = word, blankedSentence = "I like _____.", sentence = "I like $word.", translation = "",
        meaningHint = "", choices = listOf(word, "b", "c", "d").let { c -> c.toMutableList().also { it.add(answerIndex, it.removeAt(0)) } },
        answerIndex = answerIndex
    )

    @Test fun scoresRightAndWrongAnswers() {
        val s = QuizSession(listOf(question("apple", 0), question("pear", 2), question("plum", 1)))
        assertEquals(3, s.total)
        assertEquals(false, s.answered)
        assertEquals(true, s.choose(0))
        assertTrue(s.answered)
        s.next()
        assertEquals(false, s.choose(1))
        s.next()
        assertEquals(true, s.choose(1))
        s.next()
        assertTrue(s.finished)
        assertNull(s.current)
        assertEquals(2, s.correctCount)
        assertEquals(listOf("pear"), s.wrong.map { it.word })
    }

    @Test fun aQuestionCanOnlyBeAnsweredOnce() {
        val s = QuizSession(listOf(question("apple", 0)))
        assertEquals(false, s.choose(2))
        assertNull(s.choose(0))
        assertEquals(2, s.selected)
        assertEquals(0, s.correctCount)
        assertEquals(1, s.wrong.size)
    }

    @Test fun nextWaitsForAnAnswer() {
        val s = QuizSession(listOf(question("apple", 0), question("pear", 0)))
        s.next()
        assertEquals(0, s.index)
        s.choose(0)
        s.next()
        assertEquals(1, s.index)
        assertNull(s.selected)
    }

    @Test fun outOfRangeChoicesAreIgnored() {
        val s = QuizSession(listOf(question("apple", 0)))
        assertNull(s.choose(-1))
        assertNull(s.choose(4))
        assertFalse(s.answered)
    }

    @Test fun emptyQuizIsFinished() {
        val s = QuizSession(emptyList())
        assertTrue(s.finished)
        assertNull(s.choose(0))
        s.next()
        assertEquals(0, s.index)
    }
}
