package com.hdlee73.englishstudy.docvoice.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class XlsxTranslateTest {
    private val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    private fun book(sheet: String, shared: String? = null): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(n: String, body: String) { z.putNextEntry(ZipEntry(n)); z.write(body.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""")
            put("xl/workbook.xml", """<workbook xmlns="$main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="S" sheetId="1" r:id="rId9"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId9" Type="x" Target="worksheets/sheet1.xml"/></Relationships>""")
            put("xl/worksheets/sheet1.xml", sheet)
            if (shared != null) put("xl/sharedStrings.xml", shared)
        }
        return out.toByteArray()
    }

    private fun sheetXml(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == "xl/worksheets/sheet1.xml") return String(z.readBytes())
            }
        }
        error("no sheet")
    }

    private val shared = """<sst xmlns="$main"><si><t>apple</t></si><si><r><t>red </t></r><r><t>car</t></r></si><si><t>사과</t></si></sst>"""

    private val sheet = """<worksheet xmlns="$main"><dimension ref="A1:C3"/><sheetData>
        <row r="1" spans="1:3"><c r="A1" t="s"><v>0</v></c><c r="C1"><v>7</v></c></row>
        <row r="2"><c r="A2" t="s"><v>1</v></c><c r="B2" s="3"/></row>
        <row r="3"><c r="A3" t="inlineStr"><is><t>keep</t></is></c><c r="B3" t="inlineStr"><is><t>보관</t></is></c></row>
        <row r="4"><c r="A4" t="s"><v>2</v></c></row>
        <row r="5"><c r="A5"><v>42</v></c></row>
        </sheetData></worksheet>"""

    @Test fun findsEnglishRowsAndSkipsFilledOrNonEnglish() {
        val x = XlsxTranslate.open(book(sheet, shared))
        assertEquals(listOf("apple", "red car"), x.targets.map { it.text })
        assertEquals(listOf(1, 2), x.targets.map { it.row })
        assertEquals(1, x.alreadyFilled)
    }

    @Test fun writesKoreanIntoColumnBKeepingTheRest() {
        val x = XlsxTranslate.open(book(sheet, shared))
        val xml = sheetXml(x.write(mapOf("apple" to "사과", "red car" to "빨간 자동차")))
        val again = XlsxTranslate.open(book(xml, shared))
        // Filled rows are not offered again.
        assertEquals(emptyList<String>(), again.targets.map { it.text })
        assertEquals(3, again.alreadyFilled)
        assertTrue(xml.contains("<c r=\"B1\" t=\"inlineStr\"><is><t>사과</t></is></c>"))
        // B1 is created between A1 and C1.
        assertTrue(xml.indexOf("r=\"A1\"") < xml.indexOf("r=\"B1\"") && xml.indexOf("r=\"B1\"") < xml.indexOf("r=\"C1\""))
        // An empty styled B2 keeps its style.
        assertTrue(Regex("<c [^>]*s=\"3\"[^>]*>").find(xml)!!.value.contains("inlineStr"))
        assertTrue(xml.contains("보관"))
        assertTrue(!xml.contains("spans"))
    }

    @Test fun cellsWithoutTranslationStayEmptyAndNonXlsxIsRejected() {
        val x = XlsxTranslate.open(book(sheet, shared))
        val xml = sheetXml(x.write(mapOf("apple" to "사과")))
        assertTrue(!xml.contains("r=\"B2\" t=\"inlineStr\"") && xml.contains("B2"))
        try { XlsxTranslate.open("not a zip".toByteArray()); error("should fail") } catch (_: ExtractException) {}
    }

    @Test fun columnLettersToIndex() {
        assertEquals(0, XlsxTranslate.columnOf("A12"))
        assertEquals(1, XlsxTranslate.columnOf("B1"))
        assertEquals(26, XlsxTranslate.columnOf("AA1"))
    }
}
