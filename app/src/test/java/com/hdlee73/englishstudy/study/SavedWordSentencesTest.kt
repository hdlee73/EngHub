package com.hdlee73.englishstudy.study

import com.hdlee73.englishstudy.dictionary.WordEntry
import com.hdlee73.englishstudy.speaking.model.SentencePair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedWordSentencesTest {
    private fun entry(word: String, examples: String) =
        WordEntry(word = word, ipa = "", korean = "뜻", english = "", examples = examples)

    @Test fun collectsExamplesOrderedByWord() {
        val pairs = SavedWordSentences.fromEntries(listOf(
            entry("banana", "She bought a banana yesterday.\t그녀는 어제 바나나를 샀다."),
            entry("Apple", "I eat an apple every day.\t나는 매일 사과를 먹는다.\nThis apple is very sweet.\t이 사과는 아주 달다.")
        ))
        assertEquals(listOf(
            SentencePair("나는 매일 사과를 먹는다.", "I eat an apple every day."),
            SentencePair("이 사과는 아주 달다.", "This apple is very sweet."),
            SentencePair("그녀는 어제 바나나를 샀다.", "She bought a banana yesterday.")
        ), pairs)
    }

    @Test fun aSentenceSharedByTwoWordsAppearsOnce() {
        val shared = "I eat an apple and a banana.\t나는 사과와 바나나를 먹는다."
        val pairs = SavedWordSentences.fromEntries(listOf(entry("apple", shared), entry("banana", shared)))
        assertEquals(1, pairs.size)
    }

    @Test fun anExampleWithoutATranslationKeepsAnEmptyKorean() {
        val pairs = SavedWordSentences.fromEntries(listOf(entry("apple", "I eat an apple every day.")))
        assertEquals(listOf(SentencePair("", "I eat an apple every day.")), pairs)
    }

    @Test fun fragmentsAndEmptyExamplesAreIgnored() {
        val pairs = SavedWordSentences.fromEntries(listOf(entry("apple", "an apple\t사과"), entry("pear", "")))
        assertTrue(pairs.isEmpty())
    }

    @Test fun noWordsGiveNoSentences() {
        assertTrue(SavedWordSentences.fromEntries(emptyList()).isEmpty())
    }
}
