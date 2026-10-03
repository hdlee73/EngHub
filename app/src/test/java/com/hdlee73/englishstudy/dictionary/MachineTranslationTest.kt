package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class MachineTranslationTest {
    // Shape of translate.googleapis.com/translate_a/single?client=gtx&dt=t&dt=bd responses.
    private val wordResponse = """[[["호기심","curiosity",null,null,10]],[["noun",["호기심","궁금증","진기한 물건","호기심","골동품"],[["호기심",["curiosity","inquisitiveness"],null,0.5]],"curiosity",1],["adjective",["curious"],null,"curiosity",2]],"en",null,null,null,1,[],[["en"],null,[1],["en"]]]"""
    private val sentenceResponse = """[[["개가 집으로 ","The dog ran home.",null,null,10],["달려갔다.",null,null,null,10]],null,"en",null,null,null,1,[]]"""

    @Test fun jsonReaderHandlesNestedArraysNullsAndEscapes() {
        val value = MiniJson.parse("""[[["a\n\"b\" 가",null,1.5,true]],{"k":[]},"x"]""") as List<*>
        val inner = (value[0] as List<*>)[0] as List<*>
        assertEquals("a\n\"b\" 가", inner[0])
        assertNull(inner[1])
        assertEquals(1.5, inner[2])
        assertEquals(true, inner[3])
        assertEquals(mapOf("k" to emptyList<Any?>()), value[1])
    }

    @Test fun jsonReaderRejectsGarbage() {
        listOf("", "[1,", "{\"a\" 1}", "[1] x", "nul").forEach { bad ->
            try { MiniJson.parse(bad); fail("accepted: $bad") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun dictionaryResponseBecomesNumberedSensesWithPartsOfSpeech() {
        val meaning = MachineTranslation.meaning(wordResponse, "curiosity")!!
        assertTrue(meaning.fromDictionary)
        assertEquals("1. [명사] 호기심, 궁금증, 진기한 물건, 골동품", meaning.text)
    }

    @Test fun senseWithoutKoreanIsSkippedAndListIsCapped() {
        val rows = (1..9).joinToString(",") { """["noun",["뜻$it"],null,"w",1]""" }
        val json = """[[["뜻","w"]],[["verb",["abc"],null,"w",1],$rows]]"""
        val meaning = MachineTranslation.meaning(json, "w")!!
        assertEquals(StudyMeanings.MAX_SENSES, meaning.text.lines().size)
        assertFalse(meaning.text.contains("abc"))
    }

    @Test fun phraseWithoutDictionaryFallsBackToPlainTranslation() {
        val json = """[[["~의 앞에","in front of",null,null,10]],null,"en"]"""
        val meaning = MachineTranslation.meaning(json, "in front of")!!
        assertFalse(meaning.fromDictionary)
        assertEquals("1. ~의 앞에", meaning.text)
    }

    @Test fun untranslatedEchoIsRejected() {
        assertNull(MachineTranslation.meaning("""[[["xyzzy","xyzzy"]],null,"en"]""", "xyzzy"))
        assertNull(MachineTranslation.sentence("""[[["hello there","hello there"]]]""", "hello there"))
        assertNull(MachineTranslation.sentence("not json"))
    }

    @Test fun sentenceSegmentsAreJoined() {
        assertEquals("개가 집으로 달려갔다.", MachineTranslation.sentence(sentenceResponse, "The dog ran home."))
    }

    @Test fun requestUrlEncodesTheQueryAndOnlyAsksForTheDictionaryWhenNeeded() {
        val word = MachineTranslation.url("take care of", true)
        assertTrue(word.contains("dt=bd") && word.endsWith("q=take+care+of"))
        val sentence = MachineTranslation.url("It's 5 o'clock & late.", false)
        assertFalse(sentence.contains("dt=bd"))
        assertTrue(sentence.endsWith("q=It%27s+5+o%27clock+%26+late."))
    }
}
