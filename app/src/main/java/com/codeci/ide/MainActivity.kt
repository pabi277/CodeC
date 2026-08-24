package com.codeci.ide

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeci.ide.ui.navigation.Screen
import com.codeci.ide.ui.screens.EditorScreen
import com.codeci.ide.ui.screens.FileManagerScreen
import com.codeci.ide.ui.screens.HomeScreen
import com.codeci.ide.ui.screens.LogsScreen
import com.codeci.ide.ui.screens.ModulesScreen
import com.codeci.ide.ui.screens.SettingsScreen
import com.codeci.ide.ui.screens.TemplatesScreen
import com.codeci.ide.ui.screens.TerminalScreen
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.stats.StatsManager
import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.theme.AppThemeMode
import com.codeci.ide.ui.theme.MyApplicationTheme
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.utils.AppLogger
import com.codeci.ide.ui.utils.FileManager
import com.codeci.ide.ui.utils.FileNameUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var storagePermissionLauncher: ActivityResultLauncher<Array<String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val anyGranted = permissions.values.any { it }
            if (anyGranted) {
                val home = ShellEnvironment.homeDir(filesDir)
                ShellEnvironment.setupStorageDirectory(home)
                Toast.makeText(this, getString(R.string.storage_setup_complete), Toast.LENGTH_SHORT).show()
            }
        }

        handleStoragePermissionIntent(intent)

        setContent {
            val context = LocalContext.current
            val themeManager = remember { ThemeManager(context) }
            val settingsManager = remember { SettingsManager(context) }
            val appTheme by themeManager.appThemeFlow.collectAsState(initial = AppThemeMode.SYSTEM)
            val accentColor by settingsManager.accentColorFlow.collectAsState(initial = "#FF6200EE")

            val isDarkTheme = when (appTheme) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme, accentHex = accentColor) {
                MainApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStoragePermissionIntent(intent)
    }

    fun requestStoragePermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        storagePermissionLauncher?.launch(permissions)
    }

    private fun handleStoragePermissionIntent(intent: Intent?) {
        if (intent?.action == ACTION_REQUEST_STORAGE_PERMISSION) {
            requestStoragePermissions()
        }
    }

    companion object {
        const val ACTION_REQUEST_STORAGE_PERMISSION = "com.codeci.ide.action.REQUEST_STORAGE_PERMISSION"
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Home,
        Screen.Editor,
        Screen.Terminal,
        Screen.Modules,
        Screen.Settings
    )

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            AppLogger.i("Navigation", "Navigated to ${destination.route}")
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route?.startsWith(screen.route.substringBefore("?")) == true
                    } == true
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = selected,
                        onClick = {
                            navController.navigate(
                                when (screen) {
                                    is Screen.Editor -> Screen.Editor.createRoute(null)
                                    is Screen.Terminal -> Screen.Terminal.createRoute(null)
                                    else -> screen.route
                                }
                            ) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToEditor = { fileName ->
                        navController.navigate(Screen.Editor.createRoute(fileName)) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToFileManager = {
                        navController.navigate(Screen.FileManager.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTemplates = {
                        navController.navigate(Screen.Templates.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToModules = {
                        navController.navigate(Screen.Modules.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTerminal = {
                        navController.navigate(Screen.Terminal.createRoute(null)) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = Screen.Editor.route,
                arguments = listOf(navArgument("fileName") { nullable = true })
            ) { backStackEntry ->
                val fileName = backStackEntry.arguments?.getString("fileName")
                EditorScreen(
                    fileName = fileName,
                    onNavigateBack = { navController.popBackStack() },
                    onFileRenamed = { newName ->
                        navController.navigate(Screen.Editor.createRoute(newName)) {
                            popUpTo(Screen.Editor.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenInTerminal = { cmd ->
                        navController.navigate(Screen.Terminal.createRoute(cmd)) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = Screen.Terminal.route,
                arguments = listOf(
                    navArgument("cmd") { nullable = true },
                    navArgument("nonce") { nullable = true }
                )
            ) { backStackEntry ->
                val cmd = backStackEntry.arguments?.getString("cmd")
                    ?.takeIf { it.isNotBlank() && it != "{cmd}" }
                TerminalScreen(
                    initialCommand = cmd,
                    commandNonce = backStackEntry.arguments?.getString("nonce")
                )
            }
            composable(Screen.FileManager.route) {
                FileManagerScreen(
                    onFileSelected = { selectedFile ->
                        navController.navigate(Screen.Editor.createRoute(selectedFile))
                    }
                )
            }
            composable(Screen.Templates.route) {
                val context = LocalContext.current
                TemplatesScreen(
                    onUseTemplate = { fileName, code ->
                        val safe = FileNameUtils.sanitizeFileName(fileName)
                        if (safe != null) {
                            val fm = FileManager(context)
                            if (fm.saveFile(safe, code)) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    StatsManager(context).incrementFilesCreated()
                                }
                                navController.navigate(Screen.Editor.createRoute(safe)) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                )
            }
            composable(Screen.Modules.route) {
                ModulesScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToLogs = {
                        navController.navigate(Screen.Logs.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Logs.route) {
                LogsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
