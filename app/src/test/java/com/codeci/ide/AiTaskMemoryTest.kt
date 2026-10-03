package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentPrompt
import com.codeci.ide.ui.ai.AiAgentStep
import com.codeci.ide.ui.ai.AiAgentStepKind
import com.codeci.ide.ui.ai.AiAgentWorkingSet
import com.codeci.ide.ui.ai.AiTaskMemory
import com.codeci.ide.ui.ai.AiTaskMemoryCodec
import com.codeci.ide.ui.ai.AiTaskMemoryFile
import com.codeci.ide.ui.ai.AiTaskMemoryLimits
import com.codeci.ide.ui.ai.AiTaskMemoryProtocol
import com.codeci.ide.ui.ai.AiTaskMemoryRecord
import com.codeci.ide.ui.ai.AiTaskMemoryUpdate
import com.codeci.ide.ui.ai.AiTaskPlanItem
import com.codeci.ide.ui.ai.AiPrompt
import com.codeci.ide.ui.ai.AiSource
import com.codeci.ide.ui.ai.AiToolCall
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.ai.AiToolRunner
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Level 9's pure path/version/protocol rules against real JVM files. */
class AiTaskMemoryTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun project(): File = temporary.newFolder("project").apply { File(this, "src").mkdirs() }
    private fun readCall() = AiToolCall(
        name = AiToolName.READ_FILE,
        rawName = "read_file",
        path = "src/main.c",
        start = 1,
        end = 20
    )

    @Test
    fun `file working set is keyed by canonical admitted path and content version`() {
        val root = project()
        File(root, "src/main.c").writeText("int main(void) { return 0; }\n")
        val admitted = listOf("src/main.c")
        val plan = AiTaskMemory.EMPTY.prepareRead(readCall(), root, admitted, emptyMap(), nowMs = 10L)
        assertNotNull(plan.resultCacheKey)
        val key = plan.resultCacheKey ?: error("an admitted source file should be versioned")
        assertEquals(1, plan.memory.files.size)
        val cached = plan.memory.files.single()
        assertEquals(File(root, "src/main.c").canonicalPath, cached.canonicalPath)
        assertEquals(AiTaskMemory.contentVersion(cached.content), cached.contentVersion)
        assertTrue(key.contains(cached.canonicalPath))
        assertTrue(key.contains(cached.contentVersion))
        assertEquals(
            AiAgentWorkingSet.readKey(cached.canonicalPath, 1, 1, cached.contentVersion),
            key
        )

        val same = plan.memory.prepareRead(readCall(), root, admitted, emptyMap(), nowMs = 20L)
        assertEquals("an unchanged repeat has the identical versioned key", key, same.resultCacheKey)
        assertEquals("the unchanged version supplies its full cached file content", cached.content, same.cachedFiles["src/main.c"])
        val workingSet = AiAgentWorkingSet().record(key, "the original tool result")
        assertEquals("the exact result is reusable at zero runner executions", "the original tool result", workingSet.cached(same.resultCacheKey!!))
    }

    @Test
    fun `result cache key uses the effective line range for the current file version`() {
        val root = project()
        File(root, "src/main.c").writeText((1..30).joinToString("\n") { "line $it" } + "\n")
        val admitted = listOf("src/main.c")

        val wide = AiTaskMemory.EMPTY.prepareRead(
            readCall().copy(start = 1, end = 400), root, admitted, emptyMap(), nowMs = 1L
        )
        val exact = AiTaskMemory.EMPTY.prepareRead(
            readCall().copy(start = 1, end = 30), root, admitted, emptyMap(), nowMs = 2L
        )
        val tail = AiTaskMemory.EMPTY.prepareRead(
            readCall().copy(start = 2, end = 30), root, admitted, emptyMap(), nowMs = 3L
        )

        assertEquals("end is clamped to the versioned file's last line", exact.resultCacheKey, wide.resultCacheKey)
        assertNotEquals("a genuinely different delivered range has another identity", exact.resultCacheKey, tail.resultCacheKey)
    }

    @Test
    fun `dirty-buffer and disk changes invalidate file cache and source-bound findings`() {
        val root = project()
        val file = File(root, "src/main.c").apply { writeText("int original;\n") }
        val admitted = listOf("src/main.c")
        val first = AiTaskMemory.EMPTY.prepareRead(readCall(), root, admitted, emptyMap(), nowMs = 1L)
        val memory = first.memory.applyUpdate(
            AiTaskMemoryUpdate(
                findings = listOf(AiTaskMemoryRecord("The file declares the original symbol.", "src/main.c")),
                plan = listOf(com.codeci.ide.ui.ai.AiTaskPlanItem("Check the active buffer", done = false))
            ),
            admittedPaths = admitted,
            question = "Explain the original symbol"
        )
        assertEquals(first.memory.files.single().contentVersion, memory.findings.single().sourceVersion)

        val dirty = mapOf("src/main.c" to "int edited_in_buffer;\n")
        val afterDirty = memory.reconcile(root, admitted, dirty)
        assertTrue("a dirty-buffer version cannot reuse the disk snapshot", afterDirty.files.isEmpty())
        assertTrue("facts tied to the old version are invalidated too", afterDirty.findings.isEmpty())
        val dirtyPlan = afterDirty.prepareRead(readCall(), root, admitted, dirty, nowMs = 2L)
        assertNotEquals(first.resultCacheKey, dirtyPlan.resultCacheKey)
        assertTrue("dirty text is never persisted as a file cache", dirtyPlan.memory.files.isEmpty())

        file.writeText("int changed_on_disk;\n")
        val afterDisk = memory.reconcile(root, admitted, emptyMap())
        assertTrue("disk content changes invalidate the old snapshot", afterDisk.files.isEmpty())
        assertTrue(afterDisk.findings.isEmpty())
        val changedPlan = afterDisk.prepareRead(readCall(), root, admitted, emptyMap(), nowMs = 3L)
        assertNotEquals(first.resultCacheKey, changedPlan.resultCacheKey)
    }

    @Test
    fun `secret paths secret contents and unadmitted files never become snapshots`() {
        val root = project()
        File(root, "src/main.c").writeText("safe source\n")
        File(root, "src/credentials.c").writeText("const char *password = \"not-a-real-secret\";\n")
        File(root, ".env").writeText("API_KEY=not-a-real-key\n")
        val outsideSecret = File(temporary.newFolder("outside-secret"), "secret.c")
            .apply { writeText("const char *api_key = \"not-a-real-key\";\n") }
        val linkedPath = File(root, "src/linked.c")
        Files.createSymbolicLink(linkedPath.toPath(), outsideSecret.toPath())
        val admitted = listOf("src/main.c", "src/credentials.c", "src/linked.c", ".env")

        assertNotNull(AiTaskMemory.snapshot(root, "src/main.c", admitted))
        assertNull(AiTaskMemory.snapshot(root, "src/not-admitted.c", admitted))
        assertNull(AiTaskMemory.snapshot(root, ".env", admitted))
        assertNull(AiTaskMemory.snapshot(root, "src/credentials.c", admitted))
        assertNull("a manually admitted symlink to an out-of-project secret still fails closed", AiTaskMemory.snapshot(root, "src/linked.c", admitted))
        val linkedRead = AiTaskMemory.EMPTY.prepareRead(readCall().copy(path = "src/linked.c"), root, admitted, emptyMap(), nowMs = 1L)
        assertTrue(linkedRead.memory.files.isEmpty())
        assertNull(linkedRead.resultCacheKey)
    }

    @Test
    fun `first preview and Send pack the same task memory with the plan verbatim at the end`() {
        val memory = AiTaskMemory(
            findings = listOf(AiTaskMemoryRecord("The active tab uses the current buffer.")),
            decisions = listOf(AiTaskMemoryRecord("Read-only inspection remains the scope.")),
            plan = listOf(
                AiTaskPlanItem("Trace the tab switch path", done = true),
                AiTaskPlanItem("Check the dirty-buffer reader", done = false)
            )
        )
        val prompt = AiPrompt(
            source = AiSource.PROJECT,
            fileLabel = "3 files",
            languageLabel = "",
            context = "PROJECT MAP: src/main.c",
            question = "Why does switching tabs show stale text?",
            unsaved = false,
            truncated = false,
            agent = true,
            agentMemory = memory
        )
        val previewText = prompt.userText
        val sentText = AiAgentPrompt.pack(
            question = prompt.question,
            mapText = prompt.context,
            steps = listOf(AiAgentStep(AiAgentStepKind.TASK, prompt.question)),
            memory = memory
        ).text

        assertEquals("the preview and first Send use one packed user string", previewText, sentText)
        assertTrue(previewText.endsWith(memory.renderPlanRecitation()))
        assertTrue(previewText.contains("The active tab uses the current buffer."))
        assertTrue(previewText.contains("Read-only inspection remains the scope."))
        assertFalse("the recited plan replaces the generic continue tail", previewText.contains("Continue the task."))
        val finalTurn = AiAgentPrompt.finalSynthesis(prompt.question, prompt.context, emptyList(), memory = memory)
        assertTrue("the reserved final request also ends with the same plan", finalTurn.text.endsWith(memory.renderPlanRecitation()))
    }

    @Test
    fun `tight request budget preserves the exact plan suffix`() {
        val memory = AiTaskMemory(
            plan = listOf(
                AiTaskPlanItem("Inspect the buffer snapshot", done = true),
                AiTaskPlanItem("Verify the disk reader", done = false)
            )
        )
        val plan = memory.renderPlanRecitation()
        val packed = AiAgentPrompt.pack(
            question = "Investigate the editor state. ".repeat(80),
            mapText = "PROJECT MAP: " + "src/main.c contains the editor state. ".repeat(80),
            steps = listOf(
                AiAgentStep(
                    kind = AiAgentStepKind.TOOL,
                    title = "read_file src/main.c",
                    modelResult = "FILE src/main.c — lines 1-400 of 900 [partial]\\n" + "x".repeat(2_000)
                )
            ),
            budget = 1_200,
            memory = memory
        )

        assertTrue(packed.chars <= 1_200)
        assertTrue("the recitation remains the exact request suffix", packed.text.endsWith(plan))
        assertEquals(plan, packed.text.takeLast(plan.length))
    }

    @Test
    fun `structured protocol strips update from the answer and filters raw prompts and secrets`() {
        val root = project()
        File(root, "src/main.c").writeText("int main(void) { return 0; }\n")
        val admitted = listOf("src/main.c")
        val fileMemory = AiTaskMemory.EMPTY.prepareRead(readCall(), root, admitted, emptyMap(), nowMs = 1L).memory
        val question = "Why does the editor lose the active buffer after switching tabs?"
        val reply = "The buffer is stashed at a tab boundary.\n" +
            "${AiTaskMemoryProtocol.OPEN}\n" +
            "FINDING\tsrc/main.c\tThe current file declares the main entry point.\n" +
            "DECISION<TAB>-<TAB>Keep the editor's existing stash boundary unchanged.\n" +
            "PLAN\tDONE\tInspect the tab switch path\n" +
            "PLAN\tNEXT\tCheck the dirty-buffer snapshot\n" +
            "FINDING\t-\t$question\n" +
            "DECISION\t-\tapi_key=not-a-real-key-value\n" +
            "${AiTaskMemoryProtocol.CLOSE}"
        val extracted = AiTaskMemoryProtocol.extract(reply)
        assertEquals("The buffer is stashed at a tab boundary.", extracted.visibleText)
        assertNotNull(extracted.update)
        val next = fileMemory.applyUpdate(extracted.update!!, admitted, question)
        assertEquals(1, next.findings.size)
        assertEquals("src/main.c", next.findings.single().sourcePath)
        assertEquals(1, next.decisions.size)
        assertEquals(2, next.plan.size)
        assertEquals("Inspect the tab switch path", next.plan.first().text)

        val encoded = AiTaskMemoryCodec.encode(next)
        assertNotNull(encoded)
        assertTrue(encoded!!.size <= AiTaskMemoryLimits.MAX_STORE_BYTES)
        val decoded = AiTaskMemoryCodec.decode(encoded)
        assertEquals(next.sanitizedForStore(admitted), decoded)
        val storedBytes = String(encoded, Charsets.ISO_8859_1)
        assertFalse("the raw prompt is not stored", storedBytes.contains(question))
        assertFalse("the secret-like value is not stored", storedBytes.contains("not-a-real-key-value"))
    }

    @Test
    fun `Level 6 replay duplicate executions drop from 23 to zero with 23 cache hits`() {
        val root = project()
        File(root, "src/main.c").writeText("int main(void) { return 0; }\n")
        val admitted = listOf("src/main.c")
        val call = readCall()
        var memory = AiTaskMemory.EMPTY
        var workingSet = AiAgentWorkingSet()
        var executions = 0
        var reused = 0
        repeat(24) { index ->
            val prepared = memory.prepareRead(call, root, admitted, emptyMap(), nowMs = index.toLong())
            memory = prepared.memory
            workingSet = workingSet.note(prepared.progressSignature)
            val key = prepared.resultCacheKey
            val cached = key?.let(workingSet::cached)
            if (cached != null) {
                reused++
            } else {
                val outcome = AiToolRunner.execute(call, root, admitted)
                assertTrue(outcome.ok)
                executions++
                if (key != null) workingSet = workingSet.record(key, outcome.text)
            }
        }
        // AiLevel6BaselineTest intentionally remains the historical 23-duplicate
        // execution baseline; this post-fix replay measures the new result separately.
        assertEquals(1, executions)
        assertEquals(23, reused)
        println("AI_LEVEL9_REPLAY historical_duplicate_executions=23 post_fix_duplicate_executions=0 reused=$reused")
    }

    @Test
    fun `task memory limits the file working set and renders the plan at the end`() {
        val root = temporary.newFolder("many-files")
        val entries = (1..(AiTaskMemoryLimits.MAX_FILES + 4)).map { index ->
            val path = "file$index.c"
            val file = File(root, path).apply { writeText("int item$index;\n") }
            AiTaskMemoryFile(path, AiTaskMemory.contentVersion(file.readText()), file.readText(), index.toLong(), file.canonicalPath)
        }
        val memory = AiTaskMemory(files = entries)
        val bounded = memory.sanitizedForStore(entries.map { it.relativePath })
        assertEquals(AiTaskMemoryLimits.MAX_FILES, bounded.files.size)
        assertTrue(bounded.files.sumOf { it.content.toByteArray().size } <= AiTaskMemoryLimits.MAX_TOTAL_FILE_BYTES)
        assertTrue(memory.renderPlanRecitation().endsWith("\n"))
        assertTrue(memory.renderPlanRecitation().contains("No stored steps yet"))
    }
}
