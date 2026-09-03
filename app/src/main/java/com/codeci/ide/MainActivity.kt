package com.codeci.ide

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.IntentCompat
import com.codeci.ide.ui.navigation.Screen
import com.codeci.ide.ui.projects.EditorLaunchState
import com.codeci.ide.ui.projects.IncomingImportBridge
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.projects.ProjectPathUtils
import com.codeci.ide.ui.screens.EditorScreen
import com.codeci.ide.ui.screens.FileManagerScreen
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
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var storagePermissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var notificationPermissionLauncher: ActivityResultLauncher<String>? = null
    private var cameraPermissionLauncher: ActivityResultLauncher<String>? = null
    private var cameraCaptureLauncher: ActivityResultLauncher<Uri>? = null

    /** CodeCApi notify request parked while the Android 13+ dialog is up. */
    private var pendingNotificationRequest: CodecApiProtocol.Request? = null
    private var pendingNotificationApiDir: File? = null

    /** CodeCApi camera.capture request parked while permission + photo run. */
    private var pendingCameraRequest: CodecApiProtocol.Request? = null
    private var pendingCameraApiDir: File? = null
    private var pendingCameraTarget: File? = null

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

        // Phase 18 — camera.capture (runtime CAMERA). The bridge parks the
        // CodeCApi request; the permission launcher answers the dialog, and
        // the TakePicture contract drives the actual photo (via FileProvider).
        cameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            completeCameraPermission(granted)
        }
        cameraCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            completeCameraCapture(success)
        }

        handleStoragePermissionIntent(intent)
        handleIncomingIntent(intent)

        setContent {
            val context = LocalContext.current
            val themeManager = remember { ThemeManager(context) }
            val settingsManager = remember { SettingsManager(context) }
            val appTheme by themeManager.appThemeFlow.collectAsState(initial = AppThemeMode.SYSTEM)
            val accentColor by settingsManager.accentColorFlow.collectAsState(initial = "#FF6200EE")

            val isDarkTheme = ThemeManager.effectiveDark(appTheme, isSystemInDarkTheme())

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
        handleIncomingIntent(intent)
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

    /** Phase 24.7 — "Open with CodeC": import a shared file/ZIP and open it. */
    private fun handleIncomingIntent(intent: Intent?) {
        val action = intent?.action ?: return
        val uri: Uri? = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java
            )
            else -> null
        } ?: return
        val mime = intent.type ?: contentResolver.getType(uri) ?: uri.toString()
        val isZip = mime.equals("application/zip", ignoreCase = true) ||
            mime.contains("zip", ignoreCase = true) ||
            uri.toString().substringBefore('?').endsWith(".zip", ignoreCase = true)
        val imported = if (isZip) IncomingImportBridge.importZip(this, uri)
        else IncomingImportBridge.importFile(this, uri, mime)
        if (imported != null) {
            IncomingImportBridge.offer(imported)
            Toast.makeText(this, "Imported ${imported.fileName}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Could not import the shared file", Toast.LENGTH_SHORT).show()
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

    /**
     * Phase 18: drives the parked `camera.capture` request. When CAMERA is
     * already held, the bridge already wrote the interim `CAPTURING:` marker
     * and this starts the photo capture directly; otherwise the runtime
     * dialog is shown first.
     */
    fun requestCameraPermission(request: CodecApiProtocol.Request) {
        pendingCameraRequest = request
        pendingCameraApiDir =
            ShellEnvironment.codecApiDir(ShellEnvironment.prefixDir(filesDir))
        pendingCameraTarget = null
        if (cameraPermissionGranted()) {
            startCameraCapture()
        } else {
            cameraPermissionLauncher?.launch(Manifest.permission.CAMERA)
        }
    }

    private fun cameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun completeCameraPermission(granted: Boolean) {
        val request = pendingCameraRequest ?: return
        val apiDir = pendingCameraApiDir ?: return
        if (!granted) {
            // Denied: write the actionable error and un-park (the CLI exits).
            pendingCameraRequest = null
            pendingCameraApiDir = null
            lifecycleScope.launch(Dispatchers.IO) {
                CodecApiBridge.resumeAfterPermission(this@MainActivity, request, apiDir, granted = false)
            }
            return
        }
        // Granted: replace NEED_PERMISSION with the CAPTURING marker (the CLI
        // keeps polling), then start the photo capture on the main thread.
        lifecycleScope.launch(Dispatchers.IO) {
            CodecApiBridge.resumeAfterPermission(this@MainActivity, request, apiDir, granted = true)
            withContext(Dispatchers.Main) { startCameraCapture() }
        }
    }

    /**
     * Validates the requested output file name and launches the system
     * camera via the TakePicture contract. The photo lands in
     * `$PREFIX/tmp/codec-api/camera/<name>` (FileProvider `files-path`), so
     * the CLI can read it back from the same prefix; the response file only
     * receives `OK:<path>` / `ERR:` once the capture returns.
     */
    private fun startCameraCapture() {
        val request = pendingCameraRequest ?: return
        val apiDir = pendingCameraApiDir ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val name = CodecApiBridge.cameraTargetName(request, apiDir)
            withContext(Dispatchers.Main) {
                if (name == null) {
                    val req = pendingCameraRequest
                    val dir = pendingCameraApiDir
                    pendingCameraRequest = null
                    pendingCameraApiDir = null
                    if (req != null && dir != null) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            CodecApiBridge.completeCameraCapture(req, dir, success = false, output = null)
                        }
                    }
                    return@withContext
                }
                val cameraDir = File(apiDir, CodecApiBridge.CAMERA_DIR_NAME)
                cameraDir.mkdirs()
                val target = File(cameraDir, name)
                // A stale photo must never look like a fresh capture.
                target.delete()
                pendingCameraTarget = target
                val uri = FileProvider.getUriForFile(
                    this@MainActivity, "$packageName.fileprovider", target
                )
                cameraCaptureLauncher?.launch(uri)
            }
        }
    }

    private fun completeCameraCapture(success: Boolean) {
        val request = pendingCameraRequest ?: return
        val apiDir = pendingCameraApiDir
        val target = pendingCameraTarget
        pendingCameraRequest = null
        pendingCameraApiDir = null
        pendingCameraTarget = null
        if (apiDir == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            CodecApiBridge.completeCameraCapture(
                request, apiDir, success = success && target != null, output = if (success) target else null
            )
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
    // 2026-08-31 bar: five tabs with Terminal dead-center —
    // Projects · Editor · Terminal · Packages · Settings. The Home dashboard
    // is gone; the app opens straight into the editor where the user left
    // off (or the Projects hub on first launch).
    val screens = listOf(
        Screen.FileManager,
        Screen.Editor,
        Screen.Terminal,
        Screen.Modules,
        Screen.Settings
    )
    // "Open where I left off": the last project file wins as the start
    // destination; first launch (or a stale entry) lands on the hub.
    val launchState = remember { EditorLaunchState.load(activity) }
    val startDestination = remember(launchState) {
        launchState?.let { Screen.Editor.createRoute(it.fileName, it.projectName) }
            ?: Screen.FileManager.route
    }

    // Phase 24.7 — an "Open with CodeC" file/ZIP arrives outside navigation
    // (onNewIntent); the bridge carries it in and the editor opens the import.
    val incomingImport by IncomingImportBridge.state.collectAsState()
    LaunchedEffect(incomingImport) {
        incomingImport?.let { import ->
            IncomingImportBridge.clear()
            navController.navigate(
                Screen.Editor.createRoute(import.fileName, import.projectName)
            ) {
                launchSingleTop = true
            }
        }
    }

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
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                FlatBottomBar(
                    screens = screens,
                    currentDestination = currentDestination,
                    onNavigate = { screen ->
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
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
                    onOpenPreview = { previewProject, name ->
                        // The project comes from the editor's current context
                        // (or the drawer entry), never from the route argument:
                        // after an in-editor folder switch the route arg can
                        // point at a different project and the preview would
                        // report "File not found".
                        navController.navigate(Screen.Preview.createRoute(name, previewProject)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenPreviewUrl = { previewProject, url ->
                        navController.navigate(Screen.Preview.createRoute(projectName = previewProject, url = url)) {
                            launchSingleTop = true
                        }
                    },
                    // Phase 16 — the editor drawer's footer jumps to Settings.
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route) {
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
                val context = LocalContext.current
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
                    onRunProjectFile = { projectName, path ->
                        val projectRoot = com.codeci.ide.ui.projects.ProjectManager(context)
                            .project(projectName)?.root
                        val command = if (projectRoot != null) {
                            com.codeci.ide.ui.terminal.TerminalHandoff
                                .projectFileRunCommand(projectRoot, path)
                        } else {
                            "echo 'CodeC: project $projectName was removed'"
                        }
                        navController.navigate(Screen.Terminal.createRoute(command)) {
                            launchSingleTop = true
                        }
                    },
                    onPreviewFile = { name ->
                        navController.navigate(Screen.Preview.createRoute(name)) {
                            launchSingleTop = true
                        }
                    },
                    // Phase 15 — the clone dialog's token hint jumps to
                    // Settings → GitHub Account (the Phase 13 card).
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = Screen.Preview.route,
                arguments = listOf(
                    navArgument("projectName") { nullable = true },
                    navArgument("fileName") { nullable = true },
                    navArgument("url") { nullable = true }
                )
            ) { backStackEntry ->
                val previewProjectName = backStackEntry.arguments?.getString("projectName")
                val previewFileName = backStackEntry.arguments?.getString("fileName")
                val previewUrl = backStackEntry.arguments?.getString("url")
                WebPreviewScreen(
                    projectName = previewProjectName,
                    fileName = previewFileName,
                    customUrl = previewUrl,
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
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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

/**
 * 2026-08-31 bottom bar: five flat tabs (Projects · Editor · Terminal ·
 * Packages · Settings — Terminal dead-center) with icon-over-label, the
 * active tab in primary color, muted grey otherwise — no M3 selection pill.
 */
@Composable
private fun FlatBottomBar(
    screens: List<Screen>,
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Keep the bar above the system navigation bar.
                .navigationBarsPadding()
        ) {
            screens.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any {
                    it.route?.startsWith(screen.route.substringBefore("?")) == true
                } == true
                val activeColor = MaterialTheme.colorScheme.primary
                val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(screen) }
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        screen.icon,
                        contentDescription = screen.title,
                        modifier = Modifier.size(24.dp),
                        tint = if (selected) activeColor else idleColor
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = screen.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) activeColor else idleColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
