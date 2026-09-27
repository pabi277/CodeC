package com.codeci.ide

import org.junit.Assert.*
import org.junit.Test

class PreviewToolsWiringTest {
    private fun source(path: String) = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/$path").readText()
    private val screen = source("screens/WebPreviewScreen.kt")
    private val native = RepoFiles.codeOnly(source("components/PreviewWebView.kt"))

    @Test fun `empty diagnostics remain openable and panel height is bounded`() {
        assertTrue(screen.contains("if (toolsVisible)"))
        assertFalse(screen.contains("if (console.isNotEmpty())"))
        assertTrue(screen.contains("PreviewToolsPolicy.panelHeight(requestedHeight, available)"))
        assertTrue(screen.contains("viewModel.clearNetwork()"))
        assertTrue(screen.contains("toolsVisible = false"))
    }
    @Test fun `network observes without fetching or injecting and release owns disposal`() {
        assertTrue(native.contains("override fun shouldInterceptRequest"))
        assertTrue(native.contains("model.addRequest(token, request.method, request.url.toString(), request.isForMainFrame)"))
        assertTrue(native.contains("return null"))
        assertFalse(native.contains("evaluateJavascript"))
        assertFalse(native.contains("openConnection"))
        assertTrue(native.contains("model.endSession(token)"))
        assertTrue(screen.contains("onRelease = { released ->"))
        assertTrue(screen.contains("released.disposePreview()"))
    }
    @Test fun `native zoom preserves file access restrictions and does not reload`() {
        assertTrue(native.contains("settings.allowUniversalAccessFromFileURLs = false"))
        assertTrue(native.contains("settings.setSupportZoom(true)"))
        assertTrue(native.contains("setInitialScale("))
        assertTrue(native.contains("zoomBy("))
        assertFalse(native.contains("reload()"))
    }
    @Test fun `viewport is actually measured and fitted and existing reload remains`() {
        assertTrue(screen.contains("Modifier.requiredSize(width, height)"))
        assertTrue(screen.contains(".graphicsLayer { scaleX = fit; scaleY = fit }"))
        assertTrue(screen.contains(".weight(1f).clipToBounds()"))
        assertTrue(screen.contains("if (reloadTick > 0) webView?.reload()"))
        assertTrue(screen.contains("OpenInBrowser.openOrCopy("))
    }
}
