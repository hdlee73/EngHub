package com.hdlee73.englishstudy.phrases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhraseBankTest {
    private val all: List<Phrase> by lazy {
        val file = listOf("src/main/assets/phrases_us_uk.txt", "app/src/main/assets/phrases_us_uk.txt").map(::File).first { it.exists() }
        PhraseBank.parse(file.readText())
    }

    @Test fun everyLineIsWellFormed() {
        assertTrue(all.size >= 300)
        assertEquals(all.size, all.map { it.phrase + "|" + it.region }.toSet().size)
        all.forEach {
            assertTrue(it.phrase, it.region in PhraseBank.REGIONS)
            assertTrue(it.phrase, it.category in PhraseBank.CATEGORIES)
            assertTrue(it.phrase, it.korean.isNotBlank() && it.example.isNotBlank() && it.exampleKo.isNotBlank())
            assertTrue(it.phrase, it.topic in PhraseBank.TOPICS)
        }
    }

    @Test fun findsBothSidesOfAUsUkDifference() {
        val found = PhraseBank.search(all, "lift").map { it.phrase }
        assertEquals("lift", found.first())
        assertTrue("elevator" in found)
    }

    @Test fun searchesKoreanAndFiltersByRegion() {
        val uk = PhraseBank.search(all, "엘리베이터", region = "영국")
        assertEquals(listOf("lift"), uk.map { it.phrase })
        assertTrue(PhraseBank.search(all, "소용없다").any { it.phrase.startsWith("It's no use") })
    }

    @Test fun emptyQueryListsEverythingInFileOrder() {
        assertEquals(all, PhraseBank.search(all, ""))
        assertTrue(PhraseBank.search(all, "", category = "슬랭").all { it.category == "슬랭" })
    }

    @Test fun narrowsByTopic() {
        val food = PhraseBank.search(all, "", topic = "음식")
        assertTrue(food.isNotEmpty() && food.all { it.topic == "음식" })
        assertTrue(PhraseBank.search(all, "aubergine", topic = "음식").isNotEmpty())
    }
}
