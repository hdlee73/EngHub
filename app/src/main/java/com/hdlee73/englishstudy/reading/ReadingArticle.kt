package com.hdlee73.englishstudy.reading

/** One reading text: a topic, a title and its paragraphs. [id] is the position in the library file ("a001"…). */
data class ReadingArticle(
    val id: String, val topic: String, val title: String, val paragraphs: List<String>,
    /** "중급" / "고급", or empty for the bundled texts. */
    val level: String = "",
    /** Where a downloaded text comes from and under which license; empty for the bundled texts. */
    val credit: String = "",
    val url: String = ""
) {
    val wordCount: Int get() = paragraphs.sumOf { p -> p.split(Regex("\\s+")).count { it.isNotBlank() } }
    /** Reading time at a comfortable learner's pace of about 150 words a minute. */
    val minutes: Int get() = maxOf(1, (wordCount + 74) / 150)
}

/**
 * The bundled library file: every article starts with `### Topic | Title` and is followed by one line per
 * paragraph. New articles are appended at the end so the ids of existing ones never change.
 */
internal object ReadingLibrary {
    fun parse(text: String): List<ReadingArticle> {
        val out = ArrayList<ReadingArticle>()
        var topic = ""
        var title = ""
        var paragraphs = ArrayList<String>()
        fun flush() {
            if (title.isNotEmpty() && paragraphs.isNotEmpty()) {
                out += ReadingArticle("a" + (out.size + 1).toString().padStart(3, '0'), topic, title, paragraphs)
            }
            title = ""; topic = ""; paragraphs = ArrayList()
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.startsWith("###")) {
                flush()
                val head = line.removePrefix("###").trim()
                topic = head.substringBefore('|', "").trim()
                title = head.substringAfter('|', head).trim()
            } else if (line.isNotEmpty() && title.isNotEmpty()) {
                paragraphs += line
            }
        }
        flush()
        return out
    }
}

internal object DailyReading {
    const val PER_DAY = 3

    /**
     * The articles for the day with number [epochDay]. The library is read in order, [PER_DAY] articles a
     * day, so every article is shown once before any comes back, and the same day always gives the same texts.
     */
    fun pick(epochDay: Long, articles: List<ReadingArticle>, count: Int = PER_DAY): List<ReadingArticle> {
        if (articles.isEmpty()) return emptyList()
        val size = articles.size
        val start = Math.floorMod(epochDay * count, size.toLong()).toInt()
        return (0 until minOf(count, size)).map { articles[(start + it) % size] }
    }
}

/** Finding the word under a fingertip. */
internal object ReadingWords {
    private fun isLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
    private fun isApostrophe(c: Char) = c == '\'' || c == '’'

    /** Range of the word that contains (or ends right before) [offset] in [text]; null when there is none. */
    fun rangeAt(text: String, offset: Int): IntRange? {
        if (text.isEmpty()) return null
        var i = offset.coerceIn(0, text.length)
        if (i >= text.length || !isLetter(text[i])) i--
        if (i < 0 || i >= text.length || !isLetter(text[i])) return null
        var start = i
        var end = i
        // Letters, plus an apostrophe between letters ("don't"), make one word.
        while (start > 0 && (isLetter(text[start - 1]) || (isApostrophe(text[start - 1]) && start > 1 && isLetter(text[start - 2])))) start--
        while (end + 1 < text.length && (isLetter(text[end + 1]) || (isApostrophe(text[end + 1]) && end + 2 < text.length && isLetter(text[end + 2])))) end++
        return start..end
    }

    private fun wordStartAfter(text: String, from: Int): Int {
        var i = from
        while (i < text.length && !isLetter(text[i])) i++
        return i
    }

    /** Start of the first word at or after the cursor [offset]; null when no word follows. */
    fun wordStartFrom(text: String, offset: Int): Int? {
        val i = wordStartAfter(text, offset.coerceIn(0, text.length))
        if (i >= text.length) return null
        return rangeAt(text, i)?.first
    }

    /** End (inclusive) of the last word that ends at or before the cursor [offset]; null when no word precedes. */
    fun wordEndBefore(text: String, offset: Int): Int? {
        var i = offset.coerceIn(0, text.length) - 1
        while (i >= 0 && !isLetter(text[i])) i--
        if (i < 0) return null
        return rangeAt(text, i)?.last
    }

    /** The sentence that contains [offset] (up to and including its closing punctuation). */
    fun sentenceRange(text: String, offset: Int): IntRange? {
        if (text.isBlank()) return null
        val o = offset.coerceIn(0, text.length - 1)
        var start = o
        while (start > 0 && !(text[start - 1].isWhitespace() && start >= 2 && text[start - 2] in ".!?")) start--
        while (start < text.length - 1 && text[start].isWhitespace()) start++
        var end = o
        while (end < text.length - 1 && !(text[end] in ".!?" && text[end + 1].isWhitespace())) end++
        return if (end >= start) start..end else null
    }

    /** The range from the first to the last of two ranges, in either order. */
    fun span(a: IntRange, b: IntRange): IntRange = minOf(a.first, b.first)..maxOf(a.last, b.last)

    /** [range] with the word before it added; unchanged at the start of the text. */
    fun extendLeft(text: String, range: IntRange): IntRange {
        var i = range.first - 1
        while (i >= 0 && !isLetter(text[i])) i--
        if (i < 0) return range
        return (rangeAt(text, i) ?: return range).first..range.last
    }

    /** [range] with the word after it added; unchanged at the end of the text. */
    fun extendRight(text: String, range: IntRange): IntRange {
        val i = wordStartAfter(text, range.last + 1)
        if (i >= text.length) return range
        return range.first..(rangeAt(text, i) ?: return range).last
    }

    /** [range] without its last word; a single word stays as it is. */
    fun shrink(text: String, range: IntRange): IntRange {
        var j = range.last
        while (j >= range.first && !isLetter(text[j])) j--
        if (j < range.first) return range
        val lastWord = rangeAt(text, j) ?: return range
        if (lastWord.first <= range.first) return range
        var k = lastWord.first - 1
        while (k >= range.first && !isLetter(text[k])) k--
        if (k < range.first) return range
        return range.first..(rangeAt(text, k) ?: return range).last
    }

    /** What to look up for a selection: one word as [lookupForm], a phrase without edge punctuation and extra spaces. */
    fun lookupText(selection: String): String {
        val trimmed = selection.trim()
        if (trimmed.none { it.isWhitespace() }) return lookupForm(trimmed)
        return trimmed.trim { !isLetter(it) && !it.isDigit() }.replace('’', '\'').replace(Regex("\\s+"), " ")
    }

    /** What to look up for a tapped word: "Gutenberg's" → "Gutenberg". */
    fun lookupForm(word: String): String =
        word.replace('’', '\'').removeSuffix("'s").removeSuffix("'").trim()
}

/** Splits long text into pieces for the translation service, at sentence ends where possible. */
internal object TextChunks {
    fun split(text: String, max: Int = 700): List<String> {
        val sentences = Regex("(?<=[.!?])\\s+").split(text.trim()).filter { it.isNotBlank() }
        val out = ArrayList<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (current.isNotEmpty() && current.length + 1 + sentence.length > max) { out += current.toString(); current.clear() }
            if (sentence.length > max) {
                // A sentence longer than the limit is cut at spaces.
                sentence.chunked(max).forEach { out += it }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}
