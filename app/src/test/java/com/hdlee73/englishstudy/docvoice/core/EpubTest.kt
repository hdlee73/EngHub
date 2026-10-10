package com.hdlee73.englishstudy.docvoice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubTest {
    private fun epub(files: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> files.forEach { (name, body) -> z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun readsChaptersInSpineOrder() {
        val data = epub(
            mapOf(
                "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
                "OEBPS/content.opf" to """<package><manifest><item id="b" href="text/two.xhtml" media-type="application/xhtml+xml"/><item id="a" href="text/one.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>""",
                "OEBPS/text/one.xhtml" to """<html><head><title>x</title></head><body><h1>Chapter One</h1><p>It was a  dark
                    night &amp; raining.</p><p>Second&nbsp;paragraph.</p></body></html>""",
                "OEBPS/text/two.xhtml" to """<html><body><p>Chapter two says &#8220;hello&#8221;.</p></body></html>"""
            )
        )
        val text = Extractors.extract("book.epub", data)
        assertEquals(
            "Chapter One\n\nIt was a dark night & raining.\n\nSecond paragraph.\n\nChapter two says “hello”.",
            text
        )
    }

    @Test fun rejectsNonEpubZip() {
        val data = epub(mapOf("a.txt" to "hi"))
        val failure = runCatching { Extractors.extract("x.epub", data) }.exceptionOrNull()
        assertTrue(failure is ExtractException)
    }
}
