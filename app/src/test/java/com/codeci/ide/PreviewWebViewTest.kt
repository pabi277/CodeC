package com.codeci.ide

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.components.PreviewWebView
import com.codeci.ide.ui.services.PreviewLevel
import com.codeci.ide.ui.viewmodels.WebPreviewViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewWebViewTest {
    @Test fun `real callback observes requests without replacing the response`() {
        val vm = WebPreviewViewModel()
        val view = PreviewWebView(ApplicationProvider.getApplicationContext<Context>(), vm)
        val client = view.webViewClient
        val request = object : WebResourceRequest {
            override fun getUrl() = Uri.parse("https://example.org/style.css?secret=hidden")
            override fun isForMainFrame() = false
            override fun isRedirect() = false
            override fun hasGesture() = false
            override fun getMethod() = "GET"
            override fun getRequestHeaders() = mutableMapOf<String, String>()
        }
        assertNull(client.shouldInterceptRequest(view, request))
        assertEquals("https://example.org/style.css", vm.network.value.single().address)
        assertFalse(vm.network.value.single().mainFrame)
        assertTrue(view.settings.supportZoom())
        assertFalse(view.settings.allowUniversalAccessFromFileURLs)
        view.webChromeClient!!.onConsoleMessage(ConsoleMessage("warn", "test.js", 12, ConsoleMessage.MessageLevel.WARNING))
        assertEquals(PreviewLevel.WARN, vm.console.value.single().level)
        view.disposePreview()
        client.shouldInterceptRequest(view, request)
        assertEquals(1, vm.network.value.size)
    }

    @Test fun `the view is born match-parent, so the page never gets a zero-height layout viewport`() {
        // Round 4 (owner, 2026-09-28): a third-party page's modal rendered as a
        // 42 px strip in CodeC and as a full card in Samsung Browser. Compose's
        // AndroidView adds a bare view with ViewGroup.addView, which stamps
        // WRAP_CONTENT on it, and Chromium's WebView reads *that* — not the exact
        // measure spec — to decide its layout height:
        //     AwLayoutSizer.updateLayoutSettings():
        //         setForceZeroLayoutHeight(isLayoutParamsHeightWrapContent())
        // A 0 px layout viewport makes every `vh` and `height:100%` resolve to 0
        // while `innerHeight` still tells the truth. addView only supplies its
        // default when a view arrives without params, so they must exist before
        // the holder ever sees the view — i.e. from the constructor.
        val vm = WebPreviewViewModel()
        val view = PreviewWebView(ApplicationProvider.getApplicationContext<Context>(), vm)
        val params = view.layoutParams
        assertNotNull(params)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params!!.height)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width)
        // And a host that adds it the way Compose does keeps them.
        val host = FrameLayout(ApplicationProvider.getApplicationContext<Context>())
        host.addView(view)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, view.layoutParams.height)
        view.disposePreview()
    }
}
