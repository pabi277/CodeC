package com.codeci.ide

import com.codeci.ide.ui.projects.WelcomeStarters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 51.1 — the first screen gets a face. Source scan + copy pins: the mark,
 * the three tiles with their language identity, the "what happens next" line
 * each tile owes the user, and the offline-C reassurance promoted to a badge.
 */
class WelcomeLayoutTest {

    private val screen = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/WelcomeScreen.kt"
    ).readText()

    @Test
    fun `the app shows its own mark on the first screen`() {
        assertTrue(
            "the welcome must render the in-app mark",
            screen.contains("painterResource(R.drawable.app_mark)"),
        )
        assertTrue(
            "the mark is display art, above the touch floor",
            Regex("""size\((\d+(?:\.\d+)?)\.dp\)""").findAll(screen)
                .any { (it.groupValues[1].toFloat()) > 48f },
        )
    }

    @Test
    fun `the welcome still offers exactly the three starter tiles`() {
        assertEquals(3, WelcomeStarters.starters.size)
        assertEquals(listOf("c", "python", "web"), WelcomeStarters.starters.map { it.id })
        val renders = Regex("""WelcomeStarters\.starters\.forEach""").findAll(screen).count()
        assertEquals("one loop, three tiles — no carousel, no illustration", 1, renders)
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
    fun `the tile renders the next-step line`() {
        assertTrue(screen.contains("starter.nextStep"))
    }

    @Test
    fun `the tiles are token-shaped, elevated cards, not flat rows`() {
        assertTrue(screen.contains("CodecTokens.radius(Radius.L)"))
        assertTrue(
            "the tiles carry the 50.1 card elevation",
            screen.contains("CodecTokens.elevation(CodecTokens.Elevation.CARD)"),
        )
        assertTrue(
            "the container is a brand role, not a raw colour",
            screen.contains("MaterialTheme.colorScheme.surfaceContainerHigh"),
        )
        assertFalse(
            "no raw corner radius may come back",
            Regex("""RoundedCornerShape\(\s*\d+\s*\.dp""").containsMatchIn(screen),
        )
    }

    @Test
    fun `the offline-C reassurance is a visible badge with its own copy`() {
        assertTrue(screen.contains("R.string.welcome_offline_c"))
        assertTrue(screen.contains("MaterialTheme.colorScheme.secondaryContainer"))
        assertTrue(screen.contains("MaterialTheme.colorScheme.onSecondaryContainer"))
    }

    @Test
    fun `the header says what the app is and what to do first`() {
        assertTrue(screen.contains("R.string.welcome_tagline"))
        assertTrue(screen.contains("R.string.welcome_pick_language"))
        assertTrue(screen.contains("headlineLarge"))
    }

    @Test
    fun `the string resources the screen asks for all exist`() {
        val strings = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()
        for (name in listOf(
            "welcome_tagline",
            "welcome_pick_language",
            "welcome_offline_c",
        )) {
            assertTrue(
                "strings.xml is missing $name",
                strings.contains("<string name=\"$name\">"),
            )
        }
    }
}
