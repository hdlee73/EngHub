package com.hdlee73.englishstudy.study

/**
 * Word lists often write the pronunciation next to the word ("remnant [rémnənt]"). The card should show the word and its
 * pronunciation separately, and the voice must read only the word.
 */
internal object WordIpa {
    private val trailing = Regex("^(.*?)\\s*(\\[[^\\]]+\\]|/[^/]+/)\\s*$")
    private val bracketed = Regex("\\s*\\[[^\\]]*\\]")
    private val slashed = Regex("\\s*/[^/]*[ˈˌəɪʊɔæɑθðʃʒŋɹː][^/]*/")

    /** The word and its pronunciation (with brackets, or empty) from a list entry. */
    fun split(raw: String): Pair<String, String> {
        val text = raw.trim()
        val m = trailing.matchEntire(text) ?: return text to ""
        val word = m.groupValues[1].trim()
        return if (word.isEmpty()) text to "" else word to m.groupValues[2]
    }

    /** [text] without any pronunciation notation, ready to be read aloud. */
    fun speakable(text: String): String = text.replace(bracketed, "").replace(slashed, "").replace(Regex("\\s+"), " ").trim()
}
