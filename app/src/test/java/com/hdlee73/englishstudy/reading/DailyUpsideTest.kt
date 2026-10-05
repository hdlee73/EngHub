package com.hdlee73.englishstudy.reading

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyUpsideTest {
    @Test fun findsArticleLinksInOrderOnce() {
        val html = """
            <a href="/economics/macro/">Macro</a>
            <a href="/industries/industrials/the-biggest-steel-plant/">One</a>
            <a href="https://www.thedailyupside.com/finance/etfs/why-buffer-etfs-persist/?utm=x">Two</a>
            <a href="/industries/industrials/the-biggest-steel-plant/">One again</a>
            <a href="/finance/etfs/page/2/">Older</a>
        """.trimIndent()
        assertEquals(
            listOf(
                "https://www.thedailyupside.com/industries/industrials/the-biggest-steel-plant/",
                "https://www.thedailyupside.com/finance/etfs/why-buffer-etfs-persist/"
            ),
            DailyUpsideSource.articleLinks(html)
        )
    }

    @Test fun readsLinksFromAFeed() {
        val feed = "<item><title>A</title><link>https://www.thedailyupside.com/technology/ai/bots-shop/</link></item>"
        assertEquals(listOf("https://www.thedailyupside.com/technology/ai/bots-shop/"), DailyUpsideSource.articleLinks(feed))
    }

    @Test fun topicComesFromTheSection() {
        assertEquals("산업", DailyUpsideSource.topicOf("https://www.thedailyupside.com/industries/industrials/x/"))
        assertEquals("뉴스", DailyUpsideSource.topicOf("https://example.com/"))
    }

    @Test fun decodesEntities() {
        assertEquals("Don’t panic & \"go\" – 5", DailyUpsideSource.decode("Don&#8217;t panic &amp; &quot;go&quot; &ndash; &#x35;"))
    }

    private val page = """
        <html><head><title>Steel &amp; Iowa | The Daily Upside</title>
        <meta property="og:title" content="The Biggest Steel Plant Is Coming &#8211; Some Hawkeyes Disagree | The Daily Upside"></head>
        <body><nav><p>Menu item that is long enough to count as a paragraph here.</p></nav>
        <article>
          <h1>The Biggest Steel Plant Is Coming</h1>
          <p>By Sean Craig</p>
          <p>Sign up for our newsletter to get the latest stories delivered to your inbox every morning.</p>
          <p>Last week, business and political leaders gathered at the White House to announce a new steel mill planned for Iowa.</p>
          <p>Building the new plant is <a href="/x">Mesabi Metallics</a>, a subsidiary of Indian conglomerate Essar Group, officials said.</p>
          <script>var p = "<p>not a paragraph at all, really, just script text</p>";</script>
          <p>The White House proclaimed the company&#8217;s plans to set up shop in Iowa are a sign of a domestic revival.</p>
          <p>© 2026 The Daily Upside. All rights reserved and so on and so forth for a long line.</p>
        </article>
        <footer><p>Footer text that is long enough to look like a real paragraph of text.</p></footer></body></html>
    """.trimIndent()

    @Test fun titleHasNoSiteName() {
        assertEquals("The Biggest Steel Plant Is Coming – Some Hawkeyes Disagree", DailyUpsideSource.title(page))
    }

    @Test fun paragraphsAreOnlyTheBody() {
        val paragraphs = DailyUpsideSource.paragraphs(page)
        assertEquals(3, paragraphs.size)
        assertTrue(paragraphs[0].startsWith("Last week"))
        assertTrue(paragraphs[1].contains("Mesabi Metallics, a subsidiary"))
        assertTrue(paragraphs[2].contains("company’s plans"))
    }

    @Test fun shortPagesAreNotWorthReading() {
        assertFalse(DailyUpsideSource.isWorthReading(listOf("one two three four five six", "a b c d e f g")))
        assertTrue(DailyUpsideSource.isWorthReading(List(4) { List(50) { "word" }.joinToString(" ") }))
    }
}
