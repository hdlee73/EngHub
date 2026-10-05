package com.hdlee73.englishstudy.reading

/**
 * One article a day from The Daily Upside (thedailyupside.com), read like a "reader mode": the list of the newest articles comes from
 * the site's feed or front page, and the article page is reduced to its title and body paragraphs. The text is shown on the learner's
 * own phone for study only and always comes with the source name and a link to the original. Everything here is plain string
 * handling, free of Android code, so it can be tested on a plain JVM.
 */
internal object DailyUpsideSource {
    const val CREDIT = "The Daily Upside"
    const val FEED = "https://www.thedailyupside.com/feed/"
    const val HOME = "https://www.thedailyupside.com/"

    private val sections = mapOf(
        "economics" to "경제", "finance" to "금융", "technology" to "기술", "industries" to "산업", "investments" to "투자"
    )
    private val notArticle = setOf("page", "feed", "amp", "tag", "author", "category")
    private val absoluteLink = Regex(
        "https?://(?:www\\.)?thedailyupside\\.com/(economics|finance|technology|industries|investments)/([a-z0-9-]+)/([a-z0-9][a-z0-9-]*)/?",
        RegexOption.IGNORE_CASE
    )
    private val relativeLink = Regex(
        "href=[\"']/(economics|finance|technology|industries|investments)/([a-z0-9-]+)/([a-z0-9][a-z0-9-]*)/?[\"']",
        RegexOption.IGNORE_CASE
    )

    /** Article addresses found in a feed or page, newest (first seen) first, each once, in one fixed spelling. */
    fun articleLinks(markup: String): List<String> {
        val found = LinkedHashSet<String>()
        fun add(section: String, sub: String, slug: String) {
            if (slug.lowercase() in notArticle || sub.lowercase() in notArticle) return
            found += "https://www.thedailyupside.com/${section.lowercase()}/${sub.lowercase()}/${slug.lowercase()}/"
        }
        // Keep the order of appearance across both kinds of link.
        val hits = ArrayList<Pair<Int, Triple<String, String, String>>>()
        absoluteLink.findAll(markup).forEach { hits += it.range.first to Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        relativeLink.findAll(markup).forEach { hits += it.range.first to Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        hits.sortedBy { it.first }.forEach { add(it.second.first, it.second.second, it.second.third) }
        return found.toList()
    }

    /** The Korean name of the section an article address belongs to ("경제"…), or "뉴스". */
    fun topicOf(url: String): String {
        val section = Regex("thedailyupside\\.com/([a-z]+)/", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)?.lowercase()
        return sections[section] ?: "뉴스"
    }

    private val suffix = Regex("\\s*[|–—-]\\s*The Daily Upside\\s*$", RegexOption.IGNORE_CASE)

    /** The headline of an article page. */
    fun title(html: String): String? {
        val og = Regex("<meta[^>]+property=[\"']og:title[\"'][^>]*?content=([\"'])(.*?)\\1", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(2)
            ?: Regex("<meta[^>]+content=([\"'])(.*?)\\1[^>]*?property=[\"']og:title[\"']", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(2)
        val h1 = Regex("<h1[^>]*>(.*?)</h1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)?.let { stripTags(it) }
        val raw = og ?: h1 ?: Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)
        return raw?.let { decode(stripTags(it)).replace(suffix, "").trim() }?.takeIf { it.isNotEmpty() }
    }

    private val dropBlocks = Regex(
        "<(script|style|noscript|svg|form|nav|footer|aside|header|iframe|button|figure)\\b.*?</\\1>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val paragraphTag = Regex("<p\\b[^>]*>(.*?)</p>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val startsLikeBoilerplate = Regex(
        "^(sign up|subscribe|share|follow|read more|related|advertisement|sponsored|get the|join |tap |click )", RegexOption.IGNORE_CASE
    )
    private val containsBoilerplate = Regex("all rights reserved|©|unsubscribe|privacy policy|terms of (use|service)|cookie", RegexOption.IGNORE_CASE)

    /** The body paragraphs of an article page: text only, without menus, sign-up boxes, captions or footers. */
    fun paragraphs(html: String): List<String> {
        val cleaned = dropBlocks.replace(html, " ")
        val start = cleaned.indexOf("<article", ignoreCase = true)
        val end = cleaned.lastIndexOf("</article>", ignoreCase = true)
        val fromArticle = if (start >= 0 && end > start) cleaned.substring(start, end) else null
        val inArticle = fromArticle?.let { collect(it) }.orEmpty()
        // A page without an <article> block (or with a very thin one) is read as a whole.
        return if (inArticle.size >= 3) inArticle else collect(cleaned)
    }

    private fun collect(markup: String): List<String> {
        val out = ArrayList<String>()
        for (match in paragraphTag.findAll(markup)) {
            val text = decode(stripTags(match.groupValues[1])).replace(Regex("\\s+"), " ").trim()
            if (text.split(' ').size < 6) continue
            if (startsLikeBoilerplate.containsMatchIn(text) || containsBoilerplate.containsMatchIn(text)) continue
            if (text.contains("newsletter", ignoreCase = true) && text.length < 180) continue
            if (out.lastOrNull() == text) continue
            out += text
        }
        return out
    }

    private fun stripTags(s: String) = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ").replace(Regex("<[^>]+>"), "")

    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—",
        "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”", "bull" to "•", "eacute" to "é"
    )

    /** Turns HTML character references (&amp;, &#8217;, &#x27;) into the characters they stand for. */
    fun decode(s: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(s) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            body.startsWith("#") -> body.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> named[body.lowercase()] ?: m.value
        }
    }

    /** True when the text is long enough to be worth a reading session. */
    fun isWorthReading(paragraphs: List<String>, minWords: Int = 150): Boolean =
        paragraphs.size >= 3 && paragraphs.sumOf { it.split(' ').size } >= minWords
}
