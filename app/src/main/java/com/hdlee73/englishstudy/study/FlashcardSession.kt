package com.hdlee73.englishstudy.study

import com.hdlee73.englishstudy.dictionary.WordEntry
import kotlin.random.Random

/** Which saved words go into a flashcard round. */
enum class DeckFilter(val label: String, val description: String) {
    TO_LEARN("외우는 중", "아직 완전히 외우지 못한 단어부터"),
    MISSED("틀린 단어", "마지막에 모르겠다고 했거나 퀴즈에서 틀린 단어"),
    ALL("전체", "저장한 모든 단어")
}

internal object FlashcardDeck {
    /** Words that match [filter], optionally shuffled. In the "learning" deck, words in lower boxes come first. */
    fun build(
        words: List<WordEntry>,
        progress: Map<String, WordProgress>,
        filter: DeckFilter,
        shuffle: Boolean,
        random: Random = Random.Default
    ): List<WordEntry> {
        fun of(word: WordEntry) = progress[progressKey(word.word)] ?: WordProgress()
        val selected = when (filter) {
            DeckFilter.ALL -> words
            DeckFilter.TO_LEARN -> words.filter { !of(it).mastered }
            DeckFilter.MISSED -> words.filter { of(it).lastResult == false }
        }
        val ordered = if (shuffle) selected.shuffled(random) else selected.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.word })
        // sortedBy is stable, so the shuffle is kept inside each box.
        return if (shuffle && filter == DeckFilter.TO_LEARN) ordered.sortedBy { of(it).box } else ordered
    }

    fun count(words: List<WordEntry>, progress: Map<String, WordProgress>, filter: DeckFilter): Int =
        build(words, progress, filter, shuffle = false).size
}

/**
 * One pass through a deck. "알아요" removes the card; "모르겠어요" puts it back a few cards later so it is
 * seen again in the same round. The round ends when every card has been known once.
 */
class FlashcardSession(cards: List<WordEntry>) {
    private val queue = ArrayDeque(cards)
    private val missedOrder = LinkedHashMap<Long, WordEntry>()

    val total: Int = cards.size
    var known: Int = 0
        private set
    /** How many times "모르겠어요" was pressed, counting a card each time it came back. */
    var missedAnswers: Int = 0
        private set

    val current: WordEntry? get() = queue.firstOrNull()
    val remaining: Int get() = queue.size
    val finished: Boolean get() = queue.isEmpty()
    /** Cards missed at least once this round, in the order they were first missed. */
    val missedCards: List<WordEntry> get() = missedOrder.values.toList()

    /** Records the answer for the current card and returns that card (null when the round is over). */
    fun answer(knows: Boolean): WordEntry? {
        val card = queue.removeFirstOrNull() ?: return null
        if (knows) {
            known++
        } else {
            missedAnswers++
            missedOrder.putIfAbsent(card.id, card)
            queue.add(minOf(REVIEW_GAP, queue.size), card)
        }
        return card
    }

    companion object {
        /** A missed card returns after this many other cards (or at the end of a shorter queue). */
        const val REVIEW_GAP = 4
    }
}
