package com.codeci.ide

import com.codeci.ide.ui.setup.SetupFlowCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 99 — the setup flow's illustrations, pinned.
 *
 * The owner's brief (2026-10-08) asked for a welcome screen with a centred logo
 * and a tagline, then "each configuration step should display a consistent 3D
 * illustration", ending in a loading state and a personalized workspace
 * summary. The screen cannot be host-tested, so the things that make that true
 * are asserted here, against the source and against the files:
 *
 * - every beat draws an illustration, from one shared card (`ui/components/StepArt`),
 *   at one size — "consistent" is a property of the code, not of a screenshot;
 * - every illustration is a real bundled file, and every one is described for a
 *   screen reader;
 * - the welcome beat is the logo + tagline the brief names, and the reading
 *   helps/steps are not decorated with a second illustration system;
 * - the loading beat's bar measures the files really written, never time.
 */
class SetupFlowArtTest {

    private val screen = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/setup/SetupFlowScreen.kt",
    ).readText()

    private val code = RepoFiles.codeOnly(screen)

    private val cards = listOf(
        "setup_01_pick",
        "setup_02_name",
        "setup_03_looks",
        "setup_04_helps",
        "setup_05_build",
        "setup_06_ready",
    )

    @Test
    fun `the setup draws its illustrations through the one shared card`() {
        // The tour's card and the setup's are the same composable (Phase 99):
        // a second illustration path would drift, and the card is where the
        // backdrop colour and the required description live.
        val art = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/components/StepArt.kt",
        ).readText()
        assertTrue(art.contains("fun StepArt("))
        assertTrue(
            "the card paints the render's own backdrop, not a theme role",
            art.contains("CodecPalette.ART_CARD_BACKDROP"),
        )
        assertTrue(
            "a description has no default: art with no words is a hole",
            !art.contains("description: String ="),
        )
        // The setup uses it, and only it.
        assertTrue(screen.contains("import com.codeci.ide.ui.components.StepArt"))
        assertEquals("six beats draw art", 6, Regex("\\bStepArt\\(").findAll(code).count())
        assertFalse(
            "no second illustration path in the setup",
            code.contains("painterResource(story.") || code.contains("AsyncImage"),
        )
    }

    @Test
    fun `every configuration step shows one illustration and one size`() {
        cards.forEach { card ->
            assertTrue("$card is not drawn by the screen", screen.contains("R.drawable.$card"))
            val asset = RepoFiles.mainSource("app/src/main/res/drawable-nodpi/$card.webp")
            assertTrue("$card.webp is missing", asset.exists())
            assertTrue(
                "$card.webp is too small to be an illustration",
                asset.length() > 8_192L,
            )
        }
        // One size for the six beats: "a consistent 3D illustration" is these
        // two facts, and a second size means one beat stops lining up.
        assertEquals(
            "one art size constant",
            1,
            Regex("private val StepArtSize = \\d+(\\.\\d+)?\\.dp").findAll(screen).count(),
        )
        assertEquals(
            "every card is drawn at that one size",
            6,
            Regex("\\.size\\(StepArtSize\\)").findAll(screen).count(),
        )
    }

    @Test
    fun `every illustration is described, in the reviewed copy surface`() {
        val descriptions = listOf(
            "ART_PICK", "ART_NAME", "ART_LOOKS", "ART_HELPS", "ART_BUILD", "ART_READY",
        )
        descriptions.forEach { name ->
            assertEquals(
                "$name must be drawn exactly once",
                1,
                Regex("SetupFlowCopy\\.$name\\b").findAll(code).count(),
            )
            assertEquals(
                "$name must be attached to a card",
                1,
                Regex("description = SetupFlowCopy\\.$name\\b").findAll(code).count(),
            )
        }
        // Every description is also part of the reviewed copy surface, so the
        // flow's own rules (banned words, ASCII, length) cover the alt text too.
        listOf(
            SetupFlowCopy.ART_PICK, SetupFlowCopy.ART_NAME, SetupFlowCopy.ART_LOOKS,
            SetupFlowCopy.ART_HELPS, SetupFlowCopy.ART_BUILD, SetupFlowCopy.ART_READY,
        ).forEach { sentence ->
            assertTrue(
                "an illustration's description must be in ALL_COPY: \"$sentence\"",
                SetupFlowCopy.ALL_COPY.contains(sentence),
            )
        }
        val described = listOf(
            SetupFlowCopy.ART_PICK, SetupFlowCopy.ART_NAME, SetupFlowCopy.ART_LOOKS,
            SetupFlowCopy.ART_HELPS, SetupFlowCopy.ART_BUILD, SetupFlowCopy.ART_READY,
        )
        described.forEach { sentence ->
            assertTrue(
                "an illustration description must describe, not name: \"$sentence\"",
                sentence.split(Regex("\\s+")).size >= 12,
            )
        }
        assertEquals("no illustration shares another's description", 6, described.toSet().size)
    }

    @Test
    fun `the welcome beat is the centred mark and the tagline`() {
        val welcome = screen.substringAfter("private fun WelcomeStep(")
            .substringBefore("@Composable\nprivate fun PlanLine(")
        assertTrue(
            "the welcome screen carries the app's own mark",
            welcome.contains("painterResource(R.drawable.app_mark)"),
        )
        assertTrue("the mark is centred", welcome.contains("horizontalAlignment = Alignment.CenterHorizontally"))
        assertTrue("the title is centred", welcome.contains("textAlign = TextAlign.Center"))
        assertTrue(
            "the welcome says the app's promise, not a settings instruction",
            welcome.contains("SetupFlowCopy.WELCOME_TAGLINE"),
        )
        assertTrue(
            "and it still offers both exits: start, or the sample game",
            welcome.contains("PrimaryButton(label = SetupFlowCopy.BUTTON_START") &&
                welcome.contains("SetupFlowCopy.SKIP_LABEL"),
        )
        // A tagline with no sentence is a label; this one is a promise.
        assertTrue(
            "the tagline must be a sentence",
            SetupFlowCopy.WELCOME_TAGLINE.trim().endsWith(".") &&
                SetupFlowCopy.WELCOME_TAGLINE.split(Regex("\\s+")).size >= 8,
        )
        assertFalse(
            "the old \"Set up your workspace\" title is not a welcome",
            screen.contains("\"Set up your workspace\""),
        )
    }

    @Test
    fun `the loading beat measures the files it really wrote`() {
        val building = screen.substringAfter("private fun BuildingStep(")
            .substringBefore("@Composable\nprivate fun ReadyStep(")
        assertTrue(
            "the bar reads the written-file count against the plan",
            building.contains("written.size.toFloat() / plan.files.size"),
        )
        assertFalse(
            "no timer-driven progress may come back",
            building.contains("delay(") || building.contains("animateFloat"),
        )
        assertTrue(
            "and the file rows it counts are still there",
            building.contains("plan.files.forEach"),
        )
        // The loading illustration exists too, and it is the one that says
        // files are being created — not a second progress widget.
        assertTrue(building.contains("R.drawable.setup_05_build"))
    }

    @Test
    fun `the receipt is the last beat and it is personalized`() {
        val ready = screen.substringAfter("private fun ReadyStep(")
        assertTrue("the summary beat draws its own art", ready.contains("R.drawable.setup_06_ready"))
        assertTrue("it names the project the user chose", ready.contains("plan.projectName"))
        assertTrue("and the file that will open", ready.contains("SetupFlowPolicy.receiptProjectLine(plan)"))
        assertTrue(
            "and the answers they gave (size, theme, the switches)",
            ready.contains("SetupFlowCopy.READY_CHIP_SIZE_PREFIX") &&
                ready.contains("picks.theme.label"),
        )
        assertEquals(
            "the summary is the last beat, and Start coding is its action",
            1,
            Regex("SetupFlowCopy\\.BUTTON_FINISH").findAll(ready).count(),
        )
    }
}
