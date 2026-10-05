package com.hdlee73.englishstudy.docvoice.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 최소한의 OLE2(CFB) 컨테이너 리더. HWP / DOC / PPT / XLS 의 스트림을 꺼내는 데 사용. */
class OleFile private constructor(private val data: ByteArray) {
    private val buf: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    private val sectorSize: Int
    private val miniSectorSize: Int
    private val miniCutoff: Int
    private val fat: IntArray
    private val miniFat: IntArray
    private val entries: List<Entry>
    private val miniStream: ByteArray

    class Entry(
        val name: String,
        val type: Int,
        val left: Int,
        val right: Int,
        val child: Int,
        val start: Int,
        val size: Long,
    )

    init {
        require(isOle(data)) { "OLE 파일이 아닙니다." }
        sectorSize = 1 shl buf.getShort(0x1E).toInt()
        miniSectorSize = 1 shl buf.getShort(0x20).toInt()
        miniCutoff = buf.getInt(0x38)
        val numFat = buf.getInt(0x2C)
        val firstDir = buf.getInt(0x30)
        val firstMiniFat = buf.getInt(0x3C)
        val numMiniFat = buf.getInt(0x40)
        var difatSector = buf.getInt(0x44)
        val numDifat = buf.getInt(0x48)

        val fatSectors = ArrayList<Int>()
        for (i in 0 until 109) {
            val s = buf.getInt(0x4C + i * 4)
            if (s >= 0 && fatSectors.size < numFat) fatSectors.add(s)
        }
        var guard = 0
        while (difatSector >= 0 && guard++ < numDifat + 1) {
            val off = sectorOffset(difatSector)
            val per = sectorSize / 4 - 1
            for (i in 0 until per) {
                val s = buf.getInt(off + i * 4)
                if (s >= 0 && fatSectors.size < numFat) fatSectors.add(s)
            }
            difatSector = buf.getInt(off + per * 4)
        }
        val perFat = sectorSize / 4
        fat = IntArray(fatSectors.size * perFat)
        for ((idx, s) in fatSectors.withIndex()) {
            val off = sectorOffset(s)
            for (i in 0 until perFat) fat[idx * perFat + i] = buf.getInt(off + i * 4)
        }

        val dirBytes = readChain(firstDir, -1)
        val list = ArrayList<Entry>()
        val db = ByteBuffer.wrap(dirBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until dirBytes.size / 128) {
            val o = i * 128
            val nameLen = (db.getShort(o + 0x40).toInt() and 0xFFFF)
            val name = if (nameLen >= 2) String(dirBytes, o, nameLen - 2, Charsets.UTF_16LE) else ""
            list.add(
                Entry(
                    name, dirBytes[o + 0x42].toInt(), db.getInt(o + 0x44), db.getInt(o + 0x48),
                    db.getInt(o + 0x4C), db.getInt(o + 0x74), db.getLong(o + 0x78) and 0xFFFFFFFFL,
                )
            )
        }
        entries = list

        miniStream = if (entries.isNotEmpty() && entries[0].start >= 0) {
            readChain(entries[0].start, entries[0].size.toInt())
        } else ByteArray(0)

        val mf = if (numMiniFat > 0 && firstMiniFat >= 0) readChain(firstMiniFat, -1) else ByteArray(0)
        val mb = ByteBuffer.wrap(mf).order(ByteOrder.LITTLE_ENDIAN)
        miniFat = IntArray(mf.size / 4) { mb.getInt(it * 4) }
    }

    private fun sectorOffset(sector: Int): Int = (sector + 1) * sectorSize

    private fun readChain(first: Int, size: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var s = first
        var guard = 0
        while (s >= 0 && guard++ < fat.size + 8) {
            val off = sectorOffset(s)
            if (off < 0 || off + sectorSize > data.size) {
                // 마지막 섹터가 잘린 파일 대응
                if (off in 0 until data.size) out.write(data, off, data.size - off)
                break
            }
            out.write(data, off, sectorSize)
            s = if (s < fat.size) fat[s] else -1
        }
        val bytes = out.toByteArray()
        return if (size >= 0 && size < bytes.size) bytes.copyOf(size) else bytes
    }

    private fun readMiniChain(first: Int, size: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var s = first
        var guard = 0
        while (s >= 0 && guard++ < miniFat.size + 8) {
            val off = s * miniSectorSize
            if (off + miniSectorSize > miniStream.size) break
            out.write(miniStream, off, miniSectorSize)
            s = if (s < miniFat.size) miniFat[s] else -1
        }
        val bytes = out.toByteArray()
        return if (size < bytes.size) bytes.copyOf(size) else bytes
    }

    private fun readEntry(e: Entry): ByteArray {
        if (e.size == 0L || e.start < 0) return ByteArray(0)
        return if (e.size < miniCutoff) readMiniChain(e.start, e.size.toInt())
        else readChain(e.start, e.size.toInt())
    }

    /** 저장소(디렉터리) 항목의 자식을 이름순 트리 순회로 모은다. */
    private fun children(parent: Entry): List<Pair<Int, Entry>> {
        val out = ArrayList<Pair<Int, Entry>>()
        val visited = HashSet<Int>()
        fun walk(id: Int) {
            if (id < 0 || id >= entries.size || !visited.add(id)) return
            val e = entries[id]
            walk(e.left)
            out.add(id to e)
            walk(e.right)
        }
        walk(parent.child)
        return out
    }

    private fun find(path: String): Entry? {
        var cur = entries.firstOrNull() ?: return null
        for (part in path.split('/').filter { it.isNotEmpty() }) {
            cur = children(cur).map { it.second }.firstOrNull { it.name.equals(part, ignoreCase = true) } ?: return null
        }
        return cur
    }

    fun hasStream(path: String): Boolean = find(path)?.type == 2

    fun stream(path: String): ByteArray? {
        val e = find(path) ?: return null
        return if (e.type == 2) readEntry(e) else null
    }

    /** 저장소 아래 스트림 이름 목록 (예: "BodyText" → Section0, Section1 …) */
    fun listStreams(storagePath: String): List<String> {
        val e = find(storagePath) ?: return emptyList()
        return children(e).filter { it.second.type == 2 }.map { it.second.name }
    }

    companion object {
        private val SIG = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

        fun isOle(data: ByteArray): Boolean =
            data.size >= 512 && SIG.indices.all { data[it] == SIG[it] }

        fun open(data: ByteArray): OleFile = OleFile(data)
    }
}
