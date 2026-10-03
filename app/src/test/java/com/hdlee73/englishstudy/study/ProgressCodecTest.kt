package com.hdlee73.englishstudy.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressCodecTest {
    @Test fun roundTrips() {
        val map = linkedMapOf(
            "apple" to WordProgress(box = 2, seen = 5, known = 3, missed = 2, quizCorrect = 4, quizWrong = 1, lastResult = true, lastStudied = 1_700_000_000_000L),
            "give up" to WordProgress(lastResult = false, lastStudied = 9L),
            "new" to WordProgress()
        )
        assertEquals(map, ProgressCodec.decode(ProgressCodec.encode(map)))
    }

    @Test fun emptyTextDecodesToNothing() {
        assertTrue(ProgressCodec.decode("").isEmpty())
        assertEquals("", ProgressCodec.encode(emptyMap()))
    }

    @Test fun malformedLinesAreSkipped() {
        val text = "apple\t1\t2\t1\t1\t0\t0\t1\t5\n" +
            "broken line\n" +
            "x\ta\tb\tc\td\te\tf\t1\t5\n" +
            "\t1\t2\t1\t1\t0\t0\t1\t5\n" +
            "pear\t0\t0\t0\t0\t0\t0\t-\t0"
        assertEquals(listOf("apple", "pear"), ProgressCodec.decode(text).keys.toList())
        assertEquals(null, ProgressCodec.decode(text)["pear"]?.lastResult)
    }

    @Test fun anOutOfRangeBoxIsClamped() {
        val p = ProgressCodec.decode("apple\t99\t1\t1\t0\t0\t0\t1\t0")["apple"]!!
        assertEquals(Leitner.MASTERED_BOX, p.box)
    }
}
