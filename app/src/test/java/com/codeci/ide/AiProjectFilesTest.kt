package com.codeci.ide

import com.codeci.ide.ui.ai.AiContextBuilder
import com.codeci.ide.ui.ai.AiContextResult
import com.codeci.ide.ui.ai.AiLimits
import com.codeci.ide.ui.ai.AiProjectFiles
import com.codeci.ide.ui.ai.AiPromptText
import com.codeci.ide.ui.ai.AiSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 78 (AI Level 2) — the whole-project file policy, host-tested with no
 * Android and no disk: every case here is a pure call.
 *
 * The three laws these cases exist to protect:
 *  1. a credential-shaped file can never be offered as context (there is no
 *     opt-in), whatever else is true about it;
 *  2. the packed body never exceeds [AiLimits.MAX_CONTEXT_CHARS] — the same
 *     ceiling `fromSelection` refuses to pass;
 *  3. nothing is dropped silently: whatever did not fit is reported with a
 *     reason, so the preview can say so.
 */
class AiProjectFilesTest {

    // ---- 1. the secret filter --------------------------------------------

    @Test
    fun `credential-shaped names are secret-like, on the name alone`() {
        for (n in listOf(
            ".env", ".env.local", ".env.production", ".npmrc", ".netrc", ".pypirc",
            ".git-credentials", "id_rsa", "id_ed25519", "server.pem", "app.p12",
            "debug.keystore", "release.jks", "signing.key", "credentials.json",
            "service-account.json", "secrets.yaml", "backup.asc"
        )) {
            assertTrue("$n must be secret-like", AiProjectFiles.isSecretLike(n))
        }
    }

    @Test
    fun `secret-like is decided on the file name, so nesting cannot dodge it`() {
        // exclusionFor only ever sees a NAME, never a path — that is the point.
        for (name in listOf(".env", ".env.prod", "id_rsa")) {
            assertEquals(
                "$name must be refused even though it would otherwise be readable",
                AiProjectFiles.Exclusion.SECRET,
                AiProjectFiles.exclusionFor(name, binary = false, chars = 500)
            )
        }
    }

    @Test
    fun `ordinary source files are not secret-like`() {
        for (n in listOf("main.c", "app.py", "index.ts", "README", "Makefile", "styles.css", "keys.md")) {
            assertFalse("$n must not be secret-like", AiProjectFiles.isSecretLike(n))
        }
    }

    @Test
    fun `the secret check runs before the text check, so it can never be re-admitted`() {
        // `.npmrc` is not a text extension here either; SECRET must still win,
        // because a later rule loosening the text list must not open the door.
        assertEquals(
            AiProjectFiles.Exclusion.SECRET,
            AiProjectFiles.exclusionFor(".npmrc", binary = false, chars = 10)
        )
        assertEquals(
            AiProjectFiles.Exclusion.NOT_TEXT,
            AiProjectFiles.exclusionFor("photo.png", binary = false, chars = 10)
        )
        assertEquals(
            AiProjectFiles.Exclusion.BINARY,
            AiProjectFiles.exclusionFor("blob.c", binary = true, chars = 10)
        )
        assertNull(AiProjectFiles.exclusionFor("main.c", binary = false, chars = 10))
    }

    // ---- 2. the text filter is narrower than the editor's search ----------

    @Test
    fun `the AI text filter refuses the extensions that carry credentials or bulk data`() {
        // ProjectSearch.isSearchable admits every one of these; the AI must not.
        for (n in listOf("prod.env", "yarn.lock", "local.properties", "data.csv", "rows.tsv")) {
            assertFalse("$n must not be an AI text file", AiProjectFiles.isTextFile(n))
        }
        for (n in listOf("main.c", "app.py", "index.ts", "Makefile", "README", ".gitignore", "build.gradle")) {
            assertTrue("$n must be an AI text file", AiProjectFiles.isTextFile(n))
        }
    }

    @Test
    fun `build output and vcs directories are never entered, but dot-config dirs are`() {
        for (d in listOf("node_modules", ".git", "build", "dist", "__pycache__", ".gradle", ".venv")) {
            assertTrue("$d must be excluded", AiProjectFiles.isExcludedDirectory(d))
        }
        for (d in listOf("src", ".github", ".vscode", "app")) {
            assertFalse("$d must be entered", AiProjectFiles.isExcludedDirectory(d))
        }
    }

    // ---- 3. deterministic relevance --------------------------------------

    @Test
    fun `keywords keep only words long enough to mean something`() {
        val words = AiProjectFiles.keywords("What does area() do in main.c?")
        assertEquals(listOf("area", "main"), words)
    }

    @Test
    fun `keywords dedupe and sort, so the same question always ranks the same`() {
        val a = AiProjectFiles.keywords("area area AREA main")
        val b = AiProjectFiles.keywords("main area")
        assertEquals(a, b)
        assertEquals(listOf("area", "main"), a)
    }

    @Test
    fun `the file the user is looking at outranks everything else`() {
        val words = listOf("area")
        val open = AiProjectFiles.relevance("other.c", "area area area", words, isOpenFile = true)
        val better = AiProjectFiles.relevance("area.c", "area area area area area", words, isOpenFile = false)
        assertTrue("the open file must win", open > better)
    }

    @Test
    fun `a question word in the path beats the same word only in the body`() {
        val words = listOf("parser")
        val inPath = AiProjectFiles.relevance("parser.c", "int x = 1;", words, isOpenFile = false)
        val inBody = AiProjectFiles.relevance("util.c", "parser parser parser", words, isOpenFile = false)
        assertTrue(inPath > inBody)
    }

    @Test
    fun `shallower paths win ties, and entry points get a nudge`() {
        val none = emptyList<String>()
        assertTrue(AiProjectFiles.pathScore("main.c", none) > AiProjectFiles.pathScore("vendor/old/main.c", none))
        assertTrue(AiProjectFiles.pathScore("main.c", none) > AiProjectFiles.pathScore("notes.c", none))
    }

    @Test
    fun `the shortlist always keeps the open file and never exceeds its limit`() {
        val paths = (1..40).map { "src/f$it.c" } + "open.c"
        val picked = AiProjectFiles.shortlist(paths, "anything", "open.c", limit = 5)
        assertEquals(5, picked.size)
        assertTrue("the open file must survive the shortlist", picked.contains("open.c"))
        assertEquals("no duplicates", picked.size, picked.distinct().size)
    }

    @Test
    fun `the shortlist works with no question at all`() {
        val picked = AiProjectFiles.shortlist(listOf("a.c", "b.py"), "", null, limit = 5)
        assertEquals(2, picked.size)
    }

    // ---- 4. the budget ----------------------------------------------------

    private fun candidate(path: String, chars: Int, lines: Int = 0) = AiProjectFiles.Candidate(
        relativePath = path,
        text = buildString {
            var written = 0
            var line = 0
            while (written < chars) {
                val l = "line $line of $path padding padding padding"
                append(l).append('\n')
                written += l.length + 1
                line++
            }
        }.take(chars),
        lines = if (lines == 0) 1 + chars / 44 else lines,
        fromBuffer = false,
        readCut = false
    )

    @Test
    fun `the packed body never exceeds the one request ceiling`() {
        val many = (1..12).map { candidate("src/file$it.c", 3_000) }
        val plan = AiProjectFiles.plan(many, "file", null)
        val body = AiPromptText.projectBody(plan.included)
        assertTrue(
            "body ${body.length} must be <= ${AiLimits.MAX_CONTEXT_CHARS}",
            body.length <= AiLimits.MAX_CONTEXT_CHARS
        )
        assertTrue("at most MAX_FILES files", plan.included.size <= AiProjectFiles.MAX_FILES)
    }

    @Test
    fun `no single file is sent beyond its own slice`() {
        val plan = AiProjectFiles.plan(listOf(candidate("big.c", 20_000)), "big", null)
        assertEquals(1, plan.included.size)
        assertTrue(plan.included[0].text.length <= AiProjectFiles.MAX_FILE_CHARS)
        assertTrue("a cut file must say so", plan.included[0].cut)
        assertTrue("a cut file must report fewer lines than it has", plan.included[0].linesSent < plan.included[0].linesInFile)
    }

    @Test
    fun `a cut happens on a whole line, never mid-line`() {
        val plan = AiProjectFiles.plan(listOf(candidate("big.c", 20_000)), "big", null)
        val sent = plan.included[0].text
        assertTrue(sent.contains("line 0 of big.c"))
        // Every generated line is "line N of big.c padding padding padding"; a
        // mid-line cut would leave a final line that is not one of those.
        val lines = sent.split("\n").filter { it.isNotBlank() }
        assertTrue("the slice must be whole lines only", lines.isNotEmpty())
        assertTrue(
            "every sent line must be complete",
            lines.all { it.startsWith("line ") && it.endsWith("padding padding padding") }
        )
    }

    @Test
    fun `what did not fit is reported, not dropped silently`() {
        val many = (1..8).map { candidate("src/file$it.c", 3_000) }
        val plan = AiProjectFiles.plan(many, "file", null)
        assertTrue("something must have been left out", plan.leftOut.isNotEmpty())
        assertTrue(plan.truncated)
        assertTrue(
            plan.leftOut.all { it.second == AiProjectFiles.Exclusion.BUDGET }
        )
        assertEquals("offered + nothing lost", many.size, plan.included.size + plan.leftOut.size)
    }

    @Test
    fun `a project with no readable file plans empty`() {
        val plan = AiProjectFiles.plan(emptyList(), "anything", null)
        assertTrue(plan.isEmpty)
        assertFalse(plan.truncated)
        assertEquals(0, plan.candidatesOffered)
    }

    @Test
    fun `a file too small to be worth sending is left out rather than half-sent`() {
        // Fill the budget with real files, then offer one that cannot get a
        // meaningful slice: it must be left out whole, never sent as a stub.
        val fillers = (1..6).map { candidate("filler$it.c", 3_000) }
        val tiny = AiProjectFiles.Candidate("tiny.c", "int x;", 1, false, false)
        val plan = AiProjectFiles.plan(fillers + tiny, "filler", null)
        val tinyIncluded = plan.included.any { it.relativePath == "tiny.c" }
        val tinyLeftOut = plan.leftOut.any { it.first == "tiny.c" }
        assertTrue("tiny is either sent whole or left out", tinyIncluded != tinyLeftOut)
        if (tinyIncluded) {
            assertEquals("int x;", plan.included.first { it.relativePath == "tiny.c" }.text)
        }
    }

    @Test
    fun `a whole small project fits and nothing is truncated`() {
        val plan = AiProjectFiles.plan(listOf(candidate("main.c", 400), candidate("area.py", 300)), "area", null)
        assertEquals(2, plan.included.size)
        assertFalse(plan.truncated)
        assertTrue(plan.leftOut.isEmpty())
    }

    @Test
    fun `the open file is packed first even when it is the least relevant by name`() {
        val plan = AiProjectFiles.plan(
            listOf(candidate("zzz.c", 500), candidate("main.c", 500)),
            "main",
            openPath = "zzz.c"
        )
        assertEquals("zzz.c", plan.included[0].relativePath)
    }

    // ---- 5. path normalisation -------------------------------------------

    @Test
    fun `path comparison tolerates a leading dot-slash and either separator`() {
        assertTrue(AiProjectFiles.samePath("src/main.c", "./src/main.c"))
        assertTrue(AiProjectFiles.samePath("src/main.c", "src\\main.c"))
        assertTrue(AiProjectFiles.samePath("/src/main.c", "src/main.c"))
        assertFalse(AiProjectFiles.samePath("src/main.c", "src/other.c"))
    }

    // ---- 6. what the request actually says --------------------------------

    @Test
    fun `the project prompt lists every file it sends`() {
        val plan = AiProjectFiles.plan(
            listOf(candidate("main.c", 400), candidate("area.py", 300)),
            "where does it start",
            null
        )
        val result = AiContextBuilder.fromProject(
            plan = plan, question = "where does it start", projectName = "demo",
            scannedFiles = 9, skippedSecret = 2, skippedNotText = 4, hitEntryCap = false
        )
        val prompt = (result as AiContextResult.Ready).prompt
        assertEquals(AiSource.PROJECT, prompt.source)
        assertEquals("2 files", prompt.fileLabel)
        for (line in AiPromptText.projectFileLines(prompt.project!!)) {
            assertTrue("the preview line must name a sent file: $line", prompt.context.contains(line.substringBefore(" — ")))
        }
        assertTrue(prompt.userText.contains("main.c"))
        assertTrue(prompt.userText.contains("area.py"))
        assertTrue(prompt.userText.contains("2 left out because they look like credentials"))
        assertTrue(prompt.userText.contains("4 not code or text"))
        assertTrue(prompt.context.length <= AiLimits.MAX_CONTEXT_CHARS)
    }

    @Test
    fun `a buffer-sourced file is labelled as unsaved in the sent text`() {
        val plan = AiProjectFiles.plan(
            listOf(
                AiProjectFiles.Candidate("main.c", "int main(){ return 0; }\n".repeat(20), 20, true, false)
            ),
            "main", null
        )
        val result = AiContextBuilder.fromProject(
            plan, "main", "demo", 1, 0, 0, false
        ) as AiContextResult.Ready
        assertTrue(result.prompt.unsaved)
        assertTrue(result.prompt.project!!.files[0].fromBuffer)
        assertTrue(result.prompt.context.contains("[unsaved edits]"))
    }

    @Test
    fun `an empty project and an over-budget project are refused differently`() {
        val empty = AiContextBuilder.fromProject(
            AiProjectFiles.plan(emptyList(), "x", null), "x", "demo", 0, 0, 0, false
        )
        assertEquals(
            com.codeci.ide.ui.ai.AiContextProblem.NO_PROJECT_FILES,
            (empty as AiContextResult.Refused).problem
        )

        // Candidates existed, so it is a size problem, not an empty project.
        val stub = AiProjectFiles.Plan(
            included = emptyList(),
            leftOut = listOf("a.c" to AiProjectFiles.Exclusion.BUDGET),
            candidatesOffered = 3,
            truncated = true
        )
        val tooBig = AiContextBuilder.fromProject(stub, "x", "demo", 3, 0, 0, false)
        assertEquals(
            com.codeci.ide.ui.ai.AiContextProblem.PROJECT_TOO_LARGE,
            (tooBig as AiContextResult.Refused).problem
        )
    }

    @Test
    fun `a too-long question is refused before anything is packed`() {
        val long = "q".repeat(AiLimits.MAX_QUESTION_CHARS + 1)
        val plan = AiProjectFiles.plan(listOf(candidate("main.c", 400)), long, null)
        val result = AiContextBuilder.fromProject(plan, long, "demo", 1, 0, 0, false)
        assertEquals(
            com.codeci.ide.ui.ai.AiContextProblem.QUESTION_TOO_LONG,
            (result as AiContextResult.Refused).problem
        )
    }

    @Test
    fun `sentChars is still exactly the instruction plus the user text`() {
        val plan = AiProjectFiles.plan(listOf(candidate("main.c", 400)), "main", null)
        val prompt = (AiContextBuilder.fromProject(plan, "main", "demo", 1, 0, 0, false) as AiContextResult.Ready).prompt
        assertEquals(prompt.systemInstruction.length + prompt.userText.length, prompt.sentChars)
    }

    @Test
    fun `the left-out line is absent when nothing was left out`() {
        val summary = com.codeci.ide.ui.ai.AiProjectSummary(
            projectName = "demo",
            files = emptyList(),
            leftOut = emptyList(),
            scannedFiles = 2,
            skippedSecret = 0,
            skippedNotText = 0,
            hitEntryCap = false
        )
        assertNull(AiPromptText.projectLeftOutLine(summary))
    }
}
