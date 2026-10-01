package com.codeci.ide

import com.codeci.ide.ui.ai.AiEditOp
import com.codeci.ide.ui.ai.AiEditProposal
import com.codeci.ide.ui.ai.AiEditProposalParser
import com.codeci.ide.ui.ai.AiFileBaseline
import com.codeci.ide.ui.ai.AiProposalResult
import com.codeci.ide.ui.ai.AiProposedFileEdit
import com.codeci.ide.ui.projects.AiApplyOutcome
import com.codeci.ide.ui.projects.AiEditApplier
import com.codeci.ide.ui.projects.AiUndoOutcome
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 79 (AI Level 3) — host JVM tests for [AiEditApplier]:
 *  - diff-approved `MODIFY`, `CREATE`, and `DELETE` on a real temp directory;
 *  - 1-task preimage journal in `noBackup/ai/undo/<project>/`;
 *  - byte-for-byte restoration on undo (including CRLF preservation);
 *  - baseline conflict detection before apply;
 *  - post-apply user-edit conflict detection before undo;
 *  - symlink, traversal, secret, and journal-cap enforcement;
 *  - automatic rollback on mid-batch write failure.
 */
class AiEditApplierTest {

    private fun tempDir(tag: String): File =
        Files.createTempDirectory("codec79-$tag").toFile()

    private fun File.write(body: String): File = apply {
        parentFile?.mkdirs()
        writeText(body)
    }

    private fun buildProposal(
        reply: String,
        baselines: Map<String, AiFileBaseline>,
        existingPaths: Set<String> = baselines.filterValues { it.exists }.keys
    ): AiEditProposal {
        val res = AiEditProposalParser.parse(reply, baselines, existingPaths)
        return (res as AiProposalResult.Proposal).proposal
    }

    @Test
    fun `modify create and delete apply cleanly and write journal in noBackup`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            File(root, "src/main.c").write("int main() {\n    return 1;\n}\n")
            File(root, "old.txt").write("legacy notes\n")

            val baselines = mapOf(
                "src/main.c" to AiFileBaseline("src/main.c", true, "int main() {\n    return 1;\n}\n"),
                "old.txt" to AiFileBaseline("old.txt", true, "legacy notes\n")
            )
            val reply = """
                <<<CODEC_EDIT path="src/main.c" op="modify">>>
                <<<SEARCH>>>
                    return 1;
                <<<REPLACE>>>
                    return 0;
                <<<END_SEARCH>>>
                <<<END_CODEC_EDIT>>>
                <<<CODEC_EDIT path="include/helper.h" op="create">>>
                #pragma once
                int helper(void);
                <<<END_CODEC_EDIT>>>
                <<<CODEC_EDIT path="old.txt" op="delete">>>
                <<<END_CODEC_EDIT>>>
            """.trimIndent()
            val proposal = buildProposal(reply, baselines)

            val outcome = AiEditApplier.apply(root, noBackup, "demo", proposal)
            assertTrue("Expected Applied, got $outcome", outcome is AiApplyOutcome.Applied)
            val applied = outcome as AiApplyOutcome.Applied
            assertEquals(listOf("src/main.c", "include/helper.h"), applied.appliedPaths)
            assertEquals(listOf("old.txt"), applied.deletedPaths)
            assertEquals("int main() {\n    return 0;\n}\n", File(root, "src/main.c").readText())
            assertEquals("#pragma once\nint helper(void);", File(root, "include/helper.h").readText())
            assertFalse("old.txt must be deleted", File(root, "old.txt").exists())
            assertNotNull(AiEditApplier.readUndoSummary(noBackup, "demo"))
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `undo restores modified files byte-for-byte including CRLF recreates deleted files and removes created files`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            val crlfOriginal = "int main() {\r\n    return 1;\r\n}\r\n".toByteArray(Charsets.UTF_8)
            val deletedOriginal = "do not lose me\r\nline 2\n".toByteArray(Charsets.UTF_8)
            val mainFile = File(root, "src/main.c").apply { parentFile?.mkdirs(); writeBytes(crlfOriginal) }
            val delFile = File(root, "docs/readme.txt").apply { parentFile?.mkdirs(); writeBytes(deletedOriginal) }

            val baselines = mapOf(
                "src/main.c" to AiFileBaseline("src/main.c", true, "int main() {\n    return 1;\n}\n"),
                "docs/readme.txt" to AiFileBaseline("docs/readme.txt", true, "do not lose me\nline 2\n")
            )
            val reply = """
                <<<CODEC_EDIT path="src/main.c" op="modify">>>
                <<<SEARCH>>>
                    return 1;
                <<<REPLACE>>>
                    return 0;
                <<<END_SEARCH>>>
                <<<END_CODEC_EDIT>>>
                <<<CODEC_EDIT path="newdir/created.c" op="create">>>
                int created = 1;
                <<<END_CODEC_EDIT>>>
                <<<CODEC_EDIT path="docs/readme.txt" op="delete">>>
                <<<END_CODEC_EDIT>>>
            """.trimIndent()
            val proposal = buildProposal(reply, baselines)

            val applyRes = AiEditApplier.apply(root, noBackup, "demo", proposal)
            assertTrue(applyRes is AiApplyOutcome.Applied)
            // CRLF preserved on modify
            assertTrue(mainFile.readText().contains("\r\n"))
            assertTrue(File(root, "newdir/created.c").isFile)
            assertFalse(delFile.exists())

            val undoRes = AiEditApplier.undo(root, noBackup, "demo")
            assertTrue("Expected Restored, got $undoRes", undoRes is AiUndoOutcome.Restored)
            assertArrayEquals("Modified file must match pre-apply bytes exactly", crlfOriginal, mainFile.readBytes())
            assertArrayEquals("Deleted file must be recreated byte-for-byte", deletedOriginal, delFile.readBytes())
            assertFalse("Created file must be removed on undo", File(root, "newdir/created.c").exists())
            assertFalse("Empty created parent folder is pruned", File(root, "newdir").exists())
            assertNull("Journal must be deleted after undo", AiEditApplier.readUndoSummary(noBackup, "demo"))
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `journal survives process death and is read back from disk`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            File(root, "a.c").write("int a = 1;\n")
            val baselines = mapOf("a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"))
            val proposal = buildProposal(
                """
                <<<CODEC_EDIT path="a.c" op="modify">>>
                int a = 2;
                <<<END_CODEC_EDIT>>>
                """.trimIndent(),
                baselines
            )
            AiEditApplier.apply(root, noBackup, "demo", proposal, nowMs = 123456L)

            // Simulate process restart: readUndoSummary from disk alone
            val summary = AiEditApplier.readUndoSummary(noBackup, "demo")
            assertNotNull(summary)
            assertEquals("demo", summary!!.projectName)
            assertEquals(123456L, summary.timestampMs)
            assertEquals(1, summary.files.size)
            assertEquals("a.c", summary.files.single().path)
            assertEquals(AiEditOp.MODIFY, summary.files.single().op)
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `journal writes nothing inside projectRoot so git status and backups stay clean`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            File(root, "a.c").write("int a = 1;\n")
            val proposal = buildProposal(
                """
                <<<CODEC_EDIT path="a.c" op="modify">>>
                int a = 2;
                <<<END_CODEC_EDIT>>>
                """.trimIndent(),
                mapOf("a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"))
            )
            AiEditApplier.apply(root, noBackup, "demo", proposal)
            val projectFiles = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).path }.toList()
            assertEquals(listOf("a.c"), projectFiles)
            assertTrue(File(noBackup, "ai/undo/demo/task.journal").isFile)
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `baseline drift on disk before apply returns BaselineConflict and leaves files untouched`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            val file = File(root, "a.c").write("int a = 1;\n")
            val proposal = buildProposal(
                """
                <<<CODEC_EDIT path="a.c" op="modify">>>
                int a = 2;
                <<<END_CODEC_EDIT>>>
                """.trimIndent(),
                mapOf("a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"))
            )
            // User edits file on disk before clicking Apply
            file.writeText("int a = 99;\n")

            val outcome = AiEditApplier.apply(root, noBackup, "demo", proposal)
            assertTrue(outcome is AiApplyOutcome.BaselineConflict)
            assertEquals(listOf("a.c"), (outcome as AiApplyOutcome.BaselineConflict).stalePaths)
            assertEquals("int a = 99;\n", file.readText())
            assertNull(AiEditApplier.readUndoSummary(noBackup, "demo"))
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `unsaved dirty editor buffer drift before apply returns BaselineConflict and never overwrites`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            val file = File(root, "a.c").write("int a = 1;\n")
            val proposal = buildProposal(
                """
                <<<CODEC_EDIT path="a.c" op="modify">>>
                int a = 2;
                <<<END_CODEC_EDIT>>>
                """.trimIndent(),
                mapOf("a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"))
            )
            val outcome = AiEditApplier.apply(
                projectRoot = root,
                noBackupRoot = noBackup,
                projectName = "demo",
                proposal = proposal,
                dirtyBuffers = mapOf("a.c" to "int a = 42; // unsaved in tab\n")
            )
            assertTrue(outcome is AiApplyOutcome.BaselineConflict)
            assertEquals("int a = 1;\n", file.readText())
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `post-apply disk edit by user triggers UserEditedConflict on undo when force is false`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            val file = File(root, "a.c").write("int a = 1;\n")
            val proposal = buildProposal(
                """
                <<<CODEC_EDIT path="a.c" op="modify">>>
                int a = 2;
                <<<END_CODEC_EDIT>>>
                """.trimIndent(),
                mapOf("a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"))
            )
            AiEditApplier.apply(root, noBackup, "demo", proposal)
            // User edits file after AI applied its change
            file.writeText("int a = 2;\n// user added this line\n")

            val undoRes = AiEditApplier.undo(root, noBackup, "demo", force = false)
            assertTrue(undoRes is AiUndoOutcome.UserEditedConflict)
            assertEquals(listOf("a.c"), (undoRes as AiUndoOutcome.UserEditedConflict).modifiedPaths)
            assertTrue("User's newer edit must not be destroyed", file.readText().contains("user added this line"))
            assertNotNull("Journal stays until confirmed or replaced", AiEditApplier.readUndoSummary(noBackup, "demo"))
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `post-apply dirty editor buffer triggers UserEditedConflict on undo when force is false`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            File(root, "a.c").write("int a = 1;\n")
            val proposal = buildProposal(
                """
                <<<CODEC_EDIT path="a.c" op="modify">>>
                int a = 2;
                <<<END_CODEC_EDIT>>>
                """.trimIndent(),
                mapOf("a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"))
            )
            AiEditApplier.apply(root, noBackup, "demo", proposal)

            val undoRes = AiEditApplier.undo(root, noBackup, "demo", dirtyPaths = setOf("a.c"), force = false)
            assertTrue(undoRes is AiUndoOutcome.UserEditedConflict)
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `undo with force true restores pre-AI bytes after user confirms conflict`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            val file = File(root, "a.c").write("int a = 1;\n")
            val proposal = buildProposal(
                """
                <<<CODEC_EDIT path="a.c" op="modify">>>
                int a = 2;
                <<<END_CODEC_EDIT>>>
                """.trimIndent(),
                mapOf("a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"))
            )
            AiEditApplier.apply(root, noBackup, "demo", proposal)
            file.writeText("int a = 3;\n")

            val forced = AiEditApplier.undo(root, noBackup, "demo", force = true)
            assertTrue(forced is AiUndoOutcome.Restored)
            assertEquals("int a = 1;\n", file.readText())
            assertNull(AiEditApplier.readUndoSummary(noBackup, "demo"))
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `symlink target and symlink parent directory are refused by the applier`() {
        val root = tempDir("proj")
        val outside = tempDir("outside")
        val noBackup = tempDir("nobackup")
        try {
            val outsideFile = File(outside, "victim.c").write("int victim = 1;\n")
            Files.createSymbolicLink(File(root, "link.c").toPath(), outsideFile.toPath())
            Files.createSymbolicLink(File(root, "linked_dir").toPath(), outside.toPath())

            val directSymlinkProposal = AiEditProposal(
                prose = "",
                files = listOf(
                    AiProposedFileEdit("link.c", AiEditOp.MODIFY, "int victim = 1;\n", "int victim = 0;\n", "", 1, 1)
                ),
                baselines = mapOf("link.c" to AiFileBaseline("link.c", true, "int victim = 1;\n"))
            )
            val res1 = AiEditApplier.apply(root, noBackup, "demo", directSymlinkProposal)
            assertTrue(res1 is AiApplyOutcome.Failed)
            assertEquals("int victim = 1;\n", outsideFile.readText())

            val dirSymlinkProposal = AiEditProposal(
                prose = "",
                files = listOf(
                    AiProposedFileEdit("linked_dir/new.c", AiEditOp.CREATE, "", "int h = 1;\n", "", 1, 0)
                ),
                baselines = mapOf("linked_dir/new.c" to AiFileBaseline("linked_dir/new.c", false, ""))
            )
            val res2 = AiEditApplier.apply(root, noBackup, "demo", dirSymlinkProposal)
            assertTrue(res2 is AiApplyOutcome.Failed)
            assertFalse(File(outside, "new.c").exists())
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `secret and traversal paths are refused by the applier even if constructed directly`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            for (badPath in listOf("../escape.c", ".env", ".git/config", ".codec/project.json")) {
                val prop = AiEditProposal(
                    prose = "",
                    files = listOf(AiProposedFileEdit(badPath, AiEditOp.CREATE, "", "x", "", 1, 0)),
                    baselines = emptyMap()
                )
                val res = AiEditApplier.apply(root, noBackup, "demo", prop)
                assertTrue("$badPath must fail in applier", res is AiApplyOutcome.Failed)
            }
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `subset selection applies only checked files and leaves unchecked files untouched`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            File(root, "a.c").write("int a = 1;\n")
            File(root, "b.c").write("int b = 1;\n")
            val baselines = mapOf(
                "a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"),
                "b.c" to AiFileBaseline("b.c", true, "int b = 1;\n")
            )
            val proposal = buildProposal(
                """
                <<<CODEC_EDIT path="a.c" op="modify">>>
                int a = 2;
                <<<END_CODEC_EDIT>>>
                <<<CODEC_EDIT path="b.c" op="modify">>>
                int b = 2;
                <<<END_CODEC_EDIT>>>
                """.trimIndent(),
                baselines
            ).toggleFile("b.c") // uncheck b.c

            val res = AiEditApplier.apply(root, noBackup, "demo", proposal)
            assertTrue(res is AiApplyOutcome.Applied)
            assertEquals("int a = 2;", File(root, "a.c").readText())
            assertEquals("int b = 1;\n", File(root, "b.c").readText())
            assertEquals(listOf("a.c"), AiEditApplier.readUndoSummary(noBackup, "demo")!!.files.map { it.path })
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `second applied task replaces the first task journal for that project`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            File(root, "a.c").write("int a = 1;\n")
            File(root, "b.c").write("int b = 1;\n")
            val p1 = buildProposal(
                "<<<CODEC_EDIT path=\"a.c\" op=\"modify\">>>\nint a = 2;\n<<<END_CODEC_EDIT>>>",
                mapOf("a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"))
            )
            AiEditApplier.apply(root, noBackup, "demo", p1)

            val p2 = buildProposal(
                "<<<CODEC_EDIT path=\"b.c\" op=\"modify\">>>\nint b = 2;\n<<<END_CODEC_EDIT>>>",
                mapOf("b.c" to AiFileBaseline("b.c", true, "int b = 1;\n"))
            )
            AiEditApplier.apply(root, noBackup, "demo", p2)

            val summary = AiEditApplier.readUndoSummary(noBackup, "demo")!!
            assertEquals(listOf("b.c"), summary.files.map { it.path })
            AiEditApplier.undo(root, noBackup, "demo")
            assertEquals("int a = 2;", File(root, "a.c").readText())
            assertEquals("int b = 1;\n", File(root, "b.c").readText())
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `clearProjectJournal and clearAllJournals remove journals as required by D6`() {
        val root1 = tempDir("proj1")
        val root2 = tempDir("proj2")
        val noBackup = tempDir("nobackup")
        try {
            File(root1, "a.c").write("int a = 1;\n")
            File(root2, "b.c").write("int b = 1;\n")
            val p1 = buildProposal(
                "<<<CODEC_EDIT path=\"a.c\" op=\"modify\">>>\nint a = 2;\n<<<END_CODEC_EDIT>>>",
                mapOf("a.c" to AiFileBaseline("a.c", true, "int a = 1;\n"))
            )
            val p2 = buildProposal(
                "<<<CODEC_EDIT path=\"b.c\" op=\"modify\">>>\nint b = 2;\n<<<END_CODEC_EDIT>>>",
                mapOf("b.c" to AiFileBaseline("b.c", true, "int b = 1;\n"))
            )
            AiEditApplier.apply(root1, noBackup, "p1", p1)
            AiEditApplier.apply(root2, noBackup, "p2", p2)

            AiEditApplier.clearProjectJournal(noBackup, "p1")
            assertNull(AiEditApplier.readUndoSummary(noBackup, "p1"))
            assertNotNull(AiEditApplier.readUndoSummary(noBackup, "p2"))

            AiEditApplier.clearAllJournals(noBackup)
            assertNull(AiEditApplier.readUndoSummary(noBackup, "p2"))
        } finally {
            root1.deleteRecursively()
            root2.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `preimages exceeding MAX_JOURNAL_BYTES are refused before touching any project file`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            val huge = "x".repeat(AiEditApplier.MAX_JOURNAL_BYTES + 1024)
            File(root, "big.c").write(huge)
            val prop = AiEditProposal(
                prose = "",
                files = listOf(AiProposedFileEdit("big.c", AiEditOp.MODIFY, huge, "int small = 0;\n", "", 1, 1)),
                baselines = mapOf("big.c" to AiFileBaseline("big.c", true, huge))
            )
            val res = AiEditApplier.apply(root, noBackup, "demo", prop)
            assertTrue(res is AiApplyOutcome.Failed)
            assertTrue((res as AiApplyOutcome.Failed).message.contains("journal limit"))
            assertEquals(huge.length, File(root, "big.c").readText().length)
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }

    @Test
    fun `mid-batch write failure rolls back earlier files in the batch from their preimages`() {
        val root = tempDir("proj")
        val noBackup = tempDir("nobackup")
        try {
            File(root, "first.c").write("int first = 1;\n")
            // Make a directory at `second.c/sub` so creating a file inside `second.c` (where `first.c` is a file)
            // or writing to a path blocked by a directory fails mid-batch:
            // Specifically, create a file `blocker.c` on disk, then ask CREATE to create `blocker.c/nested.c`!
            File(root, "blocker.c").write("not a directory\n")

            val prop = AiEditProposal(
                prose = "",
                files = listOf(
                    AiProposedFileEdit("first.c", AiEditOp.MODIFY, "int first = 1;\n", "int first = 2;\n", "", 1, 1),
                    AiProposedFileEdit("blocker.c/nested.c", AiEditOp.CREATE, "", "int n = 1;\n", "", 1, 0)
                ),
                baselines = mapOf(
                    "first.c" to AiFileBaseline("first.c", true, "int first = 1;\n"),
                    "blocker.c/nested.c" to AiFileBaseline("blocker.c/nested.c", false, "")
                )
            )

            val res = AiEditApplier.apply(root, noBackup, "demo", prop)
            assertTrue("Expected Failed, got $res", res is AiApplyOutcome.Failed)
            val failed = res as AiApplyOutcome.Failed
            assertTrue(failed.rolledBack)
            assertEquals("first.c must be rolled back to its preimage", "int first = 1;\n", File(root, "first.c").readText())
            assertNull("Journal is cleaned up after rolled-back failure", AiEditApplier.readUndoSummary(noBackup, "demo"))
        } finally {
            root.deleteRecursively()
            noBackup.deleteRecursively()
        }
    }
}
