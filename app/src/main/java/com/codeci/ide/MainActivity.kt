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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.codeci.ide.ui.projects.WelcomeStarters
import com.codeci.ide.ui.screens.EditorScreen
import com.codeci.ide.ui.screens.FeedbackScreen
import com.codeci.ide.ui.screens.FileManagerScreen
import com.codeci.ide.ui.screens.LogsScreen
import com.codeci.ide.ui.screens.ModulesScreen
import com.codeci.ide.ui.screens.SettingsScreen
import com.codeci.ide.ui.screens.TemplatesScreen
import com.codeci.ide.ui.screens.TerminalScreen
import com.codeci.ide.ui.screens.WebPreviewScreen
import com.codeci.ide.ui.guide.CoachMarkPlan
import com.codeci.ide.ui.guide.GuideAnchor
import com.codeci.ide.ui.guide.GuideAnchors
import com.codeci.ide.ui.guide.GuideCoachMarks
import com.codeci.ide.ui.guide.GuideScreen
import com.codeci.ide.ui.screens.WelcomeScreen
import com.codeci.ide.ui.editor.EditorChromeState
import com.codeci.ide.ui.editor.NavBarPolicy
import com.codeci.ide.ui.editor.lsp.LspManager
import com.codeci.ide.ui.editor.lsp.StdioLspProviderFactory
import com.codeci.ide.ui.editor.lsp.SystemBinaryProbe
import com.codeci.ide.ui.editor.sora.ActiveLspManager
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.services.LiveRunStamps
import com.codeci.ide.ui.services.OpenInBrowser
import com.codeci.ide.ui.services.TempGc
import com.codeci.ide.ui.support.ExitFeedbackDialog
import com.codeci.ide.ui.support.ExitSurvey
import com.codeci.ide.ui.stats.StatsManager
import com.codeci.ide.ui.terminal.CodecApiBridge
import com.codeci.ide.ui.terminal.CodecApiProtocol
import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.theme.AppThemeMode
import com.codeci.ide.ui.theme.MyApplicationTheme
import com.codeci.ide.ui.theme.AccentPalette
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.utils.AppLogger
import com.codeci.ide.ui.utils.FileNameUtils
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

    /**
     * Phase 25.2 device-round instrumentation: the sandbox has no JVM/device,
     * so a field crash is invisible to the agent, and the owner's device is
     * NOT rooted (Android 11+ hides Android/data from file managers). Append
     * every uncaught exception to filesDir/crash-log.txt — read back and
     * shown IN-APP by `CrashReportOverlay` on the next launch — then let the
     * system handler run (dialog etc). Plain java.io — no CodeC internals.
     */
    private fun installCrashLog() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val file = java.io.File(filesDir, "crash-log.txt")
                file.appendText(
                    buildString {
                        append("\n==== ")
                        append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date()))
                        append("  thread=")
                        append(thread.name)
                        append(" ====\n")
                        // 2026-09-06 (Phase 29 device round): HEADER-FIRST,
                        // FRAME-CAPPED records. The diagnosis is the exception
                        // line + the FIRST frames (origin + app/sora code); the
                        // trailing Compose/ViewRootImpl/Looper frames are
                        // identical in every crash and previously pushed the
                        // header out of CrashReportOverlay's display window —
                        // three reports arrived tail-only and undiagnosable.
                        appendThrowable(this, throwable, frameCap = 80)
                        var cause = throwable.cause
                        var depth = 0
                        while (cause != null && depth < 5) {
                            append("Caused by: ")
                            appendThrowable(this, cause, frameCap = 8)
                            cause = cause.cause
                            depth++
                        }
                    }
                )
                // Bound the file: keep the newest records only.
                if (file.length() > 60_000) {
                    val text = file.readText()
                    val cut = text.indexOf("\n==== ", text.length - 45_000)
                    if (cut >= 0) file.writeText(text.substring(cut + 1))
                }
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Exception line first (the diagnosis), then the first [frameCap] frames. */
    private fun appendThrowable(sb: StringBuilder, t: Throwable, frameCap: Int) {
        sb.append(t.toString()).append('\n')
        val frames = t.stackTrace
        for (i in 0 until minOf(frames.size, frameCap)) {
            sb.append("\tat ").append(frames[i]).append('\n')
        }
        if (frames.size > frameCap) {
            sb.append("\t… ").append(frames.size - frameCap)
                .append(" more frames (Compose/ViewRootImpl tail omitted)\n")
        }
        for (suppressed in t.suppressed.take(3)) {
            sb.append("Suppressed: ").append(suppressed.toString()).append('\n')
            suppressed.stackTrace.take(4).forEach { sb.append("\tat ").append(it).append('\n') }
        }
    }

    /**
     * Phase 42.3 — the crash-loop ledger state for THIS launch, decided
     * synchronously as the first work of the process (before ANY init
     * whose crash should count as a startup crash). Two counters in a
     * SharedPreferences — no daemon, no service, no DataStore coroutine.
     */
    private var startupPlan: com.codeci.ide.ui.crash.StartupLedger.Plan =
        com.codeci.ide.ui.crash.StartupLedger.Plan.NORMAL
    private var startupLedger: com.codeci.ide.ui.crash.StartupLedger? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            val prefs = getSharedPreferences("codec-startup", MODE_PRIVATE)
            val store = object : com.codeci.ide.ui.crash.StartupLedger.Store {
                override fun readStarting(): Boolean = prefs.getBoolean("starting", false)
                override fun readCount(): Int = prefs.getInt("count", 0)
                override fun write(starting: Boolean, count: Int) {
                    // commit (not apply): the marker must be durable BEFORE
                    // any later code can crash this start, or the crash
                    // would never be counted.
                    prefs.edit().putBoolean("starting", starting).putInt("count", count).commit()
                }
            }
            val ledger = com.codeci.ide.ui.crash.StartupLedger(store)
            startupLedger = ledger
            startupPlan = ledger.noteLaunch()
        }
        installCrashLog()
        enableEdgeToEdge()
        // Phase 39.1 — bound CodeC/temp/runs on every cold start. Never
        // touches a stamp that is currently running (LiveRunStamps) and
        // never walks outside runs/. Failures are counted, never thrown.
        try {
            val gcThread = Thread {
                try {
                    val tempRoot = java.io.File(filesDir, "CodeC/temp")
                    TempGc.sweep(tempRoot, busy = LiveRunStamps.snapshot())
                } catch (_: Throwable) {
                    // GC must never crash the app.
                }
            }
            gcThread.isDaemon = true
            gcThread.name = "codec-temp-gc"
            gcThread.start()
        } catch (_: Throwable) {
            // ignore
        }

        // Phase 44.2 — repair a userland install that a process kill
        // interrupted, and sweep its orphans. `UserlandInstaller.swapPrefix`
        // needs two renames (`usr` → `usr.old-<ts>`, staging → `usr`); a kill
        // between them used to leave NO `usr` at all plus an orphan nobody ever
        // cleaned, and the next launch answered "offline — using built-in cc"
        // (which reads like success) while `pkg` was gone. Same daemon-thread
        // shape as the TempGc sweep above: never delays the first frame, never
        // throws, and the installer waits for it (SetupRecoveryGate) so the two
        // never write the same directories at once.
        try {
            val setupThread = Thread {
                try {
                    val report = com.codeci.ide.ui.terminal.SetupRecovery.recover(
                        filesDir = filesDir,
                        prefixDir = ShellEnvironment.prefixDir(filesDir),
                        ledger = com.codeci.ide.ui.terminal.SetupLedgerPrefs.ledger(this),
                        log = { msg -> AppLogger.i("SetupRecovery", msg) }
                    )
                    // One honest line when CodeC had to put a previous userland
                    // back; the live setup bar covers everything else.
                    if (report.restored != null) {
                        com.codeci.ide.ui.terminal.SetupNoticeBridge.post(report.message)
                    }
                } catch (_: Throwable) {
                    // A repair must never be the reason the app does not start.
                    com.codeci.ide.ui.terminal.SetupRecoveryGate.finished()
                }
            }
            setupThread.isDaemon = true
            setupThread.name = "codec-setup-recovery"
            setupThread.start()
        } catch (_: Throwable) {
            com.codeci.ide.ui.terminal.SetupRecoveryGate.finished()
        }


        // Phase 29.1 — preload the TextMate grammar sets (VS Code grammars)
        // on a background thread while the user is still navigating to the
        // editor, so the first file open finds them already parsed. Purely a
        // latency optimization: any language not yet warm loads itself on
        // first open (SoraEditorHost's language effect).
        lifecycleScope.launch(Dispatchers.Default) {
            com.codeci.ide.ui.editor.sora.TextMateSupport.warmUp(applicationContext)
            // Phase 30.1 — parse the vendored snippet packs (MIT
            // friendly-snippets) in the same background hop: a few ms per
            // language, and the first completion after opening a file is then
            // a cache hit. Purely a latency optimization — SnippetLibrary
            // loads any language on demand, and the engine falls back to its
            // own tables if an asset ever fails to read.
            com.codeci.ide.ui.editor.snippets.SnippetLibrary.warmUp(applicationContext)
        }

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
            // Phase 42.3 — safe mode: the persisted visual inputs are the
            // ones a startup crash could come from (a garbage accent string
            // reaching AccentPalette's parser), so while the session flag
            // is set they resolve to their DEFAULTS. Session-only: nothing
            // is written back or deleted (SafeMode.resolve is the pinned law).
            val safeModeActive = com.codeci.ide.ui.crash.SafeMode.active
            val appTheme by (
                if (safeModeActive) kotlinx.coroutines.flow.flowOf(AppThemeMode.SYSTEM)
                else themeManager.appThemeFlow
                ).collectAsState(initial = AppThemeMode.SYSTEM)
            val accentColor by (
                if (safeModeActive) kotlinx.coroutines.flow.flowOf(AccentPalette.DEFAULT_STORAGE_HEX)
                else settingsManager.accentColorFlow
                ).collectAsState(initial = AccentPalette.DEFAULT_STORAGE_HEX)

            val isDarkTheme = ThemeManager.effectiveDark(appTheme, isSystemInDarkTheme())

            MyApplicationTheme(darkTheme = isDarkTheme, accentHex = accentColor) {
                MainApp(onStartupFinished = {
                    // Phase 42.3 — the main screen was drawn: this start
                    // succeeded, so the loop counter and the marker clear.
                    startupLedger?.noteStartupFinished()
                })
                // Phase 25.2 device-round instrumentation: if the previous
                // run crashed, surface the report in-app (no root / file
                // manager needed) before anything else.
                // Phase 42.3 — when TWO consecutive starts died, the same
                // overlay gains the loop sentence, the safe-mode door, and
                // a one-tap hand-off to the feedback screen.
                com.codeci.ide.ui.crash.CrashReportOverlay(
                    loopDetected = startupPlan ==
                        com.codeci.ide.ui.crash.StartupLedger.Plan.LOOP_SUSPECTED,
                    onStartWithoutSettings = {
                        com.codeci.ide.ui.crash.SafeMode.activateForSession()
                        // The accepted reduced start IS the repair: count
                        // a crash AFTER it from zero like any other.
                        startupLedger?.noteStartupFinished()
                        recreate()
                    },
                    onSendReport = {
                        com.codeci.ide.ui.crash.CrashHandOffBridge.requestCrashReport()
                    }
                )
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
        // Phase 31.1 — install the LSP completion manager so
        // `CodeCLanguage.requireAutoComplete` can fan out to language
        // servers (clangd, pylsp, tsserver). The activity owns the
        // lifecycle (L2): on `onPause` we shut every active provider
        // down so a backgrounded app stops holding server processes.
        // Master-completion-OFF is read from DataStore on install so a
        // Settings change while the app is backgrounded is honoured the
        // next time the editor surfaces, and a flow collector keeps
        // the running manager in sync with the Settings switch (the
        // 31.1 README L3 — "completion master OFF = no LSP process").
        val settings = SettingsManager(this)
        // Phase 31.5 — wire the hand-rolled LSP stdio client
        // (LspStdioClient) via StdioLspProviderFactory. The factory
        // builds a per-language LspStdioClient on the first
        // completion request; the manager owns the lifecycle. The
        // `projectRoot` is the application private dir (no compile
        // flags on the first run — a real project path would come
        // from the editor's "current file" once the analyzer sends
        // it through LspRequestContext, see CodeCAnalyzer).
        val projects = File(filesDir, "CodeC/projects")
        val manager = LspManager(
            probe = SystemBinaryProbe(filesDir),
            providerFactory = StdioLspProviderFactory(
                projectRoot = if (projects.isDirectory) projects.absolutePath else filesDir.absolutePath,
                filesDir = filesDir,
            ),
        )
        ActiveLspManager.install(manager)
        lifecycleScope.launch {
            settings.completionMasterFlow.collect { master ->
                manager.setMasterEnabled(master)
            }
        }
    }

    override fun onPause() {
        // Phase 31.1 — L2: a backgrounded app does not need its LSP
        // providers; killing them now means the next onResume starts
        // fresh, and the 200 ms startup cost is paid at the right
        // moment (the user is going to the editor next, not the
        // background). The Settings collector is bound to this
        // activity's lifecycleScope, so it stops here too.
        ActiveLspManager.clear()
        super.onPause()
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
        val uri = when (action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java
            )
            else -> null
        } ?: return
        val resolvedUri: Uri = uri
        val mime = intent.type ?: contentResolver.getType(resolvedUri) ?: resolvedUri.toString()
        val isZip = mime.equals("application/zip", ignoreCase = true) ||
            mime.contains("zip", ignoreCase = true) ||
            resolvedUri.toString().substringBefore('?').endsWith(".zip", ignoreCase = true)
        val imported = if (isZip) IncomingImportBridge.importZip(this, resolvedUri)
        else IncomingImportBridge.importFile(this, resolvedUri, mime)
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
fun MainApp(onStartupFinished: () -> Unit = {}) {
    val navController = rememberNavController()
    val activity = requireNotNull(LocalActivity.current) as ComponentActivity
    val terminalViewModel: TerminalViewModel = viewModel(viewModelStoreOwner = activity)
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    // Phase 42.3 — "the main screen has been drawn": report the successful
    // start to the crash-loop ledger exactly once per process (first
    // composition of the shell). Spec: PART_42_3 §2.
    LaunchedEffect("codec-startup-finished") { onStartupFinished() }

    // Phase 42.3 — the crash overlay's [Send a report]: open Phase 41's
    // feedback screen with both attachments pre-ticked (crash=1).
    val crashReportRequested by com.codeci.ide.ui.crash.CrashHandOffBridge
        .reportCrashRequested.collectAsState()
    LaunchedEffect(crashReportRequested) {
        if (crashReportRequested) {
            com.codeci.ide.ui.crash.CrashHandOffBridge.clear()
            navController.navigate(Screen.Feedback.createRoute(reportCrash = true)) {
                launchSingleTop = true
            }
        }
    }
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
    // Phase 33.1 — first-run welcome (three starter tiles). The flag is read
    // ONCE at startup into local state, so a Settings reset ("show welcome
    // again") only affects the NEXT launch instead of yanking the user out of
    // Settings mid-session. Local state also makes a tile tap replace the
    // welcome immediately, without waiting for the DataStore round-trip.
    // null = still reading; false = show the welcome; true = normal shell.
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager(activity) }
    var firstLaunchComplete by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(settingsManager) {
        firstLaunchComplete = settingsManager.firstLaunchCompleteFlow.first()
    }
    // Phase 45.1 — the guide's flag is read ONCE at startup, for the same reason
    // the welcome's is: Settings → "Reset tips" must affect the NEXT launch, not
    // yank the user out of Settings mid-session. `guideRequested` is the three
    // "view the guide again" doors (Settings → Help & guide, Projects ⋮ → Guide,
    // the editor ☰ drawer's footer): it shows the SAME screen without changing
    // what the flag means. null = still reading; false = show the guide.
    var guideCompleted by remember { mutableStateOf<Boolean?>(null) }
    var guideRequested by remember { mutableStateOf(false) }
    // Phase 45.2 — the coach marks already seen (a CSV of step ids). The plan is
    // pure; this is only its persistence.
    var coachSeen by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(settingsManager) {
        guideCompleted = settingsManager.guideCompletedFlow.first()
        coachSeen = CoachMarkPlan.parseSeen(settingsManager.coachMarksSeenCsvFlow.first())
    }
    // Phase 44.1 — set once, when the first-run welcome hands over: this is
    // the launch where the one-time userland download should be ON SCREEN
    // while it happens (the owner's own solution to the invisible install).
    var setupDiverted by remember { mutableStateOf(false) }

    if (firstLaunchComplete == false) {
        WelcomeScreen(
            onStarterChosen = { starter ->
                scope.launch {
                    val project = withContext(Dispatchers.IO) {
                        WelcomeStarters.ensureProject(ProjectManager(activity), starter)
                    }
                    if (project != null) {
                        // Save the launch state BEFORE flipping the flag so the
                        // shell that replaces the welcome opens the starter file
                        // (and the next launch opens it too — 33.1 exit 2).
                        EditorLaunchState.save(activity, project.name, starter.entryFile)
                        // Persist for the NEXT launch, then flip the local state
                        // so the normal shell replaces the welcome right away.
                        settingsManager.setFirstLaunchComplete(true)
                        // Set BEFORE the flag that swaps the welcome for the
                        // shell: the shell's first composition decides the
                        // start destination from it.
                        setupDiverted = true
                        firstLaunchComplete = true
                    }
                }
            }
        )
        return
    }
    if (firstLaunchComplete == null) {
        // The flag is still reading: render nothing for the one frame so a
        // returning user never flashes the welcome and a new user never
        // flashes the hub.
        return
    }
    // Phase 45.1 — the SECOND first-launch gate: tiles → guide → shell. It is
    // decided before the setup divert and before the NavHost exists, so the
    // guide can never compete with the Phase 44 setup bar (PART_45_1's ordering
    // rule: the guide explains the download the terminal is about to show, and a
    // user who has not been told what the app is should not be dropped into a
    // shell watching a progress bar). Safe mode is the one exception — a reduced
    // start exists to do LESS at startup, and the flag stays false so a normal
    // launch still shows the guide exactly once.
    if (!com.codeci.ide.ui.crash.SafeMode.active &&
        (guideRequested || guideCompleted == false)
    ) {
        GuideScreen(
            onFinished = {
                // SKIP and START CODING are the same act: the guide is over and
                // it does not come back on its own (the no-nag law). Re-opening
                // it later writes a flag that is already true — no-op.
                guideRequested = false
                guideCompleted = true
                scope.launch { settingsManager.setGuideCompleted(true) }
            }
        )
        return
    }
    if (guideCompleted == null && !guideRequested) {
        // Still reading the guide flag: one frame of nothing, exactly like the
        // welcome above — a returning user never flashes the guide.
        return
    }
    // "Open where I left off": the last project file wins as the start
    // destination; a fresh install with no last file lands on the hub.
    // Phase 42.3 — safe mode NEVER reopens the saved session: a
    // half-written project file is exactly the kind of startup culprit the
    // guard exists for, so the reduced start lands on the hub with the
    // last file untouched on disk.
    val launchState = remember {
        if (com.codeci.ide.ui.crash.SafeMode.active) null else EditorLaunchState.load(activity)
    }
    // Phase 44.1 — the setup truth, read ONCE for the start destination and
    // then observed by the setup bar below. A fresh install (welcome just
    // handed over, no usable Linux tools yet, and this device does have a
    // bootstrap) starts on the Terminal tab so the download is visible while
    // it runs; every other launch keeps the pre-44 behaviour. Computed once on
    // purpose: a startDestination that changed later would rebuild the nav
    // graph and throw away the user's navigation state.
    val setupProgress by terminalViewModel.setupProgress.collectAsState()
    val setupFacts by terminalViewModel.setupFacts.collectAsState()
    val startDestination = remember(launchState) {
        launchState?.let { Screen.Editor.createRoute(it.fileName, it.projectName) }
            ?: Screen.FileManager.route
    }
    // Phase 44.1 (device round 1) — "it opens the terminal 1st", decided from
    // the DISK, not from the ViewModel's first (possibly stale) facts, and done
    // with the SAME navigate() the bottom tab bar uses. Two reasons for the
    // shape: a `startDestination` that has to resolve a route carrying
    // arguments is graph-construction risk we do not need to take, and the
    // owner's report was about an UPDATED install (no first-run welcome), where
    // a rule keyed on "is this the welcome hand-over" could not fire at all.
    val setupLaunchDivert = remember {
        val prefix = ShellEnvironment.prefixDir(activity.filesDir)
        val phase = runCatching {
            com.codeci.ide.ui.terminal.SetupLedgerPrefs.ledger(activity).read().phase
        }.getOrDefault(com.codeci.ide.ui.terminal.SetupPhase.IDLE)
        setupDiverted || com.codeci.ide.ui.terminal.SetupGatePolicy.startOnTerminal(
            usable = com.codeci.ide.ui.terminal.SetupGatePolicy.userlandUsable(prefix, phase),
            abiSupported = com.codeci.ide.ui.terminal.UserlandManifest.archName() != null
        )
    }
    LaunchedEffect(setupLaunchDivert) {
        if (!setupLaunchDivert) return@LaunchedEffect
        if (navController.currentDestination?.route.orEmpty().startsWith("terminal")) {
            return@LaunchedEffect
        }
        navController.navigate(Screen.Terminal.createRoute(null)) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = false
        }
    }
    // The welcome promised a starter file. Once the setup settles, open it — but
    // only while the user is still on the tab we diverted them to (nothing is
    // yanked out from under a tap), and never while the setup is settled-but-
    // unusable: in that state the terminal is the only way out, so sending the
    // user to the editor is how the bar becomes a wall (device round 1).
    // C works offline either way (TCC is in the APK), which is why a FAILED or
    // UNSUPPORTED setup still releases the user into the editor.
    LaunchedEffect(setupDiverted, setupProgress.stage, setupFacts.usable) {
        if (!setupDiverted) return@LaunchedEffect
        val target = launchState
        if (target == null) {
            setupDiverted = false
            return@LaunchedEffect
        }
        if (!setupProgress.settled) return@LaunchedEffect
        val stuck = !setupFacts.usable &&
            setupProgress.stage != com.codeci.ide.ui.terminal.SetupStage.FAILED &&
            setupProgress.stage != com.codeci.ide.ui.terminal.SetupStage.UNSUPPORTED
        if (stuck) return@LaunchedEffect
        val current = navController.currentDestination?.route.orEmpty()
        if (!current.startsWith("terminal")) return@LaunchedEffect
        setupDiverted = false
        navController.navigate(Screen.Editor.createRoute(target.fileName, target.projectName)) {
            launchSingleTop = true
        }
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

    // Phase 32.1 — the 5-tab bar hides while the EDITOR is the destination
    // and a keyboard is on screen (the system IME, or CodeC Keys). Other tabs
    // keep the pre-32 behaviour (the bar hides only while the soft keyboard
    // is up). A swipe-up on the hidden bar's handle reveals it again — sticky
    // until the user leaves the editor. The decision is pure (NavBarPolicy);
    // the editor reports its keyboard via EditorChromeState.
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val inEditor = currentDestination?.route
        ?.startsWith(Screen.Editor.route.substringBefore("?")) == true
    val editorKeysVisible by EditorChromeState.keysVisible.collectAsState()
    // Phase 45.2 (device round) — the two facts the tour cannot observe from
    // anchors: is a dialog open (a box would be cut underneath it), and is the
    // drawer open (only its own two beats may show then).
    val editorDialogOpen by EditorChromeState.dialogOpen.collectAsState()
    val editorDrawerOpen by EditorChromeState.drawerOpen.collectAsState()
    var navRevealed by remember { mutableStateOf(false) }
    LaunchedEffect(inEditor) {
        if (!inEditor) navRevealed = false
    }
    val hideNav = NavBarPolicy.hideNavBar(
        inEditor = inEditor,
        imeVisible = isImeVisible,
        keysVisible = editorKeysVisible,
        revealed = navRevealed,
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

    // Phase 41 follow-up (owner, after device round 1) — the exit survey:
    // a back press that would CLOSE the app (nothing left to pop) shows the
    // rate/experience/review dialog instead, and "tap again to exit" is the
    // dialog's own back handling. Off-able from the Feedback screen
    // (testing-phase default ON); when off, back closes directly.
    // (settingsManager is the one MainApp already holds for first-launch.)
    val exitPromptEnabled by settingsManager.feedbackExitPromptEnabledFlow.collectAsState(initial = true)
    var exitPromptVisible by remember { mutableStateOf(false) }

    if (exitPromptVisible) {
        ExitFeedbackDialog(
            onShareExperience = { rating ->
                exitPromptVisible = false
                navController.navigate(Screen.Feedback.createRoute(rating)) {
                    launchSingleTop = true
                }
            },
            onReview = {
                OpenInBrowser.openOrCopy(
                    context = activity,
                    url = ExitSurvey.REPO_URL,
                    clipboardLabel = "CodeC repository",
                    copyInstead = ExitSurvey.REPO_URL,
                    failureMessage = "No browser — the repo link is copied"
                )
            },
            onExit = { activity.finish() },
            onDismiss = { exitPromptVisible = false }
        )
    }

    // Registered BEFORE the Scaffold/NavHost so anything composed inside
    // them (NavHost's own pop handling, dialogs, sheets) wins while it can
    // consume the back press; this handler only decides what back does AT
    // THE ROOT: the exit survey, or a direct close when it is switched off.
    // Phase 42.3 — the exit survey is a BRIDGE/SNACKS surface outside the
    // safe-mode boundary: while safe mode is on, the last thing a user in a
    // reduced session needs is a "how was your experience" prompt whose
    // [NOT NOW] would have to write a preference; back exits directly.
    BackHandler(enabled = !exitPromptVisible) {
        if (!navController.popBackStack()) {
            when {
                com.codeci.ide.ui.crash.SafeMode.active -> activity.finish()
                exitPromptEnabled -> exitPromptVisible = true
                else -> activity.finish()
            }
        }
    }

    // Phase 45.2 — the coach marks' overlay sits ABOVE the scaffold, because
    // the "Show tabs" handle it spotlights lives in the scaffold's bottomBar, and
    // at the window origin, because anchors publish boundsInWindow().
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            when {
                // Phase 32.1 — the bar is visible (or was revealed by the
                // handle) exactly as before: five flat tabs.
                !hideNav -> FlatBottomBar(
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
                            // Phase 44 device round 1 (owner: *"terminal not
                            // opening editor opening"*): `restoreState = true`
                            // restores the WHOLE saved sub-stack for that
                            // destination, so a user who had opened a file after
                            // using the terminal came back to the EDITOR when
                            // they tapped Terminal. A tab tap means "show me
                            // this tab", so the Terminal tab never restores;
                            // every other tab keeps the pre-44 behaviour
                            // (Phase 49 is the systematic nav pass).
                            restoreState = screen !is Screen.Terminal
                        }
                        // A tab tap is a deliberate navigation: the reveal is
                        // over (leaving the editor also resets it below).
                        navRevealed = false
                    }
                )
                // Phase 32.1 — hidden because CodeC Keys is up (no IME in the
                // way): show the thin reveal handle instead of the bar.
                inEditor && !isImeVisible -> EditorNavRevealHandle(
                    onReveal = { navRevealed = true }
                )
                // Hidden because the soft keyboard is up (any tab): nothing,
                // the pre-32 behaviour.
                else -> Unit
            }
        }
    ) { innerPadding ->
        // Phase 42.3 — the safe-mode banner rides above the NavHost inside
        // the scaffold padding: dismissible, one slim line, and it can
        // never hide the export row behind a modal.
        var safeModeBannerVisible by remember {
            mutableStateOf(com.codeci.ide.ui.crash.SafeMode.active)
        }
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (safeModeBannerVisible) {
                com.codeci.ide.ui.crash.SafeModeBanner(
                    onDismiss = { safeModeBannerVisible = false }
                )
            }
            // Phase 44.1 — the setup bar: one slim non-modal line, visible
            // from EVERY tab, with the moving percentage and a VIEW action to
            // the terminal. Not dismissible while the setup is in flight
            // (dismissing it would recreate the invisible-download bug); the ✕
            // appears once it has settled, and always for the boot repair's
            // one-time note.
            val setupNote by com.codeci.ide.ui.terminal.SetupNoticeBridge.message.collectAsState()
            // Device round 1: the bar was a wall in the settled-but-unusable
            // state — no action button, and a ✕ that cleared a note which was
            // not there. Now: one tap anywhere goes to the setup, and the ✕
            // (only while settled, per SetupGatePolicy.barDismissAllowed) really
            // removes the bar until its TEXT changes, so a new install or a
            // repair brings it back.
            val setupBarText = setupNote
                ?: com.codeci.ide.ui.terminal.SetupGatePolicy.barText(setupProgress, setupFacts)
            var dismissedSetupBar by remember { mutableStateOf<String?>(null) }
            val goToSetup: () -> Unit = {
                navController.navigate(Screen.Terminal.createRoute(null)) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    // No restoreState: "VIEW SETUP" means the terminal, not
                    // whatever happened to be above it last time (device round 1).
                    restoreState = false
                }
            }
            val dismissSetupBar: () -> Unit = {
                if (setupNote != null) {
                    com.codeci.ide.ui.terminal.SetupNoticeBridge.clear()
                } else {
                    dismissedSetupBar = setupBarText
                }
            }
            val setupBarDismissible = setupNote != null ||
                com.codeci.ide.ui.terminal.SetupGatePolicy.barDismissAllowed(setupProgress)
            if (setupBarText != null && setupBarText != dismissedSetupBar) {
                com.codeci.ide.ui.components.SetupBar(
                    progress = setupProgress,
                    facts = setupFacts,
                    note = setupNote,
                    onViewSetup = goToSetup,
                    onDismiss = if (setupBarDismissible) dismissSetupBar else null
                )
            }
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.weight(1f)
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
                            // Phase 44 device round 1: this hand-off carries a
                            // command that only the terminal can run, so it must
                            // arrive there — never on a restored sub-stack.
                            restoreState = false
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
                    },
                    // Phase 45.1 — the third door back to the guide: the ☰
                    // drawer's footer, where a user who is lost in the editor
                    // looks first (the owner's "open view again[ing]").
                    onOpenGuide = { guideRequested = true }
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
                    // Phase 45.1 — the second door back to the guide: the
                    // Projects hub's ⋮ menu.
                    onOpenGuide = { guideRequested = true },
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
                            // Phase 44 device round 1: the Packages tab's
                            // "VIEW SETUP" must arrive at the terminal. With
                            // restoreState the previously saved sub-stack came
                            // back on top of it — an editor the user had opened
                            // earlier — which is what the owner reported.
                            restoreState = false
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    // Phase 45.1 — the first door back to the guide: Settings →
                    // About → Help & guide (its "Reset tips" neighbour brings the
                    // coach marks back too).
                    onOpenGuide = { guideRequested = true },
                    onNavigateToLogs = {
                        navController.navigate(Screen.Logs.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToFeedback = { reportCrash ->
                        navController.navigate(Screen.Feedback.createRoute(reportCrash = reportCrash)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Logs.route) {
                LogsScreen(onNavigateBack = { navController.popBackStack() })
            }
            // Phase 41 follow-up — feedback's own screen (Settings → OPEN,
            // or the exit survey's SHARE EXPERIENCE with a rating).
            // Phase 42.3 — `crash=1` arrives from the crash overlay's
            // [Send a report]: both attachments pre-ticked.
            composable(
                route = Screen.Feedback.route,
                arguments = listOf(
                    navArgument("rating") { defaultValue = 0 },
                    navArgument("crash") { defaultValue = 0 }
                )
            ) { backStackEntry ->
                val rating = backStackEntry.arguments?.getInt("rating") ?: 0
                val crash = backStackEntry.arguments?.getInt("crash") ?: 0
                FeedbackScreen(
                    onNavigateBack = { navController.popBackStack() },
                    exitRating = rating,
                    initialReportCrash = crash == 1
                )
            }
            }
        }
    }
        // Phase 45.2 — one spotlight at a time, at most two per arrival, and
        // never while another surface owns the screen: the exit survey, safe
        // mode, or a Phase 44 download that is actually moving (CHECKING is a
        // startup transient, not work, so it does not suppress a mark).
        // Phase 45.2 (device round) — ONE ordered tour, not two-marks-per-screen:
        // the plan walks ☰ → project → app.py → RUN ▶ → preview Back → the reveal
        // handle → Packages tab → its install card → Terminal tab → its chip, and
        // the highlighted control is the only forward button. No `surface` and no
        // per-arrival counter any more; the pure plan decides from the anchors
        // that are really laid out.
        GuideCoachMarks(
            seen = coachSeen,
            blockedByForeground = exitPromptVisible ||
                com.codeci.ide.ui.crash.SafeMode.active ||
                editorDialogOpen ||
                setupProgress.stage == com.codeci.ide.ui.terminal.SetupStage.DOWNLOADING ||
                setupProgress.stage == com.codeci.ide.ui.terminal.SetupStage.VERIFYING ||
                setupProgress.stage == com.codeci.ide.ui.terminal.SetupStage.EXTRACTING,
            drawerOpen = editorDrawerOpen,
            onSeen = { next ->
                coachSeen = next
                scope.launch {
                    settingsManager.setCoachMarksSeenCsv(CoachMarkPlan.serializeSeen(next))
                }
            }
        )
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
                // Phase 40.5 — 0.65 measured 3.53:1 on the LIGHT nav bar; 0.8 is 6.86:1
                // dark and 5.23:1 light, so the labels stay readable in both themes.
                val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                // Phase 45.2 — the tour's "small tour of package and terminal"
                // walks the user by spotlighting the tabs themselves, so the bar
                // publishes exactly the two rects the tour can use (the pure
                // plan maps route → anchor; the other three tabs publish
                // nothing). When Phase 32.1 hides the bar these withdraw, which
                // is why the reveal-handle step comes first in the tour.
                val tabAnchorId = CoachMarkPlan.tabAnchorFor(screen.route)
                val tabModifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(screen) }
                    .padding(vertical = 7.dp)
                val anchoredTab: Modifier = if (tabAnchorId != null) {
                    tabModifier.then(GuideAnchor.modifier(tabAnchorId))
                } else {
                    tabModifier
                }
                Column(
                    modifier = anchoredTab,
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

/**
 * Phase 32.1 — the thin handle shown where the 5-tab bar would sit while the
 * bar is hidden in the editor. Tapping it, or swiping it up, reveals the bar
 * again (sticky until the user leaves the editor). The drag threshold lives
 * in the pure [NavBarPolicy], so the gesture's "did they mean it" test is
 * host-tested; this composable only converts px → dp and renders the handle.
 */
@Composable
private fun EditorNavRevealHandle(onReveal: () -> Unit) {
    val density = LocalDensity.current
    var dragDy by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragDy = 0f },
                    onDragEnd = {
                        val totalDragDp = dragDy / density.density
                        if (NavBarPolicy.revealOnSwipe(totalDragDp)) onReveal()
                        dragDy = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        dragDy += dragAmount
                        change.consume()
                    }
                )
            }
            .clickable(onClick = onReveal)
            // Phase 45.2 — the owner's *"the tap to the open down side of the
            // keyboard"*: this handle is spotlit the first time it is really on
            // screen (it exists only while the bar is hidden), and never before.
            .then(GuideAnchor.modifier(GuideAnchors.NAV_HANDLE)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Show tabs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
