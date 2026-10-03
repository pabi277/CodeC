package com.codeci.ide

import com.codeci.ide.ui.ai.AiTaskMemory
import com.codeci.ide.ui.ai.AiTaskMemoryRecord
import com.codeci.ide.ui.ai.AiTaskPlanItem
import com.codeci.ide.ui.ai.AiTaskMemoryLimits
import com.codeci.ide.ui.ai.AiToolCall
import com.codeci.ide.ui.ai.AiToolName
import com.codeci.ide.ui.projects.AiTaskMemoryStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The durable edge is bounded, app-private, clearable and never project-owned. */
class AiTaskMemoryStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fixture(): Triple<File, File, List<String>> {
        val noBackup = temporary.newFolder("no-backup")
        val root = temporary.newFolder("project")
        File(root, "src").mkdirs()
        File(root, "src/main.c").writeText("int main(void) { return 0; }\n")
        return Triple(noBackup, root, listOf("src/main.c"))
    }

    private fun memory(root: File, admitted: List<String>) = AiTaskMemory.EMPTY.prepareRead(
        AiToolCall(AiToolName.READ_FILE, "read_file", path = "src/main.c", start = 1, end = 10),
        root,
        admitted,
        emptyMap(),
        nowMs = 123L
    ).memory

    @Test
    fun `memory is outside the project under the no-backup task path and round trips`() {
        val (noBackup, root, admitted) = fixture()
        val store = AiTaskMemoryStore(noBackup, "demo")
        val memory = memory(root, admitted)
        assertTrue(store.save(memory, admitted))

        val stored = File(noBackup, "ai/task/demo/task-memory.bin")
        assertTrue(stored.isFile)
        assertTrue(stored.length() <= AiTaskMemoryLimits.MAX_STORE_BYTES)
        assertFalse("task memory never appears in the project", File(root, "ai/task/demo/task-memory.bin").exists())
        val loaded = store.load(root, admitted)
        assertEquals(memory.files.single().contentVersion, loaded.files.single().contentVersion)
        assertEquals(memory.files.single().content, loaded.files.single().content)
    }

    @Test
    fun `load deletes invalidated snapshots from durable storage`() {
        val (noBackup, root, admitted) = fixture()
        val store = AiTaskMemoryStore(noBackup, "demo")
        assertTrue(store.save(memory(root, admitted), admitted))
        val stored = File(noBackup, "ai/task/demo/task-memory.bin")
        assertTrue(stored.isFile)

        File(root, "src/main.c").writeText("int changed_after_snapshot;\n")
        val loaded = store.load(root, admitted)

        assertTrue("changed disk content cannot reuse the stale cache", loaded.files.isEmpty())
        assertFalse("invalidated source bytes are removed from the persistent copy", stored.exists())
    }

    @Test
    fun `secret-like structured notes are filtered before the durable write`() {
        val (noBackup, root, admitted) = fixture()
        val store = AiTaskMemoryStore(noBackup, "demo")
        val secret = "not-a-real-secret-value"
        val unsafe = memory(root, admitted).copy(
            findings = listOf(AiTaskMemoryRecord("api_key=$secret")),
            plan = listOf(AiTaskPlanItem("Copy api_key=$secret into the report"))
        )

        assertTrue(store.save(unsafe, admitted))
        val stored = File(noBackup, "ai/task/demo/task-memory.bin")
        val storedBytes = String(stored.readBytes(), Charsets.ISO_8859_1)
        assertFalse(storedBytes.contains(secret))
        val loaded = store.load(root, admitted)
        assertTrue(loaded.findings.isEmpty())
        assertTrue(loaded.plan.isEmpty())
    }

    @Test
    fun `project deletion and key deletion cleanup remove only the task-memory tree`() {
        val (noBackup, root, admitted) = fixture()
        val store = AiTaskMemoryStore(noBackup, "demo")
        assertTrue(store.save(memory(root, admitted), admitted))
        assertTrue(AiTaskMemoryStore.clearProject(noBackup, "demo"))
        assertFalse(File(noBackup, "ai/task/demo").exists())

        assertTrue(store.save(memory(root, admitted), admitted))
        assertTrue(AiTaskMemoryStore.clearAll(noBackup))
        assertFalse(File(noBackup, "ai/task").exists())
        // Credentials/settings in sibling ai/ files are not deleted by task-memory cleanup.
        File(noBackup, "ai/ai_settings.properties").apply { parentFile?.mkdirs(); writeText("model=default\n") }
        assertTrue(AiTaskMemoryStore.clearAll(noBackup))
        assertTrue(File(noBackup, "ai/ai_settings.properties").isFile)
    }

    @Test
    fun `turning persistent memory off clears storage but keeps task-local memory usable`() {
        val (noBackup, root, admitted) = fixture()
        val enabled = AiTaskMemoryStore(noBackup, "demo")
        val taskLocal = memory(root, admitted)
        assertTrue(enabled.save(taskLocal, admitted))
        assertTrue(File(noBackup, "ai/task/demo/task-memory.bin").isFile)

        val disabled = AiTaskMemoryStore(noBackup, "demo", persistentEnabled = false)
        assertTrue(disabled.save(taskLocal, admitted))
        assertTrue("off mode removes any previously persisted copy", !File(noBackup, "ai/task/demo/task-memory.bin").exists())
        assertTrue("the in-memory working set itself remains unchanged", taskLocal.files.single().content.contains("int main"))
        assertTrue(disabled.load(root, admitted).isEmpty())
    }
}
