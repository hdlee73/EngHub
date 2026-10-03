package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class WordFormsTest {
    private val dictionary = setOf("go", "study", "eat", "city", "stop", "teach", "take", "big", "happy", "hope",
        "hop", "visit", "watch", "house", "knife", "leaf", "be", "write", "lie", "agree", "nice", "hot", "travel",
        "try", "carry", "story", "baby", "bus", "potato", "wolf", "use", "us", "come", "com", "sing", "singe", "walk")
    private fun find(word: String) = WordForms.find(word) { it in dictionary }

    @Test fun irregularVerbsResolveToTheirBase() {
        assertEquals(BaseForm("go", "과거형"), find("went"))
        assertEquals(BaseForm("go", "과거분사"), find("gone"))
        assertEquals(BaseForm("eat", "과거형"), find("ate"))
        assertEquals(BaseForm("teach", "과거형·과거분사"), find("taught"))
        assertEquals(BaseForm("take", "과거형"), find("took"))
        assertEquals(BaseForm("write", "과거분사"), find("written"))
        assertEquals("be", find("was")?.base)
    }

    @Test fun regularInflectionsResolveOnlyToRealHeadwords() {
        assertEquals(BaseForm("study", "과거형·과거분사"), find("studied"))
        assertEquals("city", find("cities")?.base)
        assertEquals("stop", find("stopped")?.base)
        assertEquals("hope", find("hoped")?.base)
        assertEquals("hop", find("hopped")?.base)
        assertEquals("visit", find("visited")?.base)
        assertEquals("agree", find("agreed")?.base)
        assertEquals("use", find("using")?.base)
        assertEquals("come", find("coming")?.base)
        assertEquals("sing", find("singing")?.base)
        assertEquals("lie", find("lying")?.base)
        assertEquals("travel", find("travelled")?.base)
        assertEquals("watch", find("watches")?.base)
        assertEquals("house", find("houses")?.base)
        assertEquals("knife", find("knives")?.base)
        assertEquals("potato", find("potatoes")?.base)
        assertEquals(BaseForm("big", "비교급"), find("bigger"))
        assertEquals(BaseForm("happy", "비교급"), find("happier"))
        assertEquals(BaseForm("hot", "최상급"), find("hottest"))
        assertEquals(BaseForm("nice", "비교급"), find("nicer"))
    }

    @Test fun neverInventsAHeadword() {
        assertNull(find("curiousity"))
        assertNull(find("xyzzyed"))
        assertNull(find("bus"))
    }

    @Test fun koreanShowsFormThenBaseSenses() {
        val text = BaseForm("go", "과거형").korean("1. [동사] 가다.\n2. [동사] 떠나다.")
        assertTrue(text.startsWith("[변화형] go의 과거형"))
        assertTrue(text.contains("1. [동사] 가다."))
        assertTrue(BaseForm("dog", BaseForm.NOUN_OR_VERB_S).korean("1. [명사] 개.").contains("dog의 복수형"))
        assertTrue(BaseForm("walk", BaseForm.NOUN_OR_VERB_S).korean("1. [동사] 걷다.").contains("3인칭 단수 현재형"))
    }

    @Test fun headwordsThatAreAlsoIrregularFormsGetANote() {
        val saw = WordForms.irregular("saw")!!
        assertEquals("see", saw.base)
        assertTrue(saw.note("1. [동사] 보다.").contains("see의 과거형이기도 합니다 — [동사] 보다."))
        assertNull(WordForms.irregular("water"))
    }

    @Test fun pointerOnlyRowsExposeTheirBase() {
        assertEquals("ask", WordForms.pointerBase("1. [동사] ask의 과거형 및 과거분사형."))
        assertEquals("entity", WordForms.pointerBase("1. [명사] entity의 복수형."))
        // A row with a real sense of its own is left alone.
        assertNull(WordForms.pointerBase("1. [명사] mean의 복수형.\n2. [명사] 수단, 방법."))
    }

    @Test fun spellingSuggestionsComeFromOneEdit() {
        assertTrue("curiosity" in Spelling.edits1("curiousity"))
        assertTrue("receive" in Spelling.edits1("recieve"))
        assertTrue(Spelling.edits1("ab").isEmpty())
        assertEquals(listOf("receive", "relieve"),
            Spelling.rank("recieve", listOf("relieve" to "KOWIKTIONARY", "receive" to "KOWIKTIONARY")))
    }
}
