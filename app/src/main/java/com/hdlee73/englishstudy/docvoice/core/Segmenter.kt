package com.hdlee73.englishstudy.docvoice.core

import java.util.Locale

/** 문장 분리 · 화자 매핑 · 문단 묶기 (Android 의존 없는 순수 로직; 데스크톱 앱의 segment.py 와 동일 규칙). */

data class Segment(val start: Double, val end: Double, val text: String)

data class Sentence(
    val start: Double,
    val end: Double,
    val text: String,
    var speaker: Int? = null,
)

data class Paragraph(val sentences: List<Sentence>) {
    val text: String get() = sentences.joinToString(" ") { it.text }
    val start: Double get() = sentences.first().start
    val end: Double get() = sentences.last().end
    val speaker: Int? get() = sentences.first().speaker
}

object Segmenter {
    private val HANGUL = Regex("[\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7AF]")
    private val WORD = Regex("[A-Za-z0-9]+(?:['’\\-][A-Za-z0-9]+)*")
    private val ALNUM = Regex("[0-9A-Za-z\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7AF]")
    private val KO_END = Regex("(다|요|죠|까|네|니다)[\"'”’)\\]]*$")

    private const val TERMINATORS = ".?!。！？…"
    private const val CLOSERS = "\"'”’)]»"
    private val ABBREV = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "e.g", "i.e",
        "no", "inc", "ltd", "co", "corp", "u.s", "u.k", "a.m", "p.m", "fig", "approx",
        "dept", "est", "vol", "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep",
        "sept", "oct", "nov", "dec",
    )
    private val CAN_END = setOf("etc", "inc", "ltd", "co", "corp", "u.s", "u.k", "a.m", "p.m")

    fun hasHangul(text: String) = HANGUL.containsMatchIn(text)

    /** 한글 비율이 일정 이상이면 한국어 문장으로 본다. */
    fun isKorean(text: String, ratio: Double = 0.15): Boolean {
        var letters = 0
        var hangul = 0
        for (c in text) {
            val isH = HANGUL.matches(c.toString())
            if (isH || c in 'A'..'Z' || c in 'a'..'z') {
                letters++
                if (isH) hangul++
            }
        }
        return letters > 0 && hangul.toDouble() / letters >= ratio
    }

    fun englishWordCount(text: String) = WORD.findAll(text).count()

    private fun isAbbrev(text: String, i: Int, j: Int): Boolean {
        if (text[i] != '.' || j != i + 1) return false
        var a = i
        while (a > 0 && !text[a - 1].isWhitespace()) a--
        val tok = text.substring(a, i)
        if (tok.isEmpty()) return false
        val low = tok.lowercase(Locale.ROOT).trimStart('(', '[', '"', '\'', '“', '‘')
        val lineStart = a == 0 || text[a - 1] == '\n'
        if (lineStart && tok.all { it.isDigit() } && tok.length <= 3) return true
        if (tok.length == 1 && tok[0].isLetter() && tok[0].isUpperCase()) return true
        if (low in ABBREV) {
            if (low in CAN_END) {
                var k = j
                while (k < text.length && text[k].isWhitespace()) k++
                if (k < text.length && text[k].isUpperCase()) return false
            }
            return true
        }
        return false
    }

    /** 문장 경계를 (시작, 끝[exclusive]) 로 돌려준다. 줄바꿈은 항상 경계. */
    fun splitSpans(text: String): List<IntRange> {
        val spans = ArrayList<IntRange>()
        val n = text.length
        var start = 0

        fun flush(a0: Int, b0: Int) {
            var a = a0
            var b = b0
            while (a < b && text[a].isWhitespace()) a++
            while (b > a && text[b - 1].isWhitespace()) b--
            if (b > a) spans.add(a until b)
        }

        var i = 0
        while (i < n) {
            val ch = text[i]
            if (ch == '\n') {
                flush(start, i)
                start = i + 1
                i++
                continue
            }
            if (ch in TERMINATORS) {
                var j = i + 1
                while (j < n && text[j] in TERMINATORS) j++
                while (j < n && text[j] in CLOSERS) j++
                val atEnd = j >= n || text[j].isWhitespace()
                val fullwidth = ch == '。' || ch == '！' || ch == '？'
                if ((atEnd || fullwidth) && !isAbbrev(text, i, j)) {
                    flush(start, j)
                    start = j
                }
                i = j
                continue
            }
            i++
        }
        flush(start, n)
        return spans
    }

    fun splitSentences(text: String): List<String> =
        splitSpans(text).map { text.substring(it.first, it.last + 1) }

    // ---- Whisper 세그먼트 → 문장 ------------------------------------------------

    private fun endsSentence(tail: String): Boolean {
        val t = tail.trimEnd()
        if (t.isEmpty()) return false
        var k = t.length
        while (k > 0 && t[k - 1] in CLOSERS) k--
        return (k > 0 && t[k - 1] in TERMINATORS) || KO_END.containsMatchIn(t)
    }

    private class Span(val c0: Int, val c1: Int, val t0: Double, val t1: Double)

    private fun interp(pos: Int, spans: List<Span>, asEnd: Boolean): Double {
        var idx: Int
        if (asEnd) {
            idx = spans.indexOfFirst { it.c1 >= pos }
            if (idx < 0) idx = spans.size - 1
        } else {
            idx = spans.indexOfLast { it.c0 <= pos }
            if (idx < 0) idx = 0
            if (pos > spans[idx].c1 && idx + 1 < spans.size) idx++
        }
        val s = spans[idx]
        val p = pos.coerceIn(s.c0, s.c1)
        return if (s.c1 == s.c0) s.t0 else s.t0 + (p - s.c0).toDouble() / (s.c1 - s.c0) * (s.t1 - s.t0)
    }

    fun buildSentences(
        segments: List<Segment>,
        minEnWords: Int = 3,
        pauseBoundary: Double = 0.8,
        maxPendingChars: Int = 160,
    ): List<Sentence> {
        val segs = segments.filter { it.text.isNotBlank() }
        if (segs.isEmpty()) return emptyList()

        val full = StringBuilder()
        val spans = ArrayList<Span>()
        var prev: Segment? = null
        for (seg in segs) {
            val t = seg.text.trim()
            if (prev != null) {
                val tail = full.substring(full.lastIndexOf("\n") + 1)
                val gap = seg.start - prev.end
                val boundary = gap >= pauseBoundary ||
                    tail.length >= maxPendingChars ||
                    (endsSentence(tail) && hasHangul(tail))
                full.append(if (boundary) "\n" else " ")
            }
            val c0 = full.length
            full.append(t)
            spans.add(Span(c0, full.length, seg.start, maxOf(seg.end, seg.start)))
            prev = seg
        }

        val fullText = full.toString()
        val raw = ArrayList<Sentence>()
        for (r in splitSpans(fullText)) {
            val a = r.first
            val b = r.last + 1
            val text = fullText.substring(a, b)
            if (!ALNUM.containsMatchIn(text)) continue
            val t0 = interp(a, spans, asEnd = false)
            val t1 = interp(b, spans, asEnd = true)
            raw.add(Sentence(t0, maxOf(t1, t0), text))
        }
        return mergeShortEnglish(raw, minEnWords)
    }

    /** 단어 수가 minWords 미만인 영어 조각은 독립 문장으로 보지 않고 이웃 문장에 붙인다. */
    fun mergeShortEnglish(sentences: List<Sentence>, minWords: Int = 3): List<Sentence> {
        val out = ArrayList<Sentence>()
        var pending: Sentence? = null
        for (s in sentences) {
            var cur = s
            var mergedPending = false
            val p = pending
            if (p != null) {
                cur = Sentence(p.start, s.end, p.text + " " + s.text, s.speaker)
                pending = null
                mergedPending = true
            }
            val short = !hasHangul(cur.text) && englishWordCount(cur.text) < minWords
            if (short && !mergedPending && out.isNotEmpty()) {
                val last = out.last()
                out[out.size - 1] = Sentence(last.start, cur.end, last.text + " " + cur.text, last.speaker)
            } else if (short && out.isEmpty()) {
                pending = cur
            } else {
                out.add(cur)
            }
        }
        pending?.let { out.add(it) }
        return out
    }

    // ---- 화자 · 문단 ------------------------------------------------------------

    /** 겹치는 시간이 가장 긴 화자를 각 문장에 지정한다. turns = (start, end, speaker) */
    fun assignSpeakers(sentences: List<Sentence>, turns: List<Triple<Double, Double, Int>>) {
        for (s in sentences) {
            val acc = HashMap<Int, Double>()
            for ((t0, t1, spk) in turns) {
                val ov = minOf(s.end, t1) - maxOf(s.start, t0)
                if (ov > 0) acc[spk] = (acc[spk] ?: 0.0) + ov
            }
            s.speaker = acc.maxByOrNull { it.value }?.key
        }
    }

    /** 화자 변경 · 발언 간격(gap초 이상) · 길이 초과 시 줄(문단)을 나눈다. */
    fun groupParagraphs(
        sentences: List<Sentence>,
        gap: Double = 1.5,
        maxChars: Int = 500,
        useSpeaker: Boolean = true,
    ): List<Paragraph> {
        val result = ArrayList<MutableList<Sentence>>()
        var cur: MutableList<Sentence>? = null
        var chars = 0
        var prev: Sentence? = null
        for (s in sentences) {
            var isNew = cur == null
            val p = prev
            if (!isNew && p != null) {
                isNew = when {
                    useSpeaker && s.speaker != null && p.speaker != null && s.speaker != p.speaker -> true
                    s.start - p.end >= gap -> true
                    chars >= maxChars -> true
                    else -> false
                }
            }
            if (isNew) {
                cur = ArrayList()
                result.add(cur)
                chars = 0
            }
            cur!!.add(s)
            chars += s.text.length
            prev = s
        }
        return result.map { Paragraph(it) }
    }

    fun fmtTime(sec: Double): String {
        val s = maxOf(sec, 0.0)
        val total = s.toInt()
        val h = total / 3600
        val m = (total % 3600) / 60
        val ss = total % 60
        val frac = ((s - total) * 10).toInt()
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%d", h, m, ss, frac)
    }
}
