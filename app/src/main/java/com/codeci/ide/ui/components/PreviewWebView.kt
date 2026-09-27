package com.codeci.ide.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.codeci.ide.ui.services.PreviewToolsPolicy
import com.codeci.ide.ui.viewmodels.WebPreviewViewModel
import kotlin.math.roundToInt

/** One native page, one capture session. No request replay or JS instrumentation. */
@SuppressLint("SetJavaScriptEnabled")
class PreviewWebView(context: Context, private val model: WebPreviewViewModel) : WebView(context) {
    private val token = model.beginSession()
    private var zoomPercent = 100
    private var disposed = false

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = false
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        setInitialScale((resources.displayMetrics.density * zoomPercent).roundToInt())
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                model.addRequest(token, request.method, request.url.toString(), request.isForMainFrame)
                return null // Observe only. WebView owns cookies, cache, redirects and the response.
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (!disposed) applyZoom(zoomPercent)
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val level = when (message.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> "error"
                    ConsoleMessage.MessageLevel.WARNING -> "warn"
                    ConsoleMessage.MessageLevel.TIP -> "info"
                    ConsoleMessage.MessageLevel.DEBUG -> "debug"
                    ConsoleMessage.MessageLevel.LOG -> "log"
                }
                model.addConsole(token, level, message.message(), message.lineNumber())
                return true
            }
        }
    }

    /** Changes the current page without reloading; initial scale covers later navigations. */
    @Suppress("DEPRECATION")
    fun applyZoom(percent: Int) {
        if (disposed || percent !in PreviewToolsPolicy.zoomPresets) return
        zoomPercent = percent
        val targetScale = resources.displayMetrics.density * percent / 100f
        setInitialScale((targetScale * 100).roundToInt())
        val current = scale
        if (current.isFinite() && current > 0f) {
            zoomBy((targetScale / current).coerceIn(.01f, 100f))
        }
    }

    fun disposePreview() {
        if (disposed) return
        disposed = true
        model.endSession(token) // Synchronised against any in-flight worker callback.
        stopLoading()
        webChromeClient = null
        webViewClient = WebViewClient()
        destroy()
    }
}
