package com.hdlee73.englishstudy.docvoice.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 받아쓰기 결과를 xlsx / docx / txt 로 저장 (순수 JVM, 외부 라이브러리 없이 OOXML 직접 생성). PDF 는 PdfExporter. */
object Exporter {
    val FORMATS = listOf("xlsx", "docx", "pdf", "txt", "srt")

    // 앱 UI 와 같은 파스텔 계열
    private const val LAVENDER = "E9E4FA"
    private const val ROW_ALT = "F7F5FD"

    fun speakerLabel(spk: Int?) = if (spk != null) "화자 ${spk + 1}" else ""

    internal fun esc(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> Unit // XML 에서 허용되지 않는 제어문자
                ch.code == 0xFFFE || ch.code == 0xFFFF -> Unit
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, content) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private const val XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"

    // ---- XLSX : 문장 1개 = 1행 --------------------------------------------------

    fun xlsx(sentences: List<Sentence>, includeTime: Boolean = true, showSpeaker: Boolean = false): ByteArray {
        val headers = ArrayList<String>()
        headers.add("번호")
        if (includeTime) { headers.add("시작"); headers.add("종료") }
        if (showSpeaker) headers.add("화자")
        headers.add("문장")
        val widths = headers.map {
            when (it) { "번호" -> 7; "시작", "종료" -> 12; "화자" -> 9; else -> 90 }
        }

        fun colName(i: Int): String = ('A' + i).toString()
        fun strCell(ref: String, style: Int, text: String) =
            "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(text)}</t></is></c>"

        val sheet = StringBuilder()
        sheet.append(XML)
        sheet.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sheet.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        sheet.append("<cols>")
        widths.forEachIndexed { i, w -> sheet.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>") }
        sheet.append("</cols><sheetData>")
        sheet.append("<row r=\"1\">")
        headers.forEachIndexed { i, h -> sheet.append(strCell("${colName(i)}1", 1, h)) }
        sheet.append("</row>")
        sentences.forEachIndexed { idx, s ->
            val r = idx + 2
            val style = if ((idx + 1) % 2 == 0) 3 else 2
            sheet.append("<row r=\"$r\">")
            var c = 0
            sheet.append("<c r=\"${colName(c)}$r\" s=\"$style\"><v>${idx + 1}</v></c>"); c++
            if (includeTime) {
                sheet.append(strCell("${colName(c)}$r", style, Segmenter.fmtTime(s.start))); c++
                sheet.append(strCell("${colName(c)}$r", style, Segmenter.fmtTime(s.end))); c++
            }
            if (showSpeaker) { sheet.append(strCell("${colName(c)}$r", style, speakerLabel(s.speaker))); c++ }
            sheet.append(strCell("${colName(c)}$r", style, s.text)) // inlineStr 이므로 '=' 로 시작해도 수식이 되지 않음
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")

        val styles = XML +
            "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
            "<fonts count=\"2\">" +
            "<font><sz val=\"11\"/><color rgb=\"FF4A4560\"/><name val=\"Malgun Gothic\"/></font>" +
            "<font><b/><sz val=\"11\"/><color rgb=\"FF4A4560\"/><name val=\"Malgun Gothic\"/></font>" +
            "</fonts>" +
            "<fills count=\"4\">" +
            "<fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF$LAVENDER\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF$ROW_ALT\"/><bgColor indexed=\"64\"/></patternFill></fill>" +
            "</fills>" +
            "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
            "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
            "<cellXfs count=\"4\">" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
            "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\"/></xf>" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>" +
            "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf>" +
            "</cellXfs>" +
            "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
            "</styleSheet>"

        return zip(
            listOf(
                "[Content_Types].xml" to (XML +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                    "</Types>"),
                "_rels/.rels" to (XML +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>"),
                "xl/workbook.xml" to (XML +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"문장\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"),
                "xl/_rels/workbook.xml.rels" to (XML +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                    "</Relationships>"),
                "xl/styles.xml" to styles,
                "xl/worksheets/sheet1.xml" to sheet.toString(),
            )
        )
    }

    // ---- DOCX -----------------------------------------------------------------

    fun prefixOf(p: Paragraph, includeTime: Boolean, showSpeaker: Boolean): String {
        val parts = ArrayList<String>()
        if (includeTime) parts.add("[" + Segmenter.fmtTime(p.start).dropLast(2) + "]")
        if (showSpeaker && p.speaker != null) parts.add(speakerLabel(p.speaker))
        return parts.joinToString(" ")
    }

    fun docx(paragraphs: List<Paragraph>, title: String = "", includeTime: Boolean = true, showSpeaker: Boolean = false): ByteArray {
        val body = StringBuilder()
        if (title.isNotEmpty()) {
            body.append("<w:p><w:pPr><w:spacing w:after=\"280\"/></w:pPr><w:r><w:rPr><w:b/><w:color w:val=\"6B5BB5\"/><w:sz w:val=\"32\"/></w:rPr>")
            body.append("<w:t xml:space=\"preserve\">${esc(title)}</w:t></w:r></w:p>")
        }
        for (p in paragraphs) {
            body.append("<w:p>")
            val prefix = prefixOf(p, includeTime, showSpeaker)
            if (prefix.isNotEmpty()) {
                body.append("<w:r><w:rPr><w:color w:val=\"9A90C0\"/><w:sz w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">${esc(prefix)} </w:t></w:r>")
            }
            body.append("<w:r><w:t xml:space=\"preserve\">${esc(p.text)}</w:t></w:r></w:p>")
        }
        val w = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""
        val document = XML + "<w:document $w><w:body>$body" +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1417\" w:right=\"1417\" w:bottom=\"1417\" w:left=\"1417\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>" +
            "</w:body></w:document>"
        val styles = XML + "<w:styles $w><w:docDefaults><w:rPrDefault><w:rPr>" +
            "<w:rFonts w:ascii=\"Malgun Gothic\" w:hAnsi=\"Malgun Gothic\" w:eastAsia=\"Malgun Gothic\" w:cs=\"Malgun Gothic\"/>" +
            "<w:color w:val=\"4A4560\"/><w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:lang w:val=\"ko-KR\" w:eastAsia=\"ko-KR\"/>" +
            "</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"160\" w:line=\"360\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style></w:styles>"
        return zip(
            listOf(
                "[Content_Types].xml" to (XML +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                    "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
                    "</Types>"),
                "_rels/.rels" to (XML +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                    "</Relationships>"),
                "word/_rels/document.xml.rels" to (XML +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                    "</Relationships>"),
                "word/styles.xml" to styles,
                "word/document.xml" to document,
            )
        )
    }

    // ---- TXT ------------------------------------------------------------------

    private fun srtTime(sec: Double): String {
        val ms = Math.round(sec.coerceAtLeast(0.0) * 1000)
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)
    }

    /** 자막(.srt): 문장 하나를 한 줄 자막으로, 너무 길면 단어 단위로 나눠 시간을 글자 수에 비례해 배분. */
    fun srt(sentences: List<Sentence>, showSpeaker: Boolean = false, maxChars: Int = 42): ByteArray {
        val sb = StringBuilder()
        var n = 1
        sentences.forEachIndexed { i, s ->
            val nextStart = sentences.getOrNull(i + 1)?.start ?: Double.MAX_VALUE
            val end = minOf(maxOf(s.end, s.start + 0.8), maxOf(nextStart, s.start + 0.5))
            val label = if (showSpeaker && s.speaker != null) speakerLabel(s.speaker) + " " else ""
            // 줄 나누기
            val words = s.text.trim().split(Regex("\\s+"))
            val lines = ArrayList<String>()
            var cur = StringBuilder()
            for (w in words) {
                if (cur.isNotEmpty() && cur.length + 1 + w.length > maxChars) { lines.add(cur.toString()); cur = StringBuilder() }
                if (cur.isNotEmpty()) cur.append(' ')
                cur.append(w)
            }
            if (cur.isNotEmpty()) lines.add(cur.toString())
            // 한 자막은 최대 두 줄
            val cues = lines.chunked(2)
            val total = cues.sumOf { c -> c.sumOf { it.length } }.coerceAtLeast(1)
            var t = s.start
            cues.forEachIndexed { ci, cue ->
                val len = cue.sumOf { it.length }
                val e = if (ci == cues.lastIndex) end else t + (end - s.start) * len / total
                sb.append(n++).append('\n').append(srtTime(t)).append(" --> ").append(srtTime(e)).append('\n')
                cue.forEachIndexed { li, line -> sb.append(if (ci == 0 && li == 0) label else "").append(line).append('\n') }
                sb.append('\n')
                t = e
            }
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun txt(paragraphs: List<Paragraph>, includeTime: Boolean = true, showSpeaker: Boolean = false): ByteArray {
        val blocks = paragraphs.map { p ->
            val prefix = prefixOf(p, includeTime, showSpeaker)
            (if (prefix.isNotEmpty()) "$prefix " else "") + p.text
        }
        return (blocks.joinToString("\n\n") + "\n").toByteArray(Charsets.UTF_8)
    }
}
