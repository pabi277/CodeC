package com.codeci.ide

import org.junit.Assert.*
import org.junit.Test

/**
 * Phase 61 built the diagnostics panel; Phase 72.1 (2026-09-28) grew it into
 * the five tabs the owner's own shots show and gave the console a command line.
 *
 * The one deliberate reversal this file records: Phase 61 pinned that the
 * native view could not contain `evaluateJavascript` — the Network tab observed
 * requests and injected nothing. The owner's screenshots (SPCK's console with
 * *Cancel · Execute*) and his report (*“i can't run any console command”*) asked
 * for the opposite, so the native view now evaluates — but only scripts the
 * pure policies build, and only from the panel's own actions. The interception
 * path still returns null and still fetches nothing.
 */
class PreviewToolsWiringTest {
    private fun source(path: String) = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText()
    private val screen = source("screens/WebPreviewScreen.kt")
    private val panel = source("components/PreviewToolsPanel.kt")
    private val native = RepoFiles.codeOnly(source("components/PreviewWebView.kt"))

    @Test fun `empty diagnostics remain openable and panel height is bounded`() {
        assertTrue(screen.contains("if (toolsVisible)"))
        assertFalse(screen.contains("if (console.isNotEmpty())"))
        assertTrue(screen.contains("PreviewToolsPolicy.panelHeight(requestedHeight, available)"))
        assertTrue(screen.contains("viewModel.clearNetwork()"))
        assertTrue(screen.contains("toolsVisible = false"))
    }
    @Test fun `network observes without fetching or replacing the response`() {
        assertTrue(native.contains("override fun shouldInterceptRequest"))
        assertTrue(native.contains("model.addRequest(token, request.method, request.url.toString(), request.isForMainFrame)"))
        assertTrue(native.contains("return null"))
        assertFalse(native.contains("openConnection"))
        assertTrue(native.contains("model.endSession(token)"))
        assertTrue(screen.contains("onRelease = { released ->"))
        assertTrue(screen.contains("released.disposePreview()"))
    }
    @Test fun `the console runs only policy-built scripts and only on an action`() {
        // Phase 72.1 — the scripts are the policies' (never a page's payload),
        // and the evaluation happens on the view's own thread.
        assertTrue(native.contains("fun evaluate(script: String, onResult: (String?) -> Unit)"))
        assertTrue(native.contains("post {"))
        assertTrue(native.contains("evaluateJavascript(script)"))
        assertTrue(screen.contains("PreviewConsolePolicy.command(text)"))
        assertTrue(screen.contains("viewModel.appendConsole(PreviewConsolePolicy.echo(text))"))
        assertTrue(screen.contains("viewModel.appendConsole(PreviewConsolePolicy.result(raw))"))
        assertTrue(panel.contains("R.string.preview_console_execute"))
        assertTrue(panel.contains("R.string.preview_console_cancel"))
        assertTrue(panel.contains("KeyboardActions(onSend = { execute() })"))
    }
    @Test fun `the five tabs, the elements tree and the resource read are wired`() {
        assertTrue(panel.contains("PreviewToolTab.entries.forEach"))
        assertTrue(screen.contains("PreviewInspectorPolicy.treeScript()"))
        assertTrue(screen.contains("PreviewInspectorPolicy.detailScript(index)"))
        assertTrue(screen.contains("PreviewInspectorPolicy.highlightScript(next)"))
        assertTrue(screen.contains("PreviewInspectorPolicy.htmlScript(index)"))
        assertTrue(native.contains("PreviewToolsPolicy.resourcesScript()"))
        assertTrue(native.contains("PreviewToolsPolicy.viewportScript()"))
        assertTrue(screen.contains("PreviewToolsPolicy.merge(network, resources)"))
    }
    @Test fun `native zoom preserves file access restrictions and does not reload`() {
        assertTrue(native.contains("settings.allowUniversalAccessFromFileURLs = false"))
        assertTrue(native.contains("settings.setSupportZoom(true)"))
        assertTrue(native.contains("setInitialScale("))
        assertTrue(native.contains("zoomBy("))
        assertFalse(native.contains("reload()"))
    }
    @Test fun `closing the panel or leaving the console takes the keyboard with it`() {
        // The screen reserves the keyboard's inset only while the panel is open
        // (2026-09-28), so the two taps that dispose the console's command line —
        // Close, and a tab switch away from the console — must release focus in
        // the same tap; otherwise the keyboard stays up over a page drawn
        // underneath it.
        assertTrue(panel.contains("onClose = { focusManager.clearFocus(force = true); onClose() }"))
        assertTrue(panel.contains("onTab = { focusManager.clearFocus(force = true); onTab(it) }"))
        assertTrue(screen.contains("if (toolsVisible) Modifier.imePadding() else Modifier"))
    }

    @Test fun `the keyboard is only reserved while the tools panel is open`() {
        // 2026-09-28, owner: *"browser have a good view and code is very smaller
        // view"*, with an unaccounted band under the page. The console line is
        // the only text field on this screen and it lives in the tools panel, so
        // the IME inset is reserved only while that panel is open — a keyboard
        // that is not there must never shorten the page.
        assertTrue(screen.contains("if (toolsVisible) Modifier.imePadding() else Modifier"))
    }

    @Test fun `every load reports the page box into the console`() {
        // The line that tells "the page renders small" apart from "the preview
        // box is short" — the page's own answer, from the view that loaded it.
        val view = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/components/PreviewWebView.kt"
        ).readText()
        assertTrue(view.contains("reportPageBox()"))
        assertTrue(view.contains("PreviewToolsPolicy.pageBoxScript()"))
        assertTrue(view.contains("PreviewToolsPolicy.pageBoxLabel("))
    }

    @Test fun `the first load waits for a measured page box`() {
        // 2026-09-28, owner report: the snake sample rendered clipped and
        // squashed inside the preview while the same page was fine in a
        // browser. A page that sizes itself from the viewport must not be
        // loaded before the view has a size; the screen reports the box and
        // waits for it before the first loadUrl.
        assertTrue(screen.contains("var viewportSize by remember { mutableStateOf(IntSize.Zero) }"))
        assertTrue(screen.contains(".onSizeChanged { viewportSize = it }"))
        assertTrue(
            screen.contains("snapshotFlow { viewportSize }.first { it.width > 0 && it.height > 0 }")
        )
        // Bounded: a preview that is never measured must still load its page.
        assertTrue(screen.contains("withTimeoutOrNull(1_000) {"))
        // …and the reload path stays immediate: only the FIRST load waits.
        assertTrue(screen.contains("if (reloadTick > 0) {"))
    }

    @Test fun `viewport is actually measured and fitted and existing reload remains`() {
        assertTrue(screen.contains("Modifier.requiredSize(width, height)"))
        assertTrue(screen.contains(".graphicsLayer { scaleX = fit; scaleY = fit }"))
        assertTrue(screen.contains(".weight(1f).clipToBounds()"))
        assertTrue(screen.contains("if (reloadTick > 0) {"))
        // Phase 68.1 (completed part) — HTML still reloads in place; Markdown
        // must RE-RENDER, or a raw reload would show the source again.
        assertTrue(screen.contains("wv.reload()"))
        assertTrue(screen.contains("loadMarkdownInto(wv, file, previewDark)"))
        assertTrue(screen.contains("OpenInBrowser.openOrCopy("))
    }
    @Test fun `the bar carries the shots' console toggle and zoom readout`() {
        assertTrue(screen.contains("SpckIcons.Console"))
        assertTrue(screen.contains("SpckIcons.ClearCircle"))
        assertTrue(screen.contains("R.string.preview_bar_subtitle"))
        assertTrue(screen.contains("R.string.preview_console_show"))
        assertTrue(screen.contains("R.string.preview_console_hide"))
    }
}
