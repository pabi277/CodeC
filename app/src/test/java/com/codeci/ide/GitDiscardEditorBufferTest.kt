package com.codeci.ide

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.projects.GitDiscardEditors
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.viewmodels.EditorViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GitDiscardEditorBufferTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `discard reloads selected active buffer and prevents stale saves`() {
        val project = ProjectManager(context).createProject("discard_active", includeStarter = false).getOrThrow()
        val file = File(project.root, "a.txt").apply { writeText("old disk\n") }
        val vm = EditorViewModel()
        try {
            vm.openFile(context, project.name, "a.txt")
            val ticket = GitDiscardEditors.begin(project.root, "a.txt")
            try {
                file.writeText("restored index\n") // Emulate completed Git operation.
                assertFalse("even manual save must not rewrite the restored disk", vm.saveFile(context))
                assertEquals("restored index\n", file.readText())
            } finally { GitDiscardEditors.finish(ticket) }
            assertEquals("restored index\n", vm.codeText.value.text)
            assertFalse(vm.isDirty.value)
            assertFalse(vm.canUndo.value)
            assertTrue(vm.saveFile(context))
            assertEquals("restored index\n", file.readText())
        } finally { GitDiscardEditors.unregister(vm) }
    }
    @Test fun `dirty editor refuses discard while other tab edits survive a targeted reload`() {
        val project = ProjectManager(context).createProject("discard_other", includeStarter = false).getOrThrow()
        File(project.root, "a.txt").writeText("a\n")
        val other = File(project.root, "b.txt").apply { writeText("b\n") }
        val vm = EditorViewModel()
        try {
            vm.openFile(context, project.name, "a.txt")
            vm.updateCode(TextFieldValue("keep unsaved a\n"))
            val edited = vm.codeText.value.text
            assertFalse(vm.canDiscardFile(project.root, "a.txt"))
            assertTrue(runCatching { GitDiscardEditors.begin(project.root, "a.txt") }.isFailure)
            val ticket = GitDiscardEditors.begin(project.root, "b.txt")
            try { other.writeText("restored b\n") } finally { GitDiscardEditors.finish(ticket) }
            assertEquals(edited, vm.codeText.value.text)
            assertTrue(vm.isDirty.value)
            assertEquals("restored b\n", vm.openTabs.value.single { it.relativePath == "b.txt" }.buffer.text)
        } finally { GitDiscardEditors.unregister(vm) }
    }
}
