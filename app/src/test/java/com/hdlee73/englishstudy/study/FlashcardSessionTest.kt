package com.hdlee73.englishstudy.study

import com.hdlee73.englishstudy.dictionary.WordEntry
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardSessionTest {
    private fun card(id: Long, word: String) = WordEntry(id = id, word = word, ipa = "", korean = "뜻", english = "", examples = "")
    private val a = card(1, "apple")
    private val b = card(2, "Banana")
    private val c = card(3, "cherry")
    private val d = card(4, "date")

    @Test fun knownCardsLeaveTheDeckAndTheRoundEnds() {
        val s = FlashcardSession(listOf(a, b))
        assertEquals(2, s.total)
        assertEquals(a, s.current)
        assertEquals(a, s.answer(true))
        assertEquals(b, s.current)
        assertFalse(s.finished)
        s.answer(true)
        assertTrue(s.finished)
        assertNull(s.current)
        assertNull(s.answer(true))
        assertEquals(2, s.known)
        assertEquals(0, s.remaining)
    }

    @Test fun aMissedCardComesBackLaterInTheSameRound() {
        val s = FlashcardSession(listOf(a, b, c))
        s.answer(false)
        assertEquals(b, s.current)
        assertEquals(3, s.remaining)
        s.answer(true); s.answer(true)
        assertEquals(a, s.current)
        s.answer(true)
        assertTrue(s.finished)
        assertEquals(3, s.known)
        assertEquals(1, s.missedAnswers)
        assertEquals(listOf(a), s.missedCards)
    }

    @Test fun aMissedCardIsReinsertedAfterAFewOthers() {
        val cards = (1..8L).map { card(it, "w$it") }
        val s = FlashcardSession(cards)
        s.answer(false)
        val order = generateSequence { s.current?.also { s.answer(true) } }.toList()
        assertEquals(cards[0], order[FlashcardSession.REVIEW_GAP])
        assertEquals(8, order.size)
    }

    @Test fun aLoneMissedCardStaysUntilItIsKnown() {
        val s = FlashcardSession(listOf(a))
        s.answer(false)
        assertEquals(a, s.current)
        assertEquals(1, s.remaining)
        s.answer(false)
        assertEquals(2, s.missedAnswers)
        assertEquals(listOf(a), s.missedCards)
        s.answer(true)
        assertTrue(s.finished)
    }

    @Test fun anEmptyDeckIsFinishedAtOnce() {
        val s = FlashcardSession(emptyList())
        assertTrue(s.finished)
        assertNull(s.current)
    }

    @Test fun deckFiltersUseProgress() {
        val progress = mapOf(
            "apple" to WordProgress(box = Leitner.MASTERED_BOX, lastResult = true),
            "banana" to WordProgress(box = 0, lastResult = false),
            "cherry" to WordProgress(box = 1, lastResult = true)
        )
        val words = listOf(a, b, c, d)
        assertEquals(listOf(a, b, c, d), FlashcardDeck.build(words, progress, DeckFilter.ALL, shuffle = false))
        assertEquals(listOf(b, c, d), FlashcardDeck.build(words, progress, DeckFilter.TO_LEARN, shuffle = false))
        assertEquals(listOf(b), FlashcardDeck.build(words, progress, DeckFilter.MISSED, shuffle = false))
        assertEquals(3, FlashcardDeck.count(words, progress, DeckFilter.TO_LEARN))
    }

    @Test fun unshuffledDeckIsAlphabeticalIgnoringCase() {
        val deck = FlashcardDeck.build(listOf(d, b, c, a), emptyMap(), DeckFilter.ALL, shuffle = false)
        assertEquals(listOf("apple", "Banana", "cherry", "date"), deck.map { it.word })
    }

    @Test fun shuffledLearningDeckStartsWithTheWeakestWords() {
        val progress = mapOf("apple" to WordProgress(box = 2), "banana" to WordProgress(box = 1), "cherry" to WordProgress(box = 2))
        repeat(20) { seed ->
            val deck = FlashcardDeck.build(listOf(a, b, c, d), progress, DeckFilter.TO_LEARN, shuffle = true, random = Random(seed))
            assertEquals(d, deck.first())
            assertEquals(b, deck[1])
            assertEquals(setOf(a, c), deck.drop(2).toSet())
        }
    }

    @Test fun shuffledAllDeckKeepsEveryCard() {
        val deck = FlashcardDeck.build(listOf(a, b, c, d), emptyMap(), DeckFilter.ALL, shuffle = true, random = Random(3))
        assertEquals(setOf(a, b, c, d), deck.toSet())
        assertEquals(4, deck.size)
    }

    @Test fun leitnerMovesUpOnKnownAndResetsOnMiss() {
        var p = WordProgress()
        p = Leitner.afterCard(p, true, 10L)
        assertEquals(1, p.box)
        p = Leitner.afterCard(p, true, 20L)
        p = Leitner.afterCard(p, true, 30L)
        p = Leitner.afterCard(p, true, 40L)
        assertEquals(Leitner.MASTERED_BOX, p.box)
        assertTrue(p.mastered)
        assertEquals(4, p.seen)
        assertEquals(40L, p.lastStudied)
        p = Leitner.afterCard(p, false, 50L)
        assertEquals(0, p.box)
        assertEquals(1, p.missed)
        assertEquals(false, p.lastResult)
        assertFalse(p.mastered)
    }

    @Test fun quizResultsAdjustProgressLightly() {
        val start = WordProgress(box = 2, lastResult = false)
        val right = Leitner.afterQuiz(start, true, 5L)
        assertEquals(2, right.box)
        assertEquals(1, right.quizCorrect)
        assertEquals(true, right.lastResult)
        val wrong = Leitner.afterQuiz(start, false, 6L)
        assertEquals(0, wrong.box)
        assertEquals(1, wrong.quizWrong)
        assertEquals(false, wrong.lastResult)
    }

    @Test fun memoryStoreKeysIgnoreCase() {
        val store = MemoryProgressStore()
        store.update("Apple") { it.copy(box = 2) }
        store.update(" apple ") { it.copy(seen = it.seen + 1) }
        assertEquals(1, store.all().size)
        assertEquals(WordProgress(box = 2, seen = 1), store.all()["apple"])
        store.remove("APPLE")
        assertTrue(store.all().isEmpty())
    }
}
