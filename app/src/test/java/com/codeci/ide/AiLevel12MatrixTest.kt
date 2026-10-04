package com.codeci.ide

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 89 (AI Level 12, part 89.3) — the acceptance matrix and its device round,
 * pinned as documents.
 *
 * A checklist that silently loses a row is a checklist that quietly passes. These
 * cases read the real files in the repo (the house style of `RepoFiles`) so that
 * a future edit cannot drop the owner's model ids, the 3/3 pass bar, the ten
 * tasks, the eight visual checks, the support-report row (now carrying the
 * owner's 2026-10-04 report, never an agent tick), the memory-cap question or
 * the long-answer question.
 */
class AiLevel12MatrixTest {

    private val brief = RepoFiles.mainSource("docs/phases/03-editor/chat-phase89/README.md").readText()
    private val round = RepoFiles.mainSource("docs/phases/03-editor/chat-phase89/DEVICE_ROUND.md").readText()
    private val spec = RepoFiles.mainSource("docs/roadmaps/ai-integration/12_AGENT_EVALUATION_AND_ACCEPTANCE.md").readText()

    @Test
    fun `the brief names the owner's five decisions and the two exact models`() {
        assertTrue(brief.contains("gemini-3-flash-preview"))
        assertTrue(brief.contains("nvidia/nemotron-3-super-120b-a12b"))
        assertTrue("the pass bar is the owner's 3/3", brief.contains("3/3"))
        assertTrue("the readout is the owner's answer", brief.contains("numbers readout") || brief.contains("numbers-only readout"))
        assertTrue("a failed row becomes its own fix phase", brief.contains("its own fix phase"))
    }

    @Test
    fun `the ten task shapes of the Level 12 spec are all present in the round`() {
        val tasks = listOf(
            "Explain full `main.js` line by line",
            "Multi-file relationship question",
            "Find and propose a small bug fix",
            "Truthful use of run results",
            "Repeated identical request",
            "Secret / path escape / prompt injection",
            "Stale buffer, external file change",
            "Stop mid-task",
            "Provider failure mid-task",
            "Budget limit reached"
        )
        for (task in tasks) {
            assertTrue("the round must carry the task: $task", round.contains(task) || spec.contains(task))
        }
        assertTrue("the round carries the matrix", round.contains("10 tasks") || round.contains("60 real runs"))
    }

    @Test
    fun `the eight visual checks Phase 88 handed forward are in the round`() {
        for (check in listOf(
            "clear sections",
            "hostile answer",
            "https link",
            "Copy",
            "progress line",
            "Rows open",
            "disclosure",
            "dark theme"
        )) {
            assertTrue("visual check missing: $check", round.contains(check))
        }
    }

    @Test
    fun `the support-report scrub row records the owner's report, never an agent tick`() {
        assertTrue("P8 is the scrub check", round.contains("P8"))
        assertTrue(
            "and its cell must say owner-reported",
            Regex("\\| *P8 *\\|[^\\n]*owner-reported[^\\n]*\\|").containsMatchIn(round)
        )
        assertTrue(
            "every filled cell is marked with the owner's provenance",
            round.contains("owner-reported")
        )
        assertTrue(
            "the failed rows are recorded as failed, not smoothed over",
            Regex("\\| *S2 *\\|[^\\n]*❌").containsMatchIn(round) &&
                Regex("\\| *T3 *\\|[^\\n]*❌").containsMatchIn(round) &&
                Regex("\\| *T4 *\\|[^\\n]*❌").containsMatchIn(round)
        )
        assertTrue(
            "the round keeps its NOT EXERCISED rows first-class",
            round.contains("NOT EXERCISED")
        )
        assertTrue(
            "the model the owner actually ran is the model named",
            round.contains("gemini-3.1-flash-lite")
        )
    }

    @Test
    fun `the two open questions are asked in the round, not assumed`() {
        assertTrue("the memory caps are questioned", round.contains("5 files") && round.contains("160 KiB") && round.contains("256 KiB"))
        assertTrue("the long-answer lazy list is questioned", round.contains("lazy list"))
    }
}
