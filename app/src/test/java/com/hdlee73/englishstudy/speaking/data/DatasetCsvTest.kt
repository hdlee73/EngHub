package com.hdlee73.englishstudy.speaking.data

import com.hdlee73.englishstudy.speaking.model.SentencePair
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetCsvTest {
    private fun roundTrip(pairs: List<SentencePair>) =
        DatasetParser.parseCsv(ByteArrayInputStream(DatasetCsv.write(pairs).toByteArray(Charsets.UTF_8)))

    @Test fun pairsSurviveWritingAndParsing() {
        val pairs = listOf(
            SentencePair("나는 매일 사과를 먹는다.", "I eat an apple every day."),
            SentencePair("나는 사과, 바나나, 포도를 좋아한다.", "I like apples, bananas, and grapes."),
            SentencePair("그는 \"안녕\"이라고 말했다.", "He said \"hello\" to me.")
        )
        assertEquals(pairs, roundTrip(pairs))
    }

    @Test fun englishOnlySentencesKeepAnEmptyKorean() {
        val pairs = listOf(SentencePair("", "I eat an apple every day."), SentencePair("", "She likes tea, not coffee."))
        assertEquals(pairs, roundTrip(pairs))
    }

    @Test fun mixedRowsKeepTheirOrderAndLanguages() {
        val pairs = listOf(
            SentencePair("", "Where is the station?"),
            SentencePair("역이 어디예요?", "Where is the station, please?")
        )
        assertEquals(pairs, roundTrip(pairs))
    }

    @Test fun lineBreaksInsideAFieldBecomeSpaces() {
        val text = DatasetCsv.write(listOf(SentencePair("가\n나", "Hello\r\nthere world.")))
        assertEquals(1, text.trim().lines().size)
    }

    @Test fun everyRowEndsWithANewline() {
        assertTrue(DatasetCsv.write(listOf(SentencePair("가", "Hello there world."))).endsWith("\n"))
    }
}
