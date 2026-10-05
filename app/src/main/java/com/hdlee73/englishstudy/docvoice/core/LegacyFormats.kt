package com.hdlee73.englishstudy.docvoice.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.TreeMap
import java.util.zip.Inflater

class ExtractException(message: String) : Exception(message)

private val CP1252: Charset = Charset.forName("windows-1252")

internal fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
internal fun u32(b: ByteArray, o: Int): Long = (u16(b, o).toLong()) or (u16(b, o + 2).toLong() shl 16)
internal fun i32(b: ByteArray, o: Int): Int = ByteBuffer.wrap(b, o, 4).order(ByteOrder.LITTLE_ENDIAN).int

/** HWP 5.x (OLE) 본문 추출 */
object HwpBinary {
    private const val TAG_PARA_TEXT = 67

    fun extract(data: ByteArray): String {
        if (!OleFile.isOle(data)) throw ExtractException("지원하지 않는 HWP 형식입니다. (HWP 5.x 또는 HWPX만 지원)")
        val ole = OleFile.open(data)
        val header = ole.stream("FileHeader") ?: throw ExtractException("HWP 헤더를 찾지 못했습니다.")
        if (header.size < 40 || String(header, 0, 17, Charsets.US_ASCII) != "HWP Document File")
            throw ExtractException("HWP 헤더가 올바르지 않습니다.")
        val flags = u32(header, 36)
        if (flags and 0x02L != 0L) throw ExtractException("암호가 걸린 HWP 문서는 읽을 수 없습니다.")
        if (flags and 0x04L != 0L) throw ExtractException("배포용 HWP 문서는 읽을 수 없습니다. 한글에서 일반 문서로 저장 후 다시 시도하세요.")
        val compressed = flags and 0x01L != 0L
        val sections = ole.listStreams("BodyText")
            .filter { it.startsWith("Section") }
            .sortedBy { it.removePrefix("Section").toIntOrNull() ?: 0 }
        if (sections.isEmpty()) throw ExtractException("HWP에서 본문을 찾지 못했습니다.")
        val out = ArrayList<String>()
        for (name in sections) {
            var raw = ole.stream("BodyText/$name") ?: continue
            if (compressed) raw = inflateRaw(raw)
            out.add(parseSection(raw))
        }
        return out.joinToString("\n")
    }

    internal fun inflateRaw(data: ByteArray): ByteArray {
        val inf = Inflater(true)
        inf.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
            }
        } finally {
            inf.end()
        }
        return out.toByteArray()
    }

    internal fun paraText(rec: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        val n = rec.size
        while (i + 2 <= n) {
            val code = u16(rec, i)
            i += 2
            when {
                code >= 32 -> sb.append(code.toChar())
                code == 10 || code == 13 -> sb.append('\n')
                code == 30 || code == 31 -> sb.append(' ')
                code == 0 || code in 24..29 -> Unit
                else -> { // 인라인/확장 컨트롤: 코드 포함 8 WCHAR → 나머지 14바이트 건너뜀
                    if (code == 9) sb.append('\t')
                    i += 14
                }
            }
        }
        return sb.toString()
    }

    internal fun parseSection(data: ByteArray): String {
        var pos = 0
        val n = data.size
        val paras = ArrayList<String>()
        while (pos + 4 <= n) {
            val h = u32(data, pos)
            pos += 4
            val tag = (h and 0x3FF).toInt()
            var size = ((h shr 20) and 0xFFF).toLong()
            if (size == 0xFFFL) {
                if (pos + 4 > n) break
                size = u32(data, pos)
                pos += 4
            }
            val end = minOf(pos.toLong() + size, n.toLong()).toInt()
            if (tag == TAG_PARA_TEXT) paras.add(paraText(data.copyOfRange(pos, end)).trimEnd('\n'))
            pos = end
        }
        return paras.filter { it.isNotBlank() }.joinToString("\n")
    }
}

/** 구형 Word(.doc, OLE) 본문 추출 */
object DocBinary {
    fun extract(data: ByteArray): String {
        if (!OleFile.isOle(data)) throw ExtractException("DOC 파일 형식이 올바르지 않습니다.")
        val ole = OleFile.open(data)
        val word = ole.stream("WordDocument") ?: throw ExtractException("DOC 본문을 찾지 못했습니다.")
        val flags = u16(word, 0x0A)
        if (flags and 0x0100 != 0) throw ExtractException("암호가 걸린 DOC 문서는 읽을 수 없습니다.")
        val table = ole.stream(if (flags and 0x0200 != 0) "1Table" else "0Table")
            ?: throw ExtractException("DOC 구조를 해석하지 못했습니다.")
        return try {
            textFromStreams(word, table)
        } catch (e: ExtractException) {
            throw e
        } catch (e: Exception) {
            throw ExtractException("DOC를 해석하지 못했습니다: ${e.message}")
        }
    }

    internal fun textFromStreams(word: ByteArray, table: ByteArray): String {
        val ccpText = i32(word, 0x4C)
        val fcClx = u32(word, 0x1A2).toInt()
        val lcbClx = u32(word, 0x1A6).toInt()
        val clx = table.copyOfRange(fcClx, minOf(fcClx + lcbClx, table.size))
        var i = 0
        while (i < clx.size && clx[i].toInt() == 1) { // Prc 건너뜀
            i += 3 + u16(clx, i + 1)
        }
        if (i >= clx.size || clx[i].toInt() != 2) throw ExtractException("DOC 구조를 해석하지 못했습니다.")
        val lcb = u32(clx, i + 1).toInt()
        val plc = clx.copyOfRange(i + 5, minOf(i + 5 + lcb, clx.size))
        val n = (lcb - 4) / 12
        val cps = IntArray(n + 1) { i32(plc, it * 4) }
        val sb = StringBuilder()
        for (k in 0 until n) {
            val fc = u32(plc, (n + 1) * 4 + k * 8 + 2)
            val count = cps[k + 1] - cps[k]
            if (count <= 0) continue
            if (fc and 0x40000000L != 0L) {
                val off = ((fc and 0x3FFFFFFFL) / 2).toInt()
                val end = minOf(off + count, word.size)
                if (off < end) sb.append(String(word, off, end - off, CP1252))
            } else {
                val off = fc.toInt()
                val end = minOf(off + count * 2, word.size)
                if (off < end) sb.append(String(word, off, end - off, Charsets.UTF_16LE))
            }
        }
        var text = if (sb.length > ccpText && ccpText > 0) sb.substring(0, ccpText) else sb.toString()
        text = text.replace(Regex("\u0013[^\u0013\u0014\u0015]*\u0014"), "") // 필드 코드 제거
        text = text.replace('\u0007', ' ').replace('\u000b', '\n').replace('\u000c', '\n').replace('\r', '\n')
        return text.replace(Regex("[\u0000-\u0008\u000e-\u001f]"), "")
    }
}

/** 구형 PowerPoint(.ppt, OLE) 슬라이드 텍스트 추출 (마스터/노트 제외, 최선 노력) */
object PptBinary {
    fun extract(data: ByteArray): String {
        if (!OleFile.isOle(data)) throw ExtractException("PPT 파일 형식이 올바르지 않습니다.")
        val ole = OleFile.open(data)
        val stream = ole.stream("PowerPoint Document") ?: throw ExtractException("PPT 본문을 찾지 못했습니다.")
        val texts = ArrayList<String>()
        parseRecords(stream, texts, false)
        val seen = HashSet<String>()
        val uniq = ArrayList<String>()
        for (t0 in texts) {
            val t = t0.replace('\r', '\n').replace('\u000b', '\n').trim()
            if (t.isNotEmpty() && seen.add(t)) uniq.add(t)
        }
        if (uniq.isEmpty()) throw ExtractException("PPT에서 텍스트를 찾지 못했습니다.")
        return uniq.joinToString("\n")
    }

    internal fun parseRecords(data: ByteArray, out: MutableList<String>, inSlide: Boolean) {
        var pos = 0
        val n = data.size
        while (pos + 8 <= n) {
            val verInst = u16(data, pos)
            val type = u16(data, pos + 2)
            val len = u32(data, pos + 4)
            pos += 8
            val end = minOf(pos.toLong() + len, n.toLong()).toInt()
            val ver = verInst and 0xF
            if (ver == 0xF) { // 컨테이너
                val slideCtx = inSlide || type == 1006 || (type == 4080 && (verInst shr 4) == 0)
                if (type != 1016 && type != 1008) { // 마스터 / 노트 제외
                    parseRecords(data.copyOfRange(pos, end), out, slideCtx)
                }
            } else if (inSlide && type == 0x0FA0) {
                out.add(String(data, pos, end - pos, Charsets.UTF_16LE))
            } else if (inSlide && type == 0x0FA8) {
                out.add(String(data, pos, end - pos, CP1252))
            }
            pos = end
        }
    }
}

/** 구형 Excel(.xls, BIFF8) 추출 */
object XlsBinary {
    fun extract(data: ByteArray): String {
        if (!OleFile.isOle(data)) throw ExtractException("XLS 파일 형식이 올바르지 않습니다.")
        val ole = OleFile.open(data)
        val wb = ole.stream("Workbook") ?: ole.stream("Book")
            ?: throw ExtractException("XLS 본문을 찾지 못했습니다.")
        return parse(wb)
    }

    private class SegReader(val segs: List<ByteArray>) {
        var si = 0
        var pi = 0
        private fun normalize(): Boolean {
            while (si < segs.size && pi >= segs[si].size) { si++; pi = 0 }
            return si < segs.size
        }
        fun u8(): Int {
            if (!normalize()) throw IndexOutOfBoundsException()
            return segs[si][pi++].toInt() and 0xFF
        }
        fun u16(): Int = u8() or (u8() shl 8)
        fun u32(): Long = u16().toLong() or (u16().toLong() shl 16)
        fun skip(count: Long) {
            var left = count
            while (left > 0 && normalize()) {
                val step = minOf(left, (segs[si].size - pi).toLong()).toInt()
                pi += step
                left -= step
            }
        }
        fun hasMore(): Boolean = normalize()

        fun readString(): String {
            val cch = u16()
            val flags = u8()
            val rich = flags and 0x08 != 0
            val ext = flags and 0x04 != 0
            var high = flags and 0x01 != 0
            val runs = if (rich) u16() else 0
            val extLen = if (ext) u32() else 0L
            val sb = StringBuilder()
            var remaining = cch
            while (remaining > 0) {
                if (si >= segs.size) break
                if (pi >= segs[si].size) { // CONTINUE 경계: 새 조각은 플래그 바이트로 시작
                    si++
                    pi = 0
                    if (si >= segs.size) break
                    high = segs[si][pi++].toInt() and 1 != 0
                    continue
                }
                val bytesPer = if (high) 2 else 1
                val avail = (segs[si].size - pi) / bytesPer
                if (avail == 0) { pi = segs[si].size; continue }
                val take = minOf(remaining, avail)
                val seg = segs[si]
                if (high) sb.append(String(seg, pi, take * 2, Charsets.UTF_16LE))
                else sb.append(String(seg, pi, take, Charsets.ISO_8859_1))
                pi += take * bytesPer
                remaining -= take
            }
            skip(runs * 4L + extLen)
            return sb.toString()
        }
    }

    private fun parseSst(segs: List<ByteArray>): List<String> {
        val r = SegReader(segs)
        r.skip(4) // total refs
        val unique = r.u32().toInt()
        val out = ArrayList<String>()
        try {
            for (i in 0 until unique) {
                if (!r.hasMore()) break
                out.add(r.readString())
            }
        } catch (_: IndexOutOfBoundsException) {
        }
        return out
    }

    private fun rkValue(rk: Long): Double {
        var v: Double = if (rk and 2L != 0L) {
            (rk.toInt() shr 2).toDouble()
        } else {
            java.lang.Double.longBitsToDouble((rk and 0xFFFFFFFCL) shl 32)
        }
        if (rk and 1L != 0L) v /= 100.0
        return v
    }

    private fun numStr(d: Double): String =
        if (d == Math.floor(d) && Math.abs(d) < 1e15) d.toLong().toString() else d.toString()

    private fun readBiff8String(data: ByteArray, off: Int): String {
        val cch = u16(data, off)
        val flags = data[off + 2].toInt() and 0xFF
        val start = off + 3
        return if (flags and 1 != 0) String(data, start, minOf(cch * 2, data.size - start), Charsets.UTF_16LE)
        else String(data, start, minOf(cch, data.size - start), Charsets.ISO_8859_1)
    }

    internal fun parse(wb: ByteArray): String {
        var pos = 0
        val sst = ArrayList<String>()
        val sheets = ArrayList<String>()
        val stack = ArrayList<Int>()
        var cells: TreeMap<Int, TreeMap<Int, String>>? = null
        var pendingFormula: Pair<Int, Int>? = null

        fun put(r: Int, c: Int, v: String) {
            if (v.isBlank()) return
            cells!!.getOrPut(r) { TreeMap() }[c] = v.trim()
        }

        while (pos + 4 <= wb.size) {
            val type = u16(wb, pos)
            val len = u16(wb, pos + 2)
            val ds = pos + 4
            val end = minOf(ds + len, wb.size)
            var next = end
            val inSheet = stack.isNotEmpty() && stack.last() == 0x0010

            when (type) {
                0x0809, 0x0009, 0x0209, 0x0409 -> {
                    val dt = if (end - ds >= 4) u16(wb, ds + 2) else 0
                    stack.add(dt)
                    if (dt == 0x0010) cells = TreeMap()
                }
                0x000A -> {
                    if (stack.isNotEmpty()) {
                        val t = stack.removeAt(stack.size - 1)
                        if (t == 0x0010 && cells != null) {
                            val rows = cells!!.values.map { row ->
                                val vals = ArrayList<String>()
                                for (v in row.values) if (vals.isEmpty() || vals.last() != v) vals.add(v)
                                vals.joinToString(", ")
                            }.filter { it.isNotEmpty() }
                            if (rows.isNotEmpty()) sheets.add(rows.joinToString("\n"))
                            cells = null
                        }
                    }
                }
                0x002F -> throw ExtractException("암호가 걸린 XLS 문서는 읽을 수 없습니다.")
                0x00FC -> { // SST (+ CONTINUE)
                    val segs = ArrayList<ByteArray>()
                    segs.add(wb.copyOfRange(ds, end))
                    var p = end
                    while (p + 4 <= wb.size && u16(wb, p) == 0x003C) {
                        val l = u16(wb, p + 2)
                        val e2 = minOf(p + 4 + l, wb.size)
                        segs.add(wb.copyOfRange(p + 4, e2))
                        p = e2
                    }
                    sst.clear()
                    sst.addAll(parseSst(segs))
                    next = p
                }
                0x00FD -> if (inSheet && len >= 10) { // LABELSST
                    val idx = u32(wb, ds + 6).toInt()
                    if (idx in sst.indices) put(u16(wb, ds), u16(wb, ds + 2), sst[idx])
                }
                0x0204 -> if (inSheet && len >= 8) put(u16(wb, ds), u16(wb, ds + 2), readBiff8String(wb, ds + 6)) // LABEL
                0x0203 -> if (inSheet && len >= 14) { // NUMBER
                    val d = ByteBuffer.wrap(wb, ds + 6, 8).order(ByteOrder.LITTLE_ENDIAN).double
                    put(u16(wb, ds), u16(wb, ds + 2), numStr(d))
                }
                0x027E -> if (inSheet && len >= 10) put(u16(wb, ds), u16(wb, ds + 2), numStr(rkValue(u32(wb, ds + 6)))) // RK
                0x00BD -> if (inSheet && len >= 6) { // MULRK
                    val row = u16(wb, ds)
                    val first = u16(wb, ds + 2)
                    val count = (len - 6) / 6
                    for (k in 0 until count) put(row, first + k, numStr(rkValue(u32(wb, ds + 4 + k * 6 + 2))))
                }
                0x0205 -> if (inSheet && len >= 8) { // BOOLERR
                    if ((wb[ds + 7].toInt() and 0xFF) == 0) {
                        put(u16(wb, ds), u16(wb, ds + 2), if ((wb[ds + 6].toInt() and 0xFF) != 0) "TRUE" else "FALSE")
                    }
                }
                0x0006 -> if (inSheet && len >= 14) { // FORMULA (캐시된 결과 사용)
                    val row = u16(wb, ds)
                    val col = u16(wb, ds + 2)
                    if (u16(wb, ds + 12) == 0xFFFF) {
                        pendingFormula = if ((wb[ds + 6].toInt() and 0xFF) == 0) row to col else null
                    } else {
                        val d = ByteBuffer.wrap(wb, ds + 6, 8).order(ByteOrder.LITTLE_ENDIAN).double
                        put(row, col, numStr(d))
                        pendingFormula = null
                    }
                }
                0x0207 -> if (inSheet && pendingFormula != null && len >= 3) { // STRING (수식 결과)
                    put(pendingFormula!!.first, pendingFormula!!.second, readBiff8String(wb, ds))
                    pendingFormula = null
                }
            }
            pos = next
        }
        return sheets.joinToString("\n\n")
    }
}
