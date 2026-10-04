package com.hdlee73.englishstudy.reading

import com.hdlee73.englishstudy.dictionary.MiniJson
import java.net.URLEncoder

/**
 * Real English news texts from Wikinews (CC BY 2.5), read through the public MediaWiki API: a list of the latest
 * published articles, then the plain text of single articles. Parsing and trimming are kept free of any Android
 * code so they can be tested on a plain JVM.
 */
internal object WikinewsSource {
    const val CREDIT = "Wikinews · CC BY 2.5"
    private const val API = "https://en.wikinews.org/w/api.php"

    fun titlesUrl(limit: Int = 100): String =
        "$API?action=query&list=categorymembers&cmtitle=Category:Published&cmnamespace=0&cmlimit=$limit&cmsort=timestamp&cmdir=desc&format=json&formatversion=2"

    fun extractUrl(title: String): String =
        "$API?action=query&prop=extracts&explaintext=1&redirects=1&titles=${URLEncoder.encode(title, "UTF-8")}&format=json&formatversion=2"

    fun pageUrl(title: String): String = "https://en.wikinews.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("%2F", "/").replace("%3A", ":")

    private fun root(json: String): Map<*, *>? =
        ((try { MiniJson.parse(json) } catch (_: IllegalArgumentException) { null }) as? Map<*, *>)?.get("query") as? Map<*, *>

    /** Titles of the listed articles, newest first. */
    fun parseTitles(json: String): List<String> =
        (root(json)?.get("categorymembers") as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("title") as? String }

    /** The title and plain text of the first page in an extracts answer. */
    fun parseExtract(json: String): Pair<String, String>? {
        val page = (root(json)?.get("pages") as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
        val title = page["title"] as? String ?: return null
        val extract = page["extract"] as? String ?: return null
        return title to extract
    }

    private val endHeading = Regex("^=+\\s*(sources?|related news|related|external links?|see also|references?|notes?)\\s*=+$", RegexOption.IGNORE_CASE)

    /** The body paragraphs of an extract: no headings, captions, source lists or reference marks. */
    fun paragraphs(extract: String): List<String> {
        val out = ArrayList<String>()
        for (raw in extract.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("=")) {
                if (endHeading.matches(line)) break
                continue
            }
            val clean = line.replace(Regex("\\[\\d+\\]"), "").replace(Regex("\\s+"), " ").trim()
            val words = clean.split(' ').size
            if (words < 9 || clean.lastOrNull() !in listOf('.', '!', '?', '"', '”', '’', '\'')) continue
            out += clean
        }
        return out
    }

    /**
     * About two thirds of the bundled texts' length: whole paragraphs until [minWords] is reached, cut at a sentence end
     * when that overshoots [maxWords]. Null when the article is too short to be worth reading.
     */
    fun trim(paragraphs: List<String>, minWords: Int = 110, targetWords: Int = 190, maxWords: Int = 260): List<String>? {
        val out = ArrayList<String>()
        var count = 0
        for (paragraph in paragraphs) {
            val words = paragraph.split(' ').size
            if (count + words > maxWords) {
                // Take as many whole sentences of this paragraph as fit.
                val kept = StringBuilder()
                var kw = 0
                for (sentence in Regex("(?<=[.!?])\\s+").split(paragraph)) {
                    val sw = sentence.split(' ').size
                    if (count + kw + sw > maxWords) break
                    if (kept.isNotEmpty()) kept.append(' ')
                    kept.append(sentence); kw += sw
                }
                if (kept.isNotEmpty()) { out += kept.toString(); count += kw }
                break
            }
            out += paragraph; count += words
            if (count >= targetWords) break
        }
        return if (count >= minWords) out else null
    }
}

/** An estimate of how hard a text is to read (Flesch–Kincaid grade level). */
internal object Readability {
    private fun syllables(word: String): Int {
        val w = word.lowercase().filter { it in 'a'..'z' }
        if (w.isEmpty()) return 0
        var count = 0
        var previousVowel = false
        for (c in w) {
            val vowel = c in "aeiouy"
            if (vowel && !previousVowel) count++
            previousVowel = vowel
        }
        if (w.endsWith("e") && !w.endsWith("le") && count > 1) count--
        return maxOf(1, count)
    }

    fun grade(text: String): Double {
        val words = Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(text).map { it.value }.toList()
        if (words.isEmpty()) return 0.0
        val sentences = maxOf(1, Regex("[.!?]+(\\s|$)").findAll(text).count())
        val syllableCount = words.sumOf { syllables(it) }
        return 0.39 * words.size / sentences + 11.8 * syllableCount / words.size - 15.59
    }
}

/** A key expression of a text with the sentence it comes from. */
data class KeyExpression(val expression: String, val sentence: String)

internal object KeyExpressions {
    /** Up to [max] expressions worth learning: phrases first, then single words, in the order they appear. */
    fun extract(paragraphs: List<String>, max: Int = 6): List<KeyExpression> {
        val seen = HashSet<String>()
        val found = ArrayList<KeyExpression>()
        for (paragraph in paragraphs) {
            for (sentence in Regex("(?<=[.!?])\\s+").split(paragraph)) {
                if (sentence.split(' ').size < 7) continue
                val picked = com.hdlee73.englishstudy.study.KeyWordPicker.pick(sentence) ?: continue
                if (seen.add(picked.lowercase())) found += KeyExpression(picked, sentence)
            }
        }
        val phrases = found.filter { it.expression.contains(' ') }
        val chosen = LinkedHashSet<KeyExpression>()
        chosen.addAll(phrases.take(max))
        // Spread the single words over the text instead of taking only the first ones.
        val words = found.filter { !it.expression.contains(' ') }
        val room = max - chosen.size
        if (room > 0 && words.isNotEmpty()) {
            val step = maxOf(1, words.size / room)
            var i = 0
            while (chosen.size < max && i < words.size) { chosen += words[i]; i += step }
        }
        return found.filter { it in chosen }
    }
}
