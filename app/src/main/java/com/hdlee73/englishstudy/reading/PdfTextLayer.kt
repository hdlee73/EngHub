package com.hdlee73.englishstudy.reading

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One word of a PDF page: where it is in [PdfPageText.text] and its box on the page (0..1 of the page width and height, from the top left). */
class PdfWord(val start: Int, val end: Int, val left: Float, val top: Float, val right: Float, val bottom: Float)

/** The text of a PDF page with the position of every word, so the original page can be tapped and selected like text. */
class PdfPageText(val text: String, val words: List<PdfWord>) {
    /** The word under (x, y) given as fractions of the page, or the closest one within a fingertip's reach. */
    fun wordAt(x: Float, y: Float): PdfWord? {
        var best: PdfWord? = null
        var bestDistance = Float.MAX_VALUE
        for (w in words) {
            val padX = 0.012f
            val padY = 0.006f
            if (x < w.left - padX || x > w.right + padX || y < w.top - padY || y > w.bottom + padY) continue
            val dx = x - (w.left + w.right) / 2
            val dy = y - (w.top + w.bottom) / 2
            val distance = dx * dx + dy * dy * 4
            if (distance < bestDistance) { bestDistance = distance; best = w }
        }
        return best
    }

    /** The selectable range for a word: the letters without surrounding punctuation. */
    fun rangeOf(word: PdfWord): IntRange {
        var i = word.start
        while (i < word.end && !text[i].isLetter()) i++
        return (if (i < word.end) ReadingWords.rangeAt(text, i) else null) ?: (word.start until word.end)
    }

    companion object { val EMPTY = PdfPageText("", emptyList()) }
}

/** Reads the text of the pages of a PDF file, with positions (PDFBox), one page at a time. */
class PdfTextSource(private val context: Context, private val file: File) {
    private val lock = Mutex()
    private var document: PDDocument? = null
    private var failed = false
    private val cache = HashMap<Int, PdfPageText>()

    suspend fun page(index: Int): PdfPageText = withContext(Dispatchers.IO) {
        lock.withLock {
            cache[index]?.let { return@withLock it }
            val result = runCatching { read(index) }.getOrDefault(PdfPageText.EMPTY)
            cache[index] = result
            result
        }
    }

    private fun read(index: Int): PdfPageText {
        if (failed) return PdfPageText.EMPTY
        val doc = document ?: run {
            PDFBoxResourceLoader.init(context.applicationContext)
            try { PDDocument.load(file) } catch (e: Exception) { failed = true; throw e }
        }.also { document = it }
        if (doc.isEncrypted || index !in 0 until doc.numberOfPages) return PdfPageText.EMPTY
        val page = doc.getPage(index)
        // Turned pages would need their coordinates turned too; they are left unselectable rather than wrong.
        if (page.rotation % 360 != 0) return PdfPageText.EMPTY
        val box = page.cropBox
        val collector = Collector(box.width, box.height)
        collector.startPage = index + 1
        collector.endPage = index + 1
        collector.getText(doc)
        return PdfPageText(collector.sb.toString(), collector.words)
    }

    fun close() {
        runCatching { document?.close() }
        document = null
        cache.clear()
    }

    private class Collector(private val pageWidth: Float, private val pageHeight: Float) : PDFTextStripper() {
        val sb = StringBuilder()
        val words = ArrayList<PdfWord>()

        init { sortByPosition = false }

        /** Called for each word of a line. */
        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (text.isBlank() || textPositions.isEmpty() || pageWidth <= 0f || pageHeight <= 0f) return
            var left = Float.MAX_VALUE
            var top = Float.MAX_VALUE
            var right = -Float.MAX_VALUE
            var bottom = -Float.MAX_VALUE
            for (tp in textPositions) {
                left = minOf(left, tp.xDirAdj)
                right = maxOf(right, tp.xDirAdj + tp.widthDirAdj)
                top = minOf(top, tp.yDirAdj - tp.heightDir)
                bottom = maxOf(bottom, tp.yDirAdj)
            }
            val start = sb.length
            sb.append(text)
            words += PdfWord(
                start, sb.length,
                (left / pageWidth).coerceIn(0f, 1f), (top / pageHeight).coerceIn(0f, 1f),
                (right / pageWidth).coerceIn(0f, 1f), (bottom / pageHeight).coerceIn(0f, 1f)
            )
        }

        override fun writeWordSeparator() { sb.append(' ') }
        override fun writeLineSeparator() { sb.append(' ') }
        override fun writeParagraphSeparator() { sb.append(' ') }
        override fun writeParagraphStart() {}
        override fun writeParagraphEnd() {}
        override fun writePageStart() {}
        override fun writePageEnd() {}
    }
}
