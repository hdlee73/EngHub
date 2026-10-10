package com.hdlee73.englishstudy.toeic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToeicBankTest {
    private val raw: String by lazy {
        listOf("src/main/assets/toeic_bank.txt", "app/src/main/assets/toeic_bank.txt").map(::File).first { it.exists() }.readText()
    }
    private val bank: List<ToeicQuestion> by lazy { ToeicBank.parse(raw) }

    @Test fun everyLineOfTheBankIsUsable() {
        val lines = raw.lines().count { it.isNotBlank() && !it.startsWith("#") }
        assertEquals("a line was skipped because it is malformed", lines, bank.size)
        assertTrue(bank.count { it.section == "R" } >= 50)
        assertTrue(bank.count { it.section == "L" } >= 50)
        assertEquals(bank.size, bank.map { it.id }.toSet().size)
        bank.forEach {
            assertTrue(it.id, it.question.isNotBlank() || it.part == "2")
            assertTrue(it.id, it.explanation.isNotBlank())
            assertEquals(it.id, it.options.size, it.options.toSet().size)
            if (it.isListening) assertTrue(it.id, it.audioLines.isNotEmpty())
            if (it.part == "5") assertTrue(it.id, it.question.contains("------"))
        }
    }

    @Test fun aDayHasFiveReadingAndFiveListeningQuestions() {
        val day = ToeicBank.today(20000, bank)
        assertEquals(10, day.size)
        assertEquals(5, day.count { it.section == "R" })
        assertEquals(5, day.count { it.section == "L" })
        assertEquals(day, ToeicBank.today(20000, bank))
        assertTrue(ToeicBank.today(20001, bank) != day)
    }

    @Test fun noQuestionRepeatsUntilTheBankIsUsedUp() {
        val reading = bank.filter { it.section == "R" }
        val seen = HashSet<String>()
        for (d in 0 until reading.size / 5) ToeicBank.pick(1000L + d, reading).forEach { assertTrue(it.id, seen.add(it.id)) }
    }

    @Test fun shufflingKeepsTheCorrectAnswerWithItsText() {
        for (q in bank) for (day in 0L..5L) {
            val s = ToeicBank.shuffled(q, day)
            assertEquals(q.options[q.answer], s.options[s.answer])
            assertEquals(q.options.toSet(), s.options.toSet())
        }
        assertFalse((0L..20L).all { d -> ToeicBank.shuffled(bank[0], d).options == bank[0].options })
    }

    @Test fun partTwoReadsTheAnswersAfterTheQuestion() {
        val q = bank.first { it.part == "2" }
        assertEquals(1 + q.options.size, q.audioLines.size)
        assertTrue(q.audioLines[1].startsWith("A. "))
    }
}
