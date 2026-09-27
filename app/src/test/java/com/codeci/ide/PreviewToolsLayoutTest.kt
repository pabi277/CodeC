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

    @Test fun `empty console renders and resized panel never consumes all page height`() {
        var requested by mutableStateOf(240f)
        var closed = false
        compose.setContent {
            MaterialTheme {
                Column(Modifier.size(360.dp, 400.dp)) {
                    Box(Modifier.fillMaxWidth().weight(1f).testTag("page"))
                    PreviewToolsPanel(
                        console = emptyList(), network = emptyList(), tab = PreviewToolTab.CONSOLE,
                        levels = PreviewLevel.entries.toSet(),
                        height = PreviewToolsPolicy.panelHeight(requested, 400f),
                        availableHeight = 400f,
                        onTab = {}, onLevel = {}, onResize = { requested += it },
                        onHeight = { requested = it }, onClear = {}, onClose = { closed = true },
                        modifier = Modifier.fillMaxWidth().testTag("panel"),
                    )
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
}
