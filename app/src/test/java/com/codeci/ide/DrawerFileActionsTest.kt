package com.codeci.ide

import android.content.Context
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.editor.ProjectSearch
import com.codeci.ide.ui.projects.GitDiscardEditors
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.viewmodels.EditorFileEntry
import com.codeci.ide.ui.viewmodels.EditorViewModel
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrawerFileActionsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `deleting last open file cannot recreate it through manual or automatic save`() {
        val project = ProjectManager(context).createProject("delete_last", includeStarter = false).getOrThrow()
        val file = File(project.root, "last.txt").apply { writeText("saved") }
        val vm = EditorViewModel()
        try {
            vm.openSingleProjectFile(context, project.name, "last.txt")
            vm.updateCode(TextFieldValue("unsaved"))
            assertTrue(vm.deleteFileEntry(context, EditorFileEntry(project.name, "last.txt", "last.txt", 0, false)))
            assertTrue(vm.openTabs.value.isEmpty())
            assertEquals("", vm.fileName.value)
            assertFalse(vm.saveFile(context))
            vm.flushAutoSave()
            vm.saveAllTabs(context)
            assertFalse(file.exists())
        } finally { GitDiscardEditors.unregister(vm) }
    }

    @Test fun `deleting folder removes descendant buffers but preserves sibling edits`() {
        val project = ProjectManager(context).createProject("delete_tree", includeStarter = false).getOrThrow()
        File(project.root, "src").mkdirs()
        File(project.root, "src/a.txt").writeText("a")
        File(project.root, "src/b.txt").writeText("b")
        val sibling = File(project.root, "keep.txt").apply { writeText("keep") }
        val vm = EditorViewModel()
        try {
            vm.openFile(context, project.name, "keep.txt")
            vm.updateCode(TextFieldValue("keep edits"))
            vm.openFile(context, project.name, "src/a.txt")
            vm.openFile(context, project.name, "src/b.txt")
            vm.updateCode(TextFieldValue("delete edits"))
            assertTrue(vm.deleteFileEntry(context, EditorFileEntry(project.name, "src", "src", 0, true)))
            assertFalse(vm.openTabs.value.any { it.relativePath.startsWith("src/") })
            vm.saveAllTabs(context)
            assertFalse(File(project.root, "src").exists())
            assertEquals("keep edits", sibling.readText())
        } finally { GitDiscardEditors.unregister(vm) }
    }

    @Test fun `failed create and rename preserve files and report failure`() {
        val project = ProjectManager(context).createProject("drawer_names", includeStarter = false).getOrThrow()
        File(project.root, "a.txt").writeText("a")
        File(project.root, "b.txt").writeText("b")
        val vm = EditorViewModel()
        try {
            vm.openFile(context, project.name, "a.txt")
            assertFalse(vm.createAndOpenFile(context, "a.txt"))
            assertFalse(vm.createAndOpenFile(context, "../escape.txt"))
            assertFalse(vm.renameFileEntry(context, EditorFileEntry(project.name, "a.txt", "a.txt", 0, false), "b.txt"))
            assertEquals("a", File(project.root, "a.txt").readText())
            assertEquals("b", File(project.root, "b.txt").readText())
        } finally { GitDiscardEditors.unregister(vm) }
    }

    @Test fun `search jump uses line and column and refuses other project and missing file`() {
        val project = ProjectManager(context).createProject("search_jump", includeStarter = false).getOrThrow()
        val file = File(project.root, "a.txt").apply { writeText("first\n  match") }
        val vm = EditorViewModel()
        try {
            vm.openFile(context, project.name, "a.txt")
            val hit = ProjectSearch.Hit("a.txt", 2, 3, "match")
            assertFalse(vm.openSearchHit(context, "different_project", hit))
            assertTrue(vm.openSearchHit(context, project.name, hit))
            assertEquals(8, vm.codeText.value.selection.start)
            file.delete()
            assertFalse(vm.openSearchHit(context, project.name, hit))
        } finally { GitDiscardEditors.unregister(vm) }
    }

    @Test fun `nested create opens exact path and expands its ancestors`() {
        val project = ProjectManager(context).createProject("nested_create", includeStarter = false).getOrThrow()
        File(project.root, "start.txt").writeText("start")
        val vm = EditorViewModel()
        try {
            vm.openFile(context, project.name, "start.txt")
            assertTrue(vm.createAndOpenFile(context, "css/subjects.css"))
            assertTrue(File(project.root, "css/subjects.css").isFile)
            assertEquals("css/subjects.css", vm.activeTabPath.value)
            assertFalse(vm.collapsedDirs.value.contains("css"))
            assertTrue(vm.createAndOpenFile(context, "more/a.css", "css"))
            assertEquals("css/more/a.css", vm.activeTabPath.value)
        } finally { GitDiscardEditors.unregister(vm) }
    }

    @Test fun `export saves the selected dirty tab and never substitutes the active file`() {
        val project = ProjectManager(context).createProject("export_selected", includeStarter = false).getOrThrow()
        File(project.root, "a.txt").writeText("a")
        File(project.root, "b.txt").writeText("b")
        val vm = EditorViewModel()
        try {
            vm.openFile(context, project.name, "a.txt")
            vm.updateCode(TextFieldValue("selected edits"))
            vm.openFile(context, project.name, "b.txt")
            vm.updateCode(TextFieldValue("active edits"))
            val chosen = EditorFileEntry(project.name, "a.txt", "a.txt", 0, false)
            assertEquals("selected edits", vm.fileForExport(context, chosen)?.readText())
            assertEquals("active edits", vm.codeText.value.text)
            assertEquals("b.txt", vm.activeTabPath.value)
            assertNull(vm.fileForExport(context, chosen.copy(relativePath = "../outside.txt")))
            assertNull(vm.fileForExport(context, chosen.copy(projectName = "another")))
            assertNull(vm.fileForExport(context, chosen.copy(isDirectory = true)))
        } finally { GitDiscardEditors.unregister(vm) }
    }
}
