package com.codeci.ide

import com.codeci.ide.ui.ai.AiCurrentFileState
import com.codeci.ide.ui.ai.AiEditOp
import com.codeci.ide.ui.ai.AiEditProposal
import com.codeci.ide.ui.ai.AiEditProposalParser
import com.codeci.ide.ui.ai.AiFileBaseline
import com.codeci.ide.ui.ai.AiProposalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 79 (AI Level 3) — unit tests for structured edit proposal parsing,
 * path/target validation, local unified diff computation, per-file selection,
 * and baseline staleness detection (`03_EDIT_REVIEW_AND_UNDO.md`).
 */
class AiEditProposalTest {

    private fun expectProposal(res: AiProposalResult): AiEditProposal =
        (res as? AiProposalResult.Proposal)?.proposal
            ?: run { fail("Expected Proposal, got $res"); throw AssertionError() }

    private fun expectInvalid(res: AiProposalResult): String =
        (res as? AiProposalResult.Invalid)?.reason
            ?: run { fail("Expected Invalid, got $res"); throw AssertionError() }

    private val baseMainC = AiFileBaseline(
        path = "src/main.c",
        exists = true,
        content = "#include <stdio.h>\nint main() {\n    return 1;\n}\n",
        cut = false
    )

    @Test
    fun `prose-only reply returns NoProposal and never pretends prose is a patch`() {
        val text = "The bug is on line 3 where main returns 1 instead of 0.\n```c\nreturn 0;\n```"
        val res = AiEditProposalParser.parse(text, mapOf("src/main.c" to baseMainC))
        assertTrue("Prose without <<<CODEC_EDIT must be NoProposal", res is AiProposalResult.NoProposal)
    }

    @Test
    fun `modify with search and replace computes a local unified diff`() {
        val reply = """
            Return 0 from main so the exit status indicates success.
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
                return 1;
            <<<REPLACE>>>
                return 0;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertEquals("Return 0 from main so the exit status indicates success.", prop.prose)
        assertEquals(1, prop.files.size)
        val edit = prop.files.single()
        assertEquals("src/main.c", edit.path)
        assertEquals(AiEditOp.MODIFY, edit.op)
        assertTrue(edit.selected)
        assertEquals("#include <stdio.h>\nint main() {\n    return 0;\n}\n", edit.newContent)
        assertEquals(1, edit.addedLines)
        assertEquals(1, edit.removedLines)
        assertTrue(edit.unifiedDiff.contains("--- a/src/main.c"))
        assertTrue(edit.unifiedDiff.contains("+++ b/src/main.c"))
        assertTrue(edit.unifiedDiff.contains("-    return 1;"))
        assertTrue(edit.unifiedDiff.contains("+    return 0;"))
    }

    @Test
    fun `modify with multiple search and replace hunks in one file applies sequentially`() {
        val base = AiFileBaseline(
            path = "math.py",
            exists = true,
            content = "def add(a, b):\n    return a - b\n\ndef mul(a, b):\n    return a + b\n"
        )
        val reply = """
            Fix both arithmetic functions.
            <<<CODEC_EDIT path="math.py" op="modify">>>
            <<<SEARCH>>>
            def add(a, b):
                return a - b
            <<<REPLACE>>>
            def add(a, b):
                return a + b
            <<<END_SEARCH>>>
            <<<SEARCH>>>
            def mul(a, b):
                return a + b
            <<<REPLACE>>>
            def mul(a, b):
                return a * b
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("math.py" to base)))
        val edit = prop.files.single()
        assertEquals("def add(a, b):\n    return a + b\n\ndef mul(a, b):\n    return a * b\n", edit.newContent)
        assertEquals(2, edit.addedLines)
        assertEquals(2, edit.removedLines)
    }

    @Test
    fun `modify with full-file replacement succeeds when the file was not cut`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            #include <stdio.h>
            int main(void) {
                printf("ok\n");
                return 0;
            }
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        val edit = prop.files.single()
        assertTrue(edit.newContent.contains("printf(\"ok\\n\");"))
    }

    @Test
    fun `modify with full-file replacement is rejected when the file was cut for budget`() {
        val cutBaseline = baseMainC.copy(cut = true)
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            #include <stdio.h>
            int main() { return 0; }
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to cutBaseline)))
        assertTrue(reason.contains("only the first part"))
        assertTrue(reason.contains("<<<SEARCH>>>"))
    }

    @Test
    fun `modify with search and replace succeeds on a cut file`() {
        val cutBaseline = baseMainC.copy(
            content = "#include <stdio.h>\nint main() {\n    return 1;\n}\n// 500 more lines below\n",
            cut = true
        )
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
                return 1;
            <<<REPLACE>>>
                return 0;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("src/main.c" to cutBaseline)))
        assertTrue(prop.files.single().newContent.endsWith("// 500 more lines below\n"))
    }

    @Test
    fun `create new file builds dev-null header and all-addition diff`() {
        val reply = """
            Add a header file.
            <<<CODEC_EDIT path="include/util.h" op="create">>>
            #ifndef UTIL_H
            #define UTIL_H
            int util_run(void);
            #endif
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        val edit = prop.files.single()
        assertEquals("include/util.h", edit.path)
        assertEquals(AiEditOp.CREATE, edit.op)
        assertEquals(4, edit.addedLines)
        assertEquals(0, edit.removedLines)
        assertTrue(edit.unifiedDiff.contains("--- /dev/null"))
        assertTrue(edit.unifiedDiff.contains("+++ b/include/util.h"))
    }

    @Test
    fun `delete existing file builds dev-null target header and all-removal diff`() {
        val reply = """
            Remove unused main.c.
            <<<CODEC_EDIT path="src/main.c" op="delete">>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        val edit = prop.files.single()
        assertEquals("src/main.c", edit.path)
        assertEquals(AiEditOp.DELETE, edit.op)
        assertEquals(0, edit.addedLines)
        assertEquals(4, edit.removedLines)
        assertTrue(edit.unifiedDiff.contains("--- a/src/main.c"))
        assertTrue(edit.unifiedDiff.contains("+++ /dev/null"))
    }

    @Test
    fun `path traversal segments are rejected`() {
        for (bad in listOf("../escape.c", "src/../../escape.c", "./src/main.c", "src/./main.c", "src//main.c")) {
            assertNull("$bad must be rejected", AiEditProposalParser.validateTargetPath(bad))
        }
        val reply = """
            <<<CODEC_EDIT path="../escape.c" op="create">>>
            int x = 1;
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, emptyMap()))
        assertTrue(reason.contains("unsafe"))
    }

    @Test
    fun `absolute and home-relative paths are rejected`() {
        for (bad in listOf("/etc/passwd", "/sdcard/main.c", "~/main.c", "C:/main.c", "D:\\main.c")) {
            assertNull("$bad must be rejected", AiEditProposalParser.validateTargetPath(bad))
        }
    }

    @Test
    fun `secret-like target files are rejected`() {
        for (secret in listOf(".env", ".env.local", "config/.env.prod", ".npmrc", "id_rsa", "keys/server.pem", "release.keystore")) {
            assertNull("$secret must be rejected", AiEditProposalParser.validateTargetPath(secret))
        }
        val reply = """
            <<<CODEC_EDIT path=".env" op="create">>>
            SECRET=1
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, emptyMap()))
        assertTrue(reason.contains("credential"))
    }

    @Test
    fun `non-text and properties target files are rejected`() {
        for (bad in listOf("icon.png", "app.apk", "local.properties", "data.bin", "lock.lock")) {
            assertNull("$bad must be rejected", AiEditProposalParser.validateTargetPath(bad))
        }
    }

    @Test
    fun `excluded and metadata directories are rejected`() {
        for (bad in listOf(".git/config", ".codec/project.json", "node_modules/pkg/index.js", "build/gen.c", ".venv/run.py")) {
            assertNull("$bad must be rejected", AiEditProposalParser.validateTargetPath(bad))
        }
    }

    @Test
    fun `duplicate target paths in one proposal are rejected`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
                return 1;
            <<<REPLACE>>>
                return 0;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
            int main()
            <<<REPLACE>>>
            int main(void)
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue(reason.contains("more than once"))
    }

    @Test
    fun `unclosed CODEC_EDIT block is rejected as malformed`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            int main() { return 0; }
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue(reason.contains("cut off"))
    }

    @Test
    fun `a SEARCH block with no REPLACE marker is rejected and names the missing marker`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
            return 1;
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue("the marker that is missing must be named: $reason", reason.contains("has no <<<REPLACE>>>"))
    }

    // ---- Phase 93b: a forgotten END_SEARCH is not a dead end -----------------
    //
    // The owner's round-2 S2: the README ask came back "Unclosed …" and there
    // was nothing to tap. A block that *was* closed (<<<END_CODEC_EDIT>>> is
    // there) but forgot a closing marker inside is recoverable: each hunk runs
    // to its own end marker, the next SEARCH, or the closed block's end. Every
    // hunk still has to match the file byte for byte; the diff is still local;
    // the user still approves.

    @Test
    fun `a forgotten END_SEARCH marker recovers at the next SEARCH block`() {
        val base = AiFileBaseline(
            path = "math.py",
            exists = true,
            content = "def add(a, b):\n    return a - b\n\ndef mul(a, b):\n    return a + b\n",
            cut = false
        )
        val reply = """
            <<<CODEC_EDIT path="math.py" op="modify">>>
            <<<SEARCH>>>
                return a - b
            <<<REPLACE>>>
                return a + b
            <<<SEARCH>>>
            def mul(a, b):
                return a + b
            <<<REPLACE>>>
            def mul(a, b):
                return a * b
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("math.py" to base)))
        val edit = prop.files.single()
        assertEquals(
            "both hunks must apply, in order",
            "def add(a, b):\n    return a + b\n\ndef mul(a, b):\n    return a * b\n",
            edit.newContent
        )
    }

    @Test
    fun `a trailing hunk with no END_SEARCH is closed by the block's own end`() {
        val base = AiFileBaseline(path = "app.py", exists = true, content = "x = 1\n", cut = false)
        val reply = """
            <<<CODEC_EDIT path="app.py" op="modify">>>
            <<<SEARCH>>>
            x = 1
            <<<REPLACE>>>
            x = 2
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("app.py" to base)))
        assertEquals("x = 2\n", prop.files.single().newContent)
    }

    @Test
    fun `markers spelled with inner spaces or lower case are still read`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<search>>>
                return 1;
            <<<REPLACE >>>
                return 0;
            <<< end_search >>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()

        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertEquals("#include <stdio.h>\nint main() {\n    return 0;\n}\n", prop.files.single().newContent)
    }

    @Test
    fun `two REPLACE markers for one SEARCH stay malformed, never merged`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
                return 1;
            <<<REPLACE>>>
                return 0;
            <<<REPLACE>>>
                return 2;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue("merging two replacements would be a guess: $reason", reason.contains("Malformed"))
    }

    @Test
    fun `text before the first SEARCH is still rejected inside a marker block`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            here is what I would change
            <<<SEARCH>>>
                return 1;
            <<<REPLACE>>>
                return 0;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue("the stray line must not be guessed into a hunk: $reason", reason.contains("Unexpected text before"))
    }

    @Test
    fun `unknown edit operation is rejected`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="execute">>>
            rm -rf /
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue(reason.contains("Unknown edit operation"))
    }

    @Test
    fun `modify on a file not in the shared baseline is rejected`() {
        val reply = """
            <<<CODEC_EDIT path="src/other.c" op="modify">>>
            int other(void) { return 0; }
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue(reason.contains("not in the shared project context"))
    }

    @Test
    fun `create on an already existing project file is rejected`() {
        val reply = """
            <<<CODEC_EDIT path="src/existing.c" op="create">>>
            int x = 1;
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(
            AiEditProposalParser.parse(
                reply,
                baselines = mapOf("src/main.c" to baseMainC),
                existingPaths = setOf("src/main.c", "src/existing.c")
            )
        )
        assertTrue(reason.contains("already exists"))
    }

    @Test
    fun `delete on a non-existent file is rejected`() {
        val reply = """
            <<<CODEC_EDIT path="src/missing.c" op="delete">>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue(reason.contains("does not exist"))
    }

    @Test
    fun `modify with non-matching SEARCH block is rejected`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
                return 999;
            <<<REPLACE>>>
                return 0;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue(reason.contains("did not match"))
    }

    @Test
    fun `modify producing identical content is rejected`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
                return 1;
            <<<REPLACE>>>
                return 1;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val reason = expectInvalid(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertTrue(reason.contains("no changes"))
    }

    @Test
    fun `proposals touching more than MAX_EDIT_FILES are rejected`() {
        val blocks = (1..6).joinToString("\n") { i ->
            """
            <<<CODEC_EDIT path="f$i.c" op="create">>>
            int f$i = $i;
            <<<END_CODEC_EDIT>>>
            """.trimIndent()
        }
        val reason = expectInvalid(AiEditProposalParser.parse(blocks, emptyMap()))
        assertTrue(reason.contains("maximum is 5"))
    }

    @Test
    fun `per-file selection toggles individual files and updates selected counts`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
                return 1;
            <<<REPLACE>>>
                return 0;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
            <<<CODEC_EDIT path="src/helper.h" op="create">>>
            int helper(void);
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
        assertEquals(2, prop.selectedCount)
        assertEquals(2, prop.selectedAddedLines)
        assertEquals(1, prop.selectedRemovedLines)

        val toggled = prop.toggleFile("src/main.c")
        assertEquals(1, toggled.selectedCount)
        assertEquals(listOf("src/helper.h"), toggled.selectedFiles.map { it.path })
        assertEquals(1, toggled.selectedAddedLines)
        assertEquals(0, toggled.selectedRemovedLines)

        val none = toggled.selectAll(false)
        assertEquals(0, none.selectedCount)
        assertNotNull(AiEditProposalParser.validateTargetPath("src/helper.h"))
    }

    @Test
    fun `baseline staleness check detects a file edited since the proposal was built`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
                return 1;
            <<<REPLACE>>>
                return 0;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))

        // Fresh state matches baseline -> empty stale list
        val fresh = mapOf(
            "src/main.c" to AiCurrentFileState("src/main.c", exists = true, content = baseMainC.content)
        )
        assertTrue(prop.checkStale(fresh).isEmpty())

        // User edited src/main.c while reviewing -> reported stale
        val edited = mapOf(
            "src/main.c" to AiCurrentFileState("src/main.c", exists = true, content = baseMainC.content + "// user edit\n")
        )
        assertEquals(listOf("src/main.c"), prop.checkStale(edited))
    }

    @Test
    fun `baseline staleness check ignores unselected files that changed`() {
        val reply = """
            <<<CODEC_EDIT path="src/main.c" op="modify">>>
            <<<SEARCH>>>
                return 1;
            <<<REPLACE>>>
                return 0;
            <<<END_SEARCH>>>
            <<<END_CODEC_EDIT>>>
            <<<CODEC_EDIT path="src/helper.c" op="create">>>
            int helper(void) { return 2; }
            <<<END_CODEC_EDIT>>>
        """.trimIndent()
        val prop = expectProposal(AiEditProposalParser.parse(reply, mapOf("src/main.c" to baseMainC)))
            .toggleFile("src/main.c") // uncheck src/main.c, keep only src/helper.c

        val current = mapOf(
            "src/main.c" to AiCurrentFileState("src/main.c", exists = true, content = "// user changed main.c\n"),
            "src/helper.c" to AiCurrentFileState("src/helper.c", exists = false, content = "")
        )
        assertTrue("Unselected file drift must not block applying the selected file", prop.checkStale(current).isEmpty())
    }
}
