package com.hdlee73.englishstudy.dictionary

/**
 * One dictionary entry. [examples] holds one example per line as `English sentence<TAB>Korean translation`
 * (the translation may be empty); [korean] and [english] hold numbered senses.
 */
data class WordEntry(val id: Long = 0, val word: String, val ipa: String, val korean: String, val english: String, val examples: String, val source: String = "")
