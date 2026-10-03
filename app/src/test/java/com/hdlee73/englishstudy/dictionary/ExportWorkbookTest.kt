package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.assertTrue
import java.util.zip.ZipInputStream
import org.junit.Test

class ExportWorkbookTest {
    private val entry = WordEntry(word = "keep away", ipa = "", korean = "[구동사] 가까이 오지 않게 하다",
        english = "To prevent something from coming near.", examples = "Keep away from the fire.\t불 가까이에 가지 마세요.")

    private fun sheet(format: Int): String {
        ZipInputStream(ExportWorkbook.make(listOf(entry), format).inputStream()).use { zip ->
            while (true) {
                val item = zip.nextEntry ?: break
                if (item.name == "xl/worksheets/sheet1.xml") return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("sheet1.xml missing")
    }

    @Test fun wordListSheetHasWordMeaningAndBilingualExample() {
        val xml = sheet(1)
        assertTrue(xml.contains("keep away"))
        assertTrue(xml.contains("가까이 오지 않게 하다"))
        assertTrue(xml.contains("Keep away from the fire.&#10;불 가까이에 가지 마세요."))
    }

    @Test fun englishOnlySheetOmitsKorean() {
        val xml = sheet(3)
        assertTrue(xml.contains("Keep away from the fire."))
        assertTrue(!xml.contains("불 가까이에"))
    }
}
