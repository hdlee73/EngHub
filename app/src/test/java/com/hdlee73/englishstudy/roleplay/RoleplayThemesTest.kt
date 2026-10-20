package com.hdlee73.englishstudy.roleplay

import com.hdlee73.englishstudy.ai.FreeTalk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayThemesTest {
    @Test
    fun thirtyScenesAndFreeTalkFirst() {
        assertEquals(30, RoleplayThemes.SCENES.size)
        assertEquals("free", RoleplayThemes.ALL.first().id)
        assertTrue(RoleplayThemes.ALL.first().free)
        assertEquals(31, RoleplayThemes.ALL.size)
    }

    @Test
    fun idsAndTitlesAreUniqueAndComplete() {
        assertEquals(RoleplayThemes.ALL.size, RoleplayThemes.ALL.map { it.id }.toSet().size)
        assertEquals(RoleplayThemes.ALL.size, RoleplayThemes.ALL.map { it.title }.toSet().size)
        for (scene in RoleplayThemes.SCENES) {
            assertTrue(scene.id, scene.title.isNotBlank() && scene.goal.isNotBlank() && scene.persona.isNotBlank() && scene.opener.isNotBlank())
        }
    }

    @Test
    fun everyScriptedScenarioBelongsToATheme() {
        for (script in Scenarios.ALL) {
            assertNotNull(script.title, RoleplayThemes.SCENES.firstOrNull { it.title == script.title })
        }
        assertEquals(Scenarios.ALL.size, RoleplayThemes.SCENES.count { it.script != null })
    }

    @Test
    fun promptHasRoleGoalAndLastTurn() {
        val theme = RoleplayThemes.SCENES.first { it.id == "taxi" }
        val prompt = RoleplayChat.buildPrompt(theme, listOf(FreeTalk.Line(false, theme.opener), FreeTalk.Line(true, "To the airport, please.")))
        assertTrue(prompt.contains("a talkative taxi driver"))
        assertTrue(prompt.contains(theme.goal))
        assertTrue(prompt.contains("Learner: To the airport, please."))
        assertTrue(prompt.endsWith("Partner:"))
    }

    @Test
    fun modelAnswerIsCleaned() {
        assertEquals("Sure! Which terminal?", RoleplayChat.clean("Partner: Sure!\nWhich terminal?\nLearner: Terminal 2"))
    }
}
