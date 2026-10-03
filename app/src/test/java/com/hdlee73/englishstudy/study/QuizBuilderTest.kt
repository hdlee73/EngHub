package com.hdlee73.englishstudy.study

import com.hdlee73.englishstudy.dictionary.WordEntry
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizBuilderTest {
    private fun entry(id: Long, word: String, vararg examples: String, korean: String = "1. 뜻") =
        WordEntry(id = id, word = word, ipa = "", korean = korean, english = "", examples = examples.joinToString("\n"))

    private val fruits = listOf(
        entry(1, "apple", "I eat an apple every day.\t나는 매일 사과를 먹는다."),
        entry(2, "banana", "She bought a banana yesterday.\t그녀는 어제 바나나를 샀다."),
        entry(3, "cherry", "This cherry tastes very sweet.\t이 체리는 아주 달다."),
        entry(4, "grape", "He picked a grape from the bunch.\t그는 송이에서 포도 한 알을 땄다."),
        entry(5, "melon", "We shared a melon after lunch.\t우리는 점심 후에 멜론을 나눠 먹었다.")
    )

    @Test fun everyQuestionHasFourDistinctChoicesIncludingTheAnswer() {
        val questions = QuizBuilder.build(fruits, 5, Random(1))
        assertEquals(5, questions.size)
        questions.forEach { q ->
            assertEquals(4, q.choices.size)
            assertEquals(4, q.choices.map { it.lowercase() }.toSet().size)
            assertEquals(q.word, q.choices[q.answerIndex])
            assertTrue(q.isCorrect(q.answerIndex))
            assertFalse(q.isCorrect((q.answerIndex + 1) % 4))
        }
    }

    @Test fun theBlankedSentenceHidesTheWordButKeepsTheRest() {
        QuizBuilder.build(fruits, 5, Random(2)).forEach { q ->
            assertTrue(q.blankedSentence.contains(Blanker.BLANK))
            assertFalse(q.blankedSentence.contains(q.word, ignoreCase = true))
            assertTrue(q.sentence.contains(q.word, ignoreCase = true))
            assertTrue(q.translation.isNotBlank())
        }
    }

    @Test fun wrongChoicesComeFromOtherSavedWords() {
        val names = fruits.map { it.word }.toSet()
        QuizBuilder.build(fruits, 5, Random(3)).forEach { q -> assertTrue(names.containsAll(q.choices)) }
    }

    @Test fun aWordWithoutAUsableExampleIsNotQuizzed() {
        val words = fruits + entry(6, "ghost") + entry(7, "pear", "It is raining today.\t오늘은 비가 온다.")
        assertEquals(fruits.map { it.word }.toSet(), QuizBuilder.eligible(words).map { it.word }.toSet())
        assertEquals(5, QuizBuilder.build(words, 10, Random(4)).size)
    }

    @Test fun aChoiceThatAppearsInTheSentenceIsNeverOffered() {
        val words = listOf(
            entry(1, "run", "I run and swim every day.\t나는 매일 달리고 수영한다."),
            entry(2, "swim"), entry(3, "jump"), entry(4, "walk"), entry(5, "climb")
        )
        val q = QuizBuilder.build(words, 5, Random(5)).single { it.word == "run" }
        assertEquals(setOf("run", "jump", "walk", "climb"), q.choices.toSet())
    }

    @Test fun inflectedFormsOfTheAnswerAreNotOfferedAsWrongChoices() {
        val words = listOf(
            entry(1, "run", "I run every morning.\t나는 매일 아침 달린다."),
            entry(2, "running"), entry(3, "jump"), entry(4, "walk"), entry(5, "climb")
        )
        val q = QuizBuilder.build(words, 5, Random(6)).single { it.word == "run" }
        assertFalse(q.choices.contains("running"))
    }

    @Test fun fewSavedWordsAreCompletedWithCommonWords() {
        val words = listOf(entry(1, "apple", "I eat an apple every day.\t나는 매일 사과를 먹는다."))
        val q = QuizBuilder.build(words, 5, Random(7)).single()
        assertEquals(4, q.choices.size)
        assertEquals(4, q.choices.toSet().size)
        assertEquals("apple", q.choices[q.answerIndex])
    }

    @Test fun phrasesGetPhraseChoices() {
        val words = listOf(
            entry(1, "give up", "Never give up on your dream.\t네 꿈을 포기하지 마."),
            entry(2, "apple"), entry(3, "put off"), entry(4, "take off"), entry(5, "look after"), entry(6, "pear")
        )
        val q = QuizBuilder.build(words, 6, Random(8)).single { it.word == "give up" }
        assertEquals(setOf("give up", "put off", "take off", "look after"), q.choices.toSet())
    }

    @Test fun theSameSeedGivesTheSameQuiz() {
        assertEquals(QuizBuilder.build(fruits, 5, Random(42)), QuizBuilder.build(fruits, 5, Random(42)))
    }

    @Test fun theNumberOfQuestionsIsLimited() {
        assertEquals(2, QuizBuilder.build(fruits, 2, Random(9)).size)
        assertEquals(0, QuizBuilder.build(fruits, 0, Random(9)).size)
        assertEquals(5, QuizBuilder.build(fruits, 99, Random(9)).size)
        assertEquals(0, QuizBuilder.build(emptyList(), 5, Random(9)).size)
    }

    @Test fun eachWordAppearsOnlyOnce() {
        val words = QuizBuilder.build(fruits, 5, Random(10)).map { it.word }
        assertEquals(words.size, words.toSet().size)
    }

    @Test fun anInflectedExampleStillMakesAQuestion() {
        val words = listOf(entry(1, "go", "She went home early.\t그녀는 일찍 집에 갔다."))
        val q = QuizBuilder.build(words, 1, Random(11)).single()
        assertEquals("She _____ home early.", q.blankedSentence)
        assertEquals("go", q.word)
    }

    @Test fun meaningHintSkipsTagsAndNumbering() {
        assertEquals("[명사] 호기심, 궁금증", QuizBuilder.firstMeaning("[명사] 호기심, 궁금증"))
        assertEquals("달리다", QuizBuilder.firstMeaning("[동사]\n1. 달리다\n2. 운영하다"))
        assertEquals("[명사] 사과", QuizBuilder.firstMeaning("1. [명사] 사과"))
        assertEquals("", QuizBuilder.firstMeaning(""))
        assertEquals("", QuizBuilder.firstMeaning("No Korean here"))
    }

    @Test fun fallbackWordsAreUsable() {
        assertTrue(FallbackWords.ALL.size >= 100)
        assertEquals(FallbackWords.ALL.size, FallbackWords.ALL.map { it.lowercase() }.toSet().size)
        assertTrue(FallbackWords.ALL.none { it.isBlank() || it != it.trim() })
    }
}
