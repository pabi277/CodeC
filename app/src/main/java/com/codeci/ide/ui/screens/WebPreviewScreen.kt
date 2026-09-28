package com.codeci.ide.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import com.codeci.ide.ui.components.PreviewToolsPanel
import com.codeci.ide.ui.components.PreviewZoomDialog
import com.codeci.ide.ui.components.PreviewResolutionDialog
import com.codeci.ide.ui.components.PreviewWebView
import com.codeci.ide.ui.services.PreviewToolsPolicy
import com.codeci.ide.ui.services.PreviewToolTab
import com.codeci.ide.ui.services.PreviewLevel
import com.codeci.ide.ui.services.PreviewResolution
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import com.codeci.ide.ui.services.PreviewConsolePolicy
import com.codeci.ide.ui.services.PreviewDomNode
import com.codeci.ide.ui.services.PreviewInspectorPolicy
import com.codeci.ide.ui.services.PreviewNodeDetails
import com.codeci.ide.ui.services.PreviewViewport
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.ui.theme.AppThemeMode
import com.codeci.ide.ui.theme.CodecType
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.R
import com.codeci.ide.ui.components.ServerSharePanel
import com.codeci.ide.ui.components.SpckIcons
import com.codeci.ide.ui.services.LanAddressProvider
import com.codeci.ide.ui.services.LanSharePolicy
import com.codeci.ide.ui.services.PreviewChromeFacts
import com.codeci.ide.ui.services.PreviewChromePolicy
import com.codeci.ide.ui.services.PreviewLink
import com.codeci.ide.ui.services.OpenInBrowser
import com.codeci.ide.ui.services.ShareActions
import com.codeci.ide.ui.services.ShareRow
import com.codeci.ide.ui.services.ServerHost
import com.codeci.ide.ui.services.ServerHosts
import com.codeci.ide.ui.services.WebPreviewServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.utils.FileManager
import com.codeci.ide.ui.utils.FileNameUtils
import com.codeci.ide.ui.utils.MarkdownPreview
import com.codeci.ide.ui.utils.WebFileSupport
import com.codeci.ide.ui.viewmodels.WebPreviewViewModel
import java.io.File

/**
 * Phase 5.2: preview an HTML file (and its sibling CSS/JS/images) in an
 * in-app WebView. The page is loaded from the app-private project directory
 * via the existing static HTTP server (file:// fallback) or a live server URL.
 * The optional console/Network panel and viewport controls are screen-local;
 * the page still auto-reloads when its watched file changes on disk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPreviewScreen(
    fileName: String?,
    onNavigateBack: () -> Unit,
    projectName: String? = null,
    /** Phase 14 — load a live server URL (e.g. http://127.0.0.1:5000) instead of a project file. */
    customUrl: String? = null,
    viewModel: WebPreviewViewModel = viewModel()
) {
    val context = LocalContext.current
    val liveUrl = customUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    val isLive = liveUrl != null
    val htmlFile = remember(projectName, fileName) { resolveHtmlFile(context, projectName, fileName) }
    val console by viewModel.console.collectAsState()
    val reloadTick by viewModel.reloadTick.collectAsState()
    val error by viewModel.error.collectAsState()

    var webView by remember { mutableStateOf<PreviewWebView?>(null) }
    val network by viewModel.network.collectAsState()
    var toolsVisible by remember { mutableStateOf(false) }
    var toolTab by remember { mutableStateOf(PreviewToolTab.CONSOLE) }
    var levels by remember { mutableStateOf(PreviewLevel.entries.toSet()) }
    var requestedHeight by remember { mutableStateOf(240f) }
    var zoomDialog by remember { mutableStateOf(false) }
    var resolutionDialog by remember { mutableStateOf(false) }
    var resolution by remember { mutableStateOf(PreviewResolution.DEVICE) }
    var currentUrl by remember { mutableStateOf<String?>(liveUrl) }
    /**
     * The measured size of the page box. Reported so the first load can wait
     * for a real box: a page's own first layout must not be taken against 0×0
     * (the snake sample sizes its board from `100vh`).
     */
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Phase 72.1 — the shots' “Preview · 100 % Zoom” subtitle needs the number
    // the WebView is actually at, so the screen owns it and hands it down.
    // Readouts are screen state, not preferences: nothing here is persisted.
    var zoomPercent by rememberSaveable { mutableStateOf(100) }
    var fitToPhone by rememberSaveable { mutableStateOf(true) }
    val resources by viewModel.resources.collectAsState()
    val viewport by viewModel.viewport.collectAsState()
    var domTree by remember { mutableStateOf(emptyList<PreviewDomNode>()) }
    var domDetails by remember { mutableStateOf<PreviewNodeDetails?>(null) }
    var selectedNode by remember { mutableStateOf(-1) }
    var highlighted by remember { mutableStateOf(-1) }

    // Phase 68.1 (completed part) — the rendered Markdown page must match the
    // app's theme, so resolve dark exactly the way MainActivity does.
    val themeManager = remember { ThemeManager(context) }
    val appThemeMode by themeManager.appThemeFlow.collectAsState(initial = AppThemeMode.SYSTEM)
    val previewDark = ThemeManager.effectiveDark(appThemeMode, isSystemInDarkTheme())

    // Phase 9.1: serve the whole folder over a loopback HTTP server so
    // relative CSS/JS, fetch("data.json") and ES modules work like under a
    // real dev server (`file://` blocks all of those). If binding fails the
    // preview degrades to the old file:// load instead of erroring out.
    // Phase 14: live server URLs skip the static server entirely.
    //
    // Phase 37.1/37.2: the socket is owned by the shared ServerHost instead of
    // this composition. Two visible consequences — the LAN switch can rebind
    // the same folder on 0.0.0.0 (and hand peers a QR code), and a LAN preview
    // keeps serving after this screen is gone, while a loopback preview still
    // dies with it exactly as before.
    val host = ServerHosts.shared
    val servedRoot = remember(projectName, htmlFile, liveUrl) {
        if (isLive) null else resolveServedRoot(context, projectName, htmlFile)
    }
    val staticId = remember(servedRoot, projectName) {
        servedRoot?.let { ServerHost.staticId(projectName ?: it.name) }
    }
    val lanShared by LanSharePolicy.shared.enabled.collectAsState()
    var staticEntry by remember(staticId) { mutableStateOf<com.codeci.ide.ui.services.ServerEntry?>(null) }
    var staticError by remember(staticId) { mutableStateOf<String?>(null) }
    LaunchedEffect(servedRoot, staticId, lanShared) {
        val root = servedRoot
        val id = staticId
        if (root == null || id == null) {
            staticEntry = null
            staticError = null
            return@LaunchedEffect
        }
        if (lanShared) LanAddressProvider.refresh(context, host)
        when (val outcome = withContext(Dispatchers.IO) {
            host.serveStatic(id, projectName ?: root.name, root, lanShared)
        }) {
            is ServerHost.StaticOutcome.Serving -> {
                staticEntry = outcome.entry
                staticError = null
            }
            is ServerHost.StaticOutcome.Refused -> {
                staticEntry = null
                staticError = outcome.message
            }
        }
    }
    DisposableEffect(staticId, lanShared) {
        onDispose {
            val id = staticId ?: return@onDispose
            // LAN sharing means somebody else may be reading this folder right
            // now — leaving the preview must not pull the rug out (37.2 §3).
            if (!lanShared) host.stop(id)
        }
    }
    val serverPort = staticEntry?.port
    // The on-device preview always dials loopback; the LAN URL is only ever for
    // peers, so the two can never be mixed up (37.1 §3).
    val pagePath = remember(servedRoot, htmlFile) {
        val root = servedRoot
        val file = htmlFile
        if (root == null || file == null) {
            "/"
        } else {
            "/" + (ProjectPathUtils.relativePath(root, file)?.let { WebPreviewServer.urlPathFor(it) } ?: "")
        }
    }
    val shareEndpoints = remember(staticEntry, liveUrl, pagePath) {
        val entry = staticEntry?.let { it.endpoints }
            ?: liveUrl?.let { host.registry.findByUrl(it)?.endpoints }
        entry?.let {
            if (staticEntry != null) it.copy(
                loopbackUrl = it.loopbackUrl + pagePath,
                lanUrl = it.lanUrl?.plus(pagePath)
            ) else it
        }
    }

    // Phase 58.3 — the upper links move behind one hamburger (the owner's own
    // answer to the roadmap's two candidates: CodeC's preview chrome, not the
    // page inside it). The panel that used to sit above every preview is now
    // the detail behind the ☰, so the page gets that height back and the
    // address row stays as the readout — a browser that hid its address would
    // be worse than the bar this replaces. WHAT the menu offers is the pure
    // `PreviewChromePolicy`'s call, not a chain of ifs here.
    var showServerPanel by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val servers = host.registry.snapshot()
    val previewChrome = PreviewChromeFacts(
        hasAddress = currentUrl != null,
        addressIsHttp = ShareActions.isHttpUrl(currentUrl),
        hasPeerUrl = shareEndpoints?.lanUrl != null,
        // The LAN switch only ever moves a server this screen owns (37.1).
        ownsStaticServer = !isLive,
        serverCount = servers.size,
    )
    val previewLinks = PreviewChromePolicy.links(previewChrome)
    val copyAddress: (String) -> Unit = { url ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CodeC URL", url))
    }
    // The same open-or-copy policy the panel's rows use (Phase 41): a URL the
    // device cannot hand to a browser is copied instead of lost.
    val openAddress: (String) -> Unit = { url ->
        OpenInBrowser.openOrCopy(
            context = context,
            url = url,
            clipboardLabel = "CodeC URL",
            copyInstead = url,
            failureMessage = ShareActions.fallbackMessage(url)
        )
    }

    // Phase 72.1 — everything the five tabs ask for, in one place. The panel
    // stays presentation-only: these lambdas own the WebView calls and the
    // clipboard, and every script comes from a pure policy.
    val copyText: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CodeC", text))
    }
    val refreshElements: () -> Unit = {
        webView?.evaluate(PreviewInspectorPolicy.treeScript()) { raw ->
            domTree = PreviewInspectorPolicy.parseTree(raw)
            domDetails = null
            selectedNode = -1
            highlighted = -1
        }
    }
    val selectNode: (Int) -> Unit = { index ->
        selectedNode = index
        webView?.evaluate(PreviewInspectorPolicy.detailScript(index)) { raw ->
            domDetails = PreviewInspectorPolicy.parseDetails(raw)
        }
    }
    val toggleHighlight: (Int) -> Unit = { index ->
        val next = if (highlighted == index) -1 else index
        highlighted = next
        webView?.evaluate(PreviewInspectorPolicy.highlightScript(next)) { }
    }
    val copyHtml: (Int) -> Unit = { index ->
        webView?.evaluate(PreviewInspectorPolicy.htmlScript(index)) { raw ->
            val html = PreviewConsolePolicy.unquote(raw)
            if (html.isNotBlank()) copyText(html)
        }
    }
    val runCommand: (String) -> Unit = { text ->
        val script = PreviewConsolePolicy.command(text)
        if (script != null) {
            viewModel.appendConsole(PreviewConsolePolicy.echo(text))
            webView?.evaluate(script) { raw ->
                viewModel.appendConsole(PreviewConsolePolicy.result(raw))
            }
        }
    }
    val clearCache: () -> Unit = {
        webView?.clearCache()
        viewModel.clearConsole()
        viewModel.clearNetwork()
        viewModel.requestReload()
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = {
                // Phase 72.1 — the shots' two-line bar: the page, then what the
                // preview is doing with it (Preview · N % Zoom).
                Column {
                    Text(htmlFile?.name ?: (fileName ?: if (isLive) "Live server" else "Preview"))
                    Text(
                        text = stringResource(R.string.preview_bar_subtitle, zoomPercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onNavigateBack
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Phase 58.3 — the ☰, and only when the policy says it has
                // something to offer (a dead control is not drawn). The editor
                // already uses this glyph for its own overflow ("the shot's
                // glyph"), so the two screens agree on what a menu looks like.
                if (PreviewChromePolicy.hasMenu(previewChrome)) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                SpckIcons.EditorMenu,
                                contentDescription = stringResource(R.string.preview_menu)
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            for (link in previewLinks) {
                                when (link) {
                                    PreviewLink.COPY_PAGE_ADDRESS -> DropdownMenuItem(
                                        text = { Text(stringResource(R.string.preview_menu_copy_address)) },
                                        onClick = {
                                            menuOpen = false
                                            currentUrl?.let(copyAddress)
                                        }
                                    )
                                    PreviewLink.OPEN_PHONE_BROWSER -> DropdownMenuItem(
                                        text = { Text(ShareActions.label(ShareRow.ON_PHONE)) },
                                        onClick = {
                                            menuOpen = false
                                            currentUrl?.let(openAddress)
                                        }
                                    )
                                    PreviewLink.OPEN_PEER_LINK -> DropdownMenuItem(
                                        text = { Text(ShareActions.label(ShareRow.OTHER_DEVICES)) },
                                        onClick = {
                                            menuOpen = false
                                            val url = shareEndpoints?.let {
                                                ShareActions.browserUrl(it, ShareRow.OTHER_DEVICES)
                                            }
                                            url?.let(openAddress)
                                        }
                                    )
                                    PreviewLink.LAN_SHARING -> DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (lanShared) R.string.preview_menu_lan_off
                                                    else R.string.preview_menu_lan_on
                                                )
                                            )
                                        },
                                        onClick = {
                                            menuOpen = false
                                            LanSharePolicy.shared.set(!lanShared)
                                        }
                                    )
                                    PreviewLink.SERVER_OPTIONS -> DropdownMenuItem(
                                        text = { Text(stringResource(R.string.preview_menu_server_options)) },
                                        onClick = {
                                            menuOpen = false
                                            // The detail the menu promised: the
                                            // panel itself, unchanged (QR, every
                                            // server, the notices), just no
                                            // longer spending the page's height
                                            // by default.
                                            showServerPanel = true
                                        }
                                    )
                                    PreviewLink.CONSOLE -> DropdownMenuItem(
                                        text = { Text(stringResource(if (toolsVisible)
                                            R.string.preview_hide_console else R.string.preview_show_console)) },
                                        onClick = { menuOpen = false; toolsVisible = !toolsVisible }
                                    )
                                    PreviewLink.ZOOM -> DropdownMenuItem(
                                        text = { Text(stringResource(R.string.preview_zoom)) },
                                        onClick = { menuOpen = false; zoomDialog = true }
                                    )
                                    PreviewLink.RESOLUTION -> DropdownMenuItem(
                                        text = { Text(stringResource(R.string.preview_resolution)) },
                                        onClick = { menuOpen = false; resolutionDialog = true }
                                    )
                                    PreviewLink.STOP_SERVERS -> DropdownMenuItem(
                                        text = { Text(stringResource(R.string.preview_menu_stop_servers)) },
                                        onClick = {
                                            menuOpen = false
                                            host.stopAll()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                // Phase 72.1 — the shots' own console button: one tap, and its
                // glyph swaps to ⊘ while the panel is open, exactly as the
                // shots swap theirs.
                IconButton(onClick = { toolsVisible = !toolsVisible }) {
                    Icon(
                        if (toolsVisible) SpckIcons.ClearCircle else SpckIcons.Console,
                        contentDescription = stringResource(
                            if (toolsVisible) R.string.preview_console_hide
                            else R.string.preview_console_show
                        ),
                    )
                }
                IconButton(
                    onClick = { viewModel.requestReload() },
                    enabled = error == null
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        )

        // Phase 14 — the address bar: shows the live server URL (or the static
        // preview URL) and a "live" badge while a server project is running.
        val address = currentUrl
        if (address != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive) {
                    Text(
                        text = "● ${stringResource(R.string.server_preview_live)}",
                        color = Color(0xFF55FF55),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = address,
                    style = TextStyle(
                        fontFamily = CodecType.codeFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            // Phase 37.1 — the peer-facing address + QR, right under the bar
            // that shows the local one. The LAN switch only appears for the
            // static preview, because that is the server this screen owns.
            //
            // Phase 58.3 — this panel is no longer the default: it spends a
            // hundred-odd dp above the page for information a user needs
            // occasionally, so it now opens from the ☰ (its own "Server
            // options…" item) and closes from its own header. Nothing it
            // carried was removed, and the ☰ stays visible while it is open —
            // the way back cannot be hidden by the thing it opened.
            if (shareEndpoints != null && PreviewChromePolicy.panelVisible(showServerPanel)) {
                ServerSharePanel(
                    endpoints = shareEndpoints,
                    lanShared = lanShared,
                    onToggleLan = { enabled -> LanSharePolicy.shared.set(enabled) },
                    onOpenUrl = { url -> webView?.loadUrl(url) },
                    servers = servers,
                    onStopAll = { host.stopAll() },
                    showSwitch = !isLive,
                    // No `dense`: the panel itself is unchanged, so every
                    // affordance it had (both copy buttons, the QR, STOP ALL)
                    // is still there. Only its default visibility moved.
                    onClose = { showServerPanel = false }
                )
            }
            staticError?.let { message ->
                Text(
                    text = message,
                    color = Color(0xFFFFB347),
                    style = TextStyle(fontSize = 11.sp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // Height comes from remaining content constraints, not screen metrics.
        // Diagnostics are unweighted but bounded; the page owns the remainder.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val available = maxHeight.value
            val panelHeight = PreviewToolsPolicy.panelHeight(requestedHeight, available)
            Column(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(1f).clipToBounds(),
                    contentAlignment = Alignment.Center,
                ) {
                    val simulated = resolution != PreviewResolution.DEVICE
                    val width = if (simulated) resolution.widthDp.dp else maxWidth
                    val height = if (simulated) resolution.heightDp.dp else maxHeight
                    val fit = PreviewToolsPolicy.fitScale(maxWidth.value, maxHeight.value,
                        width.value, height.value)
                    // requiredSize controls native measurement; layer scaling fits
                    // the page visually without lying to responsive CSS about size.
                    AndroidView(
                        modifier = Modifier.requiredSize(width, height)
                            .onSizeChanged { viewportSize = it }
                            .graphicsLayer { scaleX = fit; scaleY = fit },
                        factory = { ctx -> PreviewWebView(ctx, viewModel).also { webView = it } },
                        onRelease = { released ->
                            released.disposePreview()
                            if (webView === released) webView = null
                        },
                    )
                    // Keep the native view alive under an error, so fixing the
                    // target can load it again instead of waiting for a missing view.
                    if (error != null) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center) {
                            Text(error.orEmpty(), modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (toolsVisible) {
                    PreviewToolsPanel(
                        console = console,
                        // The table the shots show: the WebView's own observed
                        // requests joined, by address, with the page's timings.
                        network = PreviewToolsPolicy.merge(network, resources),
                        resources = resources,
                        domTree = domTree,
                        details = domDetails,
                        selectedNode = selectedNode,
                        highlighted = highlighted,
                        viewport = viewport,
                        fitToPhone = fitToPhone,
                        zoomPercent = zoomPercent,
                        resolution = resolution,
                        tab = toolTab,
                        levels = levels,
                        height = panelHeight,
                        availableHeight = available,
                        onTab = { toolTab = it },
                        onLevels = { levels = it },
                        onResize = { delta -> requestedHeight = PreviewToolsPolicy.panelHeight(
                            PreviewToolsPolicy.panelHeight(requestedHeight, available) + delta, available) },
                        onHeight = { requestedHeight = PreviewToolsPolicy.panelHeight(it, available) },
                        onClear = {
                            if (toolTab == PreviewToolTab.CONSOLE) viewModel.clearConsole()
                            else viewModel.clearNetwork()
                        },
                        onClose = { toolsVisible = false },
                        onCommand = runCommand,
                        onCopyText = copyText,
                        onSelectNode = selectNode,
                        onToggleHighlight = toggleHighlight,
                        onCopyHtml = copyHtml,
                        onRefreshElements = refreshElements,
                        onRefreshResources = { webView?.captureResources() },
                        onZoom = { zoomDialog = true },
                        onResolution = { resolutionDialog = true },
                        onFitToPhone = { fitToPhone = it },
                        onClearCache = clearCache,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    if (zoomDialog) PreviewZoomDialog(
        onDismiss = { zoomDialog = false },
        onZoom = {
            webView?.applyZoom(it)
            zoomPercent = it
        }
    )
    if (resolutionDialog) PreviewResolutionDialog(
        selected = resolution, onDismiss = { resolutionDialog = false },
        onResolution = { resolution = it }
    )

    // Initial load once the WebView instance and the target URL are known.
    LaunchedEffect(webView, htmlFile, liveUrl, serverPort) {
        val wv = webView ?: return@LaunchedEffect
        // A page that sizes itself from the viewport (the snake sample caps its
        // board by `100vh`) must not be loaded into a box that has no size yet:
        // the tools panel can hold every pixel of the preview area, and a page
        // loaded at 0×0 lays itself out against a viewport that does not exist.
        // Bounded, so a preview that is never measured still loads.
        withTimeoutOrNull(1_000) {
            snapshotFlow { viewportSize }.first { it.width > 0 && it.height > 0 }
        }
        if (liveUrl != null) {
            // Phase 14: live server mode — the URL comes from the runner's
            // detected bind line; load it directly, no static server needed.
            viewModel.clearError()
            currentUrl = liveUrl
            wv.loadUrl(liveUrl)
            return@LaunchedEffect
        }
        val file = htmlFile
        when {
            file == null -> viewModel.reportError("Cannot resolve file: ${fileName ?: ""}")
            !file.exists() || !file.isFile -> viewModel.reportError("File not found: ${file.name}")
            !WebFileSupport.isPreviewable(file.name) ->
                viewModel.reportError("Preview supports HTML and Markdown files")
            else -> {
                viewModel.clearError()
                if (WebFileSupport.isMarkdown(file.name)) {
                    // Phase 68.1 (completed part) — Run ▶ on Markdown renders
                    // Markdown; the raw file is the source, not the preview.
                    // Base URL stays the file's folder so relative images
                    // resolve. No static-server wait: the page is self-owned.
                    currentUrl = "file://" + file.absolutePath
                    loadMarkdownInto(wv, file, previewDark)
                } else {
                    val port = serverPort
                    // The host owns the socket now, so the port can arrive one
                    // frame after the file does. Wait for it (a `file://` load
                    // first would flash, then reload, and lose the fetch/module
                    // behaviour the server exists to provide).
                    if (servedRoot != null && port == null && staticError == null) return@LaunchedEffect
                    val viaServer = if (port != null) "http://127.0.0.1:$port$pagePath" else null
                    currentUrl = viaServer ?: ("file://" + file.absolutePath)
                    wv.loadUrl(currentUrl.orEmpty())
                }
            }
        }
    }

    // Phase 14 — live server mode: server templates read index.html per
    // request, so watching the project's index.html makes Save → auto-reload
    // work exactly like the static preview (Reload always works too).
    val liveWatchFile = remember(projectName, liveUrl) {
        if (liveUrl != null && projectName != null) {
            runCatching {
                ProjectManager(context).project(projectName)?.root
                    ?.let { File(it, "index.html") }?.takeIf { it.isFile }
            }.getOrNull()
        } else {
            null
        }
    }
    LaunchedEffect(htmlFile, liveWatchFile, liveUrl) {
        viewModel.watch(if (liveUrl == null) htmlFile else liveWatchFile)
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.watch(null) }
    }

    // Phase 72.1 — opening the panel asks the page what it is made of: the
    // Resource Timing entries behind Network's Type/Size/Time and the
    // Resources tab, and the DOM tree when Elements is the tab on screen.
    LaunchedEffect(toolsVisible, toolTab, reloadTick, webView) {
        if (!toolsVisible) return@LaunchedEffect
        webView?.captureResources()
        if (toolTab == PreviewToolTab.ELEMENTS) refreshElements()
    }

    // Phase 72.1 — the Settings switch reaches the next load, never the current
    // one: a page that already rendered must not be re-laid out under the user.
    LaunchedEffect(webView, fitToPhone) { webView?.fitToPhone = fitToPhone }

    // Reload the WebView whenever the file changed on disk or Refresh was tapped.
    LaunchedEffect(reloadTick) {
        if (reloadTick > 0) {
            val wv = webView ?: return@LaunchedEffect
            val file = htmlFile
            if (file != null && file.isFile && WebFileSupport.isMarkdown(file.name)) {
                // Phase 68.1 (completed part) — a raw reload would show the
                // Markdown source again; re-render the page instead.
                loadMarkdownInto(wv, file, previewDark)
            } else {
                wv.reload()
            }
        }
    }
}

/**
 * Phase 68.1 (completed part) — load a RENDERED Markdown page into the
 * WebView: pure [MarkdownPreview] converts the file, [MarkdownPreview.page]
 * wraps it in the themed shell, and the base URL is the file's folder so
 * relative image paths resolve. No JavaScript in the page, ever.
 */
private fun loadMarkdownInto(wv: PreviewWebView, file: File, dark: Boolean) {
    val body = runCatching { file.readText() }.getOrDefault("")
    wv.loadDataWithBaseURL(
        "file://" + (file.parentFile?.absolutePath ?: "") + "/",
        MarkdownPreview.page(MarkdownPreview.toHtml(body), dark, file.name),
        "text/html",
        "UTF-8",
        null
    )
}

private fun resolveServedRoot(context: Context, projectName: String?, htmlFile: File?): File? {
    if (htmlFile == null) return null
    return if (projectName != null) {
        ProjectManager(context).project(projectName)?.root
    } else {
        htmlFile.parentFile?.takeIf { it.isDirectory }
    }
}

private fun resolveHtmlFile(context: Context, projectName: String?, fileName: String?): File? {
    if (fileName.isNullOrBlank()) return null
    if (projectName != null) {
        val project = ProjectManager(context).project(projectName) ?: return null
        val path = ProjectPathUtils.sanitizeRelativePath(fileName) ?: return null
        return ProjectPathUtils.resolveInside(project.root, path)
    }
    val safe = FileNameUtils.sanitizeFileName(fileName) ?: return null
    val dir = runCatching { FileManager(context).getProjectDir() }.getOrNull() ?: return null
    return File(dir, safe)
}
