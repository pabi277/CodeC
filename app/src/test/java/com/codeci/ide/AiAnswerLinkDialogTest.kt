package com.codeci.ide

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codeci.ide.ui.ai.AiCopy
import com.codeci.ide.ui.ai.AiMarkdownAnswer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 88.3 / Level 11 — no network effect without two taps. A link in an AI
 * answer opens a confirm dialog showing the full URL and host; only **Open**
 * calls `openUri`, and `javascript:` / `http:` targets never become links.
 * Robolectric Compose: runs on CI only (the host harness cannot run Compose).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiAnswerLinkDialogTest {
    @get:Rule val compose = createComposeRule()

    private class RecordingUriHandler : UriHandler {
        val opened = mutableListOf<String>()
        override fun openUri(uri: String) {
            opened += uri
        }
    }

    private fun show(answer: String): RecordingUriHandler {
        val handler = RecordingUriHandler()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                MaterialTheme { AiMarkdownAnswer(answer, streaming = false) }
            }
        }
        return handler
    }

    @Test
    fun `tapping an https link shows the dialog with the full url and host and opens nothing`() {
        val url = "https://developer.android.com/x?y=1#z"
        val handler = show("[docs]($url)")
        compose.onNodeWithText("docs").performClick()
        compose.onNodeWithText(AiCopy.LINK_DIALOG_TITLE).assertIsDisplayed()
        compose.onNodeWithText(url).assertIsDisplayed()
        compose.onNodeWithText("developer.android.com").assertIsDisplayed()
        assertEquals(emptyList<String>(), handler.opened)
    }

    @Test
    fun `only Open opens, exactly once, with the url the dialog showed`() {
        val url = "https://developer.android.com/x?y=1#z"
        val handler = show("[docs]($url)")
        compose.onNodeWithText("docs").performClick()
        compose.onNodeWithText(AiCopy.LINK_CANCEL).performClick()
        compose.onNodeWithText(AiCopy.LINK_DIALOG_TITLE).assertDoesNotExist()
        assertEquals(emptyList<String>(), handler.opened)
        compose.onNodeWithText("docs").performClick()
        compose.onNodeWithText(AiCopy.LINK_OPEN).performClick()
        assertEquals(listOf(url), handler.opened)
        compose.onNodeWithText(AiCopy.LINK_DIALOG_TITLE).assertDoesNotExist()
    }

    @Test
    fun `a javascript link is inert text - no dialog and no open`() {
        val handler = show("[click me](javascript:alert(1))")
        compose.onNodeWithText("click me", substring = true).performClick()
        compose.onNodeWithText(AiCopy.LINK_DIALOG_TITLE).assertDoesNotExist()
        // The hostile target is visible as plain text instead of hidden behind a link.
        compose.onNodeWithText("javascript:alert(1)", substring = true).assertIsDisplayed()
        assertEquals(emptyList<String>(), handler.opened)
    }

    @Test
    fun `a plain http link is inert text - no dialog and no open`() {
        val handler = show("[local server](http://localhost:8080)")
        compose.onNodeWithText("local server", substring = true).performClick()
        compose.onNodeWithText(AiCopy.LINK_DIALOG_TITLE).assertDoesNotExist()
        compose.onNodeWithText("http://localhost:8080", substring = true).assertIsDisplayed()
        assertEquals(emptyList<String>(), handler.opened)
    }
}
