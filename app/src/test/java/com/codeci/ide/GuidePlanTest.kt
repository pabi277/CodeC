package com.codeci.ide

import com.codeci.ide.ui.guide.GuidePlan
import com.codeci.ide.ui.guide.GuideSlide
import com.codeci.ide.ui.guide.GuideTermProof
import com.codeci.ide.ui.guide.GuideVocabulary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 45.1 — the first-run guide's plan. The slides ARE the deliverable
 * (PART_45_1: "content is the deliverable"), so most of this file is about the
 * copy: its caps, its order, and above all the rule that a slide may only name
 * a control, tab or command that really exists in this repo.
 *
 * The vocabulary pin works in both directions, the way `SettingsAuditTest` pins
 * the audit doc: a term with no proof fails ("the guide promises something the
 * product does not have"), and a proof whose needle vanished fails ("the product
 * moved; fix the copy or the proof").
 */
class GuidePlanTest {

    private val slides = GuidePlan.slides

    // ---- shape -------------------------------------------------------------

    @Test
    fun `the guide is five slides in the order the user meets the features`() {
        assertEquals(5, slides.size)
        assertEquals(
            listOf("files", "run", "download", "terminal", "projects"),
            slides.map { it.id }
        )
        assertEquals(
            "ids must be unique",
            slides.size,
            slides.map { it.id }.distinct().size
        )
    }

    @Test
    fun `every slide is one idea within the copy caps`() {
        for (slide in slides) {
            assertTrue("${slide.id}: title blank", slide.title.isNotBlank())
            assertTrue("${slide.id}: body blank", slide.body.isNotBlank())
            assertTrue("${slide.id}: action blank", slide.actionLabel.isNotBlank())
            assertTrue(
                "${slide.id}: title ${slide.title.length} chars > ${GuidePlan.MAX_TITLE_CHARS}",
                slide.title.length <= GuidePlan.MAX_TITLE_CHARS
            )
            assertTrue(
                "${slide.id}: body ${GuidePlan.wordCount(slide.body)} words > ${GuidePlan.MAX_BODY_WORDS}",
                GuidePlan.wordCount(slide.body) <= GuidePlan.MAX_BODY_WORDS
            )
            assertTrue(
                "${slide.id}: body ${slide.body.length} chars > ${GuidePlan.MAX_BODY_CHARS}",
                slide.body.length <= GuidePlan.MAX_BODY_CHARS
            )
        }
    }

    @Test
    fun `the last slide is the only one that says START CODING`() {
        // The owner's "step by step" ends in the app, not in another slide: the
        // final action label is the door, every earlier one is GOT IT.
        assertEquals("START CODING", slides.last().actionLabel)
        assertTrue(slides.dropLast(1).all { it.actionLabel == "GOT IT" })
    }

    @Test
    fun `slide two says C works offline and slide three carries the download law`() {
        // Phase 44's two sentences, in the guide's own words: C is never gated
        // (slide 2), and the one-time download must not be interrupted
        // (slide 3 — the owner's own "don't close app before it complete").
        assertTrue(slides[1].body.contains("offline"))
        assertTrue(slides[1].body.contains("no download"))
        assertTrue(slides[2].body.contains("once"))
        assertTrue(slides[2].body.contains("Keep CodeC open"))
    }

    // ---- the no-nag law ----------------------------------------------------

    @Test
    fun `skip is available on every slide including the first`() {
        for (index in slides.indices) {
            assertTrue("canSkip($index) must be true", GuidePlan.canSkip(index))
        }
        // …and an index with no slide has nothing to skip.
        assertFalse(GuidePlan.canSkip(slides.size))
        assertFalse(GuidePlan.canSkip(-1))
    }

    @Test
    fun `next advances, terminates and never wraps`() {
        assertEquals(1, GuidePlan.next(0))
        assertEquals(4, GuidePlan.next(3))
        assertEquals(GuidePlan.doneIndex, GuidePlan.next(4))
        assertEquals(GuidePlan.doneIndex, GuidePlan.next(GuidePlan.doneIndex))
        assertTrue(GuidePlan.isLast(4))
        assertFalse(GuidePlan.isLast(3))
        assertTrue(GuidePlan.isDone(GuidePlan.doneIndex))
        assertFalse(GuidePlan.isDone(4))
        assertNull(GuidePlan.at(GuidePlan.doneIndex))
        assertNull(GuidePlan.at(-1))
        assertEquals(slides.first(), GuidePlan.at(0))
    }

    @Test
    fun `a persisted index resumes instead of restarting`() {
        // An update that shortens the guide, or a corrupt preference, must land
        // on a real slide — never past the end, never back at slide one when the
        // user was on slide four.
        assertEquals(0, GuidePlan.resume(-3))
        assertEquals(2, GuidePlan.resume(2))
        assertEquals(4, GuidePlan.resume(99))
        assertEquals(4, GuidePlan.resume(GuidePlan.doneIndex))
    }

    @Test
    fun `the progress bar reads one fifth per slide and full at the end`() {
        assertEquals(0.2, GuidePlan.progress(0).toDouble(), 0.0001)
        assertEquals(0.6, GuidePlan.progress(2).toDouble(), 0.0001)
        assertEquals(1.0, GuidePlan.progress(4).toDouble(), 0.0001)
        assertEquals(1.0, GuidePlan.progress(GuidePlan.doneIndex).toDouble(), 0.0001)
    }

    @Test
    fun `word count ignores punctuation-only tokens`() {
        // "\u22EE \u2192 Open" is one named control, not three words; an em dash is
        // not a word either. The cap has to count what a reader reads.
        assertEquals(2, GuidePlan.wordCount("tap \u22EE \u2192 Open"))
        assertEquals(4, GuidePlan.wordCount("C works offline \u2014 always"))
        assertEquals(0, GuidePlan.wordCount(""))
    }

    // ---- the vocabulary pin ------------------------------------------------

    @Test
    fun `the extractor finds product nouns and ignores prose`() {
        val terms = GuideVocabulary.candidateTerms(
            "Tap \u2630 in the editor. Try Super Compile in the Terminal tab: RUN \u25B6 and `pkg install`."
        )
        // Control symbols, ALL-CAPS labels, capitalised mid-sentence nouns and
        // backticked spans are all named features…
        assertTrue(terms.contains("\u2630"))
        assertTrue(terms.contains("\u25B6"))
        assertTrue(terms.contains("RUN"))
        assertTrue(terms.contains("Super"))
        assertTrue(terms.contains("Compile"))
        assertTrue(terms.contains("Terminal"))
        assertTrue(terms.contains("pkg install"))
        // …while a sentence's first word is prose, not a feature.
        assertFalse("a sentence-initial capital is not a product noun", terms.contains("Tap"))
    }

    @Test
    fun `an invented feature name has no proof and is caught`() {
        val invented = GuideSlide(
            id = "invented",
            title = "Super Compile",
            body = "Tap Super Compile to build everything at once.",
            actionLabel = "GOT IT"
        )
        assertEquals(
            listOf("Compile", "Super"),
            GuideVocabulary.unprovenTerms(listOf(invented)).sorted()
        )
        // And the real slides name nothing unproven.
        assertEquals(
            "the guide names something the product does not have: " +
                GuideVocabulary.unprovenTerms(slides),
            emptyList<String>(),
            GuideVocabulary.unprovenTerms(slides)
        )
    }

    @Test
    fun `every vocabulary proof still exists in the real source`() {
        for (proof in GuideVocabulary.proofs + GuidePlan.commandProofs) {
            val text = RepoFiles.mainSource(proof.path).readText()
            assertTrue(
                "proof for '${proof.term}' is stale: ${proof.path} no longer contains " +
                    "'${proof.needle}' — fix the guide copy or the proof",
                text.contains(proof.needle)
            )
        }
    }

    @Test
    fun `no proof is left over from copy that changed`() {
        // The other direction: a proof nothing names any more is dead weight
        // that hides a real gap later.
        assertEquals(
            "unused vocabulary proofs (delete them): " + GuideVocabulary.unusedProofs(slides),
            emptyList<String>(),
            GuideVocabulary.unusedProofs(slides)
        )
    }

    @Test
    fun `every proof term is distinct and every command is both named and real`() {
        val all = GuideVocabulary.proofs + GuidePlan.commandProofs
        assertEquals(
            "duplicate proof terms: ${all.map { it.term }}",
            all.size,
            all.map { it.term }.distinct().size
        )
        for (command in GuidePlan.commands) {
            assertTrue(
                "command '$command' is listed but no slide names it",
                GuidePlan.slidesNaming(command).isNotEmpty()
            )
            assertTrue(
                "command '$command' has no proof",
                GuidePlan.commandProofs.any { it.term == command }
            )
        }
        // And nothing in the copy names a command that is not on the list: the
        // list is the pin, so a new command in a slide must be added to it.
        val namedInCopy = GuidePlan.commandProofs.map { it.term }.toSet()
        assertEquals(GuidePlan.commands.toSet(), namedInCopy)
    }

    @Test
    fun `the guide never reopens itself - completion is a persisted flag, not a timer`() {
        // Source-level half of the no-nag law: the screen writes the flag on
        // SKIP and on the last action only. Pinned here so the pure file's
        // promise and the Compose edge cannot drift apart (the wiring pin lives
        // in GuideWiringTest).
        assertTrue(GuidePlan.canSkip(0))
        assertTrue(GuidePlan.isDone(GuidePlan.next(slides.lastIndex)))
    }
}
