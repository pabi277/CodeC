package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectScaffold
import com.codeci.ide.ui.projects.WelcomeStarters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 33.1 — the first-run starter tiles. Three tiles only (C / Python /
 * HTML); each maps to a project type + entry file that [ProjectScaffold]
 * already scaffolds, so a tile tap reuses the wizard's starter bytes and RUN ▶
 * then behaves exactly as it does for any project of that type.
 */
class WelcomeStartersTest {

    @Test
    fun `three starters only, with the expected ids`() {
        assertEquals(listOf("c", "python", "web"), WelcomeStarters.starters.map { it.id })
    }

    @Test
    fun `every starter has a title, subtitle, and a valid project name`() {
        for (starter in WelcomeStarters.starters) {
            assertTrue("blank title on ${starter.id}", starter.title.isNotBlank())
            assertTrue("blank subtitle on ${starter.id}", starter.subtitle.isNotBlank())
            assertTrue("blank project name on ${starter.id}", starter.projectName.isNotBlank())
        }
    }

    @Test
    fun `project names are unique so a tap never collides`() {
        val names = WelcomeStarters.starters.map { it.projectName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `every starter entry file is what the wizard scaffolds for that type`() {
        for (starter in WelcomeStarters.starters) {
            val scaffolded = ProjectScaffold.filesFor(starter.projectType).map { it.relativePath }
            assertTrue(
                "entry ${starter.entryFile} missing from scaffold of type ${starter.projectType}",
                scaffolded.contains(starter.entryFile),
            )
        }
    }

    @Test
    fun `byId round-trips each starter`() {
        for (starter in WelcomeStarters.starters) {
            assertEquals(starter, WelcomeStarters.byId(starter.id))
        }
        assertNotNull(WelcomeStarters.byId("c"))
    }
}
