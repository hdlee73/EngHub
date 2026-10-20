package com.hdlee73.englishstudy.roleplay

import com.hdlee73.englishstudy.ai.FreeTalk
import com.hdlee73.englishstudy.ai.RuleEngine
import com.hdlee73.englishstudy.ai.SmallTalk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FreeTalkTest {

    private fun line(user: Boolean, text: String) = FreeTalk.Line(user, text)

    @Test
    fun repliesToTopicWithFollowUpQuestion() {
        val r = SmallTalk.reply(listOf(line(false, "Hi! What's on your mind today?"), line(true, "I ate pizza for dinner")), Random(1))
        assertTrue(r, r.endsWith("?"))
    }

    @Test
    fun answersQuestionsAboutItself() {
        val r = SmallTalk.reply(listOf(line(true, "How are you?")), Random(2))
        assertTrue(r, r.contains("doing great"))
    }

    @Test
    fun doesNotRepeatQuestionsWhileFreshOnesRemain() {
        val history = mutableListOf(line(true, "I love coffee"))
        val asked = mutableSetOf<String>()
        repeat(3) {
            val r = SmallTalk.reply(history, Random(it))
            assertTrue("repeated: $r", asked.add(r))
            history += line(false, r)
            history += line(true, "I drink coffee every morning")
        }
    }

    @Test
    fun sayGoodbye() {
        assertTrue(SmallTalk.reply(listOf(line(true, "ok bye")), Random(3)).contains("See you"))
    }

    @Test
    fun echoSwapsPronouns() {
        assertEquals("Oh, you went to Busan with my friends? That's interesting!".replace("my", "your"),
            SmallTalk.echo("I went to Busan with my friends."))
    }

    @Test
    fun suggestionIgnoresCaseAndPunctuation() {
        assertFalse(FreeTalk.meaningfullyDifferent("i like it", "I like it."))
        assertTrue(FreeTalk.meaningfullyDifferent("he don't know", "he doesn't know"))
    }

    @Test
    fun modelReplyIsCleaned() {
        assertEquals("That sounds fun! What did you eat?",
            FreeTalk.cleanModelReply("Lingo: That sounds fun!\nWhat did you eat?\nLearner: pizza"))
    }

    @Test
    fun promptEndsWithPartnerTurn() {
        val p = FreeTalk.buildPrompt(listOf(line(true, "hello")))
        assertTrue(p.contains("Learner: hello"))
        assertTrue(p.endsWith("Lingo:"))
    }

    @Test
    fun roleplayNowIsNotNo() {
        val s = Scenarios.ALL.first()
        assertFalse(Scenarios.reply(s, 0, "I need it now").startsWith("No problem"))
        assertTrue(Scenarios.reply(s, 0, "No, thanks").startsWith("No problem"))
    }

    @Test
    fun roleplayFinishes() {
        val s = Scenarios.ALL.first()
        assertFalse(Scenarios.isFinished(s, s.prompts.size - 1))
        assertTrue(Scenarios.isFinished(s, s.prompts.size))
    }

    @Test
    fun punctuationOnlyChangesAreNotListed() {
        assertTrue(RuleEngine.findCorrections("i like it", "I like it.").isEmpty())
        assertNotEquals(0, RuleEngine.findCorrections("he have a car", "he has a car").size)
    }
}
