package com.codeci.ide

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.components.PreviewToolsPanel
import com.codeci.ide.ui.services.*
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewToolsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Panel(
        tab: PreviewToolTab,
        height: Float,
        available: Float,
        zoomPercent: Int = 100,
        onResize: (Float) -> Unit = {},
        onHeight: (Float) -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        PreviewToolsPanel(
            console = emptyList(),
            network = emptyList(),
            resources = emptyList(),
            domTree = emptyList(),
            details = null,
            selectedNode = -1,
            highlighted = -1,
            viewport = PreviewViewport.AUTHORED,
            fitToPhone = true,
            zoomPercent = zoomPercent,
            resolution = PreviewResolution.DEVICE,
            tab = tab,
            levels = PreviewLevel.entries.toSet(),
            height = height,
            availableHeight = available,
            onTab = {},
            onLevels = {},
            onResize = onResize,
            onHeight = onHeight,
            onClear = {},
            onClose = onClose,
            onCommand = {},
            onCopyText = {},
            onSelectNode = {},
            onToggleHighlight = {},
            onCopyHtml = {},
            onRefreshElements = {},
            onRefreshResources = {},
            onZoom = {},
            onResolution = {},
            onFitToPhone = {},
            onClearCache = {},
        )
    }

    @Test fun `empty console renders and resized panel never consumes all page height`() {
        var requested by mutableStateOf(240f)
        var closed = false
        compose.setContent {
            MaterialTheme {
                Column(Modifier.size(360.dp, 400.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).testTag("page"))
                    Box(Modifier.fillMaxWidth().testTag("panel")) {
                        Panel(
                            tab = PreviewToolTab.CONSOLE,
                            height = PreviewToolsPolicy.panelHeight(requested, 400f),
                            available = 400f,
                            onResize = { requested += it },
                            onHeight = { requested = it },
                            onClose = { closed = true },
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("panel").assertHeightIsEqualTo(240.dp)
        compose.onNodeWithTag("page").assertHeightIsEqualTo(160.dp)
        compose.onNodeWithText("No messages match the selected levels.").assertExists()
        compose.onNodeWithContentDescription("Resize preview console")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(900f) }
        compose.onNodeWithTag("panel").assertHeightIsEqualTo(260.dp)
        compose.onNodeWithTag("page").assertHeightIsEqualTo(140.dp)
        compose.onNodeWithText("Close").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun `the five tabs of the shots are all on the strip`() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.size(360.dp, 600.dp)) {
                    Box(Modifier.fillMaxWidth().testTag("panel")) {
                        Panel(tab = PreviewToolTab.CONSOLE, height = 400f, available = 600f)
                    }
                }
            }
        }
        for (label in listOf("Console", "Elements", "Network", "Resources", "Settings")) {
            compose.onNodeWithText(label).assertExists()
        }
        compose.onNodeWithText("Type JavaScript, then Execute").assertExists()
    }

    @Test fun `settings states the zoom, the resolution and the viewport honestly`() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.size(360.dp, 600.dp)) {
                    Box(Modifier.fillMaxWidth().testTag("panel")) {
                        Panel(tab = PreviewToolTab.SETTINGS, height = 400f, available = 600f, zoomPercent = 150)
                    }
                }
            }
        }
        compose.onNodeWithText("150%").assertExists()
        compose.onNodeWithText("Fit page to phone").assertExists()
        compose.onNodeWithText("This page sets its own viewport; it was left as authored.").assertExists()
        compose.onNodeWithText("Clear cache").assertExists()
    }
}
