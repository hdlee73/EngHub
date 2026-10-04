package com.hdlee73.englishstudy.study

import org.junit.Assert.assertEquals
import org.junit.Test

class WordIpaTest {
    @Test fun splitsTheWordFromItsPronunciation() {
        assertEquals("remnant" to "[rémnənt]", WordIpa.split("remnant [rémnənt]"))
        assertEquals("drill" to "/dɹɪl/", WordIpa.split("drill /dɹɪl/"))
        assertEquals("give up" to "", WordIpa.split("give up"))
        assertEquals("[only]" to "", WordIpa.split("[only]"))
    }

    @Test fun speakableDropsPronunciationSymbols() {
        assertEquals("remnant", WordIpa.speakable("remnant [rémnənt]"))
        assertEquals("drill", WordIpa.speakable("drill /dɹɪl/"))
        assertEquals("either and/or both", WordIpa.speakable("either and/or both"))
        assertEquals("The remnant of it.", WordIpa.speakable("The remnant [rémnənt] of it."))
    }
}
