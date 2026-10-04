package com.hdlee73.englishstudy.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTranslationTest {
    @Test fun detectsTheDirectionFromTheScript() {
        assertEquals(Direction.KO_EN, TextTranslation.detect("안녕하세요", Direction.EN_KO))
        assertEquals(Direction.EN_KO, TextTranslation.detect("Hello there", Direction.KO_EN))
        assertEquals(Direction.KO_EN, TextTranslation.detect("나는 AI를 좋아해요 정말로", Direction.EN_KO))
        assertEquals(Direction.KO_EN, TextTranslation.detect("123 !!", Direction.KO_EN))
        assertEquals(Direction.EN_KO, TextTranslation.detect("", Direction.EN_KO))
    }

    @Test fun swapGoesBack() {
        assertEquals(Direction.KO_EN, Direction.EN_KO.swapped())
        assertEquals(Direction.EN_KO, Direction.KO_EN.swapped())
    }

    @Test fun urlCarriesLanguagesAndEncodedText() {
        val url = TextTranslation.url("a b&c", Direction.KO_EN)
        assertTrue(url.contains("sl=ko&tl=en"))
        assertTrue(url.endsWith("q=a+b%26c"))
    }

    @Test fun parseJoinsSegments() {
        val json = """[[["안녕하세요. ","Hello. ",null,null,1],["잘 지내요?","How are you?",null,null,1]],null,"en"]"""
        assertEquals("안녕하세요. 잘 지내요?", TextTranslation.parse(json))
        assertNull(TextTranslation.parse("[[],null]"))
        assertNull(TextTranslation.parse("not json"))
        assertNull(TextTranslation.parse("""[null]"""))
    }

    @Test fun piecesKeepLinesAndBlankLines() {
        val pieces = TextTranslation.pieces("One.\n\nTwo three.")
        assertEquals(3, pieces.size)
        assertEquals(listOf("One."), pieces[0])
        assertNull(pieces[1])
        assertEquals(listOf("Two three."), pieces[2])
    }
}
