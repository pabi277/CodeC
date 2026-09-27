package com.codeci.ide

import android.content.Context
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
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
}
