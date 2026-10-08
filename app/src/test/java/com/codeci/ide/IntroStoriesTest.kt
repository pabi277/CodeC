package com.codeci.ide

import com.codeci.ide.ui.screens.INTRO_STORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 98 — the illustrated tour, pinned.
 *
 * The owner's brief (2026-10-08) named four things the first-run tour has to
 * show — coding offline, the edit-run-check loop, toolkit support, and sample
 * projects — each as one page with a bold heading and a subtext, plus a live
 * progress indicator at the bottom. This is that list, checkable.
 *
 * What a *source scan* can hold (and the screen cannot be host-tested for):
 *
 * - the deck is the five pages, in the promised order, with no page reusing
 *   another's illustration;
 * - every illustration is a bundled file that really exists in the repository
 *   (a typo in an `R.drawable` name would otherwise only fail in CI's compile,
 *   or on a device);
 * - every illustration carries a description for TalkBack, and every string is
 *   ASCII plus the app's two typographic marks;
 * - the pages keep the house voice: no store words, no "level" labels, no
 *   marketing.
 *
 * `FirstOpenGameArenaTest` keeps the behaviour pins (swipe, the timer and its
 * pause control, the skip, the agreement gate); this test owns the words, the
 * art and the order.
 */
class IntroStoriesTest {

    private val intro = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/FirstRunIntroScreen.kt",
    ).readText()

    private val stories = INTRO_STORIES

    @Test
    fun `the tour is five pages and the screen counts the same five`() {
        assertEquals("the tour is the owner's five pages", 5, stories.size)
        assertTrue(
            "the progress indicator counts the same pages the deck holds",
            intro.contains("private const val INTRO_PAGE_COUNT = 5"),
        )
    }

    @Test
    fun `the pages are the four promises plus the agreement, in that order`() {
        // Each page's own words have to carry its theme, in the brief's order:
        // offline first, then the loop, the toolkit, and the sample projects.
        val expected = listOf(
            listOf("offline", "no account"),
            listOf("edit", "run", "compiler errors"),
            listOf("linux tools", "download"),
            listOf("snake", "block party", "tic-tac-toe"),
            listOf("device", "no ads"),
        )
        stories.forEachIndexed { index, story ->
            val page = (story.title + " " + story.body).lowercase()
            expected[index].forEach { needle ->
                assertTrue(
                    "page ${index + 1} (\"${story.title}\") must say \"$needle\"",
                    page.contains(needle),
                )
            }
        }
        assertEquals(
            "the last page is the one that asks for agreement",
            "BEFORE YOU START",
            stories.last().eyebrow,
        )
    }

    @Test
    fun `every page names its own illustration and the file is really there`() {
        val ids = stories.map { it.art }
        assertEquals("no page may reuse another's art", ids.size, ids.toSet().size)
        assertTrue("every page must point at a real resource id", ids.all { it != 0 })
        // The five cards, in the order the tour shows them.
        val cards = listOf(
            "intro_01_offline",
            "intro_02_loop",
            "intro_03_tools",
            "intro_04_sample",
            "intro_05_privacy",
        )
        cards.forEach { stem ->
            val asset = RepoFiles.mainSource("app/src/main/res/drawable-nodpi/$stem.webp")
            assertTrue("$stem.webp is missing", asset.exists())
            assertTrue(
                "$stem.webp is too small to be an illustration",
                asset.length() > 8_192L,
            )
        }
        val at = cards.map { stem -> intro.indexOf("R.drawable.$stem") }
        assertTrue("every card must be referenced by name", at.all { it >= 0 })
        assertEquals(
            "the deck's order is the tour's order",
            at.sorted(),
            at,
        )
    }

    @Test
    fun `every illustration is described for a screen reader`() {
        stories.forEach { story ->
            assertTrue(
                "\"${story.title}\" has no art description",
                story.artDescription.length > 20,
            )
        }
    }

    @Test
    fun `every page has a chip, a bold heading and a subtext`() {
        val allowed = setOf('·', '—', '–', '→')
        stories.forEach { story ->
            assertTrue("a page with no eyebrow chip", story.eyebrow.isNotBlank())
            assertTrue("a page with no heading", story.title.isNotBlank())
            assertTrue("a page with no subtext", story.body.isNotBlank())
            assertTrue(
                "the heading must fit a phone's bold line: \"${story.title}\"",
                story.title.length <= 44,
            )
            assertTrue(
                "the heading must not be a paragraph: \"${story.title}\"",
                story.title.split(Regex("\\s+")).size <= 8,
            )
            (story.eyebrow + story.title + story.body + (story.caption ?: "")).forEach { character ->
                assertTrue(
                    "non-ascii character '$character' in \"${story.title}\"",
                    character.code in 32..126 || character in allowed,
                )
            }
        }
    }

    @Test
    fun `the tour keeps the house voice`() {
        val all = stories.joinToString(" ") { it.eyebrow + " " + it.title + " " + it.body + " " + (it.caption ?: "") }
        // The setup flow's banned list, re-used rather than re-invented: CodeC
        // is not a store and the tour is not a game.
        listOf(
            "userland", "premium", "upgrade", "unlock", "credits", "ad-free",
            "leaderboard", "streak", "score", "beginner", "intermediate", "advanced", "expert",
        ).forEach { banned ->
            val hit = Regex("\\b" + Regex.escape(banned) + "\\b", RegexOption.IGNORE_CASE)
            assertFalse("the tour must not say \"$banned\"", hit.containsMatchIn(all))
        }
    }

    @Test
    fun `the progress indicator is at the bottom and measures the real window`() {
        // The indicator sits under the page, in the screen's bottom column - the
        // owner's reference, and the place a thumb is not covering the words.
        val indicator = intro.substringAfter("private fun IntroDots(")
            .substringBefore("\n@Composable")
        assertTrue("the indicator draws one dot per page", indicator.contains("repeat(INTRO_PAGE_COUNT)"))
        assertTrue(
            "the current dot fills with the real story timer",
            indicator.contains("fillMaxWidth(normalized)"),
        )
        assertTrue(
            "the indicator is announced as progress",
            indicator.contains("progressBarRangeInfo"),
        )
        // It is called from the bottom column, after the story column's weight.
        val bottom = intro.substringAfter("// Phase 98 — the honest progress indicator")
        assertTrue("the bottom bar holds the indicator", bottom.contains("IntroDots(page = page"))
        assertTrue(
            "the reading window still exists and is still pausable",
            intro.contains("CodecMotion.storyTimer(remainingMs)") && intro.contains("Pause timer"),
        )
    }

    @Test
    fun `the tour shows the bundled art instead of drawing its own`() {
        assertTrue(
            "the pages must render the bundled cards",
            intro.contains("painterResource(story.art)"),
        )
        // The Phase 90-97 vector art lived in five private composables; the tour
        // ships one illustration path now, so a second one coming back is a
        // regression rather than a style choice.
        listOf("CodeRunArtwork", "WorkflowArtwork", "ToolsArtwork", "ArcadeArtwork", "PrivacyArtwork")
            .forEach { old ->
                assertFalse("$old is back alongside the bundled art", intro.contains(old))
            }
    }
}
