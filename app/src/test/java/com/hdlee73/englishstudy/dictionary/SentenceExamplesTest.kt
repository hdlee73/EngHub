package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class SentenceExamplesTest {
    @Test fun rejectsFragments() {
        assertFalse(SentenceExamples.isSentence("a spontaneous decision"))
        assertFalse(SentenceExamples.isSentence("pay off"))
        assertFalse(SentenceExamples.isSentence("Hello!"))
    }
    @Test fun acceptsSentencesAndQuestions() {
        assertTrue(SentenceExamples.isSentence("We finally paid off our mortgage."))
        assertTrue(SentenceExamples.isSentence("Could you look after my cat?"))
        assertTrue(SentenceExamples.isSentence("He said, \"Please take off your shoes.\""))
    }
    @Test fun cleansSavedFragmentsAndKeepsTranslations() {
        assertEquals("We paid off the loan.\t대출을 다 갚았다.", SentenceExamples.clean("a loan\nWe paid off the loan.\t대출을 다 갚았다."))
    }
    @Test fun noPlaceholderSentenceIsInventedOrKept() {
        assertEquals("", SentenceExamples.clean("a loan"))
        assertEquals("", SentenceExamples.clean("I heard “loan” in a conversation today.\t오늘 대화에서 “loan”라는 표현을 들었습니다."))
    }
}
