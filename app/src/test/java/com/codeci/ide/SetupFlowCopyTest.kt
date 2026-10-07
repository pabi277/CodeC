package com.codeci.ide

import com.codeci.ide.ui.setup.LearningLinks
import com.codeci.ide.ui.setup.SetupFlowCopy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 97 — the flow's words, pinned.
 *
 * The audit's finding was that CodeC's problem is signs, not features, and that
 * where copy was rewritten it is good (`git_install_explainer`,
 * `no_projects_hint`). These assertions hold the flow to the same voice: short
 * sentences, no jargon, no store words, no emoji, and no promise the app cannot
 * keep. [SetupFlowCopy.ALL_COPY] is the surface — a string that is not in it is
 * a string these tests cannot see, which is why the object says so in a comment.
 */
class SetupFlowCopyTest {

    @Test
    fun `every body sentence stays inside the word budget`() {
        SetupFlowCopy.allSubtitles.forEach { sentence ->
            val words = sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            assertTrue(
                "too long (${words.size} words, budget ${SetupFlowCopy.MAX_WORDS}): \"$sentence\"",
                words.size <= SetupFlowCopy.MAX_WORDS,
            )
        }
    }

    @Test
    fun `no banned word survives anywhere in the flow`() {
        val copy = SetupFlowCopy.ALL_COPY.joinToString(" ")
        SetupFlowCopy.BANNED_WORDS.forEach { banned ->
            // Word boundaries, not substrings: "expected" is not an "xp" and
            // "package" is not an "age" (the Phase 45 lesson - pin the rule,
            // never the accident).
            val hit = Regex("\\b" + Regex.escape(banned) + "\\b", RegexOption.IGNORE_CASE)
            assertFalse("the flow must not say \"$banned\"", hit.containsMatchIn(copy))
        }
    }

    @Test
    fun `the flow is ascii only, so no emoji can render differently per device`() {
        // The app already ships two typographic marks (the middle dot in the
        // output counts, "3 errors · 1 warning", and the em dash in strings.xml);
        // they are allowed here. Everything else above ASCII is not: an emoji
        // renders differently on every OEM and is exactly what the reference
        // flow's hoisted illustrations suffer from.
        val allowed = setOf('·', '—', '–')
        SetupFlowCopy.ALL_COPY.forEach { line ->
            line.forEach { character ->
                assertTrue(
                    "non-ascii character '$character' in \"$line\"",
                    character.code in 32..126 || character in allowed,
                )
            }
        }
    }

    @Test
    fun `the words the audit retired never come back`() {
        val copy = SetupFlowCopy.ALL_COPY.joinToString(" ")
        // "userland" is the installer's word for the same download the rest of
        // the app now calls Linux tools; the flow never needs it at all.
        assertFalse(Regex("\\buserland\\b", RegexOption.IGNORE_CASE).containsMatchIn(copy))
        // The flow never asks anyone what level they are (the labels are in
        // BANNED_WORDS); a template's own difficulty is allowed to say Level 2.
        assertTrue(Regex("\\blevel\\b", RegexOption.IGNORE_CASE).containsMatchIn(
            SetupFlowCopy.PICK_TEMPLATES_DOOR,
        ))
    }

    @Test
    fun `the plan rows name the run control the way the editor labels it`() {
        assertTrue(SetupFlowCopy.WELCOME_PLAN_3_DETAIL.contains("Run"))
    }

    @Test
    fun `skip says what the user gets instead`() {
        val skip = SetupFlowCopy.SKIP_LABEL.lowercase()
        assertTrue("skip must be visible by name", skip.contains("skip"))
        assertTrue("skip must name its consequence", skip.contains("sample"))
    }

    @Test
    fun `the learning door says where the link goes and what it does not send`() {
        val subtitle = LearningLinks.LEARN_SUBTITLE.lowercase()
        assertTrue(subtitle.contains("browser"))
        assertTrue(subtitle.contains("nothing about you"))
        assertTrue("the course's length is a real fact, not a slogan", LearningLinks.LEARN_TITLE.contains("19"))
    }

    @Test
    fun `the privacy document carries the outbound-link promises the doors make`() {
        val doc = RepoFiles.mainSource("docs/guides/DATA_AND_PRIVACY.md").readText()
        // The audit's rule: a door that leaves the app must be described in the
        // privacy document in the same change that adds it. The three sentences
        // below are that description; deleting one fails here.
        assertTrue(doc.contains("Opening the website (Phase 97)"))
        assertTrue(doc.contains("Nothing about you is sent with the link"))
        assertTrue(doc.contains("OpenInBrowser.kt"))
        assertTrue(doc.contains("LearningLinks.kt"))
    }

    @Test
    fun `every outbound address is https on the project's own site`() {
        LearningLinks.ALL.forEach { url ->
            assertTrue("not https: $url", url.startsWith("https://"))
            assertTrue("foreign host: $url", url.startsWith(LearningLinks.SITE_URL))
        }
        assertTrue(LearningLinks.LEARN_URL.endsWith("/learn/"))
        assertEqualsUnique(LearningLinks.ALL)
    }

    private fun assertEqualsUnique(urls: List<String>) {
        assertTrue("duplicate addresses in LearningLinks.ALL", urls.toSet().size == urls.size)
    }

    /** The copy surface and the link surface are one reviewable list. */
    @Test
    fun `no flow string is longer than a phone line can carry`() {
        SetupFlowCopy.ALL_COPY.forEach { line ->
            assertTrue("very long string: \"$line\"", line.length <= 120)
        }
    }

    @Test
    fun `learning links are described as an open action, not a promise of content`() {
        // The door's label is a short noun phrase; the promise lives in the
        // subtitle. Keeping them apart is what stops a row becoming a sentence.
        assertTrue(LearningLinks.FLOW_DOOR_LABEL.length <= 40)
        assertTrue(LearningLinks.HUB_LEARN_LINE.endsWith("?"))
    }
}
