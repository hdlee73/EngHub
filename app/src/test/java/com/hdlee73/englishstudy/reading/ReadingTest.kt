package com.hdlee73.englishstudy.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadingTest {
    private val sample = """
        ### Science | Why Sleep?
        First paragraph here.
        Second paragraph here.

        ### Food | Coffee
        Only one paragraph.
        ### Broken
    """.trimIndent()

    @Test fun libraryFileIsParsedIntoArticles() {
        val articles = ReadingLibrary.parse(sample)
        assertEquals(2, articles.size)
        assertEquals("a001", articles[0].id)
        assertEquals("Science", articles[0].topic)
        assertEquals("Why Sleep?", articles[0].title)
        assertEquals(listOf("First paragraph here.", "Second paragraph here."), articles[0].paragraphs)
        assertEquals("a002", articles[1].id)
    }

    @Test fun bundledLibraryIsWellFormed() {
        val file = File("src/main/assets/reading_articles.txt")
        val articles = ReadingLibrary.parse(file.readText(Charsets.UTF_8))
        assertTrue("at least 45 articles, found ${articles.size}", articles.size >= 45)
        assertEquals("size is a multiple of 3 so every day gets a full set", 0, articles.size % 3)
        assertEquals(articles.size, articles.map { it.title }.toSet().size)
        articles.forEach { a ->
            assertTrue("${a.title}: 3+ paragraphs", a.paragraphs.size >= 3)
            assertTrue("${a.title}: ${a.wordCount} words", a.wordCount in 180..420)
            assertTrue("${a.title}: topic missing", a.topic.isNotBlank())
            assertTrue("${a.title}: no tabs or markup", a.paragraphs.none { '\t' in it || it.startsWith("#") })
        }
        // The three texts of a day have three different topics.
        articles.chunked(3).forEach { day ->
            assertEquals("topics repeat in ${day.map { it.title }}", 3, day.map { it.topic }.toSet().size)
        }
    }

    @Test fun eachDayGivesThreeArticlesAndAllAreSeenBeforeRepeating() {
        val articles = (1..9).map { ReadingArticle("a$it", "t$it", "title$it", listOf("text")) }
        val days = (0L..2L).map { DailyReading.pick(it, articles) }
        assertTrue(days.all { it.size == 3 })
        assertEquals(9, days.flatten().map { it.id }.toSet().size)
        assertEquals(DailyReading.pick(1, articles), DailyReading.pick(1, articles))
        assertEquals(DailyReading.pick(0, articles).map { it.id }, DailyReading.pick(3, articles).map { it.id })
    }

    @Test fun smallLibrariesStillWork() {
        val one = listOf(ReadingArticle("a1", "t", "x", listOf("y")))
        assertEquals(1, DailyReading.pick(5, one).size)
        assertTrue(DailyReading.pick(5, emptyList()).isEmpty())
    }

    @Test fun tappedWordIsFoundFromAnyLetterAndJustAfterTheLastOne() {
        val text = "The printing press, invented by Gutenberg's team, didn't stop."
        fun word(offset: Int) = ReadingWords.rangeAt(text, offset)?.let { text.substring(it.first, it.last + 1) }
        assertEquals("printing", word(6))
        assertEquals("printing", word(4))
        assertEquals("printing", word(12))
        assertEquals("press", word(17))
        assertEquals("Gutenberg's", word(text.indexOf("Gutenberg") + 3))
        assertEquals("didn't", word(text.indexOf("didn't") + 2))
        assertNull(ReadingWords.rangeAt("", 0))
        assertNull(ReadingWords.rangeAt("123 456", 2))
    }

    @Test fun possessivesAreRemovedForLookup() {
        assertEquals("Gutenberg", ReadingWords.lookupForm("Gutenberg's"))
        assertEquals("farmers", ReadingWords.lookupForm("farmers'"))
        assertEquals("didn't", ReadingWords.lookupForm("didn't"))
        assertEquals("Gutenberg", ReadingWords.lookupForm("Gutenberg’s"))
    }

    @Test fun longTextIsSplitAtSentenceEnds() {
        val text = (1..30).joinToString(" ") { "This is sentence number $it." }
        val parts = TextChunks.split(text, 200)
        assertTrue(parts.all { it.length <= 200 })
        assertTrue(parts.all { it.endsWith(".") })
        assertEquals(text, parts.joinToString(" "))
        assertEquals(listOf("Short text."), TextChunks.split("Short text."))
    }
}
