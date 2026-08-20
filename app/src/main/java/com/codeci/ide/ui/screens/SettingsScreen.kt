package com.codeci.ide.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.ui.services.EmbeddedCompiler
import com.codeci.ide.ui.services.TermuxCompiler
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.utils.DeviceDiagnostics
import com.codeci.ide.ui.theme.AppThemeMode
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.theme.getEditorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onNavigateToLogs: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    
    val currentAppTheme by themeManager.appThemeFlow.collectAsState(initial = AppThemeMode.SYSTEM)
    val currentEditorTheme by themeManager.editorThemeFlow.collectAsState(initial = EditorThemeType.DRACULA)

    val fontSize by settingsManager.fontSizeFlow.collectAsState(initial = 14f)
    val fontFamily by settingsManager.fontFamilyFlow.collectAsState(initial = "Monospace")
    val tabSize by settingsManager.tabSizeFlow.collectAsState(initial = 4)
    val lineNumbers by settingsManager.lineNumbersFlow.collectAsState(initial = true)
    val autoIndent by settingsManager.autoIndentFlow.collectAsState(initial = true)
    val wordWrap by settingsManager.wordWrapFlow.collectAsState(initial = false)

    val cStandard by settingsManager.cStandardFlow.collectAsState(initial = "C11")
    val warningLevel by settingsManager.warningLevelFlow.collectAsState(initial = "Standard")
    val optimizationLevel by settingsManager.optimizationLevelFlow.collectAsState(initial = "O0")
    val compilerBackend by settingsManager.compilerBackendFlow.collectAsState(initial = "auto")
    val terminalFontSize by settingsManager.terminalFontSizeFlow.collectAsState(initial = 14f)
    val accentColor by settingsManager.accentColorFlow.collectAsState(initial = "#FF6200EE")

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(com.codeci.ide.R.string.settings_title)) })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            
            // EDITOR SETTINGS
            SettingsSectionHeader("Editor Settings")
            
            SettingsSlider(
                title = "Font Size",
                value = fontSize,
                valueRange = 12f..32f,
                steps = 20,
                onValueChange = { scope.launch { settingsManager.setFontSize(it) } },
                valueLabel = "${fontSize.toInt()} sp"
            )
            
            SettingsDropdown(
                title = "Font Family",
                selectedOption = fontFamily,
                options = listOf("Monospace", "Courier", "Sans Serif", "Serif"),
                onOptionSelected = { scope.launch { settingsManager.setFontFamily(it) } }
            )
            
            SettingsDropdown(
                title = "Tab Size",
                selectedOption = "$tabSize spaces",
                options = listOf("2 spaces", "4 spaces", "8 spaces"),
                onOptionSelected = { scope.launch { settingsManager.setTabSize(it.split(" ")[0].toInt()) } }
            )
            
            SettingsSwitch(title = "Line Numbers", checked = lineNumbers, onCheckedChange = { scope.launch { settingsManager.setLineNumbers(it) } })
            SettingsSwitch(title = "Auto Indent", checked = autoIndent, onCheckedChange = { scope.launch { settingsManager.setAutoIndent(it) } })
            SettingsSwitch(title = "Word Wrap", checked = wordWrap, onCheckedChange = { scope.launch { settingsManager.setWordWrap(it) } })

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // COMPILER SETTINGS
            SettingsSectionHeader("Compiler Settings")
            
            SettingsDropdown(
                title = "C Standard",
                selectedOption = cStandard,
                options = listOf("C89", "C99", "C11", "C17"),
                onOptionSelected = { scope.launch { settingsManager.setCStandard(it) } }
            )
            
            SettingsDropdown(
                title = "Warning Level",
                selectedOption = warningLevel,
                options = listOf("None", "Standard", "All (-Wall -Wextra)"),
                onOptionSelected = { scope.launch { settingsManager.setWarningLevel(it) } }
            )
            
            SettingsDropdown(
                title = "Optimization Level",
                selectedOption = optimizationLevel,
                options = listOf("O0", "O1", "O2", "O3"),
                onOptionSelected = { scope.launch { settingsManager.setOptimizationLevel(it) } }
            )

            SettingsDropdown(
                title = "Compiler Engine",
                selectedOption = when (compilerBackend) {
                    "termux" -> "Termux"
                    "bundled" -> "Bundled Clang"
                    "embedded" -> "Built-in (TCC)"
                    else -> "Auto"
                },
                options = listOf("Auto", "Built-in (TCC)", "Bundled Clang", "Termux"),
                onOptionSelected = { option ->
                    scope.launch {
                        settingsManager.setCompilerBackend(
                            when (option) {
                                "Built-in (TCC)" -> "embedded"
                                "Bundled Clang" -> "bundled"
                                "Termux" -> "termux"
                                else -> "auto"
                            }
                        )
                    }
                }
            )
            SettingsItem(
                title = "Engine notes",
                subtitle = "Auto: built-in TCC first (offline, instant), then the downloaded " +
                    "Clang, then Termux when Android blocks both. Built-in (TCC): a full C " +
                    "compiler inside the APK — works offline like Coding C, no downloads, no " +
                    "Termux. TCC targets C99; use Bundled Clang for advanced C11/C17 code."
            )

            // BUILT-IN COMPILER CARD
            var tccState by remember { mutableStateOf(loadTccUiState(context)) }
            SettingsSectionHeader("Built-in Compiler")
            SettingsItem(
                title = "TCC (Tiny C Compiler)",
                subtitle = buildTccStatusText(tccState)
            )

            // TERMUX BRIDGE CARD
            var termuxState by remember { mutableStateOf(loadTermuxState(context)) }
            var probing by remember { mutableStateOf(false) }

            SettingsSectionHeader("Termux Engine")
            SettingsItem(
                title = "Termux",
                subtitle = buildTermuxStatusText(termuxState, probing)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { TermuxCompiler.openTermux(context) }) {
                    Text("OPEN TERMUX")
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            probing = true
                            termuxState = termuxState.copy(probeResult = null)
                            val probe = withContext(Dispatchers.IO) {
                                TermuxCompiler.runCommand(
                                    context = context,
                                    arguments = listOf("-c", "echo codec-bridge-ok"),
                                    label = "CodeC bridge check",
                                    timeoutSeconds = 8
                                )
                            }
                            termuxState = loadTermuxState(context).copy(probeResult = formatProbe(probe))
                            probing = false
                        }
                    },
                    enabled = termuxState.installed && !probing
                ) {
                    Text(if (probing) "CHECKING…" else "CHECK BRIDGE")
                }
            }
            SettingsItem(
                title = "How to enable",
                subtitle = "1) Install Termux 0.109+ from F-Droid or GitHub (termux.dev). " +
                    "2) In Termux run: echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && " +
                    "termux-reload-settings. 3) Grant CodeC the \"Run commands in Termux " +
                    "environment\" permission (Android Settings → Apps → CodeC IDE → Permissions → " +
                    "Additional permissions). 4) In Termux run: pkg update && pkg install clang"
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsSectionHeader(stringResource(com.codeci.ide.R.string.terminal_settings))
            SettingsSlider(
                title = stringResource(com.codeci.ide.R.string.terminal_font_size),
                value = terminalFontSize,
                valueRange = 8f..28f,
                steps = 19,
                onValueChange = { scope.launch { settingsManager.setTerminalFontSize(it) } },
                valueLabel = "${terminalFontSize.toInt()} sp"
            )
            SettingsItem(
                title = stringResource(com.codeci.ide.R.string.nav_terminal),
                subtitle = "In-app VT/ANSI terminal with a real PTY. cc is the built-in TCC; pkg arrives in Phase 3."
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // APPEARANCE
            SettingsSectionHeader("Appearance")
            
            Text(
                text = "App Theme",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AppThemeMode.values().forEach { themeMode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { themeManager.setAppTheme(themeMode) } }
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = currentAppTheme == themeMode,
                        onClick = { scope.launch { themeManager.setAppTheme(themeMode) } }
                    )
                    Text(
                        text = themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            SettingsDropdown(
                title = "Editor Theme",
                selectedOption = currentEditorTheme.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " "),
                options = EditorThemeType.values().map { it.name.lowercase().replaceFirstChar { char -> char.uppercase() }.replace("_", " ") },
                onOptionSelected = { option ->
                    val theme = EditorThemeType.values().first { 
                        it.name.lowercase().replaceFirstChar { char -> char.uppercase() }.replace("_", " ") == option 
                    }
                    scope.launch { themeManager.setEditorTheme(theme) }
                }
            )
            
            SettingsDropdown(
                title = stringResource(com.codeci.ide.R.string.accent_color),
                selectedOption = accentColor,
                options = listOf("#FF6200EE", "#FF018786", "#FFB00020", "#FF1976D2", "#FFFF9800"),
                onOptionSelected = { scope.launch { settingsManager.setAccentColor(it) } }
            )

            Box(modifier = Modifier.padding(16.dp)) {
                ThemePreview(editorTheme = currentEditorTheme, fontSize = fontSize)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // STORAGE
            SettingsSectionHeader("Storage")
            
            SettingsItem(
                title = "Projects Location",
                subtitle = context.getExternalFilesDir(null)?.absolutePath ?: "Internal Storage"
            )
            
            SettingsAction(
                title = "Clear Cache",
                actionText = "CLEAR",
                onClick = {
                    val cacheDir = File(context.cacheDir.absolutePath)
                    cacheDir.deleteRecursively()
                    Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                }
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // ABOUT
            SettingsSectionHeader("About")
            
            var versionTaps by remember { mutableStateOf(0) }
            val devModeUnlocked by settingsManager.devModeUnlockedFlow.collectAsState(initial = false)
            val showFilePaths by settingsManager.showFilePathsFlow.collectAsState(initial = false)

            SettingsItem(
                title = "App Version", 
                subtitle = "1.3.1 (Beta)",
                onClick = {
                    if (com.codeci.ide.BuildConfig.DEBUG && !devModeUnlocked) {
                        versionTaps++
                        if (versionTaps >= 7) {
                            scope.launch { settingsManager.setDevModeUnlocked(true) }
                            Toast.makeText(context, "Developer options unlocked!", Toast.LENGTH_SHORT).show()
                        } else if (versionTaps >= 4) {
                            Toast.makeText(context, "You are ${7 - versionTaps} steps away from being a developer.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            SettingsItem(title = "GitHub", subtitle = "https://github.com/pabi277/CodeC")
            SettingsAction(
                title = stringResource(com.codeci.ide.R.string.install_from_github),
                actionText = "INSTALL",
                onClick = {
                    scope.launch {
                        val updater = com.codeci.ide.ui.services.ApkUpdateManager(context)
                        Toast.makeText(context, context.getString(com.codeci.ide.R.string.checking_update), Toast.LENGTH_SHORT).show()
                        val release = updater.fetchLatestRelease()
                        if (release == null) {
                            Toast.makeText(context, context.getString(com.codeci.ide.R.string.update_none), Toast.LENGTH_LONG).show()
                            updater.openReleasesPage()
                            return@launch
                        }
                        Toast.makeText(context, context.getString(com.codeci.ide.R.string.update_found, release.name), Toast.LENGTH_SHORT).show()
                        if (!updater.canRequestPackageInstalls()) {
                            Toast.makeText(context, context.getString(com.codeci.ide.R.string.allow_unknown_sources), Toast.LENGTH_LONG).show()
                            context.startActivity(updater.installPermissionIntent())
                            return@launch
                        }
                        val apk = updater.downloadApk(release.apkUrl)
                        if (apk == null) {
                            Toast.makeText(context, context.getString(com.codeci.ide.R.string.update_download_failed), Toast.LENGTH_LONG).show()
                            updater.openReleasesPage()
                        } else {
                            updater.installApk(apk)
                        }
                    }
                }
            )
            SettingsItem(title = "Licenses", subtitle = "Open source licenses")
            
            if (com.codeci.ide.BuildConfig.DEBUG && devModeUnlocked) {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSectionHeader("Developer Options")

                SettingsSwitch(
                    title = "Show File Paths",
                    checked = showFilePaths,
                    onCheckedChange = { scope.launch { settingsManager.setShowFilePaths(it) } }
                )

                SettingsAction(
                    title = "Export App Logs",
                    actionText = "EXPORT",
                    onClick = {
                        val logs = com.codeci.ide.ui.utils.AppLogger.getLogsString()
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, logs)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Logs"))
                    }
                )

                SettingsAction(
                    title = "View App Logs",
                    actionText = "VIEW",
                    onClick = onNavigateToLogs
                )

                SettingsAction(
                    title = "Clear ALL Data",
                    actionText = "CLEAR",
                    onClick = {
                        val fileManager = com.codeci.ide.ui.utils.FileManager(context)
                        fileManager.listFiles().forEach { fileManager.deleteFile(it.name) }
                        Toast.makeText(context, "All files deleted", Toast.LENGTH_SHORT).show()
                    }
                )

                SettingsAction(
                    title = "Test Compiler Service",
                    actionText = "TEST",
                    onClick = {
                        scope.launch {
                            val compiler = com.codeci.ide.ui.services.CompilerService(context)
                            val res = compiler.compile(
                                "int main() { return 0; }",
                                com.codeci.ide.ui.services.CompilerSettings("c11", warnings = true, optimization = 0)
                            )
                            Toast.makeText(context, "Compile success: ${res.success}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                SettingsAction(
                    title = "Simulate Module Download",
                    actionText = "SIMULATE",
                    onClick = {
                        scope.launch {
                            com.codeci.ide.ui.utils.AppLogger.i("Developer", "Started module download simulation")
                            Toast.makeText(context, "Module downloading...", Toast.LENGTH_SHORT).show()
                            kotlinx.coroutines.delay(2000)
                            com.codeci.ide.ui.utils.AppLogger.i("Developer", "Module download simulation complete")
                            Toast.makeText(context, "Module download complete!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                SettingsAction(
                    title = "Force Crash",
                    actionText = "CRASH",
                    onClick = {
                        throw RuntimeException("Forced crash from Developer Options")
                    }
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@Composable
fun SettingsSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsSlider(title: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, steps: Int, onValueChange: (Float) -> Unit, valueLabel: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(text = valueLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(title: String, selectedOption: String, options: List<String>, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsAction(title: String, actionText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onClick) {
            Text(actionText)
        }
    }
}

private data class TccUiState(val abi: String?, val available: Boolean)

private fun loadTccUiState(context: Context): TccUiState {
    val abi = EmbeddedCompiler.abiDir()
    val available = if (abi != null) EmbeddedCompiler.ensureExtracted(context) else false
    return TccUiState(abi, available)
}

private fun buildTccStatusText(state: TccUiState): String = when {
    state.abi == null ->
        "No built-in compiler for this device's CPU (${DeviceDiagnostics.abiSummary()}). " +
            "The app will use the Clang module or Termux instead."
    state.available ->
        "Ready ✓ — a full C compiler inside the APK (${state.abi}). Offline, instant, no " +
            "downloads and no Termux needed for everyday C code."
    else ->
        "Present but could not start. Reinstall the app, or use another Compiler Engine."
}

private data class TermuxUiState(
    val installed: Boolean,
    val permissionGranted: Boolean,
    val probeResult: String? = null
)

private fun loadTermuxState(context: Context): TermuxUiState = TermuxUiState(
    installed = TermuxCompiler.isTermuxInstalled(context),
    permissionGranted = TermuxCompiler.isRunCommandPermissionGranted(context)
)

private fun buildTermuxStatusText(state: TermuxUiState, probing: Boolean): String {
    if (!state.installed) {
        return "Not installed. Install Termux 0.109+ from F-Droid or GitHub (https://termux.dev), " +
            "open it once, then run: pkg update && pkg install clang"
    }
    if (probing) return "Checking the Termux bridge…"
    state.probeResult?.let { return it }
    return if (state.permissionGranted) {
        "Installed, permission granted. Tap CHECK BRIDGE to verify, then pick \"Termux\" as the " +
            "Compiler Engine above."
    } else {
        "Installed, but CodeC is not allowed to run commands inside Termux yet. Grant the " +
            "\"Run commands in Termux environment\" permission (App Info → Permissions → " +
            "Additional permissions) and enable allow-external-apps in Termux (see \"How to " +
            "enable\" below)."
    }
}

private fun formatProbe(probe: TermuxCompiler.TermuxResult): String = when {
    probe.timedOut ->
        "No response from Termux. Enable allow-external-apps: in Termux run: echo " +
            "\"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings"
    probe.internalFailure != null -> "Not ready: ${probe.internalFailure}"
    probe.exitCode == 0 -> "Ready ✓ — CodeC can compile and run through Termux."
    else -> "Unexpected result (exit ${probe.exitCode})."
}

@Composable
fun ThemePreview(editorTheme: EditorThemeType, fontSize: Float) {
    val colors = getEditorTheme(editorTheme)
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        val previewText = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.keyword)) { append("int ") }
            withStyle(SpanStyle(color = colors.function)) { append("main") }
            withStyle(SpanStyle(color = colors.operator)) { append("() {\n") }
            
            append("    ")
            withStyle(SpanStyle(color = colors.comment)) { append("// Print a message\n") }
            
            append("    ")
            withStyle(SpanStyle(color = colors.function)) { append("printf") }
            withStyle(SpanStyle(color = colors.operator)) { append("(") }
            withStyle(SpanStyle(color = colors.string)) { append("\"Hello, %d!\\n\"") }
            withStyle(SpanStyle(color = colors.operator)) { append(", ") }
            withStyle(SpanStyle(color = colors.number)) { append("2026") }
            withStyle(SpanStyle(color = colors.operator)) { append(");\n") }
            
            append("    ")
            withStyle(SpanStyle(color = colors.keyword)) { append("return ") }
            withStyle(SpanStyle(color = colors.number)) { append("0") }
            withStyle(SpanStyle(color = colors.operator)) { append(";\n}") }
        }
        
        Text(
            text = previewText,
            fontFamily = FontFamily.Monospace,
            color = colors.text,
            fontSize = fontSize.sp
        )
    }
}
