package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class MeaningMergeTest {
    private val palmLocal = "1. [동사] 손 안에 감추다."

    @Test fun missingPartOfSpeechIsDetectedFromTheEnglishEntry() {
        assertEquals(setOf("명사"), MeaningMerge.missingParts(palmLocal, listOf("noun", "verb")))
        assertEquals(emptySet<String>(), MeaningMerge.missingParts("1. [명사] 책.\n2. [동사] 예약하다.", listOf("noun", "verb")))
        // No part-of-speech labels in the Korean list: nothing can be said to be missing.
        assertEquals(emptySet<String>(), MeaningMerge.missingParts("1. 슬프다, 우울하다.", listOf("adjective")))
        assertEquals(setOf("명사"), MeaningMerge.missingParts("1. [의존 명사] 권: 책을 세는 단위.".replace("명사", "동사"), listOf("noun")))
    }

    @Test fun extrasKeepOnlyWordsTheListDoesNotHave() {
        val online = "1. [명사] 손바닥, 야자나무\n2. [동사] 손 안에 감추다"
        assertEquals(listOf("[명사] 손바닥, 야자나무"), MeaningMerge.extras(online, null, palmLocal, 2))
        val covered = "1. [명사] 손바닥.\n2. [동사] 손 안에 감추다."
        assertEquals(listOf("[명사] 야자나무"), MeaningMerge.extras(online, null, covered, 2))
        assertEquals(emptyList<String>(), MeaningMerge.extras("1. [명사] 손바닥", null, covered, 2))
    }

    @Test fun extrasCanBeRestrictedToMissingPartsAndLimited() {
        val auto = "1. [명사] 손바닥, 야자수\n2. [동사] 슬쩍 감추다\n3. [명사] 종려"
        assertEquals(listOf("[명사] 손바닥, 야자수"), MeaningMerge.extras(auto, setOf("명사"), palmLocal, 1))
        assertEquals(emptyList<String>(), MeaningMerge.extras(auto, setOf("부사"), palmLocal, 2))
    }

    @Test fun combinedListFollowsTheEnglishPartOrderAndIsRenumbered() {
        val combined = MeaningMerge.combine(palmLocal, listOf("[명사] 손바닥, 야자나무"), listOf("noun", "verb"))
        assertEquals("1. [명사] 손바닥, 야자나무\n2. [동사] 손 안에 감추다.", combined)
    }

    @Test fun orderIsLeftAloneWhenThereIsNothingToReorder() {
        val verbs = "1. [동사] 가다.\n2. [동사] 떠나다."
        assertEquals(verbs, MeaningMerge.combine(verbs, emptyList(), listOf("verb", "noun")))
        assertEquals("1. [명사] 책.\n2. [동사] 예약하다.", MeaningMerge.combine("1. [명사] 책.\n2. [동사] 예약하다.", emptyList(), listOf("noun", "verb")))
    }

    @Test fun notesAndContinuationLinesSurvive() {
        val text = "※ want의 과거형이기도 합니다 — [동사] 바라다\n1. [형용사] 찾는\n(광고에서)\n2. [동사] 원하다"
        val combined = MeaningMerge.combine(text, emptyList(), listOf("verb", "adjective"))
        assertEquals("※ want의 과거형이기도 합니다 — [동사] 바라다\n1. [동사] 원하다\n2. [형용사] 찾는\n(광고에서)", combined)
    }

    @Test fun listIsCappedAtTheDisplayLimit() {
        val many = (1..5).joinToString("\n") { "$it. [명사] 뜻$it" }
        val extras = (6..9).map { "[명사] 뜻$it" }
        assertEquals(StudyMeanings.MAX_SENSES, MeaningMerge.combine(many, extras, emptyList()).lines().size)
    }

    @Test fun extrasCanBeLimitedToTheTopWordsAndPartsOfSpeech() {
        val source = "1. [명사] 야자, 손바닥, 손, 종려나무, 야자수\n2. [동사] 감추다"
        val got = MeaningMerge.extras(source, setOf("명사"), "1. [명사] 손바닥, 손뼉", 2, maxWords = 2, topWords = 3)
        assertEquals(listOf("[명사] 야자"), got)
        assertTrue(MeaningMerge.extras(source, setOf("형용사"), "", 2).isEmpty())
    }
}
