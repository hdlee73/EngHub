package com.hdlee73.englishstudy.study

import java.util.Locale

/**
 * Chooses the part of an English sentence that is worth learning and therefore worth blanking in a quiz:
 * a phrasal verb or fixed expression when the sentence contains one ("gave up", "looking forward to"),
 * otherwise the most informative single word. Grammar words and everyday filler (be, have, very, thing…)
 * are never chosen, and neither are names, numbers or contractions.
 */
internal object KeyWordPicker {
    /** The chosen text exactly as it is written in [sentence], or null when nothing suitable is in it. */
    fun pick(sentence: String): String? {
        val tokens = tokenize(sentence)
        if (tokens.isEmpty()) return null
        phrase(sentence, tokens)?.let { return it }
        return word(sentence, tokens)
    }

    private class Token(val text: String, val start: Int, val end: Int) {
        val lower: String = text.lowercase(Locale.ROOT)
    }

    private val tokenRegex = Regex("[A-Za-z]+(?:['’][A-Za-z]+)?")

    private fun tokenize(sentence: String): List<Token> =
        tokenRegex.findAll(sentence).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()

    // ---- phrases ----

    private class Phrase(val rest: List<String>, val size: Int)

    private val phrasesByFirstForm: Map<String, List<Phrase>> by lazy {
        val map = HashMap<String, MutableList<Phrase>>()
        for (text in PHRASES.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val words = text.split(" ")
            val phrase = Phrase(words.drop(1), words.size)
            for (form in Blanker.variants(words.first())) map.getOrPut(form) { ArrayList() } += phrase
        }
        map.values.forEach { list -> list.sortByDescending { it.size } }
        map
    }

    private fun phrase(sentence: String, tokens: List<Token>): String? {
        for (i in tokens.indices) {
            val candidates = phrasesByFirstForm[tokens[i].lower] ?: continue
            for (phrase in candidates) {
                // A short phrasal verb may be split by a pronoun: "figure this out", "pick it up".
                val gaps = if (phrase.size == 2) listOf(0, 1) else listOf(0)
                for (gap in gaps) {
                    val lastIndex = i + gap + phrase.rest.size
                    if (lastIndex >= tokens.size) continue
                    if (gap == 1 && tokens[i + 1].lower !in PRONOUNS) continue
                    val matches = phrase.rest.indices.all { tokens[i + gap + 1 + it].lower == phrase.rest[it] }
                    val last = tokens[lastIndex]
                    // The words must be neighbours, separated by spaces only.
                    if (matches && sentence.substring(tokens[i].start, last.end).all { it.isLetter() || it == ' ' }) {
                        return sentence.substring(tokens[i].start, last.end)
                    }
                }
            }
        }
        return null
    }

    // ---- single words ----

    private fun word(sentence: String, tokens: List<Token>): String? {
        var best: Token? = null
        var bestScore = Int.MIN_VALUE
        for ((index, token) in tokens.withIndex()) {
            val w = token.lower
            if (w.length < 3 || '\'' in w || '’' in w || w in STOP) continue
            // A capital letter inside a sentence marks a name ("Seoul"); "I" is already a stop word.
            if (index > 0 && token.text[0].isUpperCase()) continue
            val score = minOf(w.length, 10) - (if (w in COMMON) 4 else 0)
            if (score < 1) continue
            if (score > bestScore) { best = token; bestScore = score }
        }
        return best?.text
    }

    private val STOP: Set<String> = (
        "the and but for nor yet not are was were been being has had have having does did doing done will would shall should " +
        "can could may might must his her him she he they them their theirs our ours you your yours its it's this that these those " +
        "who whom whose which what when where why how with without within into onto over under about above below between among " +
        "from than then there here also too very quite just only even still ever never always often again once some any each every " +
        "all both either neither such own same other another more most less least much many few little off out down upon per via " +
        "get got let lets say says said like want wants need needs make makes made take takes took come comes came goes going went gone " +
        "one two three four five six seven eight nine ten first second third mine myself yourself himself herself itself ourselves " +
        "because while until unless although though since before after during against toward towards across along around " +
        "yes please thanks thank sorry maybe perhaps really actually something anything nothing everything someone anyone everyone " +
        "isn't aren't wasn't weren't don't doesn't didn't won't can't couldn't wouldn't shouldn't i'm i've i'll i'd you're we're they're"
        ).split(" ").toSet()

    /** Everyday words that carry little to learn; they are chosen only when nothing better is in the sentence. */
    private val COMMON: Set<String> = (
        "know knew think thought see saw look looked find found give gave use used work worked call called try tried ask asked feel felt " +
        "leave left put mean meant keep kept begin began seem seemed help helped show showed hear heard play played run ran move moved " +
        "live lived believe bring brought happen happened write wrote sit sat stand stood lose lost pay paid meet met include continue " +
        "set learn change lead understand watch follow stop create speak spoke read spend spent grow open walk win offer remember love " +
        "consider appear buy bought wait serve die send sent expect build built stay fall cut reach remain tell told talk talked " +
        "good great new old big small long little right wrong high low next last early late young important able sure real best better " +
        "thing things time times people year years way ways day days man men woman women child children world life hand part place case " +
        "week company system program question number night point home water room mother area money story fact month lot study book eye " +
        "job word business issue side kind head house service friend father power hour game line end member law car city name team " +
        "minute idea kid body information back parent face level office door health person art war history party result morning reason " +
        "girl guy moment air teacher force education today tomorrow yesterday now soon later well sure fine nice free full whole"
        ).split(" ").toSet()

    private val PRONOUNS = setOf("it", "this", "that", "them", "him", "her", "me", "us", "you", "these", "those")

    private const val PHRASES =
        "look forward to, come up with, get along with, run out of, put up with, catch up with, keep up with, get away with, look up to, " +
        "cut down on, make up for, face up to, drop out of, watch out for, look out for, get rid of, take care of, give up, put off, " +
        "take off, look after, figure out, get along, break down, turn down, set up, carry on, call off, hold on, pick up, show up, " +
        "work out, keep away, keep up, come across, get over, get through, make up, turn up, bring up, break up, look for, look up, " +
        "look into, look out, find out, check out, check in, give in, give away, give back, go on, go out, go over, go through, go back, " +
        "go ahead, come back, come in, come out, come on, come over, come along, come down, get up, get on, get off, get in, get out, " +
        "get back, get by, get together, take up, take over, take out, take on, take in, take back, take down, put on, put out, put away, " +
        "put down, put back, put together, turn on, turn off, turn out, turn around, turn into, turn over, set off, set out, set down, " +
        "sit down, stand up, stand out, stand by, wake up, shut up, shut down, clean up, clear up, cheer up, cool down, calm down, " +
        "slow down, speed up, grow up, hang up, hang out, hand in, hand out, hurry up, join in, keep on, let down, let go, log in, " +
        "log out, move on, move in, move out, pass out, pass away, pay back, pay off, point out, pull out, pull over, run away, " +
        "run into, run over, run down, run out, send out, show off, sign up, sign in, sleep in, throw away, throw out, throw up, " +
        "try on, try out, use up, wash up, wear out, write down, fill in, fill out, fall down, fall apart, fall behind, end up, " +
        "drop by, drop off, cut off, cut out, back up, bring back, bring out, bring in, blow up, break in, break out, burn out, " +
        "call back, call up, care for, deal with, rely on, depend on, apply for, believe in, belong to, " +
        "consist of, result in, hear from"
}
