package com.codeci.ide

import com.codeci.ide.ui.models.TemplateProvider
import com.codeci.ide.ui.projects.GameArenaSample
import com.codeci.ide.ui.projects.ProjectNameProblem
import com.codeci.ide.ui.setup.SetupFlowPolicy
import com.codeci.ide.ui.setup.SetupPicks
import com.codeci.ide.ui.setup.SetupStart
import com.codeci.ide.ui.setup.SetupStep
import com.codeci.ide.ui.setup.SetupTheme
import com.codeci.ide.ui.setup.SetupTextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 97 — the setup flow's laws, pinned.
 *
 * The owner's brief was seven screens and a first project the user picks and
 * names (2026-10-08). The laws that make that safe to ship are here rather than
 * in the screen, because the screen cannot be host-tested and this can.
 */
class SetupFlowPolicyTest {

    // ---- the seven beats -------------------------------------------------

    @Test
    fun `the flow is the seven beats the owner asked for, in order`() {
        assertEquals(
            listOf(
                SetupStep.WELCOME,
                SetupStep.PICK,
                SetupStep.NAME,
                SetupStep.LOOKS,
                SetupStep.HELPS,
                SetupStep.BUILDING,
                SetupStep.READY,
            ),
            SetupFlowPolicy.steps,
        )
        assertEquals(SetupStep.NAME, SetupFlowPolicy.next(SetupStep.PICK))
        assertEquals(SetupStep.PICK, SetupFlowPolicy.previous(SetupStep.NAME))
        assertNull(SetupFlowPolicy.previous(SetupStep.WELCOME))
        assertNull(SetupFlowPolicy.next(SetupStep.READY))
    }

    @Test
    fun `the progress strip counts the six working beats and not the receipt`() {
        assertEquals(6, SetupFlowPolicy.PROGRESS_SEGMENTS)
        assertEquals(0, SetupFlowPolicy.progressIndex(SetupStep.WELCOME))
        assertEquals(5, SetupFlowPolicy.progressIndex(SetupStep.BUILDING))
        assertEquals(6, SetupFlowPolicy.progressIndex(SetupStep.READY))
    }

    // ---- law 1: skip is the app as it was ---------------------------------

    @Test
    fun `skip setup is exactly the legacy sample launch`() {
        val skip = SetupFlowPolicy.skipChoice()
        assertTrue(SetupFlowPolicy.isLegacySkip(skip))
        assertEquals(GameArenaSample.NAME, skip.projectName)
        assertEquals(GameArenaSample.NAME, SetupFlowPolicy.LEGACY_SAMPLE_PROJECT_NAME)

        val plan = SetupFlowPolicy.planFor(skip)
        assertEquals(GameArenaSample.NAME, plan.projectName)
        assertEquals(GameArenaSample.ENTRY_FILE, plan.entryFile)
        assertEquals(listOf(".codec/project.json") + GameArenaSample.PROJECT_FILES, plan.files)
        assertFalse("the sample is the offline path", plan.needsDownload)
    }

    @Test
    fun `continue with nothing touched is not the skip`() {
        val default = SetupFlowPolicy.defaultChoice()
        assertFalse(SetupFlowPolicy.isLegacySkip(default))
        assertEquals(SetupStart.ARCADE, default.start)
        assertEquals("MyFirstGame", default.projectName)
        assertTrue(SetupFlowPolicy.mayContinue(default, emptyList()))
    }

    // ---- law 2 and 3: every answer is a project, and the plan is the truth --

    @Test
    fun `every start maps to a project, an entry file and a real cost`() {
        SetupStart.values().forEach { start ->
            val choice = SetupFlowPolicy.defaultChoice().copy(
                start = start,
                projectName = start.defaultProjectName,
            )
            val plan = SetupFlowPolicy.planFor(choice)
            assertEquals(start.entryFile, plan.entryFile)
            assertEquals(start.projectType, plan.projectType)
            assertEquals(start.defaultProjectName, plan.projectName)
            assertTrue("the plan lists the file the editor will open", plan.files.contains(start.entryFile))
            assertEquals(start.downloadMb > 0, plan.needsDownload)
        }
    }

    @Test
    fun `only python downloads anything`() {
        SetupStart.values().forEach { start ->
            assertEquals(
                "download cost changed for ${start.id}",
                start == SetupStart.PYTHON,
                SetupFlowPolicy.planFor(SetupFlowPolicy.defaultChoice().copy(start = start)).needsDownload,
            )
        }
    }

    @Test
    fun `the arcade plan is the real asset list, config first`() {
        val plan = SetupFlowPolicy.planFor(SetupFlowPolicy.defaultChoice())
        assertEquals(".codec/project.json", plan.files.first())
        assertEquals(GameArenaSample.PROJECT_FILES, plan.files.drop(1))
    }

    // ---- law 3 for C: the variants are the shipped library, never invented --

    @Test
    fun `the C variants are exactly the shipped templates`() {
        val variants = SetupFlowPolicy.variantsFor(SetupStart.C)
        assertEquals(TemplateProvider.templates.size, variants.size)
        assertEquals(TemplateProvider.templates.map { it.id }, variants.map { it.id })
        assertEquals(TemplateProvider.templates.map { it.name }, variants.map { it.label })
        variants.forEach { variant ->
            assertTrue("level must be the library's 1-3", variant.difficulty in 1..3)
            assertTrue("the plain words come from the template's own concepts", variant.detail.isNotBlank())
        }
    }

    @Test
    fun `only C has variants; nothing is faked for the rest`() {
        assertEquals(true, SetupStart.C.hasVariants)
        listOf(SetupStart.ARCADE, SetupStart.PYTHON, SetupStart.WEB).forEach { start ->
            assertTrue(start.id, SetupFlowPolicy.variantsFor(start).isEmpty())
        }
    }

    @Test
    fun `an unpicked C variant defaults to the level one template`() {
        val choice = SetupFlowPolicy.defaultChoice().copy(start = SetupStart.C)
        assertEquals("hello_world", SetupFlowPolicy.effectiveVariantId(choice))
        val template = SetupFlowPolicy.templateFor(choice)
        assertNotNull(template)
        assertEquals("Hello World", template!!.name)
    }

    @Test
    fun `a stored-but-unknown variant falls back instead of crashing`() {
        val choice = SetupFlowPolicy.defaultChoice().copy(start = SetupStart.C, variantId = "does_not_exist")
        assertEquals(TemplateProvider.templates.first().id, SetupFlowPolicy.effectiveVariantId(choice))
    }

    @Test
    fun `the template source is the template's own code with one header line`() {
        val choice = SetupFlowPolicy.defaultChoice().copy(start = SetupStart.C, variantId = "calculator")
        val template = SetupFlowPolicy.templateFor(choice)!!
        val source = SetupFlowPolicy.templateSource(template)
        assertTrue(source.startsWith("/* Calculator"))
        assertTrue(source.contains("Variables · Arithmetic operators · Formatted printing"))
        assertTrue("the library's code is not rewritten", source.contains(template.code.trimEnd()))
    }

    @Test
    fun `a C plan writes main_c even for a template`() {
        val choice = SetupFlowPolicy.defaultChoice().copy(start = SetupStart.C, variantId = "linked_list")
        val plan = SetupFlowPolicy.planFor(choice)
        assertEquals("main.c", plan.entryFile)
        assertEquals("linked_list", plan.templateId)
        assertEquals(listOf(".codec/project.json", "main.c"), plan.files)
    }

    // ---- the name beat uses the app's own validator -------------------------

    @Test
    fun `the name verdict is the app's ProjectNameCheck, not a second copy`() {
        val choice = SetupFlowPolicy.defaultChoice()
        assertTrue(SetupFlowPolicy.nameVerdict(choice, emptyList()).ok)

        val taken = SetupFlowPolicy.nameVerdict(choice, listOf("MyFirstGame"))
        assertEquals(ProjectNameProblem.TAKEN, taken.problem)
        assertFalse(SetupFlowPolicy.mayContinue(choice, listOf("MyFirstGame")))

        val empty = SetupFlowPolicy.nameVerdict(choice.copy(projectName = "  "), emptyList())
        assertEquals(ProjectNameProblem.EMPTY, empty.problem)

        val invalid = SetupFlowPolicy.nameVerdict(choice.copy(projectName = "a/b"), emptyList())
        assertEquals(ProjectNameProblem.INVALID, invalid.problem)
    }

    @Test
    fun `the safe name is what the seeder will really use`() {
        val choice = SetupFlowPolicy.defaultChoice().copy(projectName = "My Snake")
        assertEquals("My Snake", SetupFlowPolicy.safeProjectName(choice))
        assertNull(SetupFlowPolicy.safeProjectName(choice.copy(projectName = "a/b")))
    }

    // ---- the picks are the app's own ranges ---------------------------------

    @Test
    fun `the three text sizes are real font sizes in the app's range`() {
        assertEquals(listOf(14f, 16f, 20f), SetupTextSize.values().map { it.fontSp })
        assertEquals(listOf("S", "M", "L"), SetupTextSize.values().map { it.label })
        assertEquals(SetupTextSize.M, SetupPicks().textSize)
    }

    @Test
    fun `the theme labels map onto the app's three stored modes`() {
        assertEquals(listOf("dark", "light", "auto"), SetupTheme.values().map { it.id })
        assertEquals(listOf("Dark", "Light", "Auto"), SetupTheme.values().map { it.label })
    }

    @Test
    fun `a fresh flow starts with hints off, exactly as the app ships`() {
        val picks = SetupPicks()
        assertFalse("completion_ghost defaults false today", picks.hints)
        assertTrue(picks.plainWords)
        assertTrue(picks.lineNumbers)
        assertFalse(picks.wordWrap)
        assertEquals(SetupTheme.DARK, picks.theme)
    }
}
