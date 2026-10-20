package com.hdlee73.englishstudy.dictionary

import com.hdlee73.englishstudy.ai.GenAi
import com.hdlee73.englishstudy.translate.Direction
import com.hdlee73.englishstudy.translate.MlKitTranslator
import com.hdlee73.englishstudy.translate.OnlineTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a word means in one particular sentence: Korean and English meaning, a note on the nuance, and fitting examples. */
data class ContextMeaning(
    val word: String,
    val sentence: String,
    val korean: String,
    val english: String,
    val pos: String,
    val nuance: String,
    val sentenceKorean: String,
    /** English example and its Korean translation (the translation may be empty). */
    val examples: List<Pair<String, String>>,
    /** True when Gemini Nano wrote the meaning, false when it comes from the dictionary entry. */
    val usedModel: Boolean
)

/**
 * The "context vocabulary" of the Words tab (the idea of the LocaLingo app): the meaning of a word in the sentence it was
 * found in. Gemini Nano writes it when the device has it, with the dictionary entry as a hint; otherwise the dictionary's
 * own first meaning is shown together with a translation of the sentence.
 */
internal object ContextMeanings {
    internal data class Parsed(val korean: String, val english: String, val pos: String, val nuance: String, val examples: List<String>)

    private fun hasKorean(text: String) = text.any { it in '가'..'힣' }
    private fun firstSense(text: String): String =
        text.lines().map { it.replace(Regex("^\\s*\\d+[.)]\\s*"), "").trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

    fun buildPrompt(word: String, sentence: String, entry: WordEntry?): String = buildString {
        append("You are an English vocabulary tutor for a Korean adult learner.\n")
        append("Word: \"").append(word.trim()).append("\"\n")
        append("Sentence: ").append(if (sentence.isBlank() || sentence.trim().equals(word.trim(), ignoreCase = true)) "(none; explain the most common meaning)" else "\"" + sentence.trim() + "\"").append('\n')
        if (entry != null) {
            val hints = listOf(firstSense(entry.korean), firstSense(entry.english)).filter { it.isNotEmpty() }
            if (hints.isNotEmpty()) append("Dictionary hints (may be incomplete): ").append(hints.joinToString(" / ")).append('\n')
        }
        append("\nExplain what the word means in this sentence. Reply with ONLY one JSON object and no other text:\n")
        append("{\"ko\":\"짧은 한국어 뜻\",\"en\":\"one short English definition for this sentence\",")
        append("\"pos\":\"noun, verb, adjective, adverb or phrase\",\"nuance\":\"한국어로 한두 문장 뉘앙스 설명\",")
        append("\"examples\":[\"English example 1\",\"English example 2\"]}\n")
        append("Rules: ko and nuance must be written in Korean. examples are 2 natural English sentences that use the word with the same meaning, each under 15 words.")
    }

    /** The JSON object in a model answer (it may come inside a code fence or after a remark), or null when it has no usable Korean meaning. */
    fun parse(raw: String): Parsed? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val map = (try { MiniJson.parse(raw.substring(start, end + 1)) } catch (_: IllegalArgumentException) { null }) as? Map<*, *> ?: return null
        fun text(key: String) = (map[key] as? String)?.trim().orEmpty()
        val korean = text("ko")
        if (korean.isEmpty() || !hasKorean(korean)) return null
        val examples = (map["examples"] as? List<*>).orEmpty()
            .mapNotNull { (it as? String)?.trim() }.filter { SentenceExamples.isSentence(it) }.take(3)
        return Parsed(korean, text("en"), text("pos"), text("nuance").takeIf { hasKorean(it) }.orEmpty(), examples)
    }

    private val online = OnlineTranslator()

    /** Korean translation of [text]: the online service first, ML Kit on the device second, empty when both fail. */
    private suspend fun toKorean(text: String): String =
        online.translate(text, Direction.EN_KO) ?: MlKitTranslator.translate(text, Direction.EN_KO).orEmpty()

    /** [sentence] as an example sentence of the word: capitalised and ended with a full stop, or null when it is just the word itself. */
    fun asExample(word: String, sentence: String): String? {
        val t = sentence.trim()
        if (t.isEmpty() || t.equals(word.trim(), ignoreCase = true)) return null
        val ended = if (t.last() in ".!?") t else "$t."
        return ended.takeIf { SentenceExamples.isSentence(it) }
    }

    /**
     * [knownKorean] is the Korean translation of [sentence] when it is already known (an example of the dictionary entry), so it
     * is not translated again. A [sentence] that is only the word itself gets no sentence translation.
     */
    suspend fun create(word: String, sentence: String, entry: WordEntry?, knownKorean: String? = null): ContextMeaning = withContext(Dispatchers.IO) {
        val given = sentence.trim()
        val parsed = if (GenAi.canChat()) GenAi.generate(buildPrompt(word, given, entry))?.let(::parse) else null
        val sentenceKorean = when {
            !knownKorean.isNullOrBlank() -> knownKorean
            given.isEmpty() || given.equals(word.trim(), ignoreCase = true) -> ""
            else -> toKorean(given)
        }
        if (parsed != null) {
            return@withContext ContextMeaning(
                word, given, parsed.korean, parsed.english, parsed.pos, parsed.nuance, sentenceKorean,
                parsed.examples.map { it to toKorean(it) }, usedModel = true
            )
        }
        val fallbackExamples = entry?.let { examplePairs(it.examples).take(2) }.orEmpty()
        ContextMeaning(
            word, given, entry?.let { firstSense(it.korean) }.orEmpty(), entry?.let { firstSense(it.english) }.orEmpty(), "", "",
            sentenceKorean, fallbackExamples, usedModel = false
        )
    }

    /** The saved copy of [entry] with the meaning in this sentence on top (unnumbered, so the numbered dictionary senses stay as they are). */
    fun mergeInto(entry: WordEntry, meaning: ContextMeaning): WordEntry {
        if (meaning.korean.isBlank()) return entry
        val note = if (meaning.nuance.isBlank()) "" else " (${meaning.nuance})"
        val korean = "[이 문장에서] ${meaning.korean}$note\n${entry.korean}".trim()
        val english = if (meaning.english.isBlank()) entry.english else "[in this sentence] ${meaning.english}\n${entry.english}".trim()
        val own = buildList {
            asExample(meaning.word, meaning.sentence)?.let { add(it to meaning.sentenceKorean) }
            if (meaning.usedModel) addAll(meaning.examples)
        }.joinToString("\n") { (en, ko) -> "$en\t$ko" }
        val examples = listOf(own, entry.examples).filter { it.isNotBlank() }.joinToString("\n")
        return entry.copy(korean = korean, english = english, examples = examples)
    }
}
