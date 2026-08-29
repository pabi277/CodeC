package com.codeci.ide

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.codeci.ide.ui.navigation.Screen
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.screens.EditorScreen
import com.codeci.ide.ui.screens.FileManagerScreen
import com.codeci.ide.ui.screens.HomeScreen
import com.codeci.ide.ui.screens.LogsScreen
import com.codeci.ide.ui.screens.ModulesScreen
import com.codeci.ide.ui.screens.SettingsScreen
import com.codeci.ide.ui.screens.TemplatesScreen
import com.codeci.ide.ui.screens.TerminalScreen
import com.codeci.ide.ui.screens.WebPreviewScreen
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.stats.StatsManager
import com.codeci.ide.ui.terminal.CodecApiBridge
import com.codeci.ide.ui.terminal.CodecApiProtocol
import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.theme.AppThemeMode
import com.codeci.ide.ui.theme.MyApplicationTheme
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.utils.AppLogger
import com.codeci.ide.ui.utils.FileNameUtils
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var storagePermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null

    /** CodeCApi notify request parked while the Android 13+ dialog is up. */
    private var pendingNotificationRequest: CodecApiProtocol.Request? = null
    private var pendingNotificationApiDir: File? = null

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

        // CodeC targets SDK 28 on purpose (W^X exec-of-app-data), so on
        // Android 13+ POST_NOTIFICATIONS must be requested at runtime; the
        // bridge parks the CodeCApi request and emits it here.
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            completeNotificationPermission(granted)
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

    override fun onResume() {
        super.onResume()
        if (ShellEnvironment.hasStoragePermission(this)) {
            val home = ShellEnvironment.homeDir(filesDir)
            ShellEnvironment.setupStorageDirectory(home)
        }
        recoverParkedNotificationPermission()
    }

    /**
     * Android 13+ can answer `POST_NOTIFICATIONS` through the system-owned
     * dialog it shows on first channel creation for targetSdk ≤ 32 apps —
     * in that path no `ActivityResult` reaches the launcher, so the parked
     * CodeCApi request would never be completed. Re-check after the dialog
     * is gone (onResume) and finish the request with the actual state. A
     * short delay avoids racing a launcher dialog that is still opening; if
     * the launcher already answered, the request is no longer parked.
     */
    private fun recoverParkedNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (pendingNotificationRequest == null) return
        lifecycleScope.launch {
            delay(400)
            if (pendingNotificationRequest != null) {
                completeNotificationPermission(
                    NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStoragePermissionIntent(intent)
    }

    fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(fallback)
                    } catch (e: Exception) {
                        AppLogger.e("MainActivity", "Cannot open all files access settings", e)
                    }
                }
                return
            }
        }
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

    /**
     * Shows the Android 13+ POST_NOTIFICATIONS runtime dialog for a parked
     * CodeCApi `notify.send` request. On devices below 13 the bridge never
     * emits a permission request (the permission is not runtime on older
     * API levels).
     */
    fun requestNotificationPermission(request: CodecApiProtocol.Request) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        pendingNotificationRequest = request
        pendingNotificationApiDir =
            ShellEnvironment.codecApiDir(ShellEnvironment.prefixDir(filesDir))
        notificationPermissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun completeNotificationPermission(granted: Boolean) {
        val request = pendingNotificationRequest ?: return
        val apiDir = pendingNotificationApiDir
        pendingNotificationRequest = null
        pendingNotificationApiDir = null
        if (apiDir == null) return
        // Tell the terminal (still waiting on the response file) the real
        // outcome; the CLI prints the result and exits.
        lifecycleScope.launch {
            CodecApiBridge.resumeAfterPermission(this@MainActivity, request, apiDir, granted)
        }
    }

    companion object {
        const val ACTION_REQUEST_STORAGE_PERMISSION = "com.codeci.ide.action.REQUEST_STORAGE_PERMISSION"
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val terminalViewModel: TerminalViewModel = viewModel(viewModelStoreOwner = activity)
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
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
            if (!isImeVisible) {
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
                arguments = listOf(
                    navArgument("projectName") { nullable = true },
                    navArgument("fileName") { nullable = true }
                )
            ) { backStackEntry ->
                val projectName = backStackEntry.arguments?.getString("projectName")
                val fileName = backStackEntry.arguments?.getString("fileName")
                EditorScreen(
                    projectName = projectName,
                    fileName = fileName,
                    onNavigateBack = { navController.popBackStack() },
                    onFileRenamed = { newName ->
                        navController.navigate(Screen.Editor.createRoute(newName, projectName)) {
                            popUpTo(Screen.Editor.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onProjectSelected = { project -> terminalViewModel.setProjectCwd(project.root) },
                    onOpenInTerminal = { cmd ->
                        navController.navigate(Screen.Terminal.createRoute(cmd)) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenPreview = { name ->
                        navController.navigate(Screen.Preview.createRoute(name, projectName)) {
                            launchSingleTop = true
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
                    },
                    onProjectSelected = { project -> terminalViewModel.setProjectCwd(project.root) },
                    onProjectFileSelected = { projectName, path ->
                        navController.navigate(Screen.Editor.createRoute(path, projectName)) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onProjectPreviewFile = { projectName, path ->
                        navController.navigate(Screen.Preview.createRoute(path, projectName)) {
                            launchSingleTop = true
                        }
                    },
                    onPreviewFile = { name ->
                        navController.navigate(Screen.Preview.createRoute(name)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.Preview.route,
                arguments = listOf(
                    navArgument("projectName") { nullable = true },
                    navArgument("fileName") { nullable = true }
                )
            ) { backStackEntry ->
                val previewProjectName = backStackEntry.arguments?.getString("projectName")
                val previewFileName = backStackEntry.arguments?.getString("fileName")
                WebPreviewScreen(
                    projectName = previewProjectName,
                    fileName = previewFileName,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Templates.route) {
                val context = LocalContext.current
                TemplatesScreen(
                    onUseTemplate = { fileName, code ->
                        val safe = FileNameUtils.sanitizeFileName(fileName)
                        if (safe != null) {
                            val manager = ProjectManager(context)
                            val project = manager.project("default")
                                ?: manager.createProject("default").getOrNull()
                            val target = project?.let { ProjectPathUtils.resolveInside(it.root, safe) }
                            if (target != null) {
                                runCatching {
                                    target.parentFile?.mkdirs()
                                    target.writeText(code)
                                }.onSuccess {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        StatsManager(context).incrementFilesCreated()
                                    }
                                    navController.navigate(Screen.Editor.createRoute(safe, project.name)) {
                                        popUpTo(Screen.Home.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    }
                )
            }
            composable(Screen.Modules.route) {
                ModulesScreen(
                    onNavigateToTerminal = {
                        navController.navigate(Screen.Terminal.createRoute(null)) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
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
