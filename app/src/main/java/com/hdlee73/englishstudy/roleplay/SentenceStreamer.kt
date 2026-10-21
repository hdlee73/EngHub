package com.hdlee73.englishstudy.roleplay

/**
 * Cuts the text a model writes piece by piece into sentences and hands each over as soon as it is complete, so the partner can
 * start speaking before the whole answer exists. A role label at the start ("Partner:") is dropped, and everything from a
 * made-up "Learner:" line on is ignored (the model sometimes goes on to write the learner's side).
 */
internal class SentenceStreamer(private val onSentence: (String) -> Unit) {
    private val buf = StringBuilder()
    private var stopped = false
    private var first = true

    fun feed(chunk: String) {
        if (stopped) return
        buf.append(chunk)
        val learner = buf.indexOf("Learner:")
        if (learner >= 0) { buf.setLength(learner); stopped = true }
        drain(final = stopped)
    }

    /** The model is done: whatever is left is the last sentence. */
    fun finish() {
        drain(final = true)
    }

    private fun drain(final: Boolean) {
        while (true) {
            val end = boundary()
            if (end < 0) break
            emit(buf.substring(0, end + 1))
            buf.delete(0, end + 1)
        }
        if (final && buf.isNotBlank()) {
            emit(buf.toString())
            buf.setLength(0)
        }
    }

    /** Index of the last character of the first complete sentence, or -1. A short "Mr." style piece is kept for the next one. */
    private fun boundary(): Int {
        var i = 0
        while (i < buf.length - 1) {
            val c = buf[i]
            if (c == '.' || c == '!' || c == '?') {
                var end = i
                while (end + 1 < buf.length && buf[end + 1] in "\"'”’)") end++
                if (end + 1 < buf.length && buf[end + 1].isWhitespace()) {
                    val sentence = buf.substring(0, end + 1)
                    if (c != '.' || sentence.trim().length >= 12) return end
                }
                i = end
            }
            i++
        }
        return -1
    }

    private fun emit(raw: String) {
        var text = raw.lines().joinToString(" ") { it.trim() }.trim()
        if (first) text = text.replace(Regex("^(Partner|Lingo)\\s*:\\s*"), "")
        text = text.trim().trim('"', '“', '”').trim()
        if (text.isEmpty()) return
        first = false
        onSentence(text)
    }
}
