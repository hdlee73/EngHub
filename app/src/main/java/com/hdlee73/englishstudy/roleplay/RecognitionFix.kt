package com.hdlee73.englishstudy.roleplay

import com.hdlee73.englishstudy.ai.FreeTalk
import com.hdlee73.englishstudy.ai.GenAi

/**
 * Speech recognition often mishears a learner's pronunciation ("sheep" for "ship"). With Gemini Nano on the device the
 * conversation tells which word was meant, so a clearly misheard word is replaced. Only words are fixed: the learner's own
 * grammar and wording stay as they were said, and any answer that rewrites the sentence is thrown away.
 */
internal object RecognitionFix {
    fun buildPrompt(theme: RoleplayTheme, history: List<FreeTalk.Line>, heard: String, alternatives: List<String>): String = buildString {
        append("A speech recognizer wrote down what a Korean adult learner said in English. Because of the learner's pronunciation, ")
        append("a word may be misheard as a similar-sounding word.\n")
        if (!theme.free) append("Scene: ").append(theme.title.ifBlank { theme.id }).append(". Goal: ").append(theme.goal).append(".\n")
        append("Conversation so far:\n")
        for (line in history.takeLast(6)) append(if (line.fromUser) "Learner: " else "Partner: ").append(line.text.trim()).append('\n')
        append("\nThe learner's latest line as recognized: \"").append(heard.trim()).append("\"\n")
        val others = alternatives.map { it.trim() }.filter { it.isNotEmpty() && !it.equals(heard.trim(), ignoreCase = true) }.take(4)
        if (others.isNotEmpty()) append("Other possible readings: ").append(others.joinToString(" | ") { "\"$it\"" }).append('\n')
        append("\nChange only a word that is clearly a mishearing given the conversation. Keep the learner's own wording and grammar, ")
        append("do not improve or rephrase the sentence. If it already makes sense, repeat it unchanged. Reply with ONLY the sentence.")
    }

    private fun words(text: String) = Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(text.lowercase()).map { it.value }.toList()

    /** The model's answer as a fix of [heard], or null when it is empty, unchanged, in Korean or rewrites too much of the sentence. */
    fun accept(heard: String, raw: String): String? {
        val answer = raw.trim().lines().firstOrNull { it.isNotBlank() }.orEmpty()
            .replace(Regex("^(Corrected|Sentence|Answer|Learner)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
            .trim().trim('"', '“', '”')
        if (answer.isEmpty() || answer.any { it in '가'..'힣' }) return null
        val a = words(answer)
        val h = words(heard)
        if (a.isEmpty() || h.isEmpty() || a == h) return null
        if (kotlin.math.abs(a.size - h.size) > 1) return null
        // Most words must survive: a mishearing fix changes one or two words, not the whole sentence.
        val kept = h.count { it in a }
        if (kept * 2 < h.size || h.size - kept > 2) return null
        return answer
    }

    /** The fixed sentence, or null when nothing was changed (or Gemini Nano is not available). */
    suspend fun fix(theme: RoleplayTheme, history: List<FreeTalk.Line>, heard: String, alternatives: List<String>): String? {
        if (!GenAi.canChat() || heard.isBlank()) return null
        val raw = GenAi.generate(buildPrompt(theme, history, heard, alternatives)) ?: return null
        return accept(heard, raw)
    }
}
