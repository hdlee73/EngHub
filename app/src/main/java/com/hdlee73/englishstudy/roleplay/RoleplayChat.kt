package com.hdlee73.englishstudy.roleplay

import com.hdlee73.englishstudy.ai.FreeTalk
import com.hdlee73.englishstudy.ai.GenAi
import com.hdlee73.englishstudy.ai.SmallTalk

/**
 * The partner's answers. With Gemini Nano on the device the partner plays the theme's role freely; without it 프리토킹 uses the
 * offline conversation engine and the few themes with a script follow it. A theme with neither cannot be practised.
 */
object RoleplayChat {
    class Reply(val text: String, val usedModel: Boolean, val finished: Boolean = false)

    /** True when [theme] can be practised on this device (call after [GenAi.initialize]). */
    fun available(theme: RoleplayTheme): Boolean = theme.free || theme.script != null || GenAi.canChat()

    /** The partner's first line. */
    fun opener(theme: RoleplayTheme): String =
        if (theme.free) FreeTalk.opener() else theme.script?.opener ?: theme.opener

    /**
     * The next line of the partner. [userTurns] is how many times the learner has spoken so far, including the latest turn
     * that is the last entry of [history]; it moves a fixed script forward.
     */
    suspend fun reply(theme: RoleplayTheme, history: List<FreeTalk.Line>, userTurns: Int, onSentence: ((String) -> Unit)? = null): Reply {
        if (theme.free) {
            if (!GenAi.canChat()) return FreeTalk.reply(history).let { Reply(it.text, it.usedModel) }
            generateText(FreeTalk.buildPrompt(history), onSentence)?.let { FreeTalk.cleanModelReply(it) }?.takeIf { it.isNotBlank() }?.let { return Reply(it, true) }
            return Reply(SmallTalk.reply(history), false)
        }
        if (GenAi.canChat()) {
            generateText(buildPrompt(theme, history), onSentence)?.let { clean(it) }?.takeIf { it.isNotBlank() }?.let { return Reply(it, true) }
        }
        val script = theme.script ?: return Reply("Sorry, I did not catch that. Could you say it again?", false)
        val said = history.lastOrNull { it.fromUser }?.text.orEmpty()
        val turn = (userTurns - 1).coerceAtLeast(0)
        return Reply(Scenarios.reply(script, turn, said), false, finished = Scenarios.isFinished(script, turn + 1))
    }

    /** The model's text; with [onSentence] each finished sentence is handed over while the rest is still being written. */
    private suspend fun generateText(prompt: String, onSentence: ((String) -> Unit)?): String? {
        if (onSentence == null) return GenAi.generate(prompt)
        val streamer = SentenceStreamer(onSentence)
        val full = GenAi.generateStream(prompt) { streamer.feed(it) }
        streamer.finish()
        return full
    }

    internal fun buildPrompt(theme: RoleplayTheme, history: List<FreeTalk.Line>): String = buildString {
        append("You are playing a role in an English speaking practice for a Korean adult learner.\n")
        append("Scene: ").append(theme.title.ifBlank { theme.id }).append(". Your role: ").append(theme.persona).append(".\n")
        append("The learner's goal in this scene: ").append(theme.goal).append(".\n")
        append("Stay in character. Reply in 1 to 2 short sentences of simple, natural spoken English, ")
        append("then ask one question or give one prompt that moves the scene forward. ")
        append("Never use Korean, never use lists or emojis, never describe actions in brackets, never correct the learner's grammar.\n\n")
        for (line in history.takeLast(12)) {
            append(if (line.fromUser) "Learner: " else "Partner: ").append(line.text.trim()).append('\n')
        }
        append("Partner:")
    }

    internal fun clean(raw: String): String =
        raw.trim().removePrefix("Partner:").trim()
            .substringBefore("\nLearner:").substringBefore("Learner:")
            .lines().joinToString(" ") { it.trim() }.trim().trim('"')
}
