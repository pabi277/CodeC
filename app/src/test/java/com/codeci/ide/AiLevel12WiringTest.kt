package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 89 (AI Level 12) source pins.
 *
 * The Level 12 policies are pure, so the risks this phase actually carries are
 * *absence* risks: a readout nobody calls, a number nobody stamps, a fourth
 * network road, a new persisted key, or a ceiling that moves while nobody is
 * looking. Each pin below fails the build if that happens.
 */
class AiLevel12WiringTest {

    private val aiDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private fun ai(name: String) = RepoFiles.codeOnly(File(aiDir, name).readText())
    private fun raw(name: String) = File(aiDir, name).readText()

    /** Call sites of `name()`, never the `fun name()` declaration itself. */
    private fun calls(code: String, name: String) =
        Regex("(?<!fun )$name[(][)]").findAll(code).count()

    @Test
    fun `the readout is drawn from the policy, not built inline in the sheet`() {
        val sheet = ai("AiChatSheet.kt")
        assertTrue(
            "the sheet must render exactly what the policy returns",
            sheet.contains("AiMeasurePolicy.render(state.measurements)")
        )
        assertTrue("and the numbers live in the state", ai("AiViewModel.kt").contains("val measurements: AiMeasurements"))
        // One helper owns the line, and every drawn task is drawn exactly once:
        // the activity card for an agent task with steps, the terminal area for a
        // task whose card is not on screen (single-shot, no-tool, failure).
        assertTrue("one helper owns the line", sheet.contains("private fun MeasurementsLine(state: AiUiState)"))
        assertEquals("the helper is drawn three times", 3, Regex("MeasurementsLine[(]state[)]").findAll(sheet).count())
        assertEquals(
            "two of those are the guarded terminal sites",
            2,
            Regex("if [(]state[.]agentSteps[.]isEmpty[(][)][)] MeasurementsLine[(]state[)]").findAll(sheet).count()
        )
    }

    @Test
    fun `the numbers are stamped at the seams that can observe them`() {
        val vm = ai("AiViewModel.kt")
        // first token: both stream callbacks
        assertEquals("both request paths stamp a first token", 2, calls(vm, "recordFirstToken"))
        // totals: the three terminal paths (agent finish, stop/failure, single-shot)
        assertTrue("recordFinish is called from every terminal path", calls(vm, "recordFinish") >= 3)
        assertTrue("and it can only fire once per request", vm.contains("measureStartedAtMs = 0L"))
        // usage: folded from both outcome paths
        assertEquals(
            "both request paths fold the provider's report",
            2,
            Regex("(?<!fun )recordUsage[(]\\(outcome as\\? AiOutcome[.]Answer\\)\\?[.]usage\\)").findAll(vm).count()
        )
        assertTrue("one instant serves the wall clock and the readout", vm.contains("session.budget = AiAgentBudget(startedAtMs = startedAt)"))
    }

    @Test
    fun `both providers' own reports are parsed, and absence stays null`() {
        // The provider keys are string literals, so these four read the raw file.
        assertTrue("Gemini reads usageMetadata", raw("GeminiResponse.kt").contains("usageMetadata"))
        assertTrue("NVIDIA reads usage", raw("NvidiaResponse.kt").contains("prompt_tokens"))
        assertTrue("the accumulator carries the report", raw("AiAnswer.kt").contains("reportedUsage"))
        assertTrue("and the answer exposes it", ai("AiAnswer.kt").contains("val usage: AiTokenUsage?"))
        // The old NVIDIA behaviour (drop a usage-only event) must be gone: the
        // event still carries no text, and now it carries the counts. The
        // null-ness contract is unchanged — the event is a chunk when a `usage`
        // object is present, and not a chunk at all when it is absent.
        assertEquals(
            "both NVIDIA parse sites read the counts (usage-only event and content event)",
            2,
            Regex(Regex.escape("usageFrom(usageObject)")).findAll(raw("NvidiaResponse.kt")).count()
        )
        assertTrue(
            "and the no-usage event is still not a chunk",
            raw("NvidiaResponse.kt").contains("return if (usageObject != null) GeminiChunk(usage = usageFrom(usageObject)) else null")
        )
    }

    @Test
    fun `D6 holds - the readout is display state with no store of any kind`() {
        val policy = ai("AiMeasurements.kt")
        for (needle in listOf(
            "SharedPreferences", "edit()", "DataStore", "File(", "getSharedPreferences",
            "writeText", "appendText", "commit()", "apply()", "Log."
        )) {
            assertFalse("AiMeasurements must not contain $needle", policy.contains(needle))
        }
        // reset points: the next Send and clear()
        val vm = ai("AiViewModel.kt")
        assertTrue("a new request clears the numbers", vm.contains("measurements = AiMeasurements()"))
        assertTrue("clear() resets the clock too", vm.contains("measureStartedAtMs = 0L"))
    }

    @Test
    fun `the security pins of every earlier AI level still stand`() {
        val vm = ai("AiViewModel.kt")
        assertEquals("exactly three stream sites", 3, Regex("client[.]stream[(]").findAll(vm).count())

        val openUri = aiDir.listFiles { f -> f.extension == "kt" }!!
            .sumOf { f -> Regex("openUri[(]").findAll(RepoFiles.codeOnly(f.readText())).count() }
        assertEquals("exactly two openUri sites in ui/ai", 2, openUri)

        for (file in aiDir.listFiles { f -> f.extension == "kt" }!!) {
            val code = RepoFiles.codeOnly(file.readText())
            assertFalse(
                "${file.name} must not borrow the editor's search filter",
                code.contains("isSearchable")
            )
        }
    }

    @Test
    fun `the heap sample lives outside ui-ai and the AI package stays command-free`() {
        // A single boundary sample, collected outside ui/ai: the standing guard
        // forbids `Runtime.getRuntime` inside the package (command execution),
        // so AiViewModel must not grow one back.
        for (file in aiDir.listFiles { f -> f.extension == "kt" }!!) {
            assertFalse(
                "${file.name} must not reach for Runtime",
                RepoFiles.codeOnly(file.readText()).contains("Runtime.getRuntime")
            )
        }
        val vm = ai("AiViewModel.kt")
        assertEquals("one boundary sample per request", 1, calls(vm, "HeapProbe.usedHeapBytes"))
        val probe = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/performance/HeapProbe.kt").readText()
        assertTrue("and it is the only heap reader", probe.contains("fun usedHeapBytes()"))
        for (needle in listOf("File(", "Log.", "DataStore", "SharedPreferences", "openConnection", "ProcessBuilder")) {
            assertFalse("the probe reads nothing but the heap", probe.contains(needle))
        }
    }

    @Test
    fun `no ceiling moved`() {
        val loop = raw("AiAgentLoop.kt")
        assertTrue(loop.contains("const val MAX_TURNS = 12"))
        assertTrue(loop.contains("const val MAX_TOOL_CALLS = 24"))
        assertTrue(loop.contains("const val MAX_RUNS = 2"))
        assertTrue(loop.contains("const val MAX_IDENTICAL_REPEATS = 3"))
        assertTrue(raw("AiProjectFiles.kt").contains("const val MAX_READ_CHARS = 24_000"))
        assertTrue(raw("AiTools.kt").contains("const val MAX_RESULT_CHARS = 8_000"))
        assertTrue(raw("AiTools.kt").contains("const val MAX_BATCH_READS = 8"))
        // The Level 12 bounds are their own, and they bound what may be rendered.
        assertTrue(raw("AiMeasurements.kt").contains("MAX_REPORTED_TOKENS = 2_000_000"))
    }
}
