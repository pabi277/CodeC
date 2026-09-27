package com.codeci.ide

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.components.EditorTabBar
import com.codeci.ide.ui.components.EditorTabUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Compose measurement, not a source-order pin: an unweighted child can
 * consume ALL height before a weighted editor gets measured. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorTabHeightTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `tab strip leaves remaining height for the code viewport`() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.size(width = 300.dp, height = 400.dp)) {
                    Box(Modifier.fillMaxWidth().height(64.dp))
                    Crossfade(targetState = false, label = "tabs") { noTabs ->
                        if (!noTabs) {
                            Row(Modifier.fillMaxWidth()) {
                                EditorTabBar(
                                    tabs = listOf(EditorTabUi("app.py", "app.py", false)),
                                    activePath = "app.py",
                                    onSelect = {},
                                    onClose = {},
                                    modifier = Modifier.weight(1f).testTag("tabs"),
                                )
                                Box(Modifier.size(48.dp))
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(1f).testTag("code"))
                }
            }
        }
        compose.onNodeWithTag("tabs").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("code").assertHeightIsEqualTo(288.dp)
    }
}
