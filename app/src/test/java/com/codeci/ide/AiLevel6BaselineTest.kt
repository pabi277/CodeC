package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentBudget
import com.codeci.ide.ui.ai.AiAgentDecision
import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiAgentPolicy
import com.codeci.ide.ui.ai.AiAgentPrompt
import com.codeci.ide.ui.ai.AiAgentStep
import com.codeci.ide.ui.ai.AiAgentStepKind
import com.codeci.ide.ui.ai.AiCopy
import com.codeci.ide.ui.ai.AiProjectFiles
import com.codeci.ide.ui.ai.AiProjectReader
import com.codeci.ide.ui.ai.AiProviders
import com.codeci.ide.ui.ai.AiRepoMap
import com.codeci.ide.ui.ai.AiToolCall
import com.codeci.ide.ui.ai.AiToolLimits
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.ai.AiToolParse
import com.codeci.ide.ui.ai.AiToolPolicy
import com.codeci.ide.ui.ai.AiToolProjectView
import com.codeci.ide.ui.ai.AiToolProtocol
import com.codeci.ide.ui.ai.AiToolRequest
import com.codeci.ide.ui.ai.AiToolRunner
import com.codeci.ide.ui.ai.AiToolVerdict
import com.codeci.ide.ui.ai.AiPromptText
import com.codeci.ide.ui.ai.NvidiaRequest
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 83 / AI Level 6 — an offline, deterministic baseline of the existing
 * read/tool path. It records current failures; it does not change product
 * behavior, call a provider, or claim task-quality/device measurements.
 */
class AiLevel6BaselineTest {

    @get:Rule
    val temporary = TemporaryFolder()

    private fun projects(): List<AiLevel6FixtureSupport.Project> =
        AiLevel6FixtureSupport.create(temporary.newFolder("fixture-suite"))

    private fun scan(project: AiLevel6FixtureSupport.Project) = AiProjectReader.scan(
        root = project.root,
        question = "explain the fixture source and its line markers",
        openPath = project.normalPath,
        openText = null,
        openDirty = false
    )

    private fun readCall(path: String, start: Int, end: Int, existingPaths: Set<String>): AiToolCall {
        val request = AiToolRequest(
            rawName = "read_file",
            args = linkedMapOf("path" to path, "start" to start.toString(), "end" to end.toString())
        )
        return (AiToolPolicy.validate(
            request,
            AiToolProjectView(existingPaths = existingPaths, runsRemaining = AiAgentLimits.MAX_RUNS)
        ) as AiToolVerdict.Allowed).call
    }

    @Test
    fun `four synthetic projects contain the required normal large minified binary secret symlink and injection cases`() {
        val fixtures = projects()
        assertEquals(listOf("c", "python", "javascript", "html"), fixtures.map { it.id })

        for (project in fixtures) {
            val normal = File(project.root, project.normalPath).readLines()
            assertEquals(300, normal.size)
            assertTrue(normal.first().contains("L6_${project.id.uppercase()}_NORMAL_0001"))
            assertTrue(normal[149].contains("L6_${project.id.uppercase()}_NORMAL_0150"))
            assertTrue(normal.last().contains("L6_${project.id.uppercase()}_NORMAL_0300"))

            val large = File(project.root, project.largePath)
            assertTrue(large.length() > AiProjectFiles.MAX_READ_CHARS)
            assertEquals(2_000, large.readLines().size)
            assertTrue(large.readText().contains(project.largeEndMarker))

            val minified = File(project.root, project.minifiedPath).readText()
            assertFalse(minified.contains('\n'))
            assertTrue(minified.isNotBlank())
            assertTrue(File(project.root, project.binaryPath).readBytes().contains(0.toByte()))
            assertTrue(File(project.root, project.envPath).readText().contains("not-a-real-key"))
            assertTrue(File(project.root, project.injectionPath).readText().contains("Ignore previous instructions"))

            val link = File(project.root, project.escapingSymlinkPath)
            assertTrue(AiProjectReader.isSymlink(link))
            assertFalse(AiProjectReader.insideRoot(link, project.root.canonicalFile))
            assertTrue(project.outsideTarget.isFile)
        }
    }

    @Test
    fun `the current project walk omits secret binary and escaping paths but treats injection text as ordinary data`() {
        for (project in projects()) {
            val scan = scan(project)
            val admitted = scan.allTextPaths
            assertTrue(admitted.contains(project.normalPath))
            assertTrue(admitted.contains(project.largePath))
            assertTrue(admitted.contains(project.minifiedPath))
            assertTrue(admitted.contains(project.injectionPath))
            assertFalse(admitted.contains(project.envPath))
            assertFalse(admitted.contains(project.binaryPath))
            assertFalse(admitted.contains(project.escapingSymlinkPath))
            assertEquals(1, scan.skippedSecret)
            assertTrue(scan.skippedNotText >= 1)
            assertFalse(scan.candidates.any { it.text.contains("not-a-real-key") })
            assertTrue(File(project.root, project.injectionPath).readText().contains("Ignore previous instructions"))
        }
    }

    @Test
    fun `the existing tool result is cut again for the timeline before the next model request`() {
        for (project in projects()) {
            val scan = scan(project)
            val toolCall = readCall(
                path = project.normalPath,
                start = 1,
                end = AiToolLimits.MAX_READ_LINES,
                existingPaths = scan.allTextPaths.toSet()
            )
            val outcome = AiToolRunner.execute(toolCall, project.root, scan.allTextPaths)
            assertTrue(outcome.ok)
            assertTrue(outcome.truncated)
            assertTrue(outcome.text.startsWith("FILE ${project.normalPath} — lines 1-300 of 300"))
            assertTrue(outcome.text.contains(AiToolRunner.CUT_NOTE))
            assertTrue(outcome.text.length <= AiToolLimits.MAX_RESULT_CHARS)

            // This is the exact production boundary in AiViewModel.executeToolBatch.
            val timelineDetail = outcome.text.take(AiAgentLimits.MAX_STEP_DETAIL_CHARS)
            assertEquals(AiAgentLimits.MAX_STEP_DETAIL_CHARS, timelineDetail.length)
            assertFalse("the UI clip drops the runner's honest cut marker", timelineDetail.contains(AiToolRunner.CUT_NOTE))
            val packed = AiAgentPrompt.pack(
                question = "Explain the full ${project.normalPath} line by line for a beginner.",
                mapText = "PROJECT MAP — ${project.id} fixture; ${project.normalPath} (300 lines)",
                steps = listOf(
                    AiAgentStep(
                        kind = AiAgentStepKind.TOOL,
                        title = "read_file ${project.normalPath} (lines 1-${AiToolLimits.MAX_READ_LINES})",
                        detail = timelineDetail
                    )
                )
            )
            assertTrue(packed.text.contains("1: "))
            assertFalse(packed.text.contains("L6_${project.id.uppercase()}_NORMAL_0300"))
            assertFalse(packed.text.contains(AiToolRunner.CUT_NOTE))
            val deliveredLines = Regex("""(?m)^\d+: """).findAll(packed.text).count()
            assertTrue("expected only a small prefix, got $deliveredLines lines for ${project.id}", deliveredLines in 15..30)

            // Measure the exact synthetic JSON body without sending it anywhere.
            val requestBody = NvidiaRequest.body(
                model = AiProviders.NVIDIA_DEFAULT,
                systemInstruction = AiPromptText.AGENT_ASK_SYSTEM_INSTRUCTION,
                userText = packed.text,
                maxOutputTokens = 32_768
            )
            val requestBytes = requestBody.toByteArray(StandardCharsets.UTF_8).size
            assertTrue(requestBytes > packed.chars)
            println(
                "AI_LEVEL6_BASELINE fixture=${project.id} requested=1-${AiToolLimits.MAX_READ_LINES} " +
                    "fileLines=300 delivered=$deliveredLines resultChars=${outcome.text.length} " +
                    "timelineChars=${timelineDetail.length} requestBodyBytes=$requestBytes"
            )
        }
    }

    @Test
    fun `the prefix-only reader cannot reach a generated file tail range`() {
        for (project in projects()) {
            val scan = scan(project)
            val toolCall = readCall(
                path = project.largePath,
                start = 1_900,
                end = 2_000,
                existingPaths = scan.allTextPaths.toSet()
            )
            val outcome = AiToolRunner.execute(toolCall, project.root, scan.allTextPaths)
            assertTrue(outcome.ok)
            assertFalse(outcome.text.contains(project.largeEndMarker))
            val coverage = Regex("""lines (\d+)-(\d+) of (\d+)""").find(outcome.text)
                ?: throw AssertionError("read result had no line header: ${outcome.text.take(160)}")
            val deliveredStart = coverage.groupValues[1].toInt()
            val deliveredEnd = coverage.groupValues[2].toInt()
            assertTrue("the reader returned a prefix range, not the requested tail", deliveredStart < 1_900)
            assertEquals(deliveredStart, deliveredEnd)
            println(
                "AI_LEVEL6_BASELINE tail=${project.id} sourceLines=2000 requested=1900-2000 " +
                    "declared=${deliveredStart}-${deliveredEnd} of ${coverage.groupValues[3]} " +
                    "tailMarkerReached=${outcome.text.contains(project.largeEndMarker)}"
            )
        }
    }

    @Test
    fun `a 49-call repeated-read replay reaches 24 executions 25 refusals and the unclamped mixed counter`() {
        val project = projects().first { it.id == "javascript" }
        val scan = scan(project)
        val oneBlock = """<<<CODEC_TOOL name="read_file">>>
path: ${project.normalPath}
start: 1
end: ${AiToolLimits.MAX_READ_LINES}
<<<END_CODEC_TOOL>>>"""
        val parsed = AiToolProtocol.parse(List(49) { oneBlock }.joinToString("\n")) as AiToolParse.Calls
        assertEquals(49, parsed.calls.size)
        assertTrue(parsed.calls.all { it.name == AiToolName.READ_FILE && it.args["path"] == project.normalPath })

        val decision = AiAgentPolicy.decide(
            parsed = parsed,
            budget = AiAgentBudget(startedAtMs = 1L),
            nowMs = 2L,
            projectView = AiToolProjectView(scan.allTextPaths.toSet(), AiAgentLimits.MAX_RUNS)
        ) as AiAgentDecision.ExecuteTools
        assertEquals(24, decision.calls.size)
        assertEquals(25, decision.denied.size)
        assertTrue(decision.denied.all { it.reason.contains("budget") })

        val outcomes = decision.calls.map { AiToolRunner.execute(it, project.root, scan.allTextPaths) }
        assertEquals(24, outcomes.size)
        assertEquals(1, outcomes.map { it.text }.distinct().size)
        val executionDuplicates = outcomes.size - 1
        val requestDuplicates = parsed.calls.size - 1
        assertEquals(23, executionDuplicates)
        assertEquals(48, requestDuplicates)

        // The ViewModel adds executed and refused attempts without clamping.
        val displayedCalls = AiAgentBudget().withToolCalls(decision.calls.size + decision.denied.size).toolCallsUsed
        assertEquals(49, displayedCalls)
        assertEquals(
            "9 of 12 steps · 49 of 24 reads · 0 of 2 runs",
            AiCopy.agentUsageLine(turns = 9, toolCalls = displayedCalls, runs = 0)
        )
        assertEquals(null, AiAgentBudget(toolCallsUsed = displayedCalls).blockModelTurn(2L))
        println(
            "AI_LEVEL6_BASELINE replay=requests:${parsed.calls.size},executed:${decision.calls.size}," +
                "refused:${decision.denied.size},duplicateRequests:$requestDuplicates," +
                "duplicateExecutions:$executionDuplicates,displayed:$displayedCalls/24"
        )
    }
}
