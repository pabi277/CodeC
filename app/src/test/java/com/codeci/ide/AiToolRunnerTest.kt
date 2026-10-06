package com.codeci.ide

import com.codeci.ide.ui.ai.AiToolCall
import com.codeci.ide.ui.ai.AiToolLimits
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.ai.ReadSpec
import com.codeci.ide.ui.ai.AiSecretScan
import com.codeci.ide.ui.ai.AiToolRunner
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 80 (AI Level 4) — the read-only tool runner against a real tree:
 * containment, the credential refusal, the caps, the cut markers and the
 * unsaved-buffer rule.
 *
 * The walk's admitted list (`paths`) is an input here on purpose: this test
 * proves the runner cannot open what the Level 2 filter never admitted, which
 * is the same one-place-decides rule `AiProjectReaderTest` protects.
 */
class AiToolRunnerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var root: File

    private fun tree() {
        root = tmp.newFolder("project")
        File(root, "src").mkdirs()
        File(root, "src/main.c").writeText(
            "int main(void) {\n  printf(\"hi\");\n  return 0;\n}\n"
        )
        File(root, "src/util.c").writeText("int helper(void) { return 1; }\n")
        File(root, "README.md").writeText("# Demo\nA malloc example\n")
        File(root, ".env").writeText("API_KEY=secret\nmalloc\n")
        File(root, "build").mkdirs()
        File(root, "build/out.c").writeText("int stale(void){return 0;}\n")
    }

    /** Exactly what the Level 2 walk would admit (no `.env`, no build output). */
    private val admitted = listOf("README.md", "src/main.c", "src/util.c")

    private fun call(
        name: AiToolName,
        path: String? = null,
        start: Int? = null,
        end: Int? = null,
        query: String? = null,
        max: Int? = null,
        ext: String? = null,
        pattern: String? = null,
        lines: Int? = null
    ) = AiToolCall(
        name = name, rawName = name.wire, path = path, start = start, end = end,
        query = query, max = max, ext = ext, pattern = pattern, lines = lines
    )

    // ---- list_files -------------------------------------------------------

    @Test
    fun `list_files names the admitted files and nothing else`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.LIST_FILES), root, admitted)
        assertTrue(out.ok)
        assertTrue(out.text.contains("README.md"))
        assertTrue(out.text.contains("src/main.c"))
        assertFalse(out.text.contains(".env"))
        assertFalse(out.text.contains("build/out.c"))
        assertTrue(out.text.contains("3 of 3 listed"))
    }

    @Test
    fun `list_files filters by directory and extension`() {
        tree()
        val src = AiToolRunner.execute(call(AiToolName.LIST_FILES, path = "src"), root, admitted)
        assertFalse(src.text.contains("README.md"))
        assertTrue(src.text.contains("src/util.c"))
        val ext = AiToolRunner.execute(call(AiToolName.LIST_FILES, ext = "md"), root, admitted)
        assertEquals(true, ext.text.contains("README.md"))
        assertFalse(ext.text.contains("src/main.c"))
        val none = AiToolRunner.execute(call(AiToolName.LIST_FILES, path = "docs"), root, admitted)
        assertTrue(none.text.contains("No code or text file matches"))
    }

    @Test
    fun `list_files caps its own answer`() {
        root = tmp.newFolder("big")
        val many = (1..AiToolLimits.MAX_LIST_ENTRIES + 5).map { "f$it.c" }
        many.forEach { File(root, it).writeText("int x;\n") }
        val out = AiToolRunner.execute(call(AiToolName.LIST_FILES), root, many)
        assertTrue(out.truncated)
        assertTrue(out.text.contains(AiToolRunner.CUT_NOTE))
        assertTrue(out.text.contains("${AiToolLimits.MAX_LIST_ENTRIES} of ${many.size} listed"))
    }

    // ---- search_project ---------------------------------------------------

    @Test
    fun `search finds matches with file and line and never reads a secret`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.SEARCH_PROJECT, query = "malloc"), root, admitted)
        assertTrue(out.text.contains("README.md:2"))
        assertFalse(out.text.contains(".env"))
        assertFalse(out.text.contains("API_KEY"))
    }

    @Test
    fun `search is case-insensitive and honest when nothing matches`() {
        tree()
        val hit = AiToolRunner.execute(call(AiToolName.SEARCH_PROJECT, query = "PRINTF"), root, admitted)
        assertTrue(hit.text.contains("src/main.c:2"))
        val none = AiToolRunner.execute(call(AiToolName.SEARCH_PROJECT, query = "zzz"), root, admitted)
        assertTrue(none.text.contains("no match"))
        assertFalse(none.truncated)
    }

    @Test
    fun `search honours its max and marks the cut`() {
        tree()
        File(root, "src/many.c").writeText((1..50).joinToString("\n") { "int hit$it; // needle" })
        val out = AiToolRunner.execute(
            call(AiToolName.SEARCH_PROJECT, query = "needle", max = 5),
            root, admitted + "src/many.c"
        )
        assertTrue(out.truncated)
        assertTrue(out.text.contains("5 match"))
        assertTrue(out.text.contains(AiToolRunner.CUT_NOTE))
    }

    @Test
    fun `search stops when the task is stopped`() {
        tree()
        val out = AiToolRunner.execute(
            call(AiToolName.SEARCH_PROJECT, query = "int"),
            root, admitted,
            shouldStop = { true }
        )
        assertTrue(out.text.contains(AiToolRunner.STOP_NOTE) || out.text.contains("no match"))
        assertTrue(out.truncated)
    }

    // ---- read_file --------------------------------------------------------

    @Test
    fun `read_file returns a numbered range with a truthful header`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.READ_FILE, path = "src/main.c", start = 2, end = 3), root, admitted)
        assertTrue(out.text.startsWith("FILE src/main.c — lines 2-3 of 4"))
        assertTrue(out.text.contains("2:   printf(\"hi\");"))
        assertTrue(out.text.contains("3:   return 0;"))
        assertFalse(out.text.contains("1: int main"))
    }

    @Test
    fun `read_file prefers the unsaved editor buffer and says so`() {
        tree()
        val out = AiToolRunner.execute(
            call(AiToolName.READ_FILE, path = "src/main.c"),
            root, admitted,
            dirtyBuffers = mapOf("src/main.c" to "int main(void) { /* edited */ }\n")
        )
        assertTrue(out.text.contains("[unsaved edits]"))
        assertTrue(out.text.contains("/* edited */"))
        assertFalse(out.text.contains("printf"))
    }

    @Test
    fun `cached and dirty file paths preserve out-of-range refusal semantics`() {
        tree()
        val outOfRange = call(AiToolName.READ_FILE, path = "src/main.c", start = 20, end = 24)
        val disk = AiToolRunner.execute(outOfRange, root, admitted)
        val cached = AiToolRunner.execute(
            outOfRange,
            root,
            admitted,
            cachedFiles = mapOf("src/main.c" to File(root, "src/main.c").readText())
        )
        val dirty = AiToolRunner.execute(
            outOfRange,
            root,
            admitted,
            dirtyBuffers = mapOf("src/main.c" to File(root, "src/main.c").readText())
        )
        assertFalse(disk.ok)
        assertFalse(cached.ok)
        assertFalse(dirty.ok)
        assertEquals(disk.text, cached.text)
        assertEquals(disk.text, dirty.text)

        val batch = AiToolCall(
            name = AiToolName.READ_FILES,
            rawName = "read_files",
            reads = listOf(ReadSpec("src/main.c", 20, 24))
        )
        val diskBatch = AiToolRunner.execute(batch, root, admitted)
        val cachedBatch = AiToolRunner.execute(
            batch,
            root,
            admitted,
            cachedFiles = mapOf("src/main.c" to File(root, "src/main.c").readText())
        )
        assertEquals(diskBatch.text, cachedBatch.text)
        assertTrue(diskBatch.text.contains("[refused: start 20 is past the end"))
    }

    @Test
    fun `read_file refuses secrets binaries missing files and escape paths`() {
        tree()
        File(root, "src/blob.c").writeText("int x;\u0000binary")
        assertFalse(AiToolRunner.execute(call(AiToolName.READ_FILE, path = ".env"), root, admitted).ok)
        assertFalse(AiToolRunner.execute(call(AiToolName.READ_FILE, path = "src/blob.c"), root, admitted).ok)
        assertFalse(AiToolRunner.execute(call(AiToolName.READ_FILE, path = "src/gone.c"), root, admitted).ok)
        assertFalse(AiToolRunner.execute(call(AiToolName.READ_FILE, path = "../outside.c"), root, admitted).ok)
    }

    @Test
    fun `read_file refuses a symlink that leaves the project`() {
        tree()
        val outside = tmp.newFile("outside.c")
        outside.writeText("int secret(void){return 0;}\n")
        val link = File(root, "src/link.c")
        val made = runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }.isSuccess
        if (!made) return
        assertFalse(AiToolRunner.execute(call(AiToolName.READ_FILE, path = "src/link.c"), root, admitted).ok)
    }

    @Test
    fun `read_file clips very large files with a visible marker`() {
        tree()
        val big = (1..2_000).joinToString("\n") { "line $it" }
        File(root, "src/big.c").writeText(big)
        val out = AiToolRunner.execute(
            call(AiToolName.READ_FILE, path = "src/big.c", start = 1, end = AiToolLimits.MAX_READ_LINES),
            root, admitted + "src/big.c"
        )
        assertTrue(out.text.length <= AiToolLimits.MAX_RESULT_CHARS)
        assertTrue(out.truncated)
        assertTrue(out.text.contains("more lines follow"))
    }

    // ---- find_files -------------------------------------------------------

    @Test
    fun `find_files matches a glob over admitted paths and never walks the disk`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.FIND_FILES, pattern = "*.c"), root, admitted)
        assertTrue(out.ok)
        assertTrue(out.text.contains("src/main.c"))
        assertTrue(out.text.contains("src/util.c"))
        assertFalse("the unadmitted build output stays invisible", out.text.contains("build/out.c"))
        assertFalse("and so does the secret file", out.text.contains(".env"))
        assertTrue(out.text.contains("2 of 2 listed"))
    }

    @Test
    fun `find_files treats a pattern without a slash as a name match anywhere`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.FIND_FILES, pattern = "util.c"), root, admitted)
        assertTrue(out.text.contains("src/util.c"))
    }

    @Test
    fun `find_files understands star-star across directories and is honest when empty`() {
        tree()
        val deep = AiToolRunner.execute(call(AiToolName.FIND_FILES, pattern = "src/**/*.c"), root, admitted)
        assertTrue(deep.text.contains("src/main.c"))
        val none = AiToolRunner.execute(call(AiToolName.FIND_FILES, pattern = "*.kt"), root, admitted)
        assertTrue(none.ok)
        assertTrue("an empty result is a result, not an error", none.text.contains("none of the ${admitted.size} admitted files match"))
    }

    @Test
    fun `find_files caps its answer with a visible cut`() {
        tree()
        val many = (1..40).map { "src/f$it.c" }
        for (p in many) File(root, p).writeText("int x;\n")
        val out = AiToolRunner.execute(
            call(AiToolName.FIND_FILES, pattern = "*.c", max = 5), root, admitted + many
        )
        assertTrue("the cap is named with the real total", out.text.contains("5 of "))
        assertTrue(out.text.contains("result cut"))
    }

    @Test
    fun `the slug matcher agrees with the tool and refuses nothing silently`() {
        assertTrue(AiToolRunner.globMatches("*.c", "src/main.c"))
        assertTrue(AiToolRunner.globMatches("main.c", "src/main.c"))
        assertTrue(AiToolRunner.globMatches("src/**/*.c", "src/a/b/c.c"))
        assertTrue(AiToolRunner.globMatches("src/*.c", "src/main.c"))
        assertFalse(AiToolRunner.globMatches("src/*.c", "src/a/main.c"))
        assertFalse(AiToolRunner.globMatches("*.kt", "src/main.c"))
    }

    // ---- outline_file -----------------------------------------------------

    @Test
    fun `outline_file maps a file without sending its body`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.OUTLINE_FILE, path = "src/main.c"), root, admitted)
        assertTrue(out.ok)
        assertTrue(out.text.contains("STRUCTURE src/main.c"))
        assertTrue(out.text.contains("int main(void)"))
        assertFalse("the body must not ride along", out.text.contains("printf"))
    }

    @Test
    fun `outline_file prefers the unsaved buffer like read_file does`() {
        tree()
        val out = AiToolRunner.execute(
            call(AiToolName.OUTLINE_FILE, path = "src/main.c"), root, admitted,
            dirtyBuffers = mapOf("src/main.c" to "void brandNew(void) { }\n")
        )
        assertTrue(out.text.contains("brandNew"))
        assertFalse(out.text.contains("int main(void)"))
    }

    @Test
    fun `outline_file refuses a secret a missing file and an escape - and says so`() {
        tree()
        val secret = AiToolRunner.execute(call(AiToolName.OUTLINE_FILE, path = ".env"), root, admitted)
        assertFalse(secret.ok)
        val missing = AiToolRunner.execute(call(AiToolName.OUTLINE_FILE, path = "src/nope.c"), root, admitted)
        assertFalse(missing.ok)
        val escape = AiToolRunner.execute(call(AiToolName.OUTLINE_FILE, path = "../out.c"), root, admitted)
        assertFalse(escape.ok)
        val unadmitted = AiToolRunner.execute(call(AiToolName.OUTLINE_FILE, path = "build/out.c"), root, admitted)
        assertFalse(unadmitted.ok)
        assertTrue(unadmitted.text.contains("not a code or text file in this project"))
    }

    @Test
    fun `outline_file on a definitionless file answers with an empty structure`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.OUTLINE_FILE, path = "README.md"), root, admitted)
        assertTrue(out.ok)
        assertTrue(out.text.contains("STRUCTURE README.md"))
    }

    // ---- read_run_output --------------------------------------------------

    @Test
    fun `read_run_output hands back the frozen snapshot tail with a truthful header`() {
        tree()
        val lines = (1..100).map { "line $it" }
        val out = AiToolRunner.execute(
            call(AiToolName.READ_RUN_OUTPUT, lines = 10), root, admitted, runOutput = lines
        )
        assertTrue(out.ok)
        assertTrue(out.text.contains("last 10 of 100 lines"))
        assertTrue(out.text.contains("line 91"))
        assertTrue(out.text.contains("line 100"))
        assertFalse("older lines are gone", out.text.contains("line 90\n"))
    }

    @Test
    fun `read_run_output defaults to the default window and never exceeds the cap`() {
        tree()
        val lines = (1..500).map { "l$it" }
        val def = AiToolRunner.execute(call(AiToolName.READ_RUN_OUTPUT), root, admitted, runOutput = lines)
        assertTrue(def.text.contains("last ${AiToolLimits.DEFAULT_OUTPUT_LINES} of 500 lines"))
        val big = AiToolRunner.execute(
            call(AiToolName.READ_RUN_OUTPUT, lines = 999), root, admitted, runOutput = lines
        )
        assertTrue(big.text.contains("last ${AiToolLimits.MAX_OUTPUT_LINES} of 500 lines"))
    }

    @Test
    fun `read_run_output says nothing has been built instead of failing`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.READ_RUN_OUTPUT), root, admitted)
        assertFalse("there is nothing to show", out.ok)
        assertTrue(out.text.contains("nothing has been built or run"))
    }

    @Test
    fun `read_run_output can never make anything run`() {
        val src = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai/AiToolRunner.kt").readText()
        assertTrue(src.contains("snapshot") || src.contains("runOutput"))
        assertFalse("the tool must not shell out", src.contains("ProcessBuilder"))
        assertFalse(src.contains("Runtime.getRuntime"))
        assertFalse(src.contains("exec("))
    }

    // ---- the scrub at the single result boundary --------------------------

    @Test
    fun `every tool result is scrubbed - a planted key in a file never reaches the wire`() {
        tree()
        File(root, "src/conf.c").writeText(
            "const char *key = \"AIzaSyD4bC1xH9kLmN0pQrStUvWxYz12345678\";\n"
        )
        val out = AiToolRunner.execute(
            call(AiToolName.READ_FILE, path = "src/conf.c", start = 1, end = 5),
            root, admitted + "src/conf.c"
        )
        assertTrue(out.ok)
        assertFalse("the value must not survive", out.text.contains("AIzaSyD4bC1xH9kLmN0pQrStUvWxYz12345678"))
        assertTrue(out.text.contains(AiSecretScan.MARKER))
        assertTrue("and the model is told", out.text.contains("withheld"))
    }

    @Test
    fun `the scrub names the count and leaves ordinary code alone`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.READ_FILE, path = "src/main.c", start = 1, end = 4), root, admitted)
        assertTrue(out.ok)
        assertTrue(out.text.contains("printf"))
        assertFalse(out.text.contains("withheld"))
    }

    // ---- the runner never executes a run ---------------------------------

    @Test
    fun `request_run never reaches the runner as an action`() {
        tree()
        val out = AiToolRunner.execute(call(AiToolName.REQUEST_RUN, path = "src/main.c"), root, admitted)
        assertFalse(out.ok)
        assertTrue(out.text.contains("approved by the user"))
    }
}
