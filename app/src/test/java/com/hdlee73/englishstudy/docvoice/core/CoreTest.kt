package com.hdlee73.englishstudy.docvoice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class CoreTest {
    private fun fixture(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!.use { it.readBytes() }

    private fun extract(name: String) = Extractors.extract(name, fixture(name))

    // ---- 문서 추출 ------------------------------------------------------------

    @Test fun docx() {
        val t = extract("sample.docx")
        assertTrue(t, t.contains("첫 문단입니다.") && t.contains("마지막 문단."))
    }

    @Test fun xlsx() {
        val t = extract("sample.xlsx")
        assertTrue(t, t.contains("사과") && t.contains("Hello"))
    }

    @Test fun pptx() {
        val t = extract("sample.pptx")
        assertTrue(t, t.contains("제목 슬라이드") && t.contains("본문 내용"))
    }

    @Test fun hwpx() {
        val t = extract("sample.hwpx")
        assertTrue(t, t.contains("안녕하세요 한글 문서입니다.") && t.contains("둘째 문단"))
    }

    @Test fun legacyDoc() {
        val t = extract("legacy.doc")
        assertTrue(t, t.contains("레거시 워드 문서입니다.") && t.contains("Second sentence here."))
    }

    @Test fun legacyXls() {
        val t = extract("legacy.xls")
        assertTrue(t, t.contains("가나다") && t.contains("7"))
    }

    @Test fun legacyPpt() {
        val t = extract("legacy.ppt")
        assertTrue(t, t.contains("옛날 파워포인트") && t.contains("슬라이드 내용 하나"))
    }

    @Test fun txtAndCsv() {
        assertEquals("안녕하세요\nhello", Extractors.extract("a.txt", "안녕하세요\nhello".toByteArray()))
        assertTrue(Extractors.extract("a.csv", "a,b\n1,2".toByteArray()).contains("1"))
    }

    // ---- 문장 분리 · 문단 ------------------------------------------------------

    @Test fun splitKoreanAndEnglish() {
        assertEquals(2, Segmenter.splitSentences("안녕하세요. 반갑습니다.").size)
        assertEquals(1, Segmenter.splitSentences("Version 3.14 is out").size)
    }

    @Test fun shortEnglishMerged() {
        val s = Segmenter.buildSentences(listOf(Segment(0.0, 6.0, "Hello world. Yes. I am here today.")))
        assertTrue(s.isNotEmpty())
        assertTrue(s.toString(), s.all { Segmenter.hasHangul(it.text) || Segmenter.englishWordCount(it.text) >= 3 })
    }

    @Test fun paragraphsBreakOnSpeakerAndGap() {
        val a = listOf(
            Sentence(0.0, 1.0, "가나다.", 0), Sentence(1.1, 2.0, "라마바.", 0), Sentence(2.1, 3.0, "사아자.", 1),
        )
        assertEquals(listOf(2, 1), Segmenter.groupParagraphs(a).map { it.sentences.size })
        val b = listOf(Sentence(0.0, 1.0, "가나다."), Sentence(5.0, 6.0, "라마바."))
        assertEquals(2, Segmenter.groupParagraphs(b, gap = 1.5, useSpeaker = false).size)
        assertEquals(1, Segmenter.groupParagraphs(b, gap = 10.0, useSpeaker = false).size)
    }

    @Test fun speakerAssignmentByOverlap() {
        val s = listOf(Sentence(0.0, 2.0, "가나다."), Sentence(3.0, 5.0, "라마바."))
        Segmenter.assignSpeakers(s, listOf(Triple(0.0, 2.5, 0), Triple(2.6, 5.0, 1)))
        assertEquals(0, s[0].speaker)
        assertEquals(1, s[1].speaker)
    }

    // ---- 내보내기 --------------------------------------------------------------

    private fun zipEntries(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                out[e.name] = z.readBytes()
            }
        }
        return out
    }

    private fun assertWellFormed(files: Map<String, ByteArray>) {
        val f = DocumentBuilderFactory.newInstance()
        for ((n, b) in files) if (n.endsWith(".xml") || n.endsWith(".rels")) f.newDocumentBuilder().parse(ByteArrayInputStream(b))
    }

    @Test fun xlsxOneSentencePerRow() {
        val sents = listOf(Sentence(0.0, 1.0, "안녕하세요 & <반갑습니다>.", 0), Sentence(1.0, 2.0, "Nice to meet you.", 1))
        val files = zipEntries(Exporter.xlsx(sents, includeTime = true, showSpeaker = true))
        assertTrue(files.containsKey("xl/worksheets/sheet1.xml"))
        assertWellFormed(files)
        val sheet = String(files["xl/worksheets/sheet1.xml"]!!, Charsets.UTF_8)
        assertTrue(sheet.contains("Nice to meet you."))
        assertEquals(3, Regex("<row ").findAll(sheet).count()) // 헤더 + 2문장
    }

    @Test fun docxWellFormed() {
        val paras = Segmenter.groupParagraphs(
            listOf(Sentence(0.0, 1.0, "가나다.", 0), Sentence(2.0, 3.0, "라마바.", 1)),
        )
        val files = zipEntries(Exporter.docx(paras, "제목", true, true))
        assertTrue(files.containsKey("word/document.xml"))
        assertWellFormed(files)
        assertTrue(String(files["word/document.xml"]!!, Charsets.UTF_8).contains("라마바."))
    }

    @Test fun txtOutput() {
        val paras = Segmenter.groupParagraphs(listOf(Sentence(0.0, 1.0, "가나다.", 0), Sentence(2.0, 3.0, "라마바.", 1)))
        val t = String(Exporter.txt(paras, includeTime = false, showSpeaker = true), Charsets.UTF_8)
        assertTrue(t, t.contains("화자 1 가나다.") && t.contains("화자 2 라마바."))
    }

    // ---- TTS 계획 --------------------------------------------------------------

    @Test fun ttsChunksByLanguageAndSize() {
        val text = "안녕하세요. 반갑습니다.\nHello there, how are you today? I am fine."
        val chunks = TtsPlanner.buildChunks(text)
        assertTrue(chunks.any { it.lang == "ko" } && chunks.any { it.lang == "en" })
        val long = "가".repeat(10000)
        TtsPlanner.buildChunks(long).forEach {
            assertTrue(EdgeTts.escapeXml(it.text).toByteArray().size <= TtsPlanner.MAX_BYTES)
        }
        assertEquals("+20%", EdgeTts.rateString(1.2))
        assertEquals("-30%", EdgeTts.rateString(0.7))
    }

    // ---- 리샘플러 --------------------------------------------------------------

    @Test fun resamplerLength() {
        val r = Resampler(44100)
        val out = ArrayList<Short>()
        val block = ShortArray(4410) { 1000 }
        repeat(10) { r.push(block, block.size) { out.add(it) } }
        assertTrue(out.size.toString(), out.size in 15990..16000)
        assertTrue(out.all { it.toInt() == 1000 })
    }

    @Test fun wavHeader() {
        val w = Wav.encode(shortArrayOf(1, -2, 3), 3)
        assertEquals(44 + 6, w.size)
        assertEquals("RIFF", String(w, 0, 4))
        assertEquals("WAVE", String(w, 8, 4))
        assertEquals(6, java.nio.ByteBuffer.wrap(w, 40, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test fun srtFormat() {
        val out = String(Exporter.srt(listOf(
            Sentence(0.0, 2.5, "안녕하세요 반갑습니다."),
            Sentence(3.0, 3.2, "Short one now."),
        )), Charsets.UTF_8)
        assertTrue(out.startsWith("1\n00:00:00,000 --> 00:00:02,500\n안녕하세요 반갑습니다.\n\n2\n00:00:03,000 --> "))
        assertTrue(out.contains("Short one now."))
    }

    @Test fun liveTextFormatting() {
        assertEquals("Hello I'm tom.", LiveText.format("HELLO I'M TOM", "en", true))
        assertEquals("I think I can", LiveText.format("I THINK I CAN", "en", false))
        assertEquals("Is it ok?", LiveText.format("is it ok?", "en", true))
        assertEquals("안녕하세요.", LiveText.format(" 안녕하세요 ", "ko", true))
        assertEquals("", LiveText.format("  ", "en", true))
    }
}
