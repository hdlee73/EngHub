package com.hdlee73.englishstudy.dictionary

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ExportWorkbook {
/** Builds an .xlsx workbook (format 1–3) from saved entries. */
fun make(originalEntries: List<WordEntry>, exportFormat: Int): ByteArray {
    val entries = originalEntries.map { it.studyVersion() }
    val rows = when (exportFormat) {
        2 -> mutableListOf(listOf("예문 한글 해석", "영어 예문")).apply {
            entries.forEach { e -> examplePairs(e.examples).forEach { add(listOf(it.second, it.first)) } }
        }
        3 -> mutableListOf(listOf("영어 예문")).apply {
            entries.forEach { e -> examplePairs(e.examples).forEach { add(listOf(it.first)) } }
        }
        else -> mutableListOf(listOf("영단어", "한글 의미\nEnglish definition", "영어 예문 (한글 해석 병기)")).apply {
            entries.forEach { e -> add(listOf(e.word, "${e.korean}\n${e.english}", displayExamples(e.examples))) }
        }
    }
    val sheet = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><cols><col min=\"1\" max=\"1\" width=\"28\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"52\" customWidth=\"1\"/><col min=\"3\" max=\"3\" width=\"60\" customWidth=\"1\"/></cols><sheetData>")
        rows.forEachIndexed { ri, row -> append("<row r=\"${ri + 1}\" ht=\"42\" customHeight=\"1\">"); row.forEachIndexed { ci, value -> val ref = "${'A' + ci}${ri + 1}"; append("<c r=\"$ref\" s=\"1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>") }; append("</row>") }
        append("</sheetData></worksheet>")
    }
    val attribution = entries.map { it.source.ifBlank { "의미별 자체 정리 · 직접 작성한 한영 예문" } }.distinct()
    val sourceSheet = """<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""" + attribution.mapIndexed { i, credit -> """<row r="${i + 1}"><c r="A${i + 1}" t="inlineStr"><is><t>${xml(credit)}</t></is></c></row>""" }.joinToString("") + "</sheetData></worksheet>"
    val out = ByteArrayOutputStream(); ZipOutputStream(out).use { z ->
        fun put(path: String, value: String) { z.putNextEntry(ZipEntry(path)); z.write(value.toByteArray(Charsets.UTF_8)); z.closeEntry() }
        put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>")
        put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"단어장\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"출처\" sheetId=\"2\" r:id=\"rId3\"/></sheets></workbook>")
        put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/></Relationships>")
        put("xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Arial\"/></font></fonts><fills count=\"1\"><fill><patternFill patternType=\"none\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf xfId=\"0\"/><xf xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf></cellXfs></styleSheet>")
        put("xl/worksheets/sheet1.xml", sheet)
        put("xl/worksheets/sheet2.xml", sourceSheet)
    }; return out.toByteArray()
}
}

internal fun examplePairs(stored: String): List<Pair<String, String>> = stored.lines().mapNotNull { line ->
    val parts = line.split('\t', limit = 2)
    val english = parts.firstOrNull()?.trim().orEmpty()
    if (!SentenceExamples.isSentence(english)) null else english to parts.getOrElse(1) { "" }.trim()
}

internal fun displayExamples(stored: String): String = examplePairs(stored)
    .joinToString("\n") { (en, ko) -> if (ko.isBlank()) en else "$en\n$ko" }

private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;").replace("\n", "&#10;")
