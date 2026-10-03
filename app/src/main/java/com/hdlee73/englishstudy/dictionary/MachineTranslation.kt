package com.hdlee73.englishstudy.dictionary

import java.net.URLEncoder

/**
 * Minimal JSON reader. Translation responses are nested arrays containing nulls, which are
 * awkward with org.json (null becomes JSONObject.NULL), and this keeps the parsing code
 * testable on a plain JVM. Returns List, Map, String, Double, Boolean or null.
 */
internal object MiniJson {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpace()
        require(reader.index == text.length) { "Unexpected trailing JSON at ${reader.index}" }
        return value
    }

    private class Reader(val s: String) {
        var index = 0
        fun skipSpace() { while (index < s.length && s[index].isWhitespace()) index++ }
        private fun fail(): Nothing = throw IllegalArgumentException("Invalid JSON at $index")
        fun value(): Any? {
            skipSpace()
            if (index >= s.length) fail()
            return when (val c = s[index]) {
                '[' -> list()
                '{' -> obj()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else fail()
            }
        }
        private fun literal(word: String, result: Any?): Any? {
            if (!s.startsWith(word, index)) fail()
            index += word.length
            return result
        }
        private fun number(): Double {
            val start = index
            while (index < s.length && (s[index].isDigit() || s[index] in "+-.eE")) index++
            return s.substring(start, index).toDoubleOrNull() ?: fail()
        }
        private fun list(): List<Any?> {
            index++
            val out = mutableListOf<Any?>()
            skipSpace()
            if (index < s.length && s[index] == ']') { index++; return out }
            while (true) {
                out += value()
                skipSpace()
                if (index >= s.length) fail()
                when (s[index++]) { ',' -> continue; ']' -> return out; else -> fail() }
            }
        }
        private fun obj(): Map<String, Any?> {
            index++
            val out = linkedMapOf<String, Any?>()
            skipSpace()
            if (index < s.length && s[index] == '}') { index++; return out }
            while (true) {
                skipSpace()
                if (index >= s.length || s[index] != '"') fail()
                val key = string()
                skipSpace()
                if (index >= s.length || s[index++] != ':') fail()
                out[key] = value()
                skipSpace()
                if (index >= s.length) fail()
                when (s[index++]) { ',' -> continue; '}' -> return out; else -> fail() }
            }
        }
        private fun string(): String {
            index++
            val out = StringBuilder()
            while (index < s.length) {
                val c = s[index++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (index >= s.length) fail()
                        when (val e = s[index++]) {
                            'n' -> out.append('\n'); 't' -> out.append('\t'); 'r' -> out.append('\r')
                            'b' -> out.append('\b'); 'f' -> out.append('\u000c')
                            'u' -> {
                                if (index + 4 > s.length) fail()
                                out.append(s.substring(index, index + 4).toIntOrNull(16)?.toChar() ?: fail())
                                index += 4
                            }
                            else -> out.append(e)
                        }
                    }
                    else -> out.append(c)
                }
            }
            fail()
        }
    }
}

internal data class MachineMeaning(val text: String, val fromDictionary: Boolean)

/**
 * Automatic English→Korean lookups used only when no curated dictionary has an answer: the
 * translate.googleapis.com "gtx" client returns a bilingual dictionary (dt=bd) for words and a
 * translation (dt=t) for words, phrases and sentences. Everything derived from it is labeled as
 * automatic translation in the UI.
 */
internal object MachineTranslation {
    const val CREDIT_MEANING = "한글 의미: 구글 번역 사전 (자동 번역)"
    const val CREDIT_SUPPLEMENT = "보충 뜻: 구글 번역 사전 (자동 번역)"
    const val CREDIT_EXAMPLES = "예문 해석: 구글 번역 (자동 번역)"

    private val parts = mapOf("noun" to "명사", "verb" to "동사", "adjective" to "형용사", "adverb" to "부사",
        "pronoun" to "대명사", "preposition" to "전치사", "conjunction" to "접속사", "interjection" to "감탄사",
        "article" to "관사", "abbreviation" to "약어", "determiner" to "한정사", "particle" to "불변화사",
        "numeral" to "수사", "auxiliary verb" to "조동사", "phrase" to "숙어", "prefix" to "접두사", "suffix" to "접미사")

    fun url(text: String, dictionary: Boolean): String =
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=ko&dt=t" +
            (if (dictionary) "&dt=bd" else "") + "&q=" + URLEncoder.encode(text, "UTF-8")

    private fun hasKorean(text: String) = text.any { it in '가'..'힣' }
    private fun parse(json: String): List<*>? = try { MiniJson.parse(json) as? List<*> } catch (_: IllegalArgumentException) { null }

    /** The translated sentence, or null when the response has no Korean text. */
    fun sentence(json: String, source: String = ""): String? {
        val segments = parse(json)?.firstOrNull() as? List<*> ?: return null
        val text = segments.mapNotNull { (it as? List<*>)?.firstOrNull() as? String }.joinToString("").trim()
        return text.takeIf { it.isNotBlank() && hasKorean(it) && !it.equals(source.trim(), true) }
    }

    /** Dictionary-style senses ("[명사] 호기심, 궁금증"), falling back to a plain translation. */
    fun meaning(json: String, query: String): MachineMeaning? {
        val root = parse(json) ?: return null
        val senses = (root.getOrNull(1) as? List<*>).orEmpty().mapNotNull { entry ->
            val row = entry as? List<*> ?: return@mapNotNull null
            val part = parts[(row.getOrNull(0) as? String).orEmpty().lowercase()]
            val words = (row.getOrNull(1) as? List<*>).orEmpty().mapNotNull { it as? String }
                .map { it.trim() }.filter { hasKorean(it) }.distinct().take(4)
            if (words.isEmpty()) null else (if (part != null) "[$part] " else "") + words.joinToString(", ")
        }.distinct().take(StudyMeanings.MAX_SENSES)
        if (senses.isNotEmpty()) {
            return MachineMeaning(senses.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"), true)
        }
        val translation = sentence(json, query) ?: return null
        return MachineMeaning("1. $translation", false)
    }
}
