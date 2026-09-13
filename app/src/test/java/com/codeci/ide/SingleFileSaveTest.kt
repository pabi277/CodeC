package com.codeci.ide

import android.content.Context
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.editor.EditorOpenMode
import com.codeci.ide.ui.projects.EditorLaunchState
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.viewmodels.EditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 46.2 — the single-file save/mode-switch tests (PART_46_2 §Tests).
 * These run the REAL [EditorViewModel] against a real project on Robolectric's
 * filesystem; the money assertion is the first one: **a save from
 * SINGLE_FILE writes the real project path, not the scratch folder.**
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SingleFileSaveTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newProjectWithFile(name: String, rel: String, content: String): File {
        val info = ProjectManager(context).createProject(name, includeStarter = false).getOrThrow()
        val file = File(info.root, rel)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return info.root
    }

    @Test
    fun `save from SINGLE_FILE writes the real project path - not the scratch folder`() {
        val root = newProjectWithFile("peek_save", "src/main.c", "int main(){return 0;}\n")
        val vm = EditorViewModel()

        vm.openSingleProjectFile(context, "peek_save", "src/main.c")
        assertEquals(EditorOpenMode.SINGLE_FILE, vm.openMode.value)
        assertEquals("src/main.c", vm.fileName.value)

        vm.updateCode(TextFieldValue("int main(){return 42;}\n", TextRange.Zero))
        // What the buffer holds is what the user gets (typing aids may shape
        // it; the save must be faithful to the buffer either way).
        val edited = vm.codeText.value.text
        assertTrue("the edit must be in the buffer", edited.contains("42"))
        assertTrue(vm.saveFile(context))

        // The money assertion: the buffer is ON DISK at the real project path.
        assertEquals(edited, File(root, "src/main.c").readText())
        // …and NOT in the scratch folder under that name.
        val scratchCandidate = File(
            com.codeci.ide.ui.utils.FileManager(context).getProjectDir(), "src"
        )
        assertFalse(scratchCandidate.exists())
        assertFalse(vm.isDirty.value)
    }

    @Test
    fun `a SINGLE_FILE open never writes the launch state`() {
        newProjectWithFile("peek_launch", "main.c", "x\n")
        val vm = EditorViewModel()

        vm.openSingleProjectFile(context, "peek_launch", "main.c")
        // No EditorLaunchState.write happened for the peek. The store may hold
        // nothing at all (fresh Robolectric app) — whatever it holds, it must
        // not name this peek.
        val state = EditorLaunchState.load(context)
        assertTrue(state == null || state.fileName != "main.c" || state.projectName != "peek_launch")
    }

    @Test
    fun `switching to PROJECT mode for the same file reloads from disk - no stale buffer, no lost edit`() {
        val root = newProjectWithFile("flip_forth", "main.c", "original\n")
        val vm = EditorViewModel()

        // Peek, edit, save (the flush the autosave would do).
        vm.openSingleProjectFile(context, "flip_forth", "main.c")
        vm.updateCode(TextFieldValue("edited in the peek\n", TextRange.Zero))
        assertTrue(vm.saveFile(context))

        // Mode flip, same back-stack entry: PROJECT open of the same file.
        vm.openFile(context, "flip_forth", "main.c")
        assertEquals(EditorOpenMode.PROJECT, vm.openMode.value)
        // The buffer is the DISK truth (the edit), not a stale other-mode copy.
        assertEquals("edited in the peek\n", vm.codeText.value.text)
        // …and the file on disk still holds it.
        assertEquals("edited in the peek\n", File(root, "main.c").readText())
        // PROJECT mode wrote the launch state (that is what it is for).
        val state = EditorLaunchState.load(context)
        assertNotNull(state)
        assertEquals("flip_forth", state!!.projectName)
        assertEquals("main.c", state.fileName)
    }

    @Test
    fun `flipping back to SINGLE_FILE reloads from disk instead of reusing a PROJECT buffer`() {
        newProjectWithFile("flip_back", "main.c", "from disk\n")
        val vm = EditorViewModel()

        vm.openFile(context, "flip_back", "main.c")
        assertEquals(EditorOpenMode.PROJECT, vm.openMode.value)

        vm.openSingleProjectFile(context, "flip_back", "main.c")
        assertEquals(EditorOpenMode.SINGLE_FILE, vm.openMode.value)
        assertEquals("from disk\n", vm.codeText.value.text)
        // One tab, not the project tab list.
        assertEquals(1, vm.openTabs.value.size)
        // And no git/launch-default chrome state survived the flip.
        assertNull(vm.gitBranch.value)
        assertEquals(emptyMap<String, String>(), vm.gitBadges.value)
        assertNull(vm.launchDefault.value)
    }

    @Test
    fun `a peek refuses to escape the project root`() {
        newProjectWithFile("peek_guard", "main.c", "ok\n")
        val vm = EditorViewModel()
        val scratchName = vm.fileName.value

        vm.openSingleProjectFile(context, "peek_guard", "../peek_save/src/main.c")
        // Still SCRATCH, buffer untouched: the traversal path never opened.
        assertEquals(EditorOpenMode.SCRATCH, vm.openMode.value)
        assertEquals(scratchName, vm.fileName.value)
    }

    // ---- 46.2 device round: the owner's tab law ---------------------------
    // "2 projects are different so don't open together." A PROJECT open of a
    // different project starts that project's own session; no tab list may
    // ever hold two projects' files.

    @Test
    fun `opening project B from project A's session starts B's tab list - no mixing`() {
        newProjectWithFile("tabs_a", "aaa_marker.c", "A\n")
        newProjectWithFile("tabs_b", "bbb_marker.c", "B\n")
        val vm = EditorViewModel()

        vm.openFile(context, "tabs_a", "aaa_marker.c")
        assertEquals(EditorOpenMode.PROJECT, vm.openMode.value)
        assertTrue(vm.openTabs.value.any { it.relativePath == "aaa_marker.c" })

        vm.openFile(context, "tabs_b", "bbb_marker.c")
        assertEquals("tabs_b", vm.projectName.value)
        val paths = vm.openTabs.value.map { it.relativePath }
        assertTrue("B's file must be open", paths.contains("bbb_marker.c"))
        assertFalse(
            "A's tabs must be gone - two projects never share an editor",
            paths.contains("aaa_marker.c")
        )
    }

    @Test
    fun `opening a project from a peek of another project never mixes`() {
        newProjectWithFile("peek_src", "src/peeked.py", "x = 1\n")
        newProjectWithFile("whole_dst", "main.c", "C\n")
        val vm = EditorViewModel()

        vm.openSingleProjectFile(context, "peek_src", "src/peeked.py")
        assertEquals(EditorOpenMode.SINGLE_FILE, vm.openMode.value)

        vm.openFile(context, "whole_dst", "main.c")
        assertEquals("whole_dst", vm.projectName.value)
        val paths = vm.openTabs.value.map { it.relativePath }
        assertFalse("the peeked file must not ride along", paths.contains("src/peeked.py"))
        assertTrue(paths.contains("main.c"))
        assertEquals(EditorOpenMode.PROJECT, vm.openMode.value)
    }

    @Test
    fun `a peek of project B replaces project A's tab list - one file, one project`() {
        newProjectWithFile("mix_a", "aaa_marker.c", "A\n")
        newProjectWithFile("mix_b", "bbb_marker.c", "B\n")
        val vm = EditorViewModel()

        vm.openFile(context, "mix_a", "aaa_marker.c")
        vm.openSingleProjectFile(context, "mix_b", "bbb_marker.c")
        val paths = vm.openTabs.value.map { it.relativePath }
        assertEquals(listOf("bbb_marker.c"), paths)
        // Flip back to PROJECT on A: its session starts clean, with no memory
        // of the list the B peek replaced.
        vm.openFile(context, "mix_a", "aaa_marker.c")
        assertFalse(vm.openTabs.value.any { it.relativePath == "bbb_marker.c" })
    }
}
