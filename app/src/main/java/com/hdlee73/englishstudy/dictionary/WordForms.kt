package com.hdlee73.englishstudy.dictionary

import java.util.Locale

/** An inflected search word resolved to a headword that exists in the bundled dictionary. */
internal data class BaseForm(val base: String, val form: String) {
    /** Korean meaning shown for the inflected word: a form note, then the base word's senses. */
    fun korean(baseGloss: String): String {
        val label = if (form == NOUN_OR_VERB_S) sFormLabel(baseGloss) else form
        return "[변화형] ${base}의 $label\n" + StudyMeanings.limit(baseGloss)
    }

    /** Short note appended when the word is also a headword of its own (saw, left, found). */
    fun note(baseGloss: String): String {
        val firstSense = baseGloss.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return "※ ${base}의 ${form}이기도 합니다" + if (firstSense.isEmpty()) "" else " — " + firstSense.replace(Regex("^\\d+[.)]\\s*"), "")
    }

    companion object {
        const val NOUN_OR_VERB_S = "-s"
        private fun sFormLabel(gloss: String): String {
            val noun = gloss.contains("명사]")
            val verb = gloss.contains("동사]")
            return when {
                noun && !verb -> "복수형"
                verb && !noun -> "3인칭 단수 현재형"
                else -> "복수형 / 3인칭 단수 현재형"
            }
        }
    }
}

/**
 * Finds the base headword of common inflected forms (went → go, studied → study,
 * cities → city, bigger → big). A candidate is accepted only when [exists] confirms it is
 * a real dictionary headword, so spelling rules never invent a meaning.
 */
internal object WordForms {
    private const val PAST = "과거형"
    private const val PARTICIPLE = "과거분사"
    private const val PAST_BOTH = "과거형·과거분사"
    private const val ING = "현재분사·동명사"
    private const val COMPARATIVE = "비교급"
    private const val SUPERLATIVE = "최상급"
    private const val PLURAL = "복수형"

    // base past past-participle; alternatives separated by '/'.
    private val irregularVerbs = """
        arise arose arisen|awake awoke awoken|bear bore borne/born|beat beat beaten|become became become
        begin began begun|bend bent bent|bet bet bet|bind bound bound|bite bit bitten|bleed bled bled
        blow blew blown|break broke broken|breed bred bred|bring brought brought|build built built
        burn burnt burnt|burst burst burst|buy bought bought|catch caught caught|choose chose chosen
        cling clung clung|come came come|cost cost cost|creep crept crept|cut cut cut|deal dealt dealt
        dig dug dug|do did done|draw drew drawn|dream dreamt dreamt|drink drank drunk|drive drove driven
        eat ate eaten|fall fell fallen|feed fed fed|feel felt felt|fight fought fought|find found found
        flee fled fled|fly flew flown|forbid forbade forbidden|forget forgot forgotten
        forgive forgave forgiven|freeze froze frozen|get got got/gotten|give gave given|go went gone
        grind ground ground|grow grew grown|hang hung hung|have had had|hear heard heard|hide hid hidden
        hit hit hit|hold held held|hurt hurt hurt|keep kept kept|kneel knelt knelt|know knew known
        lay laid laid|lead led led|lean leant leant|leap leapt leapt|learn learnt learnt|leave left left
        lend lent lent|let let let|lie lay lain|light lit lit|lose lost lost|make made made|mean meant meant
        meet met met|mistake mistook mistaken|overcome overcame overcome|pay paid paid|prove proved proven
        put put put|quit quit quit|read read read|ride rode ridden|ring rang rung|rise rose risen|run ran run
        say said said|see saw seen|seek sought sought|sell sold sold|send sent sent|set set set
        shake shook shaken|shine shone shone|shoot shot shot|show showed shown|shrink shrank shrunk
        shut shut shut|sing sang sung|sink sank sunk|sit sat sat|sleep slept slept|slide slid slid
        speak spoke spoken|speed sped sped|spend spent spent|spill spilt spilt|spin spun spun
        split split split|spread spread spread|spring sprang sprung|stand stood stood|steal stole stolen
        stick stuck stuck|sting stung stung|stink stank stunk|strike struck struck/stricken
        strive strove striven|swear swore sworn|sweep swept swept|swim swam swum|swing swung swung
        take took taken|teach taught taught|tear tore torn|tell told told|think thought thought
        throw threw thrown|undergo underwent undergone|understand understood understood
        undertake undertook undertaken|upset upset upset|wake woke woken|wear wore worn|weave wove woven
        weep wept wept|win won won|wind wound wound|withdraw withdrew withdrawn|write wrote written
        be was/were been
    """.trimIndent()

    private val irregular: Map<String, BaseForm> = buildMap {
        fun add(form: String, base: String, label: String) { if (form != base && form !in this) put(form, BaseForm(base, label)) }
        irregularVerbs.split('|', '\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { row ->
            val (base, past, participle) = row.split(Regex("\\s+"))
            val pasts = past.split('/')
            val participles = participle.split('/')
            pasts.forEach { p -> add(p, base, if (p in participles) PAST_BOTH else PAST) }
            participles.forEach { p -> add(p, base, PARTICIPLE) }
        }
        listOf("am", "is", "are").forEach { add(it, "be", "현재형") }
        add("has", "have", "3인칭 단수 현재형"); add("does", "do", "3인칭 단수 현재형"); add("goes", "go", "3인칭 단수 현재형")
        mapOf("men" to "man", "women" to "woman", "children" to "child", "feet" to "foot", "teeth" to "tooth",
            "mice" to "mouse", "geese" to "goose", "people" to "person", "oxen" to "ox", "lives" to "life",
            "knives" to "knife", "wives" to "wife", "leaves" to "leaf", "halves" to "half", "wolves" to "wolf",
            "shelves" to "shelf", "thieves" to "thief", "loaves" to "loaf", "selves" to "self", "calves" to "calf",
            "analyses" to "analysis", "crises" to "crisis", "theses" to "thesis", "hypotheses" to "hypothesis",
            "phenomena" to "phenomenon", "criteria" to "criterion")
            .forEach { (form, base) -> add(form, base, PLURAL) }
        mapOf("better" to "good", "worse" to "bad", "more" to "many", "less" to "little", "further" to "far",
            "farther" to "far", "elder" to "old").forEach { (form, base) -> add(form, base, COMPARATIVE) }
        mapOf("best" to "good", "worst" to "bad", "most" to "many", "least" to "little", "furthest" to "far",
            "farthest" to "far", "eldest" to "old").forEach { (form, base) -> add(form, base, SUPERLATIVE) }
    }

    /** An irregular form known regardless of whether the word is also a headword itself. */
    fun irregular(word: String): BaseForm? = irregular[normalize(word)]

    /**
     * Irregular inflections of [base] (went, gone for go). Comparatives and superlatives are left
     * out: more/most belong to "many", and blanking them would hide unrelated words in a quiz.
     */
    fun irregularFormsOf(base: String): List<String> {
        val b = normalize(base)
        return irregular.filter { (_, value) -> value.base == b && value.form != COMPARATIVE && value.form != SUPERLATIVE }.keys.toList()
    }

    private val pointer = Regex("^\\s*\\d+[.)]\\s*(?:\\[[^\\]]+\\]\\s*)?([a-z]+(?:[-'][a-z]+)*)의 (?:과거|복수|현재분사|동명사|비교급|최상급|3인칭)")

    /**
     * Some dictionary rows only say "ask의 과거형"; returns that base word when every sense is
     * such a pointer, so the base word's real senses can be shown underneath.
     */
    fun pointerBase(gloss: String): String? {
        val senses = gloss.lines().filter { it.isNotBlank() }
        val bases = senses.map { pointer.find(it)?.groupValues?.get(1) ?: return null }.distinct()
        return bases.singleOrNull()
    }

    fun find(word: String, exists: (String) -> Boolean): BaseForm? {
        val w = normalize(word)
        if (w.length < 3 || !w.all { it in 'a'..'z' }) return irregular[w]?.takeIf { exists(it.base) }
        irregular[w]?.let { if (exists(it.base)) return it }
        return candidates(w).firstOrNull { it.base != w && it.base.length >= 2 && exists(it.base) }
    }

    private fun normalize(word: String) = word.trim().lowercase(Locale.ROOT)

    private fun isVowel(c: Char) = c in "aeiou"

    /** Stem ends consonant-vowel-consonant (hop, us, writ): an 'e' was most likely dropped. */
    private fun needsE(stem: String): Boolean {
        if (stem.length < 2) return false
        val last = stem.last()
        val prev = stem[stem.length - 2]
        val beforeVowelOk = stem.length < 3 || !isVowel(stem[stem.length - 3])
        return !isVowel(last) && last !in "wxy" && isVowel(prev) && beforeVowelOk
    }

    /** stopp → stop, bigg → big, travell → travel. */
    private fun undouble(stem: String): String? =
        if (stem.length >= 3 && stem.last() == stem[stem.length - 2] && !isVowel(stem.last())) stem.dropLast(1) else null

    /** Ordered from most to least likely; the caller keeps the first real headword. */
    private fun candidates(w: String): List<BaseForm> = buildList {
        fun add(base: String?, form: String) { if (base != null && base.isNotEmpty()) add(BaseForm(base, form)) }
        when {
            w.endsWith("ies") && w.length > 4 -> add(w.dropLast(3) + "y", BaseForm.NOUN_OR_VERB_S)
            w.endsWith("ves") -> { add(w.dropLast(3) + "f", PLURAL); add(w.dropLast(3) + "fe", PLURAL); add(w.dropLast(1), BaseForm.NOUN_OR_VERB_S) }
            w.endsWith("es") && Regex("(?:s|x|z|ch|sh|o)es$").containsMatchIn(w) -> { add(w.dropLast(2), BaseForm.NOUN_OR_VERB_S); add(w.dropLast(1), BaseForm.NOUN_OR_VERB_S) }
            w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is") -> add(w.dropLast(1), BaseForm.NOUN_OR_VERB_S)
        }
        if (w.endsWith("ied") && w.length > 4) add(w.dropLast(3) + "y", PAST_BOTH)
        else if (w.endsWith("ed") && w.length > 3) {
            val stem = w.dropLast(2)
            add(w.dropLast(1), PAST_BOTH)          // liked → like, agreed → agree
            add(stem, PAST_BOTH)                   // walked → walk
            add(undouble(stem), PAST_BOTH)         // stopped → stop
        }
        if (w.endsWith("ying") && w.length >= 5) add(w.dropLast(4) + "ie", ING) // lying → lie
        if (w.endsWith("ing") && w.length > 4) {
            val stem = w.dropLast(3)
            if (needsE(stem)) { add(stem + "e", ING); add(stem, ING) } else { add(stem, ING); add(stem + "e", ING) }
            add(undouble(stem), ING)               // running → run
        }
        if (w.endsWith("iest") && w.length > 5) add(w.dropLast(4) + "y", SUPERLATIVE)
        else if (w.endsWith("est") && w.length > 4) {
            val stem = w.dropLast(3)
            add(w.dropLast(2), SUPERLATIVE); add(stem, SUPERLATIVE); add(undouble(stem), SUPERLATIVE)
        }
        if (w.endsWith("ier") && w.length > 4) add(w.dropLast(3) + "y", COMPARATIVE)
        else if (w.endsWith("er") && w.length > 3) {
            val stem = w.dropLast(2)
            add(w.dropLast(1), COMPARATIVE); add(stem, COMPARATIVE); add(undouble(stem), COMPARATIVE)
        }
    }
}

/** Offline "did you mean" candidates: every word one edit away from the query. */
internal object Spelling {
    private const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
    fun edits1(word: String): Set<String> {
        val w = word.trim().lowercase(Locale.ROOT)
        if (w.length < 3 || w.length > 24 || !w.all { it in 'a'..'z' }) return emptySet()
        val out = linkedSetOf<String>()
        for (i in 0..w.length) {
            val left = w.substring(0, i); val right = w.substring(i)
            if (right.isNotEmpty()) out += left + right.substring(1)
            if (right.length > 1) out += left + right[1] + right[0] + right.substring(2)
            for (c in LETTERS) {
                if (right.isNotEmpty()) out += left + c + right.substring(1)
                out += left + c + right
            }
        }
        out.remove(w)
        return out
    }

    /**
     * Order dictionary hits for a misspelling: swapped letters (recieve → receive) first, then
     * curated Korean-Wiktionary headwords, same first letter, and closest length.
     */
    fun rank(word: String, hits: List<Pair<String, String>>): List<String> {
        val w = word.trim().lowercase(Locale.ROOT)
        val letters = w.toList().sorted()
        return hits.sortedWith(compareBy(
            { it.first.toList().sorted() != letters },
            { it.second != "KOWIKTIONARY" },
            { it.first.firstOrNull() != w.firstOrNull() },
            { kotlin.math.abs(it.first.length - w.length) }
        )).map { it.first }.distinct()
    }
}
