package com.codeci.ide.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.R
import com.codeci.ide.ui.services.WebPreviewServer
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.utils.FileManager
import com.codeci.ide.ui.utils.FileNameUtils
import com.codeci.ide.ui.utils.WebFileSupport
import com.codeci.ide.ui.viewmodels.WebPreviewViewModel
import java.io.File

/**
 * Phase 5.2: preview an HTML file (and its sibling CSS/JS/images) in an
 * in-app WebView. The page is loaded from the app-private project directory
 * via `file://` so relative references resolve; JS console output is shown in
 * a bottom strip, and the page auto-reloads when the file changes on disk.
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

    var webView by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf<String?>(liveUrl) }

    // Phase 9.1: serve the whole folder over a loopback HTTP server so
    // relative CSS/JS, fetch("data.json") and ES modules work like under a
    // real dev server (`file://` blocks all of those). If binding fails the
    // preview degrades to the old file:// load instead of erroring out.
    // Phase 14: live server URLs skip the static server entirely.
    val servedRoot = remember(projectName, htmlFile, liveUrl) {
        if (isLive) null else resolveServedRoot(context, projectName, htmlFile)
    }
    val server = remember(servedRoot) { servedRoot?.let { WebPreviewServer.start(it) } }
    DisposableEffect(server) {
        onDispose { server?.stop() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(htmlFile?.name ?: (fileName ?: if (isLive) "Live server" else "Preview"))
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
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
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = error.orEmpty(),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { ctx -> createWebView(ctx, viewModel) { webView = it } }
            )

            if (console.isNotEmpty()) {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color(0xFF111318))
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    console.takeLast(60).forEach { line ->
                        Text(
                            text = line,
                            color = Color(0xFFA6E22E),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    // Initial load once the WebView instance and the target URL are known.
    LaunchedEffect(webView, htmlFile, liveUrl) {
        val wv = webView ?: return@LaunchedEffect
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
            !WebFileSupport.isHtml(file.name) ->
                viewModel.reportError("Preview supports HTML files (.html / .htm)")
            else -> {
                viewModel.clearError()
                val root = servedRoot
                val viaServer = if (server != null && root != null) {
                    ProjectPathUtils.relativePath(root, file)?.let { rel ->
                        "http://127.0.0.1:${server.port}/${WebPreviewServer.urlPathFor(rel)}"
                    }
                } else null
                currentUrl = viaServer ?: ("file://" + file.absolutePath)
                wv.loadUrl(currentUrl.orEmpty())
            }
        }
    }

    // Start live-reload watching for the resolved file (static mode only).
    LaunchedEffect(htmlFile, liveUrl) {
        if (liveUrl == null) htmlFile?.let { viewModel.watch(it) }
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
    LaunchedEffect(liveWatchFile, liveUrl) {
        if (liveWatchFile != null) viewModel.watch(liveWatchFile)
    }

    // Reload the WebView whenever the file changed on disk or Refresh was tapped.
    LaunchedEffect(reloadTick) {
        if (reloadTick > 0) webView?.reload()
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    viewModel: WebPreviewViewModel,
    onCreated: (WebView) -> Unit
): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Loading the user's own project files from file:// requires sibling
        // file access; universal (cross-origin) file access stays off.
        settings.allowFileAccess = true
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = false
        webViewClient = WebViewClient()
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                viewModel.addConsole(
                    levelLabel(consoleMessage.messageLevel()),
                    consoleMessage.message(),
                    consoleMessage.lineNumber()
                )
                return true
            }
        }
        onCreated(this)
    }

private fun levelLabel(level: ConsoleMessage.MessageLevel): String = when (level) {
    ConsoleMessage.MessageLevel.ERROR -> "error"
    ConsoleMessage.MessageLevel.WARNING -> "warn"
    ConsoleMessage.MessageLevel.TIP -> "info"
    ConsoleMessage.MessageLevel.DEBUG -> "debug"
    ConsoleMessage.MessageLevel.LOG -> "log"
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
