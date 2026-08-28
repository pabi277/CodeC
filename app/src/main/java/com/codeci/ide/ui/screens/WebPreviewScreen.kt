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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
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
    viewModel: WebPreviewViewModel = viewModel()
) {
    val context = LocalContext.current
    val htmlFile = remember(projectName, fileName) { resolveHtmlFile(context, projectName, fileName) }
    val console by viewModel.console.collectAsState()
    val reloadTick by viewModel.reloadTick.collectAsState()
    val error by viewModel.error.collectAsState()

    var webView by remember { mutableStateOf<WebView?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(htmlFile?.name ?: (fileName ?: "Preview")) },
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

    // Initial load once the WebView instance and the resolved file are known.
    LaunchedEffect(webView, htmlFile) {
        val wv = webView ?: return@LaunchedEffect
        val file = htmlFile
        when {
            file == null -> viewModel.reportError("Cannot resolve file: ${fileName ?: ""}")
            !file.exists() || !file.isFile -> viewModel.reportError("File not found: ${file.name}")
            !WebFileSupport.isHtml(file.name) ->
                viewModel.reportError("Preview supports HTML files (.html / .htm)")
            else -> {
                viewModel.clearError()
                wv.loadUrl("file://" + file.absolutePath)
            }
        }
    }

    // Start live-reload watching for the resolved file.
    LaunchedEffect(htmlFile) {
        htmlFile?.let { viewModel.watch(it) }
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
