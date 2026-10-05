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
    /**
     * Up to [max] expressions worth learning for an intermediate-to-advanced reader: idioms and phrasal verbs first,
     * then less common words (those not among the everyday words in [common]), spread over the text, in the order they appear.
     * Basic everyday phrases ("go back", "get up") and common words are left out.
     */
    fun extract(paragraphs: List<String>, max: Int = 8, common: Set<String> = emptySet()): List<KeyExpression> {
        val seen = HashSet<String>()
        val phrases = ArrayList<Pair<Int, KeyExpression>>()
        val words = ArrayList<Pair<Int, KeyExpression>>()
        var order = 0
        for (paragraph in paragraphs) {
            for (sentence in Regex("(?<=[.!?])\\s+").split(paragraph)) {
                val tokens = tokenize(sentence)
                if (tokens.size < 5) continue
                for (found in findPhrases(sentence, tokens)) {
                    if (seen.add(lemmaKey(found))) phrases += order++ to KeyExpression(found, sentence)
                }
                if (tokens.size < 7) continue
                hardWord(tokens, common, seen)?.let { word ->
                    seen.add(lemmaKey(word)); words += order++ to KeyExpression(word, sentence)
                }
            }
        }
        val chosen = ArrayList<Pair<Int, KeyExpression>>()
        chosen += phrases.take((max * 5 + 7) / 8)
        // Spread the words over the text instead of taking only the first ones.
        val room = max - chosen.size
        if (room > 0 && words.isNotEmpty()) {
            val step = maxOf(1, words.size / room)
            var i = 0
            while (chosen.size < max && i < words.size) { chosen += words[i]; i += step }
        }
        if (chosen.size < max) chosen += phrases.drop(chosen.count { it in phrases }).take(max - chosen.size)
        return chosen.distinctBy { it.second.expression.lowercase() }.sortedBy { it.first }.map { it.second }
    }

    private class Token(val text: String, val start: Int, val end: Int) {
        val lower: String = text.lowercase()
    }

    private val tokenRegex = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")
    private fun tokenize(sentence: String) = tokenRegex.findAll(sentence).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()

    private fun lemmaKey(text: String): String = text.lowercase().split(' ').joinToString(" ") { stem(it) }

    private fun stem(w: String): String = when {
        w.length > 5 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
        w.length > 4 && w.endsWith("es") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    // ---- idioms and phrasal verbs ----

    private class Phrase(val rest: List<String>, val size: Int)

    private val phrasesByFirstForm: Map<String, List<Phrase>> by lazy {
        val map = HashMap<String, MutableList<Phrase>>()
        for (text in (IDIOMS + "," + PHRASAL_VERBS).split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val parts = text.split(" ")
            val phrase = Phrase(parts.drop(1), parts.size)
            for (form in com.hdlee73.englishstudy.study.Blanker.variants(parts.first())) map.getOrPut(form) { ArrayList() } += phrase
        }
        map.values.forEach { list -> list.sortByDescending { it.size } }
        map
    }

    private val objectWords = setOf("it", "this", "that", "them", "him", "her", "me", "us", "you", "these", "those", "things", "plans", "costs", "prices")

    private fun findPhrases(sentence: String, tokens: List<Token>): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            var matchedEnd = -1
            val candidates = phrasesByFirstForm[tokens[i].lower]
            if (candidates != null) loop@ for (phrase in candidates) {
                // A phrasal verb of two words may be split by a short object: "rein it in", "phase them out".
                val gaps = if (phrase.size == 2) listOf(0, 1) else listOf(0)
                for (gap in gaps) {
                    val lastIndex = i + gap + phrase.rest.size
                    if (lastIndex >= tokens.size) continue
                    if (gap == 1 && tokens[i + 1].lower !in objectWords) continue
                    val matches = phrase.rest.indices.all { tokens[i + gap + 1 + it].lower == phrase.rest[it] }
                    if (matches && sentence.substring(tokens[i].start, tokens[lastIndex].end).all { it.isLetter() || it == ' ' || it == '\'' || it == '’' || it == '-' }) {
                        out += sentence.substring(tokens[i].start, tokens[lastIndex].end)
                        matchedEnd = lastIndex
                        break@loop
                    }
                }
            }
            i = if (matchedEnd >= 0) matchedEnd + 1 else i + 1
        }
        return out
    }

    // ---- less common single words ----

    private fun isCommon(w: String, common: Set<String>): Boolean {
        if (w in common || w in BASIC) return true
        val bases = buildList {
            add(stem(w))
            if (w.endsWith("ly")) add(w.dropLast(2))
            if (w.endsWith("ed") || w.endsWith("es")) add(w.dropLast(1))
            if (w.endsWith("ing")) add(w.dropLast(3) + "e")
            if (w.endsWith("ied")) add(w.dropLast(3) + "y")
        }
        return bases.any { it in common || it in BASIC }
    }

    /** The least common word of a sentence that is still a real word to learn: no names, numbers, contractions or everyday words. */
    private fun hardWord(tokens: List<Token>, common: Set<String>, seen: Set<String>): String? {
        var best: Token? = null
        var bestScore = Int.MIN_VALUE
        for ((index, token) in tokens.withIndex()) {
            val w = token.lower
            if (w.length < 5 || '\'' in w || '’' in w) continue
            if (index > 0 && token.text[0].isUpperCase()) continue
            if (isCommon(w, common) || lemmaKey(w) in seen) continue
            // Without a word-frequency list (tests), only long words count as hard.
            if (common.isEmpty() && w.length < 9) continue
            val score = minOf(w.length, 12) + if (LATINATE.any { w.endsWith(it) }) 2 else 0
            if (score > bestScore) { best = token; bestScore = score }
        }
        return best?.text
    }

    private val LATINATE = listOf("tion", "sion", "ment", "ance", "ence", "ity", "ous", "ive", "ize", "ise", "ate", "ible", "able", "ism", "ical")

    /** Everyday words kept out even when the frequency list is missing. */
    private val BASIC: Set<String> = (
        "about above after again against because before being below between could doing during every from further having here " +
        "itself other ourselves should their theirs themselves there these those through under until where which while would " +
        "people really thing things think years today little great right still never always"
        ).split(" ").toSet()

    /** Fixed expressions and idioms common in news and business writing. */
    private const val IDIOMS =
        "in the wake of, on the back of, at odds with, take a toll on, take its toll, come to a head, a far cry from, in the long run, " +
        "in the short run, keep tabs on, by and large, on the line, bear the brunt of, foot the bill, on the table, off the table, " +
        "behind the scenes, in the pipeline, up in the air, at stake, in the red, in the black, on track, off track, on the rise, " +
        "on the decline, on the horizon, in light of, in the face of, in favor of, in favour of, on behalf of, in terms of, " +
        "with regard to, by means of, at the expense of, for the sake of, in charge of, in the hope of, on the verge of, on the brink of, " +
        "at the heart of, at the forefront of, in the midst of, in the aftermath of, in response to, as a result of, in line with, " +
        "on par with, in tandem with, in keeping with, at a loss, at a premium, at the helm, on the fence, on the hook, on the ropes, " +
        "out of the woods, under the radar, under fire, under pressure, under scrutiny, under way, by the same token, for good, " +
        "for the time being, from scratch, in a nutshell, in the meantime, in the dark, in hot water, in the spotlight, in the loop, " +
        "out of the loop, on the same page, the bottom line, the tip of the iceberg, a drop in the bucket, a double-edged sword, " +
        "a wake-up call, a game changer, a level playing field, the elephant in the room, the lion's share, the last straw, " +
        "back to square one, back to the drawing board, against all odds, all in all, around the clock, a long shot, at the end of the day, " +
        "break even, break the ice, break new ground, call the shots, catch on, change hands, close the gap, cut corners, cut costs, " +
        "do the math, draw the line, foot the bill, gain ground, lose ground, get the ball rolling, give the green light, go the extra mile, " +
        "hit the ground running, hit the brakes, hold the line, jump on the bandwagon, keep an eye on, lay the groundwork, make ends meet, " +
        "make headway, make waves, miss the mark, move the needle, pave the way, play it safe, pull the plug, raise the bar, raise eyebrows, " +
        "read the room, rock the boat, set the stage, set the tone, shed light on, stand to gain, stay afloat, steer clear of, " +
        "take the lead, take center stage, take stock of, take advantage of, take into account, take for granted, think outside the box, " +
        "tighten the belt, turn the tide, turn a corner, weather the storm, make a splash, come under fire, come into play, come to terms with, " +
        "keep pace with, make sense of, fall short of, run the risk of, put pressure on, cast doubt on, lose sight of, get a handle on, " +
        "draw attention to, give rise to, lead the way, make the most of, on the other hand, as a matter of fact, by no means, " +
        "more often than not, sooner or later, little by little, time and again, once and for all, so to speak, to some extent, " +
        "to a large extent, in the first place, at first glance, at large, at will, in full swing, " +
        "head start, ballpark figure, rule of thumb, red tape, silver lining, track record, wild card, worst-case scenario, " +
        "trial and error, pros and cons, ups and downs, bread and butter, by the book, hands-on, on the ground, low-hanging fruit"

    /** Phrasal verbs above the everyday level (everyday ones such as "go back" or "get up" are left out on purpose). */
    private const val PHRASAL_VERBS =
        "rein in, roll out, roll back, ramp up, scale back, scale up, phase out, phase in, crack down on, crack down, step up, step down, " +
        "step in, wind down, wind up, spill over, shore up, iron out, weigh in, weigh on, factor in, bank on, cash in on, cash in, " +
        "buy into, buy out, sell off, sell out, splash out, shell out, fork out, pay off, pay out, pay down, pile up, prop up, " +
        "pull back, pull off, pull out of, pull through, push back, push for, push ahead, push through, put forward, put off, " +
        "put up with, put in place, rule out, rule on, run up against, run into, run out of, run up, sort out, single out, sound out, " +
        "spell out, stave off, stem from, stick to, stick with, strike down, swoop in, tap into, team up with, team up, tide over, " +
        "tip off, tone down, top up, trade off, trickle down, turn around, turn to, usher in, wade into, wear off, " +
        "weed out, win over, wipe out, wrap up, write off, zero in on, account for, act on, back down, back off, back out of, " +
        "bail out, bear out, beef up, black out, blow over, bog down, boil down to, bottom out, bounce back, branch out, " +
        "break into, break off, break through, bring about, bring forward, bring in, bring on, bring up, brush off, " +
        "build up, bump up, call for, call off, call out, carry out, carry over, catch up on, catch up with, cave in, " +
        "chip in, churn out, clamp down on, close down, come about, come across, come up against, come up with, come down to, " +
        "come forward, come through, cool off, count on, cover up, crop up, cut back on, cut back, cut down on, " +
        "dawn on, die down, die out, dig into, do away with, double down on, double down, drag on, draw on, draw up, drive up, " +
        "drive down, drop out of, drown out, dwell on, ease off, ease up, eat into, edge up, edge down, embark on, " +
        "even out, face up to, fall apart, fall back on, fall behind, fall through, fend off, fill in for, " +
        "fizzle out, flare up, follow through, follow up on, gear up for, get across, get around to, get away with, get by, " +
        "get over, get through, give in to, give up on, go ahead with, go through with, grapple with, hammer out, hand over, " +
        "hang on to, head off, hinge on, hold back, hold off on, hold off, hold out, hold up, hone in on, home in on, kick in, " +
        "kick off, knock on, lag behind, lash out, lay off, lay out, let up, level off, lock in, look into, " +
        "make up for, map out, mark down, mark up, measure up, mull over, narrow down, opt out of, opt for, own up to, " +
        "pan out, pass on, pass up, peter out, pin down, play down, play out, play up, plough ahead, plow ahead, point to, " +
        "pore over, rack up, reach out to, ride out, rope in, round up, set aside, set back, set off, set out, settle on, " +
        "shake off, shake up, shrug off, shut down, sit on, snap up, soak up, speak out, spin off, " +
        "spring up, square off, stack up, stand by, stand down, stand out, stand up for, start up, stay put, stir up, " +
        "take aback, take on, take over, talk down, think over, throw in, touch on, track down, " +
        "trigger off, turn out, warm up to, water down, while away"
}
