package com.hdlee73.englishstudy.toeic

import java.util.Random

/**
 * One TOEIC-style practice question. [section] is "R" (reading) or "L" (listening), [part] the TOEIC part (5 or 7 for reading, 2, 3 or 4 for
 * listening). [passage] is the text to read (reading) or the script that is read aloud (listening); '//' marks a line break or a change of speaker.
 */
data class ToeicQuestion(
    val id: String,
    val section: String,
    val part: String,
    val passage: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
) {
    val isListening: Boolean get() = section == "L"

    /** The script as separate lines of speech, without the "M:" / "W:" speaker marks. */
    val scriptLines: List<String>
        get() = passage.split("//").map { it.trim().replace(Regex("^[MW]:\\s*"), "") }.filter { it.isNotEmpty() }

    /** What the speaker reads aloud for this question. In part 2 the three answers are read after the question, as in the test. */
    val audioLines: List<String>
        get() = if (part == "2") scriptLines + options.mapIndexed { i, o -> "${"ABCDE"[i]}. $o" } else scriptLines

    /** The passage with line breaks, for display. */
    val passageText: String get() = passage.split("//").joinToString("\n") { it.trim() }
}

/**
 * The bundled question bank (assets/toeic_bank.txt, original questions written for EngHub, not real TOEIC items) and the daily pick:
 * every day 5 reading and 5 listening questions, taken in file order so none repeats until the whole bank has been used.
 */
object ToeicBank {
    const val PER_SECTION = 5

    fun parse(text: String): List<ToeicQuestion> {
        var reading = 0
        var listening = 0
        val out = ArrayList<ToeicQuestion>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val f = line.split('|')
            if (f.size < 10) continue
            val section = f[0].trim()
            if (section != "R" && section != "L") continue
            val options = f.subList(4, 8).map { it.trim() }.filter { it.isNotEmpty() }
            val answer = "ABCD".indexOf(f[8].trim().uppercase().firstOrNull() ?: ' ')
            if (options.size < 2 || answer !in options.indices) continue
            val id = if (section == "R") "R${++reading}" else "L${++listening}"
            out += ToeicQuestion(id, section, f[1].trim(), f[2].trim(), f[3].trim(), options, answer, f[9].trim())
        }
        return out
    }

    /** [count] questions for the day with number [epochDay] (days since 1970), wrapping around the end of the bank. */
    fun pick(epochDay: Long, bank: List<ToeicQuestion>, count: Int = PER_SECTION): List<ToeicQuestion> {
        if (bank.isEmpty()) return emptyList()
        val start = Math.floorMod(epochDay * count, bank.size.toLong()).toInt()
        return (0 until minOf(count, bank.size)).map { bank[(start + it) % bank.size] }
    }

    /** The day's ten questions: the reading ones, then the listening ones, with the answer choices in a new order each day. */
    fun today(epochDay: Long, bank: List<ToeicQuestion>): List<ToeicQuestion> {
        val reading = pick(epochDay, bank.filter { it.section == "R" })
        val listening = pick(epochDay, bank.filter { it.section == "L" })
        return (reading + listening).map { shuffled(it, epochDay) }
    }

    /** The same question with its choices in an order that depends on the day, the correct answer following its text. */
    fun shuffled(q: ToeicQuestion, epochDay: Long): ToeicQuestion {
        if (q.options.size < 2) return q
        val order = q.options.indices.toMutableList()
        val random = Random(epochDay * 1_000_003L + q.id.hashCode())
        for (i in order.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        return q.copy(options = order.map { q.options[it] }, answer = order.indexOf(q.answer))
    }
}
