package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 58.3 — the preview's ☰ is wired, and nothing it put away became
 * unreachable.
 *
 * The pure decisions live in `PreviewChromePolicy` (pinned by
 * `PreviewChromePolicyTest`). What a host JVM cannot see is the *screen*: that
 * the ☰ really renders in the preview's own app bar, that the panel really is
 * behind it, and that every item the policy can return really has an action
 * attached — a menu entry with no `onClick` is the dead control this app does
 * not draw.
 */
class PreviewChromeWiringTest {

    private val preview = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/screens/WebPreviewScreen.kt"
    ).readText()

    // The screen's own kdoc quotes the policy by name, so the pins read the
    // code (the Phase 45 round-2 lesson: pin the button, never the word).
    private val code = RepoFiles.codeOnly(preview)

    private val panel = RepoFiles.mainSource(
        "app/src/main/java/com/codeci/ide/ui/components/ServerSharePanel.kt"
    ).readText()

    private val strings = RepoFiles.mainSource(
        "app/src/main/res/values/strings.xml"
    ).readText()

    @Test
    fun `the preview's app bar carries the hamburger next to reload`() {
        assertTrue("the ☰ rides in the app bar's actions", code.contains("if (PreviewChromePolicy.hasMenu(previewChrome))"))
        assertTrue("and it is the app's own menu glyph", code.contains("SpckIcons.EditorMenu"))
        assertTrue(code.contains("contentDescription = stringResource(R.string.preview_menu)"))
        assertTrue(code.contains("DropdownMenu(") && code.contains("expanded = menuOpen"))
        // The two controls the screen always had are still there, unmoved.
        assertTrue(code.contains("Icons.AutoMirrored.Filled.ArrowBack"))
        assertTrue(code.contains("Icons.Default.Refresh"))
    }

    @Test
    fun `every item the policy can return has an action`() {
        for (link in listOf(
            "COPY_PAGE_ADDRESS",
            "OPEN_PHONE_BROWSER",
            "OPEN_PEER_LINK",
            "LAN_SHARING",
            "SERVER_OPTIONS",
            "STOP_SERVERS",
        )) {
            assertTrue("PreviewLink.$link is offered but never handled", code.contains("PreviewLink.$link ->"))
        }
        // …and each is a real action this screen already had, not a new one.
        assertTrue("the copy action", code.contains("ClipboardManager"))
        assertTrue("the same open-or-copy policy the panel uses", code.contains("OpenInBrowser.openOrCopy("))
        assertTrue("the same LAN switch", code.contains("LanSharePolicy.shared.set(!lanShared)"))
        assertTrue("the same panel", code.contains("ServerSharePanel("))
        assertTrue("the same registry stop", code.contains("host.stopAll()"))
    }

    @Test
    fun `the panel is behind the hamburger, and the address row stays`() {
        assertTrue(
            "the panel opens from its own menu item",
            code.contains("showServerPanel = true"),
        )
        assertTrue(
            "and the guard is the policy's decision, not an inline boolean",
            code.contains("if (shareEndpoints != null && PreviewChromePolicy.panelVisible(showServerPanel))"),
        )
        assertTrue(
            "the address stays on screen: a preview with no address is worse than the bar it replaced",
            code.contains("val address = currentUrl") && code.contains("text = address"),
        )
        // The panel keeps its own way back, and only for the surface that opens
        // it from a menu (the Output Panel passes nothing).
        assertTrue(panel.contains("onClose: (() -> Unit)? = null"))
        assertTrue(panel.contains("if (onClose != null) {"))
    }

    @Test
    fun `the words live in the resource file`() {
        for (key in listOf(
            "preview_menu",
            "preview_panel_title",
            "preview_menu_copy_address",
            "preview_menu_server_options",
            "preview_menu_stop_servers",
            "preview_menu_lan_on",
            "preview_menu_lan_off",
        )) {
            assertTrue("missing string: $key", strings.contains("name=\"$key\""))
        }
        assertTrue(
            "the browser actions reuse the panel's own wording",
            code.contains("ShareActions.label(ShareRow.ON_PHONE)") &&
                code.contains("ShareActions.label(ShareRow.OTHER_DEVICES)"),
        )
    }
}
