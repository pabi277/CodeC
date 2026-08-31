package com.codeci.ide.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Code
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Editor : Screen(
        "editor?projectName={projectName}&fileName={fileName}",
        "Editor",
        Icons.Default.Code
    ) {
        fun createRoute(fileName: String? = null, projectName: String? = null): String {
            val args = buildList {
                projectName?.takeIf { it.isNotBlank() }?.let { add("projectName=${Uri.encode(it)}") }
                fileName?.takeIf { it.isNotBlank() }?.let { add("fileName=${Uri.encode(it)}") }
            }
            return if (args.isEmpty()) "editor" else "editor?${args.joinToString("&")}"
        }
    }
    object Preview : Screen(
        "preview?projectName={projectName}&fileName={fileName}&url={url}",
        "Preview",
        Icons.Default.Visibility
    ) {
        /**
         * Phase 14 — [url] loads a live server URL directly (server projects);
         * otherwise [fileName] resolves a project file for the static preview.
         */
        fun createRoute(fileName: String? = null, projectName: String? = null, url: String? = null): String {
            val args = buildList {
                projectName?.takeIf { it.isNotBlank() }?.let { add("projectName=${Uri.encode(it)}") }
                fileName?.takeIf { it.isNotBlank() }?.let { add("fileName=${Uri.encode(it)}") }
                url?.takeIf { it.isNotBlank() }?.let { add("url=${Uri.encode(it)}") }
            }
            return if (args.isEmpty()) "preview" else "preview?${args.joinToString("&")}"
        }
    }
    object Terminal : Screen("terminal?cmd={cmd}&nonce={nonce}", "Term", Icons.Default.Terminal) {
        fun createRoute(cmd: String? = null): String {
            val nonce = System.currentTimeMillis().toString()
            return if (cmd.isNullOrEmpty()) {
                "terminal?nonce=$nonce"
            } else {
                "terminal?cmd=${Uri.encode(cmd)}&nonce=$nonce"
            }
        }
    }
    // Phase 15 — the Files screen became the Projects Hub (Spck-style); the
    // route keeps its historical name so deep links and saved state survive.
    object FileManager : Screen("file_manager", "Projects", Icons.Default.Folder)
    object Templates : Screen("templates", "Templates", Icons.Default.AutoAwesomeMosaic)
    object Modules : Screen("modules", "Packages", Icons.Default.Download)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Logs : Screen("logs", "Logs", Icons.Default.Settings)
}
