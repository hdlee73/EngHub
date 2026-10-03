package com.hdlee73.englishstudy.dictionary

/** Reject single words and dictionary fragments; preserve complete example sentences. */
internal object SentenceExamples {
    fun isSentence(text: String): Boolean {
        val clean = text.trim().trimEnd('"', '\u201d', '\u2019', '\'')
        return clean.lastOrNull() in listOf('.', '!', '?') &&
            Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(clean).count() >= 3
    }
    private val archaic = Regex("\\b(thou|thee|thy|thine|hath|doth|dost|hast|ere|whilst|wherein|whereof|unto|'tis|'twas|o'er|ne'er)\\b", RegexOption.IGNORE_CASE)

    /**
     * A sentence a learner can read at a glance: one plain line of at most 16 words, no quotation
     * fragments ("[...]", verse slashes), old letters (ſ) or archaic words. Dictionary quotations
     * from old books fail this; everyday sentences pass.
     */
    fun isSimple(text: String): Boolean {
        val line = text.trim()
        if (!isSentence(line) || line.length > 90 || !line.first().isUpperCase()) return false
        if (Regex("[\\[\\]/|{}<>_*@#]|\\.\\.|…").containsMatchIn(line)) return false
        if (line.any { it.isLetter() && it.code > 0x24F }) return false
        if (line.any { it in 'ſ'..'ſ' } || archaic.containsMatchIn(line)) return false
        if (Regex("\\b[A-Z]{4,}\\b").containsMatchIn(line)) return false
        return Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(line).count() in 3..16
    }

    /** Keep real example sentences only; older versions stored a placeholder sentence. */
    fun clean(stored: String): String =
        stored.lines().filter { line ->
            val english = line.substringBefore('\t')
            isSentence(english) && !(english.startsWith("I heard “") && english.endsWith("in a conversation today."))
        }.joinToString("\n")
}
