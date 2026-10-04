package com.hdlee73.englishstudy.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyWordPickerTest {
    @Test fun phrasalVerbIsChosenWithItsInflection() {
        assertEquals("gave up", KeyWordPicker.pick("He gave up smoking last year."))
        assertEquals("looking forward to", KeyWordPicker.pick("I am looking forward to the weekend."))
        assertEquals("come up with", KeyWordPicker.pick("We need to come up with a better plan."))
    }

    @Test fun longestPhraseWins() {
        assertEquals("look forward to", KeyWordPicker.pick("We look forward to meeting you."))
    }

    @Test fun phrasalVerbBeatsAnyOtherWord() {
        assertEquals("called off", KeyWordPicker.pick("They called off the extraordinary meeting."))
    }

    @Test fun plainSentencePicksTheInformativeWord() {
        assertEquals("delicious", KeyWordPicker.pick("The food was very delicious."))
        assertEquals("umbrella", KeyWordPicker.pick("Don't forget your umbrella."))
    }

    @Test fun grammarWordsAndEverydayVerbsAreAvoided() {
        // "have", "want", "something" are never chosen even though they are the longest words around.
        assertEquals("coffee", KeyWordPicker.pick("I want something hot, like coffee."))
        assertEquals("tea", KeyWordPicker.pick("I like tea."))
    }

    @Test fun namesAreNotChosen() {
        assertEquals("expensive", KeyWordPicker.pick("Is Seoul expensive?"))
    }

    @Test fun nothingSuitableGivesNull() {
        assertNull(KeyWordPicker.pick("I am so sure."))
        assertNull(KeyWordPicker.pick(""))
        assertNull(KeyWordPicker.pick("123 456"))
    }

    @Test fun aPronounMayStandInsideAPhrasalVerb() {
        assertEquals("figure this out", KeyWordPicker.pick("Give me a minute to figure this out."))
        assertEquals("picked it up", KeyWordPicker.pick("She dropped the pen and he picked it up."))
    }

    @Test fun otherSeparatedWordsAreNotAPhrase() {
        // "give" and "up" are separated by more than a pronoun, so only a single word is chosen.
        assertEquals("difficult", KeyWordPicker.pick("Never give a very difficult task up."))
    }
}
