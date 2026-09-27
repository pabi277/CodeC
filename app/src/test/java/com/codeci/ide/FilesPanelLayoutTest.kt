package com.codeci.ide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.components.EditorProjectDrawer
import com.codeci.ide.ui.editor.FileTreeCollapse
import com.codeci.ide.ui.editor.DrawerProjectList
import com.codeci.ide.ui.viewmodels.EditorFileEntry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilesPanelLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Composable private fun Drawer(width: Int, dark: Boolean, fontScale: Float = 1f,
        onLocate: () -> Unit = {}, onRename: () -> Unit = {},
        onShare: () -> Unit = {}, onDownload: () -> Unit = {}) {
        var expanded by remember { mutableStateOf(false) }
        var project by remember { mutableStateOf("Alpha") }
        var finding by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        val allEntries = listOf(EditorFileEntry(project, "data", "data", 0, true),
            EditorFileEntry(project, "data/index.json", "index.json", 1, false))
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.size(width.dp, 500.dp)) {
                    EditorProjectDrawer(
                        projectName = project, branch = "main", changeCount = 0,
                        entries = FileTreeCollapse.search(allEntries, query),
                        collapsedDirs = emptySet(), selectedPath = "data/index.json",
                        launchDefault = null, gitBadges = emptyMap(),
                        onSourceControl = {}, onSwitchBranch = {},
                        onSwitchProject = { expanded = !expanded },
                        projects = DrawerProjectList.build(listOf("Alpha", "Beta"), project),
                        projectsExpanded = expanded, onToggleProjects = { expanded = !expanded },
                        onSelectProject = { project = it ?: "Alpha" },
                        onNewFile = {}, onNewFolder = {}, onRefresh = {}, onToggleCollapseAll = {},
                        allCollapsed = false, onOpenEntry = {}, onRenameEntry = { onRename() },
                        onDeleteEntry = {}, onRunInTerminal = {}, onLaunchEntry = {},
                        onSetLaunchDefault = {}, onClearLaunchDefault = {}, onCopyPath = {},
                        onLocate = onLocate,
                        fileSearchOpen = finding, fileSearchQuery = query,
                        onSearch = { finding = true }, onFileSearchQuery = { query = it },
                        onCloseFileSearch = { finding = false; query = "" },
                        onShareFile = { onShare() }, onDownloadFile = { onDownload() }
                    )
                }
            }
        }
    }

    @Test fun `narrow light panel with large font keeps locate and row actions reachable`() {
        var located = false
        var renamed = false
        compose.setContent { Drawer(280, false, 1.4f, { located = true }, { renamed = true }) }
        compose.onNodeWithContentDescription("Files actions").assertWidthIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("Locate active file").performClick()
        compose.runOnIdle { assertTrue(located) }
        compose.onNodeWithContentDescription("Actions for index.json").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("data/index.json").assertExists()
        compose.onNodeWithText("Rename").performClick()
        compose.runOnIdle { assertTrue(renamed) }
    }

    @Test fun `dark panel project switch leaves the in-panel list visible`() {
        compose.setContent { Drawer(360, true) }
        compose.onNodeWithContentDescription("Files actions").performClick()
        compose.onNodeWithText("Switch project").performClick()
        compose.onNodeWithText("Beta").performClick()
        compose.onNodeWithText("●  Beta").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close project list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Actions for index.json").assertIsDisplayed()
    }

    @Test fun `file search remains in Files and row menu offers actual file transfer actions`() {
        var shared = false
        var downloaded = false
        compose.setContent { Drawer(360, true, onShare = { shared = true }, onDownload = { downloaded = true }) }
        compose.onNodeWithContentDescription("Find file").performClick()
        compose.onNodeWithText("Find file by name or path").performTextInput("absent")
        compose.onNodeWithText("No matching files or folders").assertIsDisplayed()
        compose.onNodeWithText("Find file by name or path").performTextReplacement("INDEX.JSON")
        compose.onNodeWithText("index.json").assertIsDisplayed()
        compose.onNodeWithContentDescription("Actions for index.json").performClick()
        compose.onNodeWithText("Share as file").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(shared) }
        compose.onNodeWithContentDescription("Actions for index.json").performClick()
        compose.onNodeWithText("Download").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(downloaded) }
        compose.onNodeWithContentDescription("Close file search").performClick()
        compose.onNodeWithText("Find file by name or path").assertDoesNotExist()
    }
}
