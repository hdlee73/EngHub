package com.hdlee73.englishstudy.docvoice.core

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * An .xlsx workbook whose first sheet has English in column A: finds the cells to translate and writes the
 * Korean into column B. Only the sheet XML is touched; every other part of the file (other sheets, styles,
 * other columns) is copied as it was. Pure JVM (no Android classes), so it is unit-testable.
 */
class XlsxTranslate private constructor(
    private val entries: LinkedHashMap<String, ByteArray>,
    private val sheetPath: String,
    private val doc: Document,
    private val sheetData: Element,
    shared: List<String>,
) {
    /** One row to translate: [text] is the English in column A of spreadsheet row [row]. */
    class Target(val row: Int, val text: String, internal val rowEl: Element, internal val a: Element?)

    /** Rows with English in column A and nothing in column B yet. */
    val targets: List<Target>

    /** Rows with English in column A whose column B already has something (left untouched). */
    var alreadyFilled = 0
        private set

    private val ns: String? = sheetData.namespaceURI
    private val prefix: String? = sheetData.prefix

    init {
        val found = ArrayList<Target>()
        var rowNo = 0
        for (rowEl in children(sheetData, "row")) {
            rowNo = rowEl.getAttribute("r").toIntOrNull() ?: (rowNo + 1)
            var a: Element? = null
            var b: Element? = null
            var pos = -1
            for (c in children(rowEl, "c")) {
                pos = columnOf(c.getAttribute("r")) ?: (pos + 1)
                if (pos == 0) a = c else if (pos == 1) b = c
            }
            val text = a?.let { cellText(it, shared) }?.trim().orEmpty()
            if (!needsTranslation(text)) continue
            if (b != null && cellText(b, shared).isNotBlank()) { alreadyFilled++; continue }
            found += Target(rowNo, text, rowEl, a)
        }
        targets = found
    }

    /** Writes [translations] (by original English text) into column B and returns the new .xlsx. Rows without one are left as they were. */
    fun write(translations: Map<String, String>): ByteArray {
        var lastRow = 0
        for (t in targets) {
            val ko = translations[t.text]?.takeIf { it.isNotBlank() } ?: continue
            setColumnB(t, ko)
            lastRow = maxOf(lastRow, t.row)
        }
        if (lastRow > 0) widenDimension()
        entries[sheetPath] = serialize(doc)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun setColumnB(t: Target, ko: String) {
        val row = t.rowEl
        row.removeAttribute("spans")
        val ref = "B${t.row}"
        var cell: Element? = null
        var before: Element? = null
        var pos = -1
        for (c in children(row, "c")) {
            pos = columnOf(c.getAttribute("r")) ?: (pos + 1)
            if (pos == 1) cell = c
            if (pos > 1 && before == null) before = c
        }
        if (cell == null) {
            cell = create("c")
            cell.setAttribute("r", ref)
            row.insertBefore(cell, before)
        } else {
            while (cell.firstChild != null) cell.removeChild(cell.firstChild)
        }
        cell.setAttribute("t", "inlineStr")
        val inline = create("is")
        val text = create("t")
        if (ko != ko.trim()) text.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve")
        text.appendChild(doc.createTextNode(ko))
        inline.appendChild(text)
        cell.appendChild(inline)
    }

    private fun create(name: String): Element =
        if (ns == null) doc.createElement(name) else doc.createElementNS(ns, if (prefix.isNullOrEmpty()) name else "$prefix:$name")

    /** The sheet's used-range hint must include column B, or Excel may ignore the new cells. */
    private fun widenDimension() {
        val dim = (sheetData.parentNode as? Element)?.let { children(it, "dimension").firstOrNull() } ?: return
        val m = Regex("^([A-Z]+)(\\d+)(?::([A-Z]+)(\\d+))?$").matchEntire(dim.getAttribute("ref")) ?: return
        val endCol = columnOf(m.groupValues[3].ifEmpty { m.groupValues[1] } + "1") ?: return
        if (endCol >= 1) return
        val endRow = m.groupValues[4].ifEmpty { m.groupValues[2] }
        dim.setAttribute("ref", "${m.groupValues[1]}${m.groupValues[2]}:B$endRow")
    }

    companion object {
        /** True for text with at least one Latin letter, i.e. something worth translating into Korean. */
        fun needsTranslation(text: String) = text.any { it in 'a'..'z' || it in 'A'..'Z' }

        fun open(data: ByteArray): XlsxTranslate {
            val entries = LinkedHashMap<String, ByteArray>()
            try {
                ZipInputStream(ByteArrayInputStream(data)).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        if (!e.isDirectory) entries[e.name] = zin.readBytes()
                    }
                }
            } catch (e: java.io.IOException) {
                throw ExtractException("엑셀(.xlsx) 파일을 열 수 없습니다.")
            }
            if (!entries.containsKey("[Content_Types].xml") || entries.keys.none { it.startsWith("xl/") }) {
                throw ExtractException("엑셀(.xlsx) 파일만 사용할 수 있어요. (.xls 는 엑셀에서 .xlsx 로 저장해 주세요)")
            }
            val path = firstSheetPath(entries) ?: throw ExtractException("엑셀 파일에서 시트를 찾을 수 없습니다.")
            val doc = parse(entries[path] ?: throw ExtractException("엑셀 파일에서 시트를 찾을 수 없습니다."))
            val sheetData = children(doc.documentElement, "sheetData").firstOrNull()
                ?: throw ExtractException("첫 번째 시트가 비어 있습니다.")
            val shared = entries["xl/sharedStrings.xml"]?.let { sharedStrings(parse(it)) } ?: emptyList()
            return XlsxTranslate(entries, path, doc, sheetData, shared)
        }

        private fun firstSheetPath(entries: Map<String, ByteArray>): String? {
            try {
                val wb = parse(entries["xl/workbook.xml"] ?: return fallbackSheet(entries))
                val sheet = descendants(wb.documentElement, "sheet").firstOrNull() ?: return fallbackSheet(entries)
                val rid = sheet.attributes.let { attrs ->
                    (0 until attrs.length).map { attrs.item(it) }.firstOrNull { it.nodeName.substringAfter(':') == "id" && it.nodeName.contains(':') }?.nodeValue
                } ?: return fallbackSheet(entries)
                val rels = parse(entries["xl/_rels/workbook.xml.rels"] ?: return fallbackSheet(entries))
                val target = descendants(rels.documentElement, "Relationship").firstOrNull { it.getAttribute("Id") == rid }?.getAttribute("Target")
                    ?: return fallbackSheet(entries)
                val path = if (target.startsWith("/")) target.drop(1) else "xl/$target"
                return path.takeIf { entries.containsKey(it) } ?: fallbackSheet(entries)
            } catch (e: ExtractException) {
                throw e
            } catch (e: Exception) {
                return fallbackSheet(entries)
            }
        }

        private fun fallbackSheet(entries: Map<String, ByteArray>): String? =
            entries.keys.filter { Regex("^xl/worksheets/sheet\\d+\\.xml$").matches(it) }
                .minByOrNull { it.filter(Char::isDigit).toInt() }

        private fun parse(bytes: ByteArray): Document {
            val f = DocumentBuilderFactory.newInstance()
            f.isNamespaceAware = true
            return try {
                f.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            } catch (e: Exception) {
                throw ExtractException("엑셀 파일의 내용을 읽지 못했습니다.")
            }
        }

        private fun serialize(doc: Document): ByteArray {
            val out = ByteArrayOutputStream()
            val t = TransformerFactory.newInstance().newTransformer()
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            t.setOutputProperty(OutputKeys.STANDALONE, "yes")
            t.transform(DOMSource(doc), StreamResult(out))
            return out.toByteArray()
        }

        private fun children(parent: Element, localName: String): List<Element> {
            val out = ArrayList<Element>()
            var n: Node? = parent.firstChild
            while (n != null) {
                if (n is Element && (n.localName ?: n.nodeName.substringAfter(':')) == localName) out += n
                n = n.nextSibling
            }
            return out
        }

        private fun descendants(parent: Element, localName: String): List<Element> {
            val all = parent.getElementsByTagNameNS("*", localName)
            return (0 until all.length).map { all.item(it) as Element }
        }

        private fun sharedStrings(doc: Document): List<String> =
            children(doc.documentElement, "si").map { richText(it) }

        /** The text of an <si> / <is> element: every <t>, except phonetic hints (<rPh>). */
        private fun richText(el: Element): String {
            val sb = StringBuilder()
            fun walk(n: Node) {
                var c: Node? = n.firstChild
                while (c != null) {
                    if (c is Element) {
                        val name = c.localName ?: c.nodeName.substringAfter(':')
                        if (name == "t") sb.append(c.textContent) else if (name != "rPh") walk(c)
                    }
                    c = c.nextSibling
                }
            }
            walk(el)
            return sb.toString()
        }

        private fun cellText(c: Element, shared: List<String>): String {
            val type = c.getAttribute("t")
            if (type == "inlineStr") return children(c, "is").firstOrNull()?.let(::richText).orEmpty()
            val v = children(c, "v").firstOrNull()?.textContent.orEmpty()
            return if (type == "s") v.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty() else v
        }

        /** Zero-based column of a cell reference like "B12", or null when there is none. */
        internal fun columnOf(ref: String): Int? {
            val letters = ref.takeWhile { it in 'A'..'Z' }
            if (letters.isEmpty()) return null
            return letters.fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1
        }
    }
}
