package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50 — the cross-device matrix is a document, so it rots the way documents
 * do. This test is the reason it cannot.
 *
 * **Why it exists.** Phase 44's first device round was run against a runbook whose
 * rows quoted sentences the app had never had ("Ready ✓", "Preparing Python (1/3)",
 * "Offline — setup paused · Retry"). The owner spent the round discovering that the
 * instructions were wrong rather than the app being wrong, and a device round is the
 * scarcest resource this project has. `docs/chat-phase50/DEVICE_MATRIX.md` now quotes
 * on-screen text in back-ticks, and this file makes that promise mechanical: every
 * quoted span must exist **in the shipped sources**, every row must belong to a real
 * part and appear in that part's `## Test log`, and the runbook must still describe
 * the build the tester can actually install.
 *
 * The rules, and what each one is for:
 *  1. **shape** — ten rounds (A–J), ids `<round><n>` contiguous from 1, no
 *     duplicates, five columns, and a `Part` cell naming one of the eleven real part
 *     files; every part owns ≥ 3 rows and every round ≥ 3 rows, so no phase of the
 *     series is quietly dropped from the round.
 *  2. **verbatim truth** — every back-ticked span in a `PASS looks like` cell is
 *     either (a) a *multi-word* sentence found inside one app string (a
 *     `strings.xml` value or a run of concatenated Kotlin literals, comments
 *     stripped), or (b) a single word that occurs as an identifier in the sources /
 *     manifest / resource names. `NN` and `…` are the doc's wildcards for the app's
 *     own number and its own interpolation.
 *  3. **not-on-screen exemptions** — a span is exempt only by *shape*: a path (it
 *     contains `/`), a shell command the tester types, a file name, a test class
 *     (`…Test`), or a demo's own names. That is the whole escape hatch, so widening
 *     it is visible in review.
 *  4. **the matrix is a matrix** — the `Run on` column names only D1–D4 (or ALL),
 *     and each class appears ≥ 3 times; the exit-prompt rows name both nav modes,
 *     because 5.B *is* a cross-nav-mode bug.
 *  5. **the paste-back law** — each row appears in exactly one part file's
 *     `## Test log`, and every id in those tables is back in the matrix. Results
 *     therefore land beside the spec they prove, or CI says so.
 *  6. **the destructive pair** — a kill during the install and a revoked file
 *     access are both in the runbook (the roadmap's two cases), and a results row
 *     cannot carry a ✅/❌ without a device, class and build.
 *  7. **the build is real** — the runbook points at `main`, never at an `arena/*`
 *     branch build (the stale-branch pointer is exactly how round 1 went astray).
 *
 * What this test cannot do, stated so nobody over-reads it: it proves the runbook is
 * *true*, not that the app is *right*. Every device row stays owed until a human
 * with a handset pastes a result (`rule.md` §5: device pass required).
 */
class DeviceMatrixTest {

    // ---------------------------------------------------------------- config --

    private companion object {
        const val MATRIX_PATH = "docs/chat-phase50/DEVICE_MATRIX.md"
        const val TEST_LOG_HEADING = "## Test log (Phase 50 — the cross-device matrix)"
        val ROUND_LETTERS = ('A'..'J').toList()

        /** part id → the file that specs it. Eleven files, six phases. */
        val PART_FILES = mapOf(
            "44.1" to "docs/chat-phase44/PART_44_1_VISIBLE_SETUP.md",
            "44.2" to "docs/chat-phase44/PART_44_2_ATOMIC_SETUP.md",
            "45.1" to "docs/chat-phase45/PART_45_1_GUIDE_SLIDES.md",
            "45.2" to "docs/chat-phase45/PART_45_2_COACH_MARKS.md",
            "46.1" to "docs/chat-phase46/PART_46_1_REMOVE_OPEN_FOLDER.md",
            "46.2" to "docs/chat-phase46/PART_46_2_SINGLE_FILE_EDITOR.md",
            "47.1" to "docs/chat-phase47/PART_47_1_DRAWER.md",
            "47.2" to "docs/chat-phase47/PART_47_2_KEYBOARD_DEFAULT.md",
            "48.1" to "docs/chat-phase48/PART_48_1_CARET_ABOVE_KEYBOARD.md",
            "49.1" to "docs/chat-phase49/PART_49_1_BACK_ROUTER.md",
            "49.2" to "docs/chat-phase49/PART_49_2_EXIT_PROMPT_CONSISTENCY.md",
        )

        /** The floor for "mostly verbatim, not mostly prose" (measured: 33). */
        const val MIN_VERBATIM_ROWS = 30
        const val MIN_ROWS_PER_PART = 3
        const val MIN_ROWS_PER_ROUND = 3
        const val MIN_ROWS_PER_CLASS = 3

        val TYPED_COMMANDS = Regex(
            """^(pkg|python3|python|pip|npm|node|cc|tcc|clang|git|ls|cat|mkdir|rm|echo|df|free)\s"""
        )
        val CODE_FILE = Regex("""^[A-Za-z0-9_.\-]+\.(c|h|cpp|hpp|py|js|ts|tsx|jsx|json|html|css|zip|apk|kt|java|txt|md|sh)$""")

        /** The two names the tour and the demo rows must be able to say. */
        val LITERAL_UI_EXCEPTIONS = setOf("demo_flask", "app.py")
    }

    // ------------------------------------------------------------- the parser --

    private class Row(
        val id: String,
        val round: Char,
        val part: String,
        val runOn: String,
        val whatToDo: String,
        val pass: String
    )

    private val matrixText: String
        get() = RepoFiles.mainSource(MATRIX_PATH).readText()

    /** The round tables only: a `### Round X — …` heading owns rows until the next `## `. */
    private fun matrixRows(): List<Row> {
        val rows = mutableListOf<Row>()
        var round: Char? = null
        for (line in matrixText.lineSequence()) {
            val heading = Regex("""^### Round ([A-J]) —""").find(line)
            if (heading != null) {
                round = heading.groupValues[1].single()
                continue
            }
            if (line.startsWith("## ")) {
                round = null
                continue
            }
            val idMatch = Regex("""^\|\s*\*?\*?([A-J])(\d+)\b[^|]*\|""").find(line) ?: continue
            val r = round ?: continue
            val cols = line.trim().trim('|').split('|').map { it.trim() }
            require(cols.size == 5) {
                "$MATRIX_PATH row '${idMatch.groupValues[1]}${idMatch.groupValues[2]}' has " +
                    "${cols.size} columns; the round tables are `# | Part | Run on | What to do | PASS looks like`"
            }
            rows += Row(
                id = cols[0].removeSurrounding("*").replace("★", "").trim(),
                round = r,
                part = cols[1],
                runOn = cols[2],
                whatToDo = cols[3],
                pass = cols[4]
            )
        }
        return rows
    }

    /** The ids listed in one part file's `## Test log` table. */
    private fun testLogRows(partFile: String): List<String> {
        val file = RepoFiles.mainSource(partFile)
        assertTrue("$partFile is missing", file.isFile)
        val text = file.readText()
        val start = text.indexOf(TEST_LOG_HEADING)
        assertTrue(
            "$partFile has no `${TEST_LOG_HEADING.removePrefix("## ")}` section — Phase 50's exit 4 " +
                "needs one per part, so a device result lands beside the spec it proves",
            start >= 0
        )
        val rest = text.substring(start + TEST_LOG_HEADING.length)
        val sectionEnd = rest.indexOf("\n## ").let { if (it < 0) rest.length else it }
        return Regex("""^\|\s*([A-J]\d+)\s*\|""")
            .findAll(rest.substring(0, sectionEnd))
            .map { it.groupValues[1] }
            .toList()
    }

    // ------------------------------------------------------- the string corpus --

    /** Kotlin/Java-ish comment stripper: a quoted `//` inside a string is not a comment. */
    private fun stripKotlinComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            val c = src[i]
            if (src.startsWith("\"\"\"", i)) {
                val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it + 3 }
                out.append(src, i, end)
                i = end
                continue
            }
            if (src.startsWith("//", i)) {
                val end = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                i = end
                continue
            }
            if (src.startsWith("/*", i)) {
                val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                i = end
                continue
            }
            if (c == '"' || c == '\'') {
                val quote = c
                var j = i + 1
                while (j < src.length) {
                    if (src[j] == '\\') {
                        j += 2
                        continue
                    }
                    if (src[j] == quote) break
                    j++
                }
                out.append(src, i, minOf(j + 1, src.length))
                i = j + 1
                continue
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun unescape(s: String): String =
        Regex("""\\u([0-9a-fA-F]{4})""").replace(s) { m ->
            m.groupValues[1].toInt(16).toChar().toString()
        }
            .replace("\\n", " ")
            .replace("\\t", " ")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", " ")

    /**
     * The doc's and the code's shared shape: interpolations and format specifiers
     * are holes, case and punctuation are noise. `Setting up CodeC's Linux tools —
     * ${p} % · C works right now` and
     * `Setting up CodeC's Linux tools — NN % · C works right now` both become
     * `setting up codec s linux tools c works right now`.
     */
    private fun normalize(raw: String): String =
        unescape(raw)
            .replace(Regex("""\$\{[^}]*\}"""), " ")
            .replace(Regex("""\$[A-Za-z_][A-Za-z0-9_]*"""), " ")
            .replace(Regex("""%\d+\$s"""), " ")
            .replace(Regex("""%[ds]"""), " ")
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    /** Every string literal in `app/src/main`, with `"a " + "b"` runs joined. */
    private fun kotlinLiterals(srcRaw: String): List<String> {
        val src = stripKotlinComments(srcRaw)
        val literal = Regex("\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"")
        val out = mutableListOf<String>()
        var group: StringBuilder? = null
        var lastEnd = -1
        for (m in literal.findAll(src)) {
            val joined = lastEnd >= 0 && Regex("""[\s+]*""").matches(src.substring(lastEnd, m.range.first))
            if (joined) {
                group!!.append(' ').append(m.value)
            } else {
                group?.let { out += it.toString() }
                group = StringBuilder(m.value)
            }
            lastEnd = m.range.last + 1
        }
        group?.let { out += it.toString() }
        return out
    }

    private fun mainKotlinFiles(): List<File> = RepoFiles.mainKotlinSources()

    /** Normalized app strings: every production literal (joins included) + every resource value. */
    private fun corpus(): List<String> {
        val entries = mutableListOf<String>()
        for (f in mainKotlinFiles()) entries += kotlinLiterals(f.readText()).map { normalize(it) }
        val xml = RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText()
        for (m in Regex("""<string name="[^"]+"[^>]*>([\s\S]*?)</string>""").findAll(xml)) {
            val v = m.groupValues[1]
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"")
            entries += normalize(v)
        }
        return entries.filter { it.isNotBlank() }
    }

    /** Identifier words of the whole production tree — for one-word spans (`running`). */
    private fun wordIndex(): Set<String> {
        val words = mutableSetOf<String>()
        val word = Regex("""[A-Za-z][A-Za-z0-9_]*""")
        for (f in mainKotlinFiles()) word.findAll(stripKotlinComments(f.readText()))
            .forEach { words += it.value.lowercase() }
        word.findAll(RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText())
            .forEach { words += it.value.lowercase() }
        Regex("name=\"([^\"]+)\"")
            .findAll(RepoFiles.mainSource("app/src/main/res/values/strings.xml").readText())
            .forEach { words += it.groupValues[1].lowercase() }
        return words
    }

    private fun isNonUi(span: String): Boolean {
        val t = span.trim()
        return t.contains('/') ||
            TYPED_COMMANDS.containsMatchIn(t + " ") ||
            CODE_FILE.matches(t) ||
            t.endsWith("Test") ||
            t in LITERAL_UI_EXCEPTIONS
    }

    /** `…`, `...` and `NN` split; then a digit run splits (the doc's other wildcards). */
    private fun partsOf(span: String): List<String> =
        Regex("""…|\.\.\.|\bNN\b""").split(unescape(span))
            .flatMap { piece -> normalize(piece).split(Regex("""\s*\d+\s*""")) }
            .filter { it.length >= 4 }

    // ------------------------------------------------------------------ rules --

    @Test
    fun `the matrix exists and holds the ten rounds`() {
        val rows = matrixRows()
        assertTrue("no rows parsed from $MATRIX_PATH", rows.isNotEmpty())
        for (row in rows) {
            assertTrue("row ${row.id} has an empty 'What to do' cell", row.whatToDo.isNotBlank())
            assertTrue("row ${row.id} has an empty 'PASS looks like' cell", row.pass.isNotBlank())
            assertTrue(
                "row ${row.id}'s 'What to do' quotes on-screen text; that belongs in the PASS column",
                !row.whatToDo.contains("PASS")
            )
        }
        assertEquals(
            "the matrix must hold one round per letter A-J (44.1, 44.2, 45.1, 45.2, the chrome " +
                "lock, 46, 47, 48, 49, and the hostile-environment round)",
            ROUND_LETTERS.toSet(),
            rows.groupBy { it.round }.keys
        )
    }

    @Test
    fun `row ids are contiguous per round and unique`() {
        val rows = matrixRows()
        val seen = mutableSetOf<String>()
        for (round in ROUND_LETTERS) {
            val ids = rows.filter { it.round == round }.map { it.id }
            assertTrue("round $round has ${ids.size} rows; a round is only worth running at ≥ $MIN_ROWS_PER_ROUND",
                ids.size >= MIN_ROWS_PER_ROUND)
            val numbers = ids.map { it.drop(1).toInt() }.sorted()
            assertEquals(
                "round $round must run 1..n with no gaps (the owner reads them in order): $ids",
                (1..ids.size).toList(),
                numbers
            )
            for (id in ids) assertTrue("duplicate row id $id", seen.add(id))
        }
    }

    @Test
    fun `every row belongs to a real part and every part owns enough rows`() {
        val rows = matrixRows()
        for (row in rows) {
            assertTrue(
                "row ${row.id} names part '${row.part}', which is not one of the eleven part files",
                PART_FILES.containsKey(row.part)
            )
        }
        for ((part, file) in PART_FILES) {
            val owned = rows.count { it.part == part }
            assertTrue(
                "$file owns only $owned matrix row(s); a phase nobody re-tests on a phone " +
                    "is a phase that regresses — every part needs ≥ $MIN_ROWS_PER_PART",
                owned >= MIN_ROWS_PER_PART
            )
            assertTrue("$file is not in the repo", RepoFiles.mainSource(file).isFile)
        }
    }

    @Test
    fun `every verbatim span in a PASS cell exists in the app`() {
        val corpus = corpus()
        val words = wordIndex()
        val bad = mutableListOf<String>()
        var verbatimRows = 0
        for (row in matrixRows()) {
            var rowHadVerbatim = false
            for (span in Regex("""`([^`\n]+)`""").findAll(row.pass).map { it.groupValues[1] }) {
                val flat = normalize(span)
                if (flat.isEmpty()) continue                  // glyphs (⬇ ✕ ☰ ▶ 🔒) carry no letters
                if (isNonUi(span)) continue                   // typed command, path, file, test class
                val isSingleWord = !span.trim().contains(' ') &&
                    span.trim().length <= 24 &&
                    !Regex("""…|\.\.\.|\bNN\b""").containsMatchIn(span)
                if (isSingleWord) {
                    val ok = span.trim().lowercase() in words || corpus.any { it.contains(flat) }
                    if (ok) {
                        rowHadVerbatim = true
                    } else {
                        bad += "${row.id}: '$span' is neither a word nor a string in app/src/main"
                    }
                    continue
                }
                val parts = partsOf(span)
                if (parts.isEmpty()) continue                 // number-only / glyph-only span
                val hit = corpus.any { entry ->
                    var cursor = -1
                    parts.all { part ->
                        val at = entry.indexOf(part, cursor + 1)
                        if (at < 0) false else { cursor = at; true }
                    }
                }
                if (hit) {
                    rowHadVerbatim = true
                } else {
                    bad += "${row.id}: '$span' is not an app string (re-worded in code, or invented)"
                }
            }
            if (rowHadVerbatim) verbatimRows++
        }
        assertEquals(
            "stale or invented on-screen text in the runbook:\n" + bad.joinToString("\n"),
            emptyList<String>(),
            bad
        )
        assertTrue(
            "only $verbatimRows of ${matrixRows().size} rows quote a real sentence — the runbook " +
                "is worth running when it is verbatim (floor $MIN_VERBATIM_ROWS)",
            verbatimRows >= MIN_VERBATIM_ROWS
        )
    }

    @Test
    fun `the run-on column is a real device class and every class is covered`() {
        val rows = matrixRows()
        val allowed = setOf("ALL", "D1", "D2", "D3", "D4")
        for (row in rows) {
            val toks = row.runOn.replace("★", "").trim().split("+").map { it.trim() }
            assertTrue("row ${row.id} has Run on '${row.runOn}'", toks.isNotEmpty() && toks.all { it in allowed })
        }
        for (cls in listOf("D1", "D2", "D3", "D4")) {
            val n = rows.count { cls in it.runOn.split("+").map { t -> t.trim() } || it.runOn == "ALL" }
            assertTrue("device class $cls is named by only $n rows", n >= MIN_ROWS_PER_CLASS)
        }
    }

    @Test
    fun `the exit-prompt rows demand both nav modes`() {
        val promptRows = matrixRows().filter {
            it.pass.contains("Enjoying CodeC") || it.pass.contains("tap back again") ||
                it.id.startsWith("I11") || it.id.startsWith("I12")
        }
        assertTrue("the matrix lost its exit-prompt rows — 5.B is the owner's bug, it must be run",
            promptRows.isNotEmpty())
        for (row in promptRows) {
            val classes = row.runOn.split("+").map { it.trim() }
            assertTrue(
                "row ${row.id} is a back/prompt row and must run on D1 (gesture) AND D2 (3-button) — " +
                    "that difference IS bug 5.B; found '${row.runOn}'",
                classes.contains("ALL") || (classes.contains("D1") && classes.contains("D2"))
            )
        }
    }

    @Test
    fun `each row is pasted back into exactly one part test log`() {
        val rows = matrixRows()
        val logs = PART_FILES.mapValues { (_, file) -> testLogRows(file) }
        for (row in rows) {
            val inItsLog = row.id in (logs[row.part] ?: emptyList())
            val elsewhere = logs.filterKeys { it != row.part }.filterValues { row.id in it }
            assertTrue(
                "row ${row.id} (part ${row.part}) is ${if (inItsLog) "" else "NOT "}listed in its own " +
                    "Test log${if (elsewhere.isEmpty()) "" else " but also in ${elsewhere.keys}"} — " +
                    "Phase 50's law is one row, one owning part file",
                inItsLog && elsewhere.isEmpty()
            )
        }
        for ((part, ids) in logs) {
            val expected = rows.filter { it.part == part }.map { it.id }.toSet()
            assertEquals("$part's Test log must hold exactly its own matrix rows", expected.toSet(), ids.toSet())
            assertTrue(
                "no duplicate rows in $part's Test log",
                ids.size == ids.toSet().size
            )
        }
    }

    @Test
    fun `the two destructive cases are in the runbook`() {
        val text = matrixText.lowercase()
        assertTrue(
            "kill-during-install is missing — the data-loss case Phase 44.2 exists for " +
                "(force-stop mid-download, and again mid-extract)",
            (text.contains("force-stop") || text.contains("force stop")) &&
                (text.contains("mid-download") || text.contains("download")) &&
                text.contains("extract")
        )
        assertTrue(
            "revoke-file-access is missing — the roadmap's second destructive case, and the " +
                "device half of the law that MANAGE_EXTERNAL_STORAGE is never load-bearing",
            text.contains("revoke")
        )
    }

    @Test
    fun `a results row never claims a result without a device`() {
        val lines = matrixText.lines()
        val start = lines.indexOfFirst { it.startsWith("### The results, as they arrive") }
        assertTrue("the matrix lost its results table (that table is the phase's deliverable)", start >= 0)
        val header = lines.drop(start).firstOrNull { it.startsWith("| Device") }
            ?: error("the results table lost its header — §3 is what makes two devices comparable")
        assertEquals(
            "the results table is Device | Class | Nav | Build | Rows run | ✅ | ❌ | Report",
            8,
            header.trim().trim('|').split('|').size
        )
        var seen = 0
        for (line in lines.drop(start + 1).takeWhile { !it.startsWith("## ") && !it.startsWith("**PASS") }) {
            if (!line.startsWith("|")) continue
            val cols = line.trim().trim('|').split('|').map { it.trim() }
            if (cols.size < 8 || cols[0].startsWith("Device") || cols[0].all { c -> c == '-' || c == ' ' }) continue
            seen++
            val claimed = cols.any { it.contains("✅") || it.contains("❌") }
            val identified = cols[0].count { it.isLetterOrDigit() } > 1 && cols[1] != "—" && cols[3] != "—"
            assertFalse(
                "the results table claims a result for '${cols[0]}' without a device line, a class " +
                    "and a build — paste COPY REPORT line 1 first, then the ticks",
                claimed && !identified
            )
        }
        assertTrue("the results table has no rows at all (a `not run yet` placeholder is expected)", seen >= 1)
    }

    @Test
    fun `the runbook points at a branch that ships all of 44-49`() {
        val header = matrixText.lines().take(60).joinToString("\n")
        assertTrue(
            "the build to install must be a `main` build — the whole series 44-49 is merged there, " +
                "and pointing at a session branch is how a round gets run on a stale APK",
            header.contains("`main`")
        )
        for (line in header.lines().filter { it.contains("Build APK") || it.contains("Get the APK") }) {
            assertFalse(
                "the runbook tells the tester to install a build off an `arena/*` branch — those go " +
                    "stale within a day and are how a round gets run against the wrong APK:\n$line",
                line.contains("arena/")
            )
        }
    }

    @Test
    fun `the matrix is reachable from the phase readme and the roadmap`() {
        val readme = RepoFiles.mainSource("docs/chat-phase50/README.md").readText()
        assertTrue("chat-phase50/README.md does not link DEVICE_MATRIX.md",
            readme.contains("DEVICE_MATRIX.md"))
        val roadmap = RepoFiles.mainSource("docs/PHASE44_50_ROADMAP.md").readText()
        assertTrue("the phase-44-50 roadmap does not link chat-phase50/",
            roadmap.contains("chat-phase50/"))
    }
}
