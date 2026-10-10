package com.hdlee73.englishstudy.docvoice.core

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.TreeMap
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

internal fun fmtCell(v: String): String {
    val s = v.trim()
    val d = s.toDoubleOrNull()
    return if (d != null && d == Math.floor(d) && Math.abs(d) < 1e15 && !s.contains('E') && !s.contains('e')) d.toLong().toString() else s
}

internal fun rowLine(cells: Iterable<String>): String {
    val vals = ArrayList<String>()
    for (c in cells) {
        val s = c.trim()
        if (s.isNotEmpty() && (vals.isEmpty() || vals.last() != s)) vals.add(s)
    }
    return vals.joinToString(", ")
}

internal fun colIndex(ref: String?): Int {
    if (ref == null) return -1
    var n = 0
    for (c in ref) {
        if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else if (c in 'a'..'z') n = n * 26 + (c - 'a' + 1) else break
    }
    return n - 1
}

internal fun local(q: String) = q.substringAfter(':')

/** 문서 → 텍스트 추출 (PDF 를 제외한 모든 형식은 Android 의존 없이 JVM 에서 동작). */
object Extractors {
    val SUPPORTED = listOf("pdf", "docx", "doc", "xlsx", "xlsm", "xls", "pptx", "ppt", "hwpx", "hwp", "epub", "txt", "md", "csv")

    /** PDF 는 Android 에서만 가능(PDFBox)하므로 호출 측이 리더를 주입한다. */
    fun extract(name: String, data: ByteArray, pdfReader: ((ByteArray) -> String)? = null): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val raw = when (ext) {
            "txt", "md" -> decodeText(data)
            "csv" -> csv(decodeText(data))
            "docx" -> docx(data)
            "xlsx", "xlsm" -> xlsx(data)
            "pptx" -> pptx(data)
            "hwpx" -> hwpx(data)
            "epub" -> Epub.extract(data)
            "hwp" -> HwpBinary.extract(data)
            "doc" -> DocBinary.extract(data)
            "xls" -> XlsBinary.extract(data)
            "ppt" -> PptBinary.extract(data)
            "pdf" -> {
                val reader = pdfReader ?: throw ExtractException("PDF 읽기를 사용할 수 없습니다.")
                reflow(reader(data))
            }
            else -> throw ExtractException("지원하지 않는 형식입니다: .$ext (지원: ${SUPPORTED.joinToString(", ")})")
        }
        val text = clean(raw)
        if (text.isEmpty()) throw ExtractException("문서에서 읽을 텍스트를 찾지 못했습니다.")
        return text
    }

    // ---- 공통 -----------------------------------------------------------------

    fun clean(text: String): String =
        text.replace("\r\n", "\n").replace('\r', '\n').replace(' ', ' ')
            .replace(Regex("[\\u0000-\\u0008\\u000b\\u000c\\u000e-\\u001f\\u007f]"), "")
            .replace(Regex("[\\ue000-\\uf8ff]"), "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    /** PDF 처럼 줄 중간에서 끊긴 문장을 이어 붙인다. */
    fun reflow(text: String): String {
        val out = ArrayList<String>()
        val bullet = Regex("^([\\-•·▪●○■□◆◇▶※*]|\\d+[.)]|[가-하][.)])\\s")
        for (ln in text.split("\n")) {
            val s = ln.trim()
            if (s.isEmpty()) {
                out.add("")
                continue
            }
            if (out.isNotEmpty() && out.last().isNotEmpty()) {
                val prev = out.last()
                if (prev.last() !in ".?!。！？…:;" && !bullet.containsMatchIn(s)) {
                    out[out.size - 1] = "$prev $s"
                    continue
                }
            }
            out.add(s)
        }
        return out.joinToString("\n")
    }



    // ---- 텍스트 / CSV ---------------------------------------------------------

    fun decodeText(data: ByteArray): String {
        if (data.size >= 2 && ((data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) || (data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte())))
            return String(data, Charsets.UTF_16)
        val body = if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) data.copyOfRange(3, data.size) else data
        try {
            return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString()
        } catch (_: CharacterCodingException) {
        }
        for (name in listOf("EUC-KR", "x-windows-949")) {
            try {
                return String(body, Charset.forName(name))
            } catch (_: Exception) {
            }
        }
        return String(body, Charsets.UTF_8)
    }

    fun csv(text: String): String {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(cell.toString()); cell.setLength(0) }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.setLength(0)
                    rows.add(row); row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows.joinToString("\n") { rowLine(it) }
    }

    // ---- ZIP / SAX 도우미 -----------------------------------------------------

    private const val MAX_ENTRY = 200L * 1024 * 1024

    private fun zipEntries(data: ByteArray, want: (String) -> Boolean): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(data)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.isDirectory || !want(e.name)) continue
                    val bos = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val n = zin.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_ENTRY) throw ExtractException("문서 내부 파일이 너무 큽니다.")
                        bos.write(buf, 0, n)
                    }
                    out[e.name] = bos.toByteArray()
                }
            }
        } catch (e: ExtractException) {
            throw e
        } catch (e: Exception) {
            throw ExtractException("문서 파일(ZIP)을 읽을 수 없습니다: ${e.message}")
        }
        return out
    }

    private fun sax(bytes: ByteArray, handler: DefaultHandler) {
        val f = SAXParserFactory.newInstance()
        f.isNamespaceAware = false
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (_: Exception) {
        }
        try {
            f.newSAXParser().parse(ByteArrayInputStream(bytes), handler)
        } catch (e: Exception) {
            throw ExtractException("문서 XML을 해석할 수 없습니다: ${e.message}")
        }
    }


    private fun numberOf(name: String): Int = Regex("(\\d+)\\.xml$").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    // ---- DOCX / PPTX (문단·표 공통) -------------------------------------------

    /** 문단(p)·텍스트(t)·표(tr/tc) 요소 이름만 다른 DrawingML/WordprocessingML 공통 처리. */
    private class ParaHandler(
        val pTag: String, val tTag: String, val trTag: String, val tcTag: String, val breakTags: Set<String>,
    ) : DefaultHandler() {
        val lines = ArrayList<String>()
        private var para = StringBuilder()
        private var inT = false
        private var inR = false
        private var tcDepth = 0
        private val cellParts = ArrayList<String>()
        private val rowCells = ArrayList<String>()

        override fun startElement(uri: String?, ln: String?, q: String, a: Attributes?) {
            val n = local(q)
            when {
                n == pTag -> para = StringBuilder()
                n == tTag -> inT = true
                n == "r" -> inR = true
                n == "tab" && inR -> para.append('\t')
                n in breakTags -> para.append(' ')
                n == tcTag -> { if (tcDepth == 0) cellParts.clear(); tcDepth++ }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inT) para.append(ch, start, length)
        }

        override fun endElement(uri: String?, ln: String?, q: String) {
            when (local(q)) {
                tTag -> inT = false
                "r" -> inR = false
                pTag -> {
                    val t = para.toString()
                    if (tcDepth > 0) cellParts.add(t) else lines.add(t)
                    para = StringBuilder()
                }
                tcTag -> {
                    tcDepth--
                    if (tcDepth == 0) rowCells.add(cellParts.filter { it.isNotBlank() }.joinToString(" "))
                }
                trTag -> if (tcDepth == 0) {
                    lines.add(rowLine(rowCells))
                    rowCells.clear()
                }
            }
        }
    }

    fun docx(data: ByteArray): String {
        val files = zipEntries(data) { it == "word/document.xml" }
        val xml = files["word/document.xml"] ?: throw ExtractException("DOCX 본문을 찾지 못했습니다.")
        val h = ParaHandler("p", "t", "tr", "tc", setOf("br", "cr"))
        sax(xml, h)
        return h.lines.joinToString("\n")
    }

    fun pptx(data: ByteArray): String {
        val files = zipEntries(data) { Regex("^ppt/slides/slide\\d+\\.xml$").matches(it) }
        if (files.isEmpty()) throw ExtractException("PPTX 슬라이드를 찾지 못했습니다.")
        val slides = ArrayList<String>()
        for (name in files.keys.sortedBy { numberOf(it) }) {
            val h = ParaHandler("p", "t", "tr", "tc", setOf("br"))
            sax(files.getValue(name), h)
            if (h.lines.any { it.isNotBlank() }) slides.add(h.lines.joinToString("\n"))
        }
        return slides.joinToString("\n\n")
    }

    // ---- XLSX -----------------------------------------------------------------

    private class SharedStringsHandler : DefaultHandler() {
        val items = ArrayList<String>()
        private var sb = StringBuilder()
        private var inT = false
        private var phonetic = 0
        override fun startElement(uri: String?, ln: String?, q: String, a: Attributes?) {
            when (local(q)) {
                "si" -> sb = StringBuilder()
                "rPh" -> phonetic++
                "t" -> inT = true
            }
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inT && phonetic == 0) sb.append(ch, start, length)
        }
        override fun endElement(uri: String?, ln: String?, q: String) {
            when (local(q)) {
                "t" -> inT = false
                "rPh" -> phonetic--
                "si" -> items.add(sb.toString())
            }
        }
    }

    private class WorkbookHandler : DefaultHandler() {
        val sheetRids = ArrayList<String>()
        val rels = HashMap<String, String>()
        override fun startElement(uri: String?, ln: String?, q: String, a: Attributes?) {
            when (local(q)) {
                "sheet" -> a?.getValue("r:id")?.let { sheetRids.add(it) }
                "Relationship" -> {
                    val id = a?.getValue("Id")
                    val target = a?.getValue("Target")
                    if (id != null && target != null) rels[id] = target
                }
            }
        }
    }


    private class SheetHandler(val shared: List<String>) : DefaultHandler() {
        val lines = ArrayList<String>()
        private var row = TreeMap<Int, String>()
        private var nextCol = 0
        private var colIdx = 0
        private var type = ""
        private var v = StringBuilder()
        private var inV = false
        private var inT = false
        private var inline = StringBuilder()
        override fun startElement(uri: String?, ln: String?, q: String, a: Attributes?) {
            when (local(q)) {
                "row" -> { row = TreeMap(); nextCol = 0 }
                "c" -> {
                    type = a?.getValue("t") ?: ""
                    val ci = colIndex(a?.getValue("r"))
                    colIdx = if (ci >= 0) ci else nextCol
                    nextCol = colIdx + 1
                    v = StringBuilder(); inline = StringBuilder()
                }
                "v" -> inV = true
                "t" -> inT = true
            }
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inV) v.append(ch, start, length) else if (inT) inline.append(ch, start, length)
        }
        override fun endElement(uri: String?, ln: String?, q: String) {
            when (local(q)) {
                "v" -> inV = false
                "t" -> inT = false
                "c" -> {
                    val value = when (type) {
                        "s" -> v.toString().trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                        "str" -> v.toString()
                        "inlineStr" -> inline.toString()
                        "b" -> if (v.toString().trim() == "1") "TRUE" else "FALSE"
                        "e" -> ""
                        else -> fmtCell(v.toString())
                    }
                    if (value.isNotBlank()) row[colIdx] = value
                }
                "row" -> {
                    val line = rowLine(row.values)
                    if (line.isNotEmpty()) lines.add(line)
                }
            }
        }
    }

    fun xlsx(data: ByteArray): String {
        val files = zipEntries(data) {
            it == "xl/sharedStrings.xml" || it == "xl/workbook.xml" || it == "xl/_rels/workbook.xml.rels" ||
                Regex("^xl/worksheets/[^/]+\\.xml$").matches(it)
        }
        val shared = SharedStringsHandler()
        files["xl/sharedStrings.xml"]?.let { sax(it, shared) }

        val ordered = ArrayList<String>()
        val wbXml = files["xl/workbook.xml"]
        val relXml = files["xl/_rels/workbook.xml.rels"]
        if (wbXml != null && relXml != null) {
            val wb = WorkbookHandler()
            sax(wbXml, wb)
            val rels = WorkbookHandler()
            sax(relXml, rels)
            for (rid in wb.sheetRids) {
                val t = rels.rels[rid] ?: continue
                val path = if (t.startsWith("/")) t.removePrefix("/") else "xl/$t"
                if (files.containsKey(path)) ordered.add(path)
            }
        }
        if (ordered.isEmpty()) ordered.addAll(files.keys.filter { it.startsWith("xl/worksheets/") }.sortedBy { numberOf(it) })
        if (ordered.isEmpty()) throw ExtractException("XLSX 시트를 찾지 못했습니다.")

        val parts = ArrayList<String>()
        for (path in ordered) {
            val h = SheetHandler(shared.items)
            sax(files.getValue(path), h)
            if (h.lines.isNotEmpty()) parts.add(h.lines.joinToString("\n"))
        }
        return parts.joinToString("\n\n")
    }

    // ---- HWPX -----------------------------------------------------------------

    private class HwpxHandler : DefaultHandler() {
        val lines = ArrayList<String>()
        private var buf = StringBuilder()
        private var inT = false
        override fun startElement(uri: String?, ln: String?, q: String, a: Attributes?) {
            if (local(q) == "t") inT = true
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inT) buf.append(ch, start, length)
        }
        override fun endElement(uri: String?, ln: String?, q: String) {
            when (local(q)) {
                "t" -> inT = false
                "p" -> { lines.add(buf.toString()); buf = StringBuilder() }
            }
        }
        fun finish() { if (buf.isNotEmpty()) lines.add(buf.toString()) }
    }

    fun hwpx(data: ByteArray): String {
        val files = zipEntries(data) { Regex("^Contents/section\\d+\\.xml$", RegexOption.IGNORE_CASE).matches(it) }
        if (files.isEmpty()) throw ExtractException("HWPX에서 본문(section) 데이터를 찾지 못했습니다. (암호 문서일 수 있습니다)")
        val lines = ArrayList<String>()
        for (name in files.keys.sortedBy { numberOf(it) }) {
            val h = HwpxHandler()
            sax(files.getValue(name), h)
            h.finish()
            lines.addAll(h.lines)
        }
        return lines.joinToString("\n")
    }
}
