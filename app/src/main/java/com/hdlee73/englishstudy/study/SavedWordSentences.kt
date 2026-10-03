package com.hdlee73.englishstudy.study

import com.hdlee73.englishstudy.dictionary.WordEntry
import com.hdlee73.englishstudy.dictionary.examplePairs
import com.hdlee73.englishstudy.dictionary.studyVersion
import com.hdlee73.englishstudy.speaking.model.SentencePair

/** Turns the example sentences of the saved words into a speaking-practice dataset. */
internal object SavedWordSentences {
    /**
     * Every example sentence of every saved word, ordered by word, without duplicates (two words may
     * share a sentence). The Korean translation is empty for examples that have none.
     */
    fun fromEntries(entries: List<WordEntry>): List<SentencePair> {
        val seen = HashSet<String>()
        val out = ArrayList<SentencePair>()
        entries.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.word }).forEach { entry ->
            examplePairs(entry.studyVersion().examples).forEach { (english, korean) ->
                if (seen.add(english.lowercase())) out += SentencePair(korean, english)
            }
        }
        return out
    }
}
