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
    @Test fun `native zoom preserves file access restrictions, and reloads only for the box`() {
        assertTrue(native.contains("settings.allowUniversalAccessFromFileURLs = false"))
        assertTrue(native.contains("settings.setSupportZoom(true)"))
        assertTrue(native.contains("setInitialScale("))
        assertTrue(native.contains("zoomBy("))
        // Phase 61 pinned `assertFalse(native.contains("reload()"))`: the view
        // observed, it never drove the page. **Reversed 2026-09-28, with its
        // reason**: the owner's console showed the same page laid out 411 CSS px
        // in a 411 dp box three times and 457 the fourth — a page laid out for a
        // box it does not have, because Chromium decides the scale before the
        // page's `viewport` meta is in effect. The one reload that fixes it is
        // the *only* reload here, it is bounded to one per load the app asked
        // for, and `PreviewToolsPolicy.boxMismatch` (pure, tested) decides it.
        // The only two ways this view ever drives the page: the app's own
        // reload passing through (and re-arming the check), and the policy-gated
        // correction. Nothing else — no timer, no observer, no page script.
        assertEquals(2, Regex("super\\.reload\\(\\)").findAll(native).count())
        assertTrue(native.contains("override fun reload()"))
        assertTrue(native.contains("private fun reloadForBox()"))
        assertTrue(native.contains("PreviewToolsPolicy.boxMismatch(known, viewWidthDp, zoomPercent)"))
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

    @Test fun `the phone pass keeps the panel and the page their size while a command is typed`() {
        // 2026-09-28, owner: "make everything from this phase phone friendly".
        // The keyboard is added back into the height the panel is sized
        // against, the page reserves (panel − keyboard) under itself, and the
        // panel is drawn over the page, bottom-aligned above the keyboard —
        // so typing neither shrinks the panel to 65 % of what is left nor
        // relays the page out at a new height.
        assertTrue(screen.contains("val imeDp = WindowInsets.ime.getBottom(LocalDensity.current) / LocalDensity.current.density"))
        assertTrue(screen.contains("val available = maxHeight.value + imeDp"))
        assertTrue(screen.contains("val panelShown = minOf(panelHeight, maxHeight.value)"))
        assertTrue(screen.contains("val pageReserve = (panelHeight - imeDp).coerceIn(0f, maxHeight.value)"))
        assertTrue(screen.contains("if (toolsVisible) Spacer(Modifier.height(pageReserve.dp))"))
        assertTrue(screen.contains("modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),"))
        // Nothing dragged yet resolves through the policy's default share.
        assertTrue(screen.contains("var requestedHeight by remember { mutableStateOf(Float.NaN) }"))
        // The panel: the strip is the handle (no 48 dp handle row), the command
        // line is one row with the shots' Cancel · Execute inside it, lines are
        // coloured by level and the list follows its newest entry.
        assertFalse(panel.contains("if (height >= 200f)"))
        assertFalse(panel.contains("OutlinedTextField("))
        assertTrue(panel.contains("BasicTextField("))
        assertTrue(panel.contains("if (focused || input.isNotBlank()) {"))
        assertTrue(panel.contains("listState.scrollToItem(visible.lastIndex)"))
        assertTrue(panel.contains("PreviewLevel.ERROR -> scheme.error to scheme.errorContainer.copy(alpha = .35f)"))
        // Two-line rows through the pure labels; no six-column header.
        assertTrue(panel.contains("PreviewToolsPolicy.nameLabel(request.address)"))
        assertTrue(panel.contains("PreviewToolsPolicy.requestSummary(request)"))
        assertTrue(panel.contains("PreviewToolsPolicy.resourceSummary(resource)"))
        assertFalse(panel.contains("PreviewTableHeader"))
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
        // 2026-09-28, the third-party page report: the line also carries the page's
        // own `viewport` declaration and the scale the WebView actually applied —
        // the numbers that separate "laid out wider than the phone" from "drawn
        // smaller than the phone", which two screenshots could not tell apart.
        assertTrue(view.contains("private fun appliedScale(): Double = getScale().toDouble()"))
        // …and it is acted on, once: a page that laid itself out for a box it no
        // longer has is loaded again, and the app's own loads are what re-arm the
        // check — the correction can never re-arm itself into a loop.
        val plain = RepoFiles.codeOnly(view)
        assertTrue(plain.contains("PreviewToolsPolicy.boxMismatch(known, viewWidthDp, zoomPercent)"))
        assertTrue(plain.contains("PreviewToolsPolicy.mismatchLabel(known.cssWidth, viewWidthDp)"))
        assertTrue(plain.contains("override fun reload()"))
        assertTrue(plain.contains("override fun loadUrl(url: String)"))
        assertTrue(plain.contains("private fun reloadForBox()"))
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
    @Test fun `the page is laid out in a real layout viewport, and the console can tell`() {
        // Round 4 (owner, 2026-09-28): a third-party page's modal was a 42 px
        // strip in CodeC and a full card in Samsung Browser — `max-height: 88vh`
        // collapsed to padding + border because the WebView's layout viewport
        // was 0 px tall. Compose's AndroidView stamps WRAP_CONTENT on a bare
        // view, and Chromium reads exactly that to force a zero layout height
        // (`AwLayoutSizer`: setForceZeroLayoutHeight(isLayoutParamsHeightWrapContent())).
        // The view is always given an exact box (`requiredSize` above), so its
        // params must say MATCH_PARENT — from the constructor, before any host
        // can add it. The Robolectric half of this pin is PreviewWebViewTest.
        assertTrue(native.contains("ViewGroup.LayoutParams.MATCH_PARENT"))
        assertTrue(native.contains("layoutParams = ViewGroup.LayoutParams("))
        assertFalse(native.contains("WRAP_CONTENT"))
        // The instrument: `innerHeight` is the visual viewport and stayed
        // truthful throughout, so the page-box line now also reports what the
        // page's own CSS gets for `100vh`, and a collapsed answer becomes a
        // warning in the console instead of a screenshot.
        val policy = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/services/PreviewToolsPolicy.kt"
        ).readText()
        assertTrue(policy.contains("height:100vh;"))
        assertTrue(policy.contains("fun layoutHeightCollapsed(box: PreviewPageBox?): Boolean"))
        assertTrue(native.contains("PreviewToolsPolicy.layoutHeightCollapsed(known)"))
        assertTrue(native.contains("PreviewToolsPolicy.collapsedLabel(known)"))
    }
    @Test fun `the bar carries the shots' console toggle and zoom readout`() {
        assertTrue(screen.contains("SpckIcons.Console"))
        assertTrue(screen.contains("SpckIcons.ClearCircle"))
        assertTrue(screen.contains("R.string.preview_bar_subtitle"))
        assertTrue(screen.contains("R.string.preview_console_show"))
        assertTrue(screen.contains("R.string.preview_console_hide"))
    }
}
