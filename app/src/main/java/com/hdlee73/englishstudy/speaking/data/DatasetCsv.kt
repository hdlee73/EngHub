package com.hdlee73.englishstudy.speaking.data

import com.hdlee73.englishstudy.speaking.model.SentencePair

/** Writes sentence pairs in the two-column CSV layout that [DatasetParser.parseCsv] reads back. */
object DatasetCsv {
    /** One `Korean,English` row per line; fields containing a comma or a quote are quoted. */
    fun write(pairs: List<SentencePair>): String =
        pairs.joinToString(separator = "\n", postfix = "\n") { "${field(it.korean)},${field(it.english)}" }

    private fun field(value: String): String {
        val clean = value.replace('\r', ' ').replace('\n', ' ').trim()
        return if (clean.contains(',') || clean.contains('"')) "\"" + clean.replace("\"", "\"\"") + "\"" else clean
    }
}
