package com.codeci.ide.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.codeci.ide.ui.services.PreviewToolsPolicy
import com.codeci.ide.ui.services.PreviewViewport
import com.codeci.ide.ui.viewmodels.WebPreviewViewModel
import kotlin.math.roundToInt

/**
 * One native page, one capture session. No request replay.
 *
 * Phase 61 kept this class free of JavaScript on purpose: the Network tab
 * *observed* requests and never injected anything. Phase 72.1 (the owner's own
 * shots, 2026-09-28) changes exactly one thing about that rule — the console he
 * types into, the Elements tree and the viewport ask are all `evaluateJavascript`
 * now, and they are all **the user's or the tool's own scripts**, never a page's
 * payload. The interception path still returns null and still fetches nothing;
 * the scripts this class runs are built by the pure policies and run only on a
 * user action or a page-load decision the owner asked for.
 */
@SuppressLint("SetJavaScriptEnabled")
class PreviewWebView(context: Context, private val model: WebPreviewViewModel) : WebView(context) {
    private val token = model.beginSession()
    private var zoomPercent = 100
    private var disposed = false

    /**
     * Phase 72.1 — the Settings tab's “Fit page to phone”. True by default
     * (the owner's report: only `index.html` loaded well), and it only ever
     * adds a default to a page that never declared a viewport.
     */
    var fitToPhone: Boolean = true

    /**
     * One correction per load. A page that laid itself out for a box it no
     * longer has (Chromium decides the scale before the page's `viewport` meta
     * is in effect — the 457-in-a-411 box the owner's console showed) is loaded
     * again, once, with the box and the meta both known. The app's own loads arm
     * the check again; the correction never arms itself, so this cannot loop.
     */
    private var boxCorrectionDone = false

    init {
        // Round 4 (owner, 2026-09-28: a third-party page's modal rendered as a
        // 42 px strip — its `max-height: 88vh` box collapsed to padding + border
        // — while Samsung Browser showed the whole card). The cause is on this
        // side, not the page's. Compose's `AndroidView` adds a bare view with
        // `ViewGroup.addView`, which stamps WRAP_CONTENT × WRAP_CONTENT on it,
        // and Chromium's WebView decides its *layout* height from those params,
        // not from the exact measure spec it is given:
        //     AwLayoutSizer.updateLayoutSettings():
        //         setForceZeroLayoutHeight(isLayoutParamsHeightWrapContent())
        // With that flag the layout viewport is 0 px tall, so every `vh` unit
        // and every `height: 100%` chain from `<html>` resolves to 0 — while
        // `window.innerHeight` (the visual viewport) still reports the true box,
        // which is why the page-box console line looked healthy. The view is
        // always given an exact box by `Modifier.requiredSize`, so MATCH_PARENT
        // is the truth about it, and it is what turns the quirk off.
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
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
                if (disposed) return
                applyZoom(zoomPercent)
                // Phase 72.1 — the answer the owner's shots demand: a page that
                // never declared a viewport gets a phone-sized default, so every
                // page renders like `index.html` instead of at desktop width.
                if (fitToPhone) {
                    evaluate(PreviewToolsPolicy.viewportScript()) { raw ->
                        model.reportViewport(token, PreviewToolsPolicy.viewportOutcome(raw))
                    }
                } else {
                    model.reportViewport(token, PreviewViewport.DISABLED)
                }
                captureResources()
                reportPageBox()
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

    /**
     * Run one script the app itself built and hand the raw answer back.
     *
     * Always posted to the view's own thread: the console's Execute button, an
     * Elements refresh and a `LaunchedEffect` may all call this, and WebView
     * refuses evaluation from anywhere but the UI thread. A disposed view
     * answers nothing — the caller's parse then reports the refused line, which
     * is the truth.
     */
    fun evaluate(script: String, onResult: (String?) -> Unit) {
        if (disposed) {
            onResult(null)
            return
        }
        post {
            if (disposed) {
                onResult(null)
                return@post
            }
            try {
                evaluateJavascript(script) { value -> onResult(value) }
            } catch (_: Throwable) {
                onResult(null)
            }
        }
    }

    /**
     * Phase 72.1 — ask the page for its Resource Timing entries, so Network's
     * Type/Size/Time columns and the Resources tab have more than the pre-
     * response observer can see. Cheap, bounded, and re-run whenever the panel
     * opens or the page reloads.
     */
    fun captureResources() {
        evaluate(PreviewToolsPolicy.resourcesScript()) { raw ->
            model.addResources(token, PreviewToolsPolicy.parseResources(raw))
        }
    }

    /**
     * Phase 72.1 follow-up (owner, 2026-09-28: *"browser have a good view and
     * code is very smaller view"*). One console line per load with the box the
     * page was laid out in, next to the box this view was given. A screenshot
     * cannot tell "the page renders small" from "the preview box is short" —
     * this line can, and it is the page's own answer, not a guess.
     */
    private fun reportPageBox() {
        val density = resources.displayMetrics.density
        val viewWidthDp = if (density > 0f) (width / density).roundToInt() else 0
        val viewHeightDp = if (density > 0f) (height / density).roundToInt() else 0
        val applied = appliedScale()
        evaluate(PreviewToolsPolicy.pageBoxScript()) { raw ->
            val box = PreviewToolsPolicy.parsePageBox(raw)
            model.addConsole(
                token, "log",
                PreviewToolsPolicy.pageBoxLabel(box, viewWidthDp, viewHeightDp, applied),
                0,
            )
            val known = box ?: return@evaluate
            // Round 4 — the tripwire for the quirk the layout params above turn
            // off: if a page ever gets a `100vh` shorter than half its own box
            // again, the console says so in one sentence instead of a screenshot
            // having to.
            if (PreviewToolsPolicy.layoutHeightCollapsed(known)) {
                model.addConsole(token, "warn", PreviewToolsPolicy.collapsedLabel(known), 0)
            }
            if (disposed || boxCorrectionDone) return@evaluate
            if (!PreviewToolsPolicy.boxMismatch(known, viewWidthDp, zoomPercent)) return@evaluate
            boxCorrectionDone = true
            model.addConsole(
                token, "log",
                PreviewToolsPolicy.mismatchLabel(known.cssWidth, viewWidthDp),
                0,
            )
            reloadForBox()
        }
    }

    /** The app asked for a reload (a file changed, the refresh button): check again. */
    override fun reload() {
        boxCorrectionDone = false
        super.reload()
    }

    /** A navigation the app asked for (a link, an address): check again too. */
    override fun loadUrl(url: String) {
        boxCorrectionDone = false
        super.loadUrl(url)
    }

    /** The correction's own load: it must not re-arm the one-correction-per-load rule. */
    private fun reloadForBox() {
        super.reload()
    }

    /** `getScale()` is deprecated, and still the only way to read the applied scale back. */
    @Suppress("DEPRECATION")
    private fun appliedScale(): Double = getScale().toDouble()

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

    /** Phase 72.1 — the Settings tab's Clear cache: the WebView's own cache only. */
    fun clearCache() {
        if (disposed) return
        clearCache(true)
        clearHistory()
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
