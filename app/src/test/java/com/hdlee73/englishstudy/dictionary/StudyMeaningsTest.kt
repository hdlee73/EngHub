package com.hdlee73.englishstudy.dictionary

import org.junit.Assert.*
import org.junit.Test

class StudyMeaningsTest {
    @Test fun limitsNumberedSensesToTheDisplayLimit() {
        val original = (1..8).joinToString("\n") { "$it. 뜻 $it" }
        assertEquals((1..StudyMeanings.MAX_SENSES).joinToString("\n") { "$it. 뜻 $it" }, StudyMeanings.limit(original))
    }
    @Test fun preservesPartOfSpeechSynonymsAndSenseOrder() {
        val text = "[형용사]\n1. 즉흥적인, 계획하지 않은\n2. 자발적인\n3. 자연 발생적인"
        assertEquals(text, StudyMeanings.limit(text))
    }
    @Test fun excludesContinuationOfOmittedSenses() {
        val kept = (1..StudyMeanings.MAX_SENSES).joinToString("\n") { "$it. kept $it\ncontinuation $it" }
        val text = kept + "\n${StudyMeanings.MAX_SENSES + 1}. omitted\ncontinuation of omitted"
        assertEquals(kept, StudyMeanings.limit(text))
    }
    @Test fun normalizesBothLanguagesBeforeSaveAndExport() {
        val e = WordEntry(word="test", ipa="", korean=(1..8).joinToString("\n") { "$it. 의미" },
            english=(1..8).joinToString("\n") { "$it. meaning" }, examples="This is an example.\t예문")
        assertEquals(StudyMeanings.MAX_SENSES, e.studyVersion().korean.lines().size)
        assertEquals(StudyMeanings.MAX_SENSES, e.studyVersion().english.lines().size)
        assertEquals(e.examples, e.studyVersion().examples)
    }
}
