package com.hdlee73.englishstudy.reading

import com.hdlee73.englishstudy.dictionary.MachineTranslation
import java.net.HttpURLConnection
import java.net.URL

/** English → Korean translation of reading paragraphs through the same online service the dictionary uses. */
internal class ReadingTranslator {
    /** The Korean text of [paragraph], or null when the service gave no usable answer. Blocks; call off the main thread. */
    fun translate(paragraph: String): String? {
        val parts = TextChunks.split(paragraph)
        val out = ArrayList<String>()
        for (part in parts) {
            val json = get(MachineTranslation.url(part, false)) ?: return null
            out += MachineTranslation.sentence(json, part) ?: return null
        }
        return out.joinToString(" ").takeIf { it.isNotBlank() }
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
        } catch (_: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
