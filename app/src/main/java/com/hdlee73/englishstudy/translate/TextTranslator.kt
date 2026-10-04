package com.hdlee73.englishstudy.translate

import com.hdlee73.englishstudy.dictionary.MiniJson
import com.hdlee73.englishstudy.reading.TextChunks
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Which way a text is translated. */
enum class Direction(val from: String, val to: String, val fromLabel: String, val toLabel: String) {
    EN_KO("en", "ko", "영어", "한국어"),
    KO_EN("ko", "en", "한국어", "영어");

    fun swapped() = if (this == EN_KO) KO_EN else EN_KO
}

/** Pure helpers of the translation tab (testable without a device). */
internal object TextTranslation {
    const val MAX_CHARS = 5000

    private fun isHangul(c: Char) = c in '가'..'힣' || c in 'ㄱ'..'ㅣ'
    private fun isLatin(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

    /** The direction that fits the text: Korean-only text goes to English, English-only text to Korean; otherwise [current]. */
    fun detect(text: String, current: Direction): Direction {
        val hangul = text.count { isHangul(it) }
        val latin = text.count { isLatin(it) }
        return when {
            hangul > 0 && latin == 0 -> Direction.KO_EN
            latin > 0 && hangul == 0 -> Direction.EN_KO
            hangul > latin * 2 -> Direction.KO_EN
            latin > hangul * 2 -> Direction.EN_KO
            else -> current
        }
    }

    fun url(text: String, direction: Direction): String =
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=${direction.from}&tl=${direction.to}&dt=t&q=" +
            URLEncoder.encode(text, "UTF-8")

    /** The translated text of a service response, or null when it has none. */
    fun parse(json: String): String? {
        val root = (try { MiniJson.parse(json) } catch (_: IllegalArgumentException) { null }) as? List<*> ?: return null
        val segments = root.firstOrNull() as? List<*> ?: return null
        val text = segments.mapNotNull { (it as? List<*>)?.firstOrNull() as? String }.joinToString("")
        return text.trim().takeIf { it.isNotEmpty() }
    }

    /** Pieces to send one by one: each line separately (blank lines are kept as null), long lines cut at sentence ends. */
    fun pieces(text: String): List<List<String>?> =
        text.trim().lines().map { line -> if (line.isBlank()) null else TextChunks.split(line.trim(), 600) }
}

/** English ↔ Korean translation through the online service the dictionary already uses. */
internal class OnlineTranslator {
    /** The translation, or null when the service gave no usable answer. Blocks; call off the main thread. */
    fun translate(text: String, direction: Direction): String? {
        val lines = ArrayList<String>()
        for (chunks in TextTranslation.pieces(text.take(TextTranslation.MAX_CHARS))) {
            if (chunks == null) { lines += ""; continue }
            val parts = ArrayList<String>()
            for (chunk in chunks) {
                val json = get(TextTranslation.url(chunk, direction)) ?: return null
                parts += TextTranslation.parse(json) ?: return null
            }
            lines += parts.joinToString(" ")
        }
        return lines.joinToString("\n").trim().takeIf { it.isNotEmpty() }
    }

    private fun get(address: String): String? {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 9000
            connection.setRequestProperty("User-Agent", "EnglishStudy/1.0 (Android)")
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
