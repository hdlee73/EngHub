package com.hdlee73.englishstudy.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikinewsTest {
    @Test fun parsesTheTitleList() {
        val json = """{"batchcomplete":true,"query":{"categorymembers":[{"pageid":1,"ns":0,"title":"First story"},{"pageid":2,"ns":0,"title":"Second: story"}]}}"""
        assertEquals(listOf("First story", "Second: story"), WikinewsSource.parseTitles(json))
        assertEquals(emptyList<String>(), WikinewsSource.parseTitles("oops"))
    }

    @Test fun parsesAnExtract() {
        val json = """{"query":{"pages":[{"pageid":7,"title":"Big news","extract":"Line one.\nLine two."}]}}"""
        assertEquals("Big news" to "Line one.\nLine two.", WikinewsSource.parseExtract(json))
        assertNull(WikinewsSource.parseExtract("""{"query":{"pages":[{"title":"Missing","missing":true}]}}"""))
    }

    @Test fun urlsAreEncoded() {
        assertTrue(WikinewsSource.extractUrl("A & B: news").contains("titles=A+%26+B%3A+news"))
        assertEquals("https://en.wikinews.org/wiki/A_%26_B:_news", WikinewsSource.pageUrl("A & B: news"))
    }

    private val extract = """
        Officials said on Monday that the new bridge across the river will open to traffic early next month, after years of delays.

        == Background ==
        Short caption
        The project began in 2019 and was meant to be finished within three years, but heavy rain and rising costs slowed the work[1] considerably.

        == Sources ==
        The bridge opens at last. Local newspaper, long enough line to look like a sentence here.
    """.trimIndent()

    @Test fun paragraphsDropHeadingsCaptionsAndSources() {
        val paragraphs = WikinewsSource.paragraphs(extract)
        assertEquals(2, paragraphs.size)
        assertTrue(paragraphs[0].startsWith("Officials said"))
        assertTrue(!paragraphs[1].contains("[1]"))
        assertTrue(paragraphs.none { it.contains("local newspaper", true) })
    }

    private fun para(words: Int) = (1..words).joinToString(" ") { "word$it" }.let { "Alpha " + it + " end." }

    @Test fun trimKeepsWholeParagraphsUntilEnough() {
        val trimmed = WikinewsSource.trim(listOf(para(80), para(80), para(80), para(80)))!!
        assertEquals(3, trimmed.size) // 3 × 82 words ≥ 190
    }

    @Test fun trimCutsAtSentenceEndWhenTooLong() {
        val long = (1..40).joinToString(" ") { "This is sentence number $it of many." }
        val trimmed = WikinewsSource.trim(listOf(para(100), long))!!
        val words = trimmed.sumOf { it.split(' ').size }
        assertTrue(words <= 260)
        assertTrue(trimmed.last().endsWith("."))
    }

    @Test fun trimRejectsShortArticles() {
        assertNull(WikinewsSource.trim(listOf(para(40), para(30))))
    }

    @Test fun readabilityRanksHardTextHigher() {
        val easy = "The cat sat on the mat. It was a sunny day. We all went out to play."
        val hard = "Parliamentary representatives deliberately postponed the comprehensive legislation, citing unprecedented constitutional complications."
        assertTrue(Readability.grade(hard) > Readability.grade(easy) + 5)
        assertEquals(0.0, Readability.grade(""), 0.0)
    }

    @Test fun keyExpressionsAreSpreadAndUnique() {
        val text = listOf(
            "Local officials have decided to carry out a large review of the aging bridge before winter.",
            "Engineers said the corrosion discovered last spring had spread much faster than anyone expected.",
            "Residents who live near the river were relieved that the repairs would finally begin soon."
        )
        val found = KeyExpressions.extract(text, 3)
        assertTrue(found.isNotEmpty() && found.size <= 3)
        assertEquals(found.size, found.map { it.expression.lowercase() }.toSet().size)
        assertTrue(found.all { it.sentence.contains(it.expression, ignoreCase = true) })
        assertNotNull(found.firstOrNull())
    }
}
