package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class SimpleExamplesTest {
    @Test fun everydaySentencesPass() {
        assertTrue(SentenceExamples.isSimple("He still resides at his parents' house."))
        assertTrue(SentenceExamples.isSimple("Tom is planting a palm tree in his backyard."))
        assertTrue(SentenceExamples.isSimple("Where do you live?"))
    }

    @Test fun oldQuotationsAndFragmentsAreRejected() {
        assertFalse(SentenceExamples.isSimple("[...] The madding Winds are huſh'd, the Tempeſts ceaſe, / And all is calm."))
        assertFalse(SentenceExamples.isSimple("Thou hast sinned, and thy house shall fall."))
        assertFalse(SentenceExamples.isSimple("He resides."))
        assertFalse(SentenceExamples.isSimple("the dog ran home."))
        assertFalse(SentenceExamples.isSimple("This sentence is far too long to be a simple example because it keeps going on and on without stopping."))
        assertFalse(SentenceExamples.isSimple("THE QUICK brown fox jumps over."))
    }

    @Test fun pronunciationIsNormalizedAndPlainWordsRejected() {
        assertEquals("/rɪˈzaɪd/", Pronunciation.clean(" rɪˈzaɪd "))
        assertEquals("/rɪˈzaɪd/", Pronunciation.clean("/rɪˈzaɪd/"))
        assertEquals("", Pronunciation.clean(""))
        assertEquals("", Pronunciation.clean(null))
        assertEquals("", Pronunciation.clean("a very long text that cannot be a transcription at all"))
        assertEquals("/kæt/", Pronunciation.first(listOf(null, "", "[kæt]", "/other/")))
    }

    @Test fun savedWordsSortInEachOrder() {
        val words = listOf("banana" to 2L, "Apple" to 3L, "cherry" to 1L)
        fun order(sort: SavedSort) = sort.apply(words, { it.first }, { it.second }).map { it.first }
        assertEquals(listOf("Apple", "banana", "cherry"), order(SavedSort.ALPHABETICAL))
        assertEquals(listOf("cherry", "banana", "Apple"), order(SavedSort.REVERSE))
        assertEquals(listOf("Apple", "banana", "cherry"), order(SavedSort.NEWEST))
        assertEquals(listOf("cherry", "banana", "Apple"), order(SavedSort.OLDEST))
        assertEquals(SavedSort.ALPHABETICAL, SavedSort.of("unknown"))
        assertEquals(SavedSort.NEWEST, SavedSort.of("NEWEST"))
    }
}
