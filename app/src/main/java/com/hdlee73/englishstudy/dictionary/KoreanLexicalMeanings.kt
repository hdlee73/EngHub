package com.hdlee73.englishstudy.dictionary

internal data class KoreanDictionarySense(val partOfSpeech: String, val translations: List<String>)

/** Lexical translations are dictionary data, not machine-translated English definitions. */
internal object KoreanLexicalMeanings {
    private val parts = mapOf("noun" to "명사", "verb" to "동사", "adjective" to "형용사",
        "adj" to "형용사", "adverb" to "부사", "adv" to "부사", "pronoun" to "대명사",
        "preposition" to "전치사", "conjunction" to "접속사", "interjection" to "감탄사",
        "article" to "관사", "phrase" to "숙어", "proper noun" to "고유명사")
    fun format(senses: List<KoreanDictionarySense>): String {
        val rows = senses.mapNotNull { sense ->
            val words = sense.translations.map { it.trim() }
                .filter { word -> word.any { it in '가'..'힣' } }.distinct()
            if (words.isEmpty()) null else {
                val part = parts[sense.partOfSpeech.lowercase()]
                (if (part != null) "[$part] " else "") + words.take(4).joinToString(", ")
            }
        }.distinct().take(StudyMeanings.MAX_SENSES)
        return rows.mapIndexed { i, row -> (i + 1).toString() + ". " + row }.joinToString("\n")
    }
}
