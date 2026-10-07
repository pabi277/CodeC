package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 97 — the setup flow's wiring, pinned by source scan.
 *
 * The same shape as `UnrestrictedUiWiringTest`: these are the facts that cannot
 * be seen from one file — that the flow is reachable, that the skip path is the
 * legacy call, that the app has exactly one outbound door, that the pure halves
 * stay Android-free, and that nothing from the retired guide system came back
 * with the flow.
 */
class SetupFlowWiringTest {

    private fun source(name: String) = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/$name"
    ).readText()

    private fun setup(name: String) = source("ui/setup/$name")

    @Test
    fun `the first-run gate shows the flow after the intro and before the shell`() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("SetupFlowScreen("))
        assertTrue("the intro still comes first", main.contains("FirstRunIntroScreen("))
        // The flow is inside the same first-run branch, and the branch returns
        // before the shell so the hub can never flash underneath it.
        val branch = main.substringAfter("if (firstLaunchComplete == false && !com.codeci.ide.ui.crash.SafeMode.active) {")
            .substringBefore("\n    // \"Open where I left off\"")
        assertTrue(branch.contains("SetupFlowScreen("))
        assertTrue(branch.contains("return"))
        assertTrue(
            "the flow only appears when both gates say a fresh install",
            branch.contains("if (!setupFinished && !setupFlowCompleteStored) {"),
        )
    }

    @Test
    fun `skip setup still goes through the sample's own seeding call`() {
        val seeding = setup("SetupSeeding.kt")
        assertTrue(seeding.contains("GameArenaSample.ensure("))
        assertTrue(seeding.contains("SetupFlowPolicy.isLegacySkip(choice)"))
        val policy = setup("SetupFlowPolicy.kt")
        assertTrue(policy.contains("fun skipChoice()"))
        assertTrue(policy.contains("GameArenaSample.NAME"))
    }

    @Test
    fun `the flow's two pure halves import nothing Android`() {
        listOf("SetupFlowPolicy.kt", "SetupFlowCopy.kt", "LearningLinks.kt").forEach { file ->
            val code = RepoFiles.codeOnly(setup(file))
            assertFalse("$file must stay Android-free", code.contains("import android."))
            assertFalse("$file must not reach for a Context", code.contains("Context"))
        }
    }

    @Test
    fun `the learning doors use the app's existing single browser path`() {
        // Phase 37's OpenInBrowser is documented as the app's ONE ACTION_VIEW
        // block ("no second ACTION_VIEW block exists in the codebase"), so the
        // Phase 97 doors route through it rather than adding a second one.
        assertTrue(source("ui/services/OpenInBrowser.kt").contains("Intent.ACTION_VIEW"))
        // The three door surfaces: the flow, Settings, and the empty hub.
        assertTrue(setup("SetupFlowScreen.kt").contains("LearningLinks.LEARN_URL"))
        assertTrue(source("ui/screens/SettingsScreen.kt").contains("LearningLinks.LEARN_URL"))
        assertTrue(source("ui/screens/FileManagerScreen.kt").contains("LearningLinks.LEARN_URL"))
        assertTrue(source("ui/screens/SettingsScreen.kt").contains("OpenInBrowser.openOrCopy("))
        assertTrue(source("ui/screens/FileManagerScreen.kt").contains("OpenInBrowser.openOrCopy("))
        assertTrue(source("MainActivity.kt").contains("OpenInBrowser.open(activity, url)"))
        // And the setup half owns no intent plumbing of its own.
        val setupCode = RepoFiles.mainKotlinSources()
            .filter { it.parentFile.name == "setup" }
            .joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
        assertFalse("the flow must not invent a second ACTION_VIEW", setupCode.contains("ACTION_VIEW"))
        assertFalse("the flow must not import Intent", setupCode.contains("import android.content.Intent"))
    }

    @Test
    fun `the flow stores one gate and no profile`() {
        val settings = source("ui/settings/SettingsManager.kt")
        assertTrue(settings.contains("SETUP_FLOW_COMPLETE"))
        assertTrue(settings.contains("setupFlowCompleteFlow"))
        // Nothing about the person: no goal, no level, no age, no experience.
        val setupSources = RepoFiles.mainKotlinSources()
            .filter { it.parentFile.name == "setup" }
            .joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
        listOf("goal", "experience", "age", "skillLevel", "userLevel").forEach { forbidden ->
            // Word boundaries: "package" is not an "age", and the keyword
            // appears at the top of every file.
            val hit = Regex("\\b" + Regex.escape(forbidden) + "\\b", RegexOption.IGNORE_CASE)
            assertFalse("the flow must not record $forbidden", hit.containsMatchIn(setupSources))
        }
    }

    @Test
    fun `the flow does not recreate the retired guide system`() {
        val setupSources = RepoFiles.mainKotlinSources()
            .filter { it.parentFile.name == "setup" }
            .joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
        listOf("CoachMark", "GuideOverlay", "Spotlight", "TooltipOverlay", "coachSeen").forEach {
            assertFalse("retired guide machinery is back: $it", setupSources.contains(it))
        }
    }

    @Test
    fun `the build screen reads the plan rather than inventing a progress bar`() {
        val screen = setup("SetupFlowScreen.kt")
        assertTrue(screen.contains("plan.files.forEach"))
        assertTrue("the file rows come from the seeder's own callbacks", screen.contains("onFileWritten"))
        assertFalse("no timed fake progress in the build beat", screen.contains("delay("))
    }

    @Test
    fun `every control in the flow declares the 48dp floor itself`() {
        // TouchTargetTest resolves files under ui/screens/ and only guards
        // IconButtons, neither of which covers this screen: the flow has no
        // IconButtons and lives in ui/setup/. Its floor is therefore asserted
        // here - each interactive helper carries an explicit MIN_TOUCH height.
        val screen = setup("SetupFlowScreen.kt")
        val floor = "heightIn(min = CodecTokens.MIN_TOUCH.dp)"
        val declared = Regex(Regex.escape(floor)).findAll(screen).count()
        assertTrue("the flow declares the 48dp floor at least 8 times (found $declared)", declared >= 8)
        assertFalse(
            "the flow must not shrink its own buttons",
            screen.contains("IconButton("),
        )
        assertFalse(
            "no hardcoded sub-48dp box on an interactive surface",
            Regex("""\.(size|requiredSize|defaultMinSize)\(\s*([0-3]?\d)\.dp""").containsMatchIn(screen),
        )
    }
}
