package com.codeci.ide.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMosaic
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Editor : Screen("editor?fileName={fileName}", "Editor", Icons.Default.Code) {
        fun createRoute(fileName: String? = null): String {
            return if (fileName != null) "editor?fileName=$fileName" else "editor"
        }
    }
    object Preview : Screen("preview?fileName={fileName}", "Preview", Icons.Default.Visibility) {
        fun createRoute(fileName: String): String = "preview?fileName=$fileName"
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
    object FileManager : Screen("file_manager", "Files", Icons.Default.Folder)
    object Templates : Screen("templates", "Templates", Icons.Default.AutoAwesomeMosaic)
    object Modules : Screen("modules", "Modules", Icons.Default.Download)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Logs : Screen("logs", "Logs", Icons.Default.Settings)
}
