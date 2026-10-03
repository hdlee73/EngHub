package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class InflectedFormsTest {
    @Test fun identifiesDictionaryVerbForms() {
        assertEquals(InflectedMeaning("strike", "과거형·과거분사", "동사"), InflectedForms.find("1. simple past and past participle of strike"))
        assertEquals("과거형", InflectedForms.find("simple past tense of go.")?.form)
        assertEquals("과거분사", InflectedForms.find("past participle of eat.")?.form)
        assertEquals("현재분사·동명사", InflectedForms.find("present participle and gerund of run.")?.form)
        assertEquals("3인칭 단수 현재형", InflectedForms.find("third-person singular simple present indicative of teach.")?.form)
    }
    @Test fun handlesNounAndComparisonForms() {
        assertEquals("child", InflectedForms.find("plural of child")?.base)
        assertEquals("비교급", InflectedForms.find("comparative form of good")?.form)
        assertEquals("최상급", InflectedForms.find("superlative of fast")?.form)
    }
    @Test fun neverGuessesUnrelatedHeadwords() {
        assertNull(InflectedForms.find("To strike a surface."))
        assertNull(InflectedForms.find("A past event involving striking."))
        assertNull(InflectedForms.find("To dress well."))
    }
    @Test fun includesBaseGlossWithoutMachineTranslation() {
        val form = InflectedForms.find("past participle of eat")!!
        assertTrue(form.korean("1. 먹다").contains("eat의 과거분사"))
        assertTrue(form.korean("1. 먹다").contains("먹다"))
        assertEquals("[동사] eat의 과거분사", form.korean(null))
    }
    @Test fun misspellingsAndVariantSpellingsPointToTheirHeadword() {
        val misspelling = InflectedForms.find("1. Misspelling of curiosity.")!!
        assertEquals("curiosity", misspelling.base)
        assertEquals("curiosity의 잘못된 철자", misspelling.korean(null))
        assertEquals("colour", InflectedForms.find("Alternative spelling of colour")?.base)
        assertEquals("show", InflectedForms.find("Obsolete form of show.")?.base)
        assertNull(InflectedForms.find("Form of address used for kings."))
    }

    @Test fun reviewedStruckIncludesRealUsage() {
        val struck = ReviewedEntries.lookup("struck")!!
        assertTrue(struck.korean.contains("strike의 과거형·과거분사"))
        assertTrue(struck.examples.contains("The ball struck the window."))
    }
}
