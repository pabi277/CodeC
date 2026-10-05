package com.codeci.ide

import com.codeci.ide.ui.ai.AiSelfCheck
import com.codeci.ide.ui.ai.AiToolLimits
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.ai.AiToolParse
import com.codeci.ide.ui.ai.AiToolPolicy
import com.codeci.ide.ui.ai.AiToolProjectView
import com.codeci.ide.ui.ai.AiToolProtocol
import com.codeci.ide.ui.ai.AiToolRequest
import com.codeci.ide.ui.ai.AiToolVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 80 (AI Level 4) — the tool wire format and its validation: every rule
 * here is a refusal, and a refusal is always reported back to the model
 * (`04_AGENT_TOOLS_AND_RUN_LOOP.md`: *"unknown tools fail closed"*, *"Every
 * call has validated typed arguments"*).
 */
class AiToolProtocolTest {

    private val view = AiToolProjectView(
        existingPaths = setOf("src/main.c", "src/util.c", "README.md", "app.py"),
        runsRemaining = 2
    )

    private fun call(name: String, vararg args: Pair<String, String>) =
        AiToolRequest(name, linkedMapOf(*args))

    private fun verdict(name: String, vararg args: Pair<String, String>) =
        AiToolPolicy.validate(call(name, *args), view)

    // ---- parsing ----------------------------------------------------------

    @Test
    fun `a single block is parsed with its arguments`() {
        val parsed = AiToolProtocol.parse(
            """
            I will look at the main file.
            <<<CODEC_TOOL name="read_file">>>
            path: src/main.c
            start: 1
            end: 60
            <<<END_CODEC_TOOL>>>
            """.trimIndent()
        )
        assertTrue(parsed is AiToolParse.Calls)
        val calls = (parsed as AiToolParse.Calls)
        assertEquals(1, calls.calls.size)
        assertEquals(AiToolName.READ_FILE, calls.calls[0].name)
        assertEquals("src/main.c", calls.calls[0].args["path"])
        assertEquals("I will look at the main file.", calls.prose)
    }

    @Test
    fun `several blocks in one answer are all parsed in order`() {
        val parsed = AiToolProtocol.parse(
            """
            <<<CODEC_TOOL name="search_project">>>
            query: malloc
            <<<END_CODEC_TOOL>>>
            <<<CODEC_TOOL name="list_files">>>
            ext: c
            <<<END_CODEC_TOOL>>>
            """.trimIndent()
        ) as AiToolParse.Calls
        assertEquals(listOf(AiToolName.SEARCH_PROJECT, AiToolName.LIST_FILES), parsed.calls.map { it.name })
        assertEquals("", parsed.prose)
    }

    @Test
    fun `an unclosed block is malformed and never becomes a call`() {
        val parsed = AiToolProtocol.parse(
            "<<<CODEC_TOOL name=\"read_file\">>>\npath: src/main.c"
        )
        assertTrue(parsed is AiToolParse.Malformed)
    }

    @Test
    fun `a line without a colon is malformed`() {
        val parsed = AiToolProtocol.parse(
            "<<<CODEC_TOOL name=\"read_file\">>>\njust some words\n<<<END_CODEC_TOOL>>>"
        )
        assertTrue(parsed is AiToolParse.Malformed)
    }

    @Test
    fun `a repeated argument is malformed rather than last-wins`() {
        val parsed = AiToolProtocol.parse(
            "<<<CODEC_TOOL name=\"read_file\">>>\npath: a.c\npath: b.c\n<<<END_CODEC_TOOL>>>"
        )
        assertTrue(parsed is AiToolParse.Malformed)
    }

    @Test
    fun `a block with no name fails closed`() {
        val parsed = AiToolProtocol.parse(
            "<<<CODEC_TOOL>>>\npath: a.c\n<<<END_CODEC_TOOL>>>"
        ) as AiToolParse.Calls
        assertEquals(1, parsed.calls.size)
        assertNull(parsed.calls[0].name)
        assertTrue(AiToolPolicy.validate(parsed.calls[0], view) is AiToolVerdict.Denied)
    }

    @Test
    fun `the timeline description names the tool and its target`() {
        assertEquals(
            "read_file src/main.c (lines 1-60)",
            AiToolProtocol.describe(call("read_file", "path" to "src/main.c", "start" to "1", "end" to "60"))
        )
        assertEquals("search_project \"malloc\"", AiToolProtocol.describe(call("search_project", "query" to "malloc")))
        assertEquals("list_files src/ *.c", AiToolProtocol.describe(call("list_files", "path" to "src", "ext" to "c")))
        assertTrue(AiToolProtocol.describe(call("delete_everything")).contains("unknown tool"))
    }

    // ---- validation: the shape of the refusals -----------------------------

    @Test
    fun `an unknown tool is denied and the model is told the real list`() {
        val v = verdict("run_terminal", "cmd" to "rm -rf /") as AiToolVerdict.Denied
        assertTrue(v.reason.contains("unknown tool"))
        assertTrue(v.reason.contains("read_file"))
        assertTrue(v.reason.contains("request_run"))
    }

    @Test
    fun `an unexpected argument is denied rather than ignored`() {
        val v = verdict("read_file", "path" to "src/main.c", "encoding" to "utf16") as AiToolVerdict.Denied
        assertTrue(v.reason.contains("encoding"))
    }

    @Test
    fun `read_file refuses traversal absolute paths credentials and unknown files`() {
        assertTrue(verdict("read_file", "path" to "../outside.txt") is AiToolVerdict.Denied)
        assertTrue(verdict("read_file", "path" to "/etc/passwd") is AiToolVerdict.Denied)
        assertTrue(verdict("read_file", "path" to "src/.env") is AiToolVerdict.Denied)
        assertTrue(verdict("read_file", "path" to "src/notes.bin") is AiToolVerdict.Denied)
        val missing = verdict("read_file", "path" to "src/other.c") as AiToolVerdict.Denied
        assertTrue(missing.reason.contains("list_files"))
    }

    @Test
    fun `read_file needs a path and sane bounds`() {
        assertTrue(verdict("read_file") is AiToolVerdict.Denied)
        assertTrue(verdict("read_file", "path" to "src/main.c", "start" to "x") is AiToolVerdict.Denied)
        assertTrue(verdict("read_file", "path" to "src/main.c", "start" to "0") is AiToolVerdict.Denied)
        assertTrue(verdict("read_file", "path" to "src/main.c", "start" to "60", "end" to "10") is AiToolVerdict.Denied)
        val wide = verdict(
            "read_file", "path" to "src/main.c",
            "start" to "1", "end" to (AiToolLimits.MAX_READ_LINES + 1).toString()
        )
        assertTrue(wide is AiToolVerdict.Denied)
    }

    @Test
    fun `read_file builds the default range from the start line`() {
        val allowed = verdict("read_file", "path" to "src/main.c", "start" to "5") as AiToolVerdict.Allowed
        assertEquals(5, allowed.call.start)
        assertEquals(5 + AiToolLimits.MAX_READ_LINES - 1, allowed.call.end)
    }

    @Test
    fun `search_project needs a real query and honours its cap`() {
        assertTrue(verdict("search_project") is AiToolVerdict.Denied)
        assertTrue(verdict("search_project", "query" to "a") is AiToolVerdict.Denied)
        assertTrue(verdict("search_project", "query" to "x".repeat(AiToolLimits.MAX_QUERY_CHARS + 1)) is AiToolVerdict.Denied)
        val allowed = verdict("search_project", "query" to "malloc", "max" to "9999") as AiToolVerdict.Allowed
        assertEquals(AiToolLimits.MAX_SEARCH_HITS, allowed.call.max)
    }

    @Test
    fun `list_files validates its directory and extension`() {
        assertTrue(verdict("list_files") is AiToolVerdict.Allowed)
        assertTrue(verdict("list_files", "path" to "src") is AiToolVerdict.Allowed)
        assertTrue(verdict("list_files", "path" to "../x") is AiToolVerdict.Denied)
        assertTrue(verdict("list_files", "path" to ".git") is AiToolVerdict.Denied)
        assertTrue(verdict("list_files", "path" to "empty") is AiToolVerdict.Denied)
        assertTrue(verdict("list_files", "ext" to "c") is AiToolVerdict.Allowed)
        assertTrue(verdict("list_files", "ext" to "c++") is AiToolVerdict.Denied)
        assertTrue(verdict("list_files", "ext" to "tar.gz") is AiToolVerdict.Denied)
    }

    @Test
    fun `request_run is allowed only inside the run budget and only on real files`() {
        assertTrue(verdict("request_run") is AiToolVerdict.Allowed)
        assertTrue(verdict("request_run", "target" to "src/main.c") is AiToolVerdict.Allowed)
        assertTrue(verdict("request_run", "target" to "nope.c") is AiToolVerdict.Denied)
        assertTrue(verdict("request_run", "target" to "../main.c") is AiToolVerdict.Denied)
        val spent = AiToolPolicy.validate(
            call("request_run"),
            view.copy(runsRemaining = 0)
        ) as AiToolVerdict.Denied
        assertTrue(spent.reason.contains("budget"))
    }

    @Test
    fun `the self-check's run question names this tool and asks for what it takes`() {
        // Phase 93b: the scripted question is a literal (AiSelfCheck stays free
        // of every other Ai class), so the literal is pinned here. The round-2
        // failure was a question asking for a *shell command* — an argument the
        // allow-list refuses — so this also pins that it asks for a project run.
        val prompt = AiSelfCheck.STEPS.single { it.id == "run" }.prompt!!
        assertTrue("the wire name is the real one", prompt.contains(AiToolName.REQUEST_RUN.wire))
        assertTrue("and the request it can deliver is named", prompt.contains("run this project"))
        assertTrue("with no invented argument key", !prompt.contains("command"))
    }

    @Test
    fun `a directory-only helper refuses what a file path helper refuses`() {
        assertNull(AiToolPolicy.safeDirectoryPath("/etc"))
        assertNull(AiToolPolicy.safeDirectoryPath("a/../b"))
        assertNull(AiToolPolicy.safeDirectoryPath("build"))
        assertNull(AiToolPolicy.safeDirectoryPath(".codec"))
        assertEquals("src/sub", AiToolPolicy.safeDirectoryPath("src/sub/"))
        assertEquals("src", AiToolPolicy.safeDirectoryPath("./src"))
    }
}
