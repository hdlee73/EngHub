package com.hdlee73.englishstudy.reading

/** One reading text: a topic, a title and its paragraphs. [id] is the position in the library file ("a001"…). */
data class ReadingArticle(val id: String, val topic: String, val title: String, val paragraphs: List<String>) {
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
