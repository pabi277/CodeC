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
        console: List<PreviewConsoleEntry> = emptyList(),
        network: List<PreviewRequest> = emptyList(),
        resources: List<PreviewResource> = emptyList(),
        onResize: (Float) -> Unit = {},
        onHeight: (Float) -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        PreviewToolsPanel(
            console = console,
            network = network,
            resources = resources,
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
        // Phone pass: Close is a 48 dp × on the strip, not a text button
        // competing with five tabs for a phone's width.
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun `on a phone the default console shows lines, not just its own rows`() {
        // 411 × 656 dp is the page area the owner's phone reported (Round 4's
        // instrument line). At the old 240 dp default the Console tab kept
        // ~31 dp for output under a 48 dp handle, the strip, the filter row
        // and a 64 dp text field. The default is half the area now and the
        // rows are slimmer: the list must get at least 160 dp (7+ lines).
        val lines = (1..40).map { PreviewConsoleEntry(PreviewLevel.LOG, "line $it", 0) }
        val height = PreviewToolsPolicy.panelHeight(Float.NaN, 656f)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.size(411.dp, 656.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).testTag("page"))
                    Box(Modifier.fillMaxWidth().testTag("panel")) {
                        Panel(tab = PreviewToolTab.CONSOLE, height = height, available = 656f, console = lines)
                    }
                }
            }
        }
        compose.onNodeWithTag("panel").assertHeightIsEqualTo(328.dp)
        compose.onNodeWithTag("page").assertHeightIsEqualTo(328.dp)
        compose.onNodeWithTag("preview_console_lines").assertHeightIsAtLeast(160.dp)
        // The command line and the shots' Execute are there without a second row.
        compose.onNodeWithText("Type JavaScript, then Execute").assertExists()
        compose.onNodeWithText("line 40").assertIsDisplayed()
    }

    @Test fun `network and resources are two-line rows that name the file`() {
        val request = PreviewRequest("GET", "http://127.0.0.1:41897/js/app.js", false, type = "script", size = 12_595L, time = 45L)
        val resource = PreviewResource("http://127.0.0.1:41897/css/style.css", "link", 2_048L, 12L)
        var tab by mutableStateOf(PreviewToolTab.NETWORK)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.size(411.dp, 656.dp)) {
                    Box(Modifier.fillMaxWidth().testTag("panel")) {
                        Panel(tab = tab, height = 328f, available = 656f, network = listOf(request), resources = listOf(resource))
                    }
                }
            }
        }
        compose.onNodeWithText("app.js").assertIsDisplayed()
        compose.onNodeWithText("GET · script · 12.3 kB · 45 ms · 127.0.0.1:41897/js").assertIsDisplayed()
        // No six-column header any more.
        compose.onNodeWithText("Method").assertDoesNotExist()
        tab = PreviewToolTab.RESOURCES
        compose.onNodeWithText("style.css").assertIsDisplayed()
        compose.onNodeWithText("link · 2.0 kB · 12 ms · 127.0.0.1:41897/css").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy address").assertIsDisplayed()
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
