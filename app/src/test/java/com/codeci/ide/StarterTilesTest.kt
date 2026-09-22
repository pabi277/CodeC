package com.codeci.ide

import com.codeci.ide.ui.projects.WelcomeStarters
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 33.1's starter tiles — C / Python / HTML — and what is left of their law
 * after Phase 58.1 retired the screen they were born on.
 *
 * The tiles were the first-run welcome's whole content. The owner's row for 58
 * is *"first open is the editor, on a snake sample we write"*, so the welcome is
 * gone; the tiles themselves are not, because they are also what the Projects
 * empty state offers a user who has no project yet (33.3). These are the pins
 * that still describe something real: three languages, each naming the file it
 * opens, rendered by one loop, from one list.
 */
class StarterTilesTest {

    private val hub = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt"
    ).readText()

    private val tile = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/components/StarterTile.kt"
    ).readText()

    @Test
    fun `there are exactly three starter tiles, and they are languages`() {
        assertEquals(3, WelcomeStarters.starters.size)
        assertEquals(listOf("c", "python", "web"), WelcomeStarters.starters.map { it.id })
    }

    @Test
    fun `every tile names its language and promises the next step`() {
        for (starter in WelcomeStarters.starters) {
            assertTrue("blank title for ${starter.id}", starter.title.isNotBlank())
            assertTrue(
                "the tile must say what the tap does (${starter.id})",
                starter.nextStep.isNotBlank(),
            )
            assertTrue(
                "the next step should name the file it opens (${starter.id})",
                starter.nextStep.contains(starter.entryFile),
            )
        }
    }

    @Test
    fun `the empty hub draws them in one loop, and the tile draws the promise`() {
        assertEquals(
            "one loop, three tiles — no carousel, no illustration",
            1,
            Regex("""WelcomeStarters\.starters\.forEach""").findAll(hub).count(),
        )
        assertTrue(
            "the tile must render the what-happens-next line (33.1/51.1)",
            tile.contains("starter.nextStep"),
        )
    }

    @Test
    fun `the first-run screen really is retired`() {
        assertFalse(
            "58.1 retired the welcome screen; a new screen with the same name is a reversal, not a tidy-up",
            File(
                RepoFiles.root(),
                "app/src/main/java/com/codeci/ide/ui/screens/WelcomeScreen.kt",
            ).exists(),
        )
        val main = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt").readText()
        assertFalse("nothing may render the retired welcome", main.contains("WelcomeScreen("))
    }
}
