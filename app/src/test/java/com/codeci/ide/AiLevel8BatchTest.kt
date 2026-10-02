package com.codeci.ide

import com.codeci.ide.ui.ai.AiAgentLimits
import com.codeci.ide.ui.ai.AiToolLimits
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.ai.AiToolParse
import com.codeci.ide.ui.ai.AiToolPolicy
import com.codeci.ide.ui.ai.AiToolProjectView
import com.codeci.ide.ui.ai.AiToolProtocol
import com.codeci.ide.ui.ai.AiToolRequest
import com.codeci.ide.ui.ai.AiToolRunner
import com.codeci.ide.ui.ai.AiToolVerdict
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 85 (AI Level 8, item 3) — the `read_files` batch tool. It proves the
 * batch is parallel read-only IO with PER-FILE security: one refused path never
 * blocks its siblings (**S5**), an escaping symlink contributes nothing, order is
 * stable, every file carries its own status, and Stop ends a batch promptly.
 */
class AiLevel8BatchTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File

    private fun tree() {
        root = tmp.newFolder("project")
        File(root, "src").mkdirs()
        File(root, "src/main.c").writeText("int main(void) {\n  printf(\"hi\");\n  return 0;\n}\n")
        File(root, "src/util.c").writeText("int helper(void) { return 1; }\n")
        File(root, "README.md").writeText("# Demo\nA malloc example\n")
        File(root, ".env").writeText("API_KEY=not-a-real-key\nmalloc\n")
        File(root, "build").mkdirs()
        File(root, "build/out.c").writeText("int stale(void){return 0;}\n")
    }

    /** Exactly what the Level 2 walk would admit (no `.env`, no build output). */
    private val admitted = listOf("README.md", "src/main.c", "src/util.c")

    private fun req(vararg args: Pair<String, String>) =
        AiToolRequest(rawName = AiToolName.READ_FILES.wire, args = linkedMapOf(*args))

    /** Validate then run, exactly as the loop does. */
    private fun run(
        paths: String,
        extraAdmitted: List<String> = emptyList(),
        dirtyBuffers: Map<String, String> = emptyMap(),
        shouldStop: () -> Boolean = { false }
    ): AiToolRunner.Outcome {
        val view = AiToolProjectView(
            existingPaths = (admitted + extraAdmitted).toSet(),
            runsRemaining = AiAgentLimits.MAX_RUNS
        )
        val verdict = AiToolPolicy.validate(req("paths" to paths), view)
        assertTrue("expected the batch to validate, got $verdict", verdict is AiToolVerdict.Allowed)
        val call = (verdict as AiToolVerdict.Allowed).call
        return AiToolRunner.execute(call, root, admitted + extraAdmitted, dirtyBuffers, shouldStop)
    }

    @Test
    fun `read_files delivers siblings while a secret path in the same batch is refused`() {
        tree()
        val out = run("src/main.c, .env, src/util.c")
        assertTrue(out.ok)
        // Siblings succeed.
        assertTrue(out.text.contains("printf"))
        assertTrue(out.text.contains("helper"))
        // The secret is refused per-file and its contents never appear (S5).
        assertTrue(out.text.contains(".env"))
        assertTrue(out.text.contains("[refused: credential-shaped]"))
        assertFalse(out.text.contains("not-a-real-key"))
    }

    @Test
    fun `read_files refuses an escaping symlink and it contributes nothing`() {
        tree()
        val outside = tmp.newFile("outside.c")
        outside.writeText("int leaked(void){return 42;}\n")
        val link = File(root, "src/link.c")
        val made = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        if (!made) return
        // Admit the link so the run-time containment check (not the walk filter)
        // is what refuses it — the symlink must still contribute nothing.
        val out = run("src/main.c, src/link.c", extraAdmitted = listOf("src/link.c"))
        assertTrue(out.ok)
        assertTrue(out.text.contains("printf"))
        assertTrue(out.text.contains("src/link.c"))
        assertTrue(out.text.contains("[refused:"))
        assertFalse("the escaping target's contents must never be delivered", out.text.contains("leaked"))
        assertFalse(out.text.contains("42"))
    }

    @Test
    fun `read_files keeps the requested order stable`() {
        tree()
        val out = run("src/util.c, README.md, src/main.c")
        assertTrue(out.ok)
        val util = out.text.indexOf("src/util.c")
        val readme = out.text.indexOf("README.md")
        val main = out.text.indexOf("src/main.c")
        assertTrue("util.c must come first", util in 0 until readme)
        assertTrue("README.md must come second", readme in 0 until main)
        assertTrue(util < readme && readme < main)
    }

    @Test
    fun `read_files reports a per-file refusal for a missing file beside a good one`() {
        tree()
        // Admit gone.c as if the walk had seen it, then leave it absent on disk so
        // the run-time read (not the walk filter) is what reports it missing.
        val out = run("src/main.c, src/gone.c", extraAdmitted = listOf("src/gone.c"))
        assertTrue(out.ok)
        assertTrue(out.text.contains("printf"))
        assertTrue(out.text.contains("src/gone.c"))
        assertTrue(out.text.contains("[refused: not found]"))
    }

    @Test
    fun `read_files refuses a file the walk never admitted`() {
        tree()
        val out = run("src/main.c, build/out.c")
        assertTrue(out.ok)
        assertTrue(out.text.contains("printf"))
        assertTrue(out.text.contains("build/out.c"))
        assertTrue(out.text.contains("[refused: not a code or text file"))
        assertFalse(out.text.contains("stale"))
    }

    @Test
    fun `read_files honours a per-file range and says when more lines follow`() {
        tree()
        val out = run("src/main.c:2-3")
        assertTrue(out.ok)
        assertTrue(out.text.contains("printf"))
        assertTrue(out.text.contains("return 0"))
        assertFalse(out.text.contains("int main"))
        // Lines 2-3 of a 4-line file: line 4 still follows, so coverage is partial.
        assertTrue(out.text.contains("[partial: more lines follow]"))
    }

    @Test
    fun `read_files stops promptly and reads nothing once stopped`() {
        tree()
        val out = run("src/main.c, src/util.c, README.md") { true }
        assertTrue(out.ok)
        assertTrue(out.truncated)
        assertTrue(out.text.contains("[partial: stopped by the user]"))
        // Stop is checked before each file, so no file's body is delivered.
        assertFalse(out.text.contains("printf"))
        assertFalse(out.text.contains("helper"))
    }

    @Test
    fun `read_files validation refuses an empty list and an oversized batch`() {
        tree()
        val view = AiToolProjectView(existingPaths = admitted.toSet(), runsRemaining = AiAgentLimits.MAX_RUNS)
        assertTrue(AiToolPolicy.validate(req("paths" to "  "), view) is AiToolVerdict.Denied)
        val tooMany = (1..AiToolLimits.MAX_BATCH_READS + 1).joinToString(", ") { "src/main.c" }
        assertTrue(AiToolPolicy.validate(req("paths" to tooMany), view) is AiToolVerdict.Denied)
        // Exactly at the cap is fine.
        val atCap = (1..AiToolLimits.MAX_BATCH_READS).joinToString(", ") { "src/main.c" }
        assertTrue(AiToolPolicy.validate(req("paths" to atCap), view) is AiToolVerdict.Allowed)
    }

    @Test
    fun `read_files rejects an unknown argument and a malformed range`() {
        tree()
        val view = AiToolProjectView(existingPaths = admitted.toSet(), runsRemaining = AiAgentLimits.MAX_RUNS)
        assertTrue(
            AiToolPolicy.validate(
                AiToolRequest(AiToolName.READ_FILES.wire, linkedMapOf("paths" to "src/main.c", "start" to "1")),
                view
            ) is AiToolVerdict.Denied
        )
        assertTrue(AiToolPolicy.validate(req("paths" to "src/main.c:9-2"), view) is AiToolVerdict.Denied)
    }

    @Test
    fun `the batch result stays within the one result cap`() {
        tree()
        val out = run("src/main.c, src/util.c, README.md, src/main.c, src/util.c")
        assertTrue(out.text.length <= AiToolLimits.MAX_RESULT_CHARS)
    }

    @Test
    fun `a read_files block parses and validates into a batch call`() {
        tree()
        val answer = "<<<CODEC_TOOL name=\"read_files\">>>\n" +
            "paths: src/main.c, src/util.c:2-3\n" +
            "<<<END_CODEC_TOOL>>>"
        val parsed = AiToolProtocol.parse(answer)
        assertTrue(parsed is AiToolParse.Calls)
        val calls = (parsed as AiToolParse.Calls).calls
        assertEquals(1, calls.size)
        val view = AiToolProjectView(existingPaths = admitted.toSet(), runsRemaining = AiAgentLimits.MAX_RUNS)
        val verdict = AiToolPolicy.validate(calls[0], view)
        assertTrue(verdict is AiToolVerdict.Allowed)
        val call = (verdict as AiToolVerdict.Allowed).call
        assertEquals(AiToolName.READ_FILES, call.name)
        assertEquals(2, call.reads?.size)
        assertEquals("src/main.c", call.reads?.get(0)?.path)
        assertEquals(2, call.reads?.get(1)?.start)
        assertEquals(3, call.reads?.get(1)?.end)
    }

    @Test
    fun `the disclosed instructions name read_files additively`() {
        val text = AiToolProtocol.INSTRUCTIONS
        assertTrue(text.contains("read_files(paths)"))
        // Additive: the existing tools are still disclosed, never replaced.
        assertTrue(text.contains("read_file(path"))
        assertTrue(text.contains("list_files"))
        assertTrue(text.contains("search_project"))
        assertTrue(text.contains("request_run"))
    }
}
