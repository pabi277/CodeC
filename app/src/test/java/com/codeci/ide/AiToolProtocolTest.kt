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
import org.junit.Assert.assertFalse
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
    fun `a write-shaped tool call is refused and told the route that can write`() {
        // Phase 93c — the owner's *"why can't the agent write code?"*. A coding
        // model naturally calls write_file/edit_file/apply_patch; the refusal is
        // correct, but it must also name the one route that ends in a file
        // change, or the model answers in prose and nothing is ever written.
        for (name in listOf("write_file", "edit_file", "apply_patch")) {
            val v = verdict(name, "path" to "README.md", "content" to "x") as AiToolVerdict.Denied
            assertTrue("$name is refused", v.reason.contains("unknown tool"))
            assertTrue("$name is told there is no write tool", v.reason.contains("no write tool"))
            assertTrue("$name learns the block format", v.reason.contains("<<<CODEC_EDIT"))
            assertTrue("$name learns the user applies it", v.reason.contains("user reviews"))
        }
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

    // ---- Phase 94 tools ----------------------------------------------------

    @Test
    fun `find_files takes a glob and clamps its cap`() {
        val ok = verdict("find_files", "pattern" to "src/*.c")
        assertTrue(ok is AiToolVerdict.Allowed)
        assertEquals("src/*.c", (ok as AiToolVerdict.Allowed).call.pattern)
        val capped = verdict("find_files", "pattern" to "**/*.c", "max" to "9999")
        assertTrue(capped is AiToolVerdict.Allowed)
        assertEquals(AiToolLimits.MAX_LIST_ENTRIES, (capped as AiToolVerdict.Allowed).call.max)
    }

    @Test
    fun `find_files refuses an empty an escaping or a shell-shaped pattern`() {
        assertTrue(verdict("find_files") is AiToolVerdict.Denied)
        assertTrue(verdict("find_files", "pattern" to "") is AiToolVerdict.Denied)
        assertTrue(verdict("find_files", "pattern" to "/etc/*.conf") is AiToolVerdict.Denied)
        assertTrue(verdict("find_files", "pattern" to "../*.c") is AiToolVerdict.Denied)
        assertTrue(verdict("find_files", "pattern" to "*.c; rm -rf /") is AiToolVerdict.Denied)
        assertTrue(verdict("find_files", "pattern" to "a".repeat(AiToolLimits.MAX_PATTERN_CHARS + 1)) is AiToolVerdict.Denied)
        assertTrue("no path argument on a glob tool", verdict("find_files", "pattern" to "*.c", "path" to "src") is AiToolVerdict.Denied)
    }

    @Test
    fun `outline_file takes the same refusals as read_file and nothing extra`() {
        val ok = verdict("outline_file", "path" to "app.py")
        assertTrue(ok is AiToolVerdict.Allowed)
        assertEquals("app.py", (ok as AiToolVerdict.Allowed).call.path)
        assertTrue(verdict("outline_file") is AiToolVerdict.Denied)
        assertTrue(verdict("outline_file", "path" to ".env") is AiToolVerdict.Denied)
        assertTrue(verdict("outline_file", "path" to "/etc/hosts") is AiToolVerdict.Denied)
        assertTrue(verdict("outline_file", "path" to "src/../util.c") is AiToolVerdict.Denied)
        assertTrue(verdict("outline_file", "path" to "src/nope.c") is AiToolVerdict.Denied)
        assertTrue(verdict("outline_file", "path" to "app.py", "start" to "1") is AiToolVerdict.Denied)
    }

    @Test
    fun `read_run_output takes only a line count and clamps both ends`() {
        val plain = verdict("read_run_output")
        assertTrue(plain is AiToolVerdict.Allowed)
        assertEquals(null, (plain as AiToolVerdict.Allowed).call.lines)
        val small = verdict("read_run_output", "lines" to "1")
        assertEquals(1, (small as AiToolVerdict.Allowed).call.lines)
        val big = verdict("read_run_output", "lines" to "9999")
        assertEquals(AiToolLimits.MAX_OUTPUT_LINES, (big as AiToolVerdict.Allowed).call.lines)
        val zero = verdict("read_run_output", "lines" to "0")
        assertEquals(1, (zero as AiToolVerdict.Allowed).call.lines)
        assertTrue(verdict("read_run_output", "lines" to "ten") is AiToolVerdict.Denied)
        assertTrue("it reads the app's own panel, never a file", verdict("read_run_output", "path" to "src/main.c") is AiToolVerdict.Denied)
        assertTrue(verdict("read_run_output", "command" to "make") is AiToolVerdict.Denied)
    }

    @Test
    fun `the timeline line names the new tools and their target`() {
        val find = verdict("find_files", "pattern" to "*.md") as AiToolVerdict.Allowed
        assertTrue(AiToolProtocol.describeCall(find.call).contains("find_files"))
        assertTrue(AiToolProtocol.describeCall(find.call).contains("*.md"))
        val outline = verdict("outline_file", "path" to "app.py") as AiToolVerdict.Allowed
        assertTrue(AiToolProtocol.describeCall(outline.call).contains("outline_file app.py"))
        val output = verdict("read_run_output", "lines" to "20") as AiToolVerdict.Allowed
        assertTrue(AiToolProtocol.describeCall(output.call).contains("last 20 lines"))
    }

    @Test
    fun `the block reader tolerates the spellings a real model writes`() {
        for (spelling in listOf(
            "<<<CODEC_TOOL name=\"find_files\">>>",
            "<<<codec_tool name=\"find_files\">>>",
            "<<< CODEC_TOOL name=\"find_files\" >>>",
            "<<<CODEC_TOOL name = find_files>>>",
            "<<<CODEC_TOOL name: find_files>>>"
        )) {
            val parsed = AiToolProtocol.parse("$spelling\npattern: *.c\n<<<END_CODEC_TOOL>>>")
            assertTrue("must parse: $spelling", parsed is AiToolParse.Calls)
            val calls = (parsed as AiToolParse.Calls).calls
            assertEquals("must parse: $spelling", 1, calls.size)
            assertEquals("must parse: $spelling", AiToolName.FIND_FILES, calls[0].name)
        }
    }

    @Test
    fun `containsBlock spots a wire block however it is spelled`() {
        assertTrue(AiToolProtocol.containsBlock("x\n<<<CODEC_TOOL name=\"read_file\">>>\npath: a.c\n<<<END_CODEC_TOOL>>>"))
        assertTrue(AiToolProtocol.containsBlock("<<< CODEC_TOOL"))
        assertTrue(AiToolProtocol.containsBlock("... <<< END_CODEC_TOOL >>>"))
        assertTrue(AiToolProtocol.containsBlock("<<<codec_tool name=\"x\">>>"))
        assertTrue(AiToolProtocol.containsBlock("END_CODEC_TOOL >>>"))
        assertFalse("prose that merely mentions the name is not a block", AiToolProtocol.containsBlock("use the course tool name here"))
    }

    @Test
    fun `the instructions teach every wire - and the new read tools are read-shaped`() {
        val instructions = AiToolProtocol.INSTRUCTIONS
        for (name in AiToolName.entries) {
            assertTrue("the model must be told about ${name.wire}", instructions.contains(name.wire))
        }
        for (banned in listOf("write_file", "delete_file", "exec", "shell")) {
            assertFalse(instructions.contains(banned))
        }
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
