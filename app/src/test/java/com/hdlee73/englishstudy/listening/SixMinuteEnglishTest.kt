package com.hdlee73.englishstudy.listening

import org.junit.Assert.assertEquals
import org.junit.Test

class SixMinuteEnglishTest {
    private val sample = "261001_6_minute_english_why_does_heartbreak_hurt_so_much_download.mp3"

    @Test
    fun fileNameIsThePublicationDate() {
        assertEquals("20261001_6min.mp3", SixMinuteEnglish.fileName("261001"))
    }

    @Test
    fun episodeKeyIsReadFromOldAndNewNames() {
        assertEquals("261001", SixMinuteEnglish.episodeKey(sample))
        assertEquals("260924", SixMinuteEnglish.episodeKey("260924_6_minute_english_why_do_we_itch_download.mp3"))
        assertEquals("261001", SixMinuteEnglish.episodeKey("6min 261001 Why does heartbreak hurt so much"))
        assertEquals("261008", SixMinuteEnglish.episodeKey("20261008_6min.mp3"))
        assertEquals(null, SixMinuteEnglish.episodeKey("Audiobook chapter 1.mp3"))
    }

    @Test
    fun episodePagesAreNewestFirst() {
        val html = """
            <a href="/learningenglish/english/features/6-minute-english_2026/ep-260924">old</a>
            <a href="/learningenglish/english/features/6-minute-english_2026/ep-261001">new</a>
            <a href="/learningenglish/english/features/6-minute-english_2026/ep-261001">again</a>
        """
        val pages = SixMinuteEnglish.episodePages(html)
        assertEquals(listOf("261001", "260924"), pages.map { it.id })
        assertEquals("https://www.bbc.co.uk/learningenglish/english/features/6-minute-english_2026/ep-261001", pages[0].url)
    }

    @Test
    fun mp3LinksPreferTheDownloadFile() {
        val html = """
            <a href="http://downloads.bbc.co.uk/learningenglish/features/6min/261001_6_minute_english_why_does_heartbreak_hurt_so_much.mp3">a</a>
            <a href="https://downloads.bbc.co.uk/learningenglish/features/6min/$sample">b</a>
            <a href="https://downloads.bbc.co.uk/learningenglish/features/6min/260924_6_minute_english_older_download.mp3">c</a>
        """
        val links = SixMinuteEnglish.mp3Links(html)
        assertEquals(listOf("261001", "260924"), links.map { it.id })
        assertEquals("https://downloads.bbc.co.uk/learningenglish/features/6min/$sample", links[0].url)
    }

    @Test
    fun subtitleKeyIgnoresFolderExtensionAndCase() {
        assertEquals(SubtitleLinks.key("Episode_One.MP3"), SubtitleLinks.key("/Download/episode_one.mp3"))
    }
}
