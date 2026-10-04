package com.hdlee73.englishstudy.study

import com.hdlee73.englishstudy.speaking.model.SentencePair
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceQuizBuilderTest {
    private val pairs = listOf(
        SentencePair("그는 작년에 담배를 끊었다.", "He gave up smoking last year."),
        SentencePair("음식이 정말 맛있었다.", "The food was very delicious."),
        SentencePair("우산을 잊지 마세요.", "Don't forget your umbrella."),
        SentencePair("이 계획은 위험하다.", "This plan is dangerous."),
        SentencePair("나는 도서관에서 공부한다.", "I study at the library."),
        SentencePair("", "No translation here, only English.")
    )

    @Test fun onlySentencesWithAKoreanTranslationAreEligible() {
        val eligible = SentenceQuizBuilder.eligible(pairs)
        assertEquals(5, eligible.size)
        assertFalse(eligible.any { it.korean.isBlank() })
    }

    @Test fun blankingHidesTheKeyWordAndKeepsTheRest() {
        val q = SentenceQuizBuilder.build(pairs, 5, Random(3)).first { it.sentence.startsWith("He gave up") }
        assertEquals("gave up", q.word)
        assertEquals("He _____ smoking last year.", q.blankedSentence)
        assertEquals("그는 작년에 담배를 끊었다.", q.translation)
    }

    @Test fun everyQuestionHasFourDistinctChoicesWithTheAnswer() {
        val questions = SentenceQuizBuilder.build(pairs, 5, Random(7))
        assertEquals(5, questions.size)
        questions.forEach { q ->
            assertEquals(4, q.choices.size)
            assertEquals(4, q.choices.map { it.lowercase() }.toSet().size)
            assertEquals(q.word, q.choices[q.answerIndex])
            assertFalse(q.trackProgress)
            // A wrong choice may not be a word of the sentence itself.
            q.choices.filter { it != q.word }.forEach { wrong ->
                assertTrue(Blanker.find(q.sentence, wrong).isEmpty())
            }
        }
    }

    @Test fun sentenceInitialCapitalIsNotGivenAway() {
        val q = SentenceQuizBuilder.build(listOf(SentencePair("조심하세요.", "Dangerous roads need care.")), 1, Random(1)).single()
        assertEquals("dangerous", q.word)
        assertTrue(q.blankedSentence.startsWith(Blanker.BLANK))
    }

    @Test fun countLimitsAndDuplicatesAreDropped() {
        val doubled = pairs + pairs
        assertEquals(3, SentenceQuizBuilder.build(doubled, 3, Random(2)).size)
        assertEquals(5, SentenceQuizBuilder.build(doubled, 99, Random(2)).size)
    }

    @Test fun reshuffledKeepsTheAnswer() {
        val q = SentenceQuizBuilder.build(pairs, 1, Random(5)).single()
        val again = q.reshuffled(Random(9))
        assertEquals(q.word, again.choices[again.answerIndex])
        assertEquals(q.choices.toSet(), again.choices.toSet())
        assertNotNull(again.blankedSentence)
    }
}
