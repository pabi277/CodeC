package com.codeci.ide.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.codeci.ide.ui.projects.GitErrors
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.input.TextFieldValue
import com.codeci.ide.ui.keyboard.CodecKeyboard
import com.codeci.ide.ui.keyboard.KeyboardDefaults
import com.codeci.ide.ui.keyboard.ShiftState
import com.codeci.ide.ui.services.EmbeddedCompiler
import com.codeci.ide.ui.services.TermuxCompiler
import com.codeci.ide.ui.projects.GitCredentialsStore
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.utils.DeviceDiagnostics
import com.codeci.ide.ui.theme.AppThemeMode
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.TerminalThemeType
import com.codeci.ide.ui.theme.ThemeManager
import com.codeci.ide.ui.theme.getEditorTheme
import com.codeci.ide.ui.theme.getTerminalTheme
import kotlin.math.roundToInt
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
    val currentTerminalTheme by themeManager.terminalThemeFlow.collectAsState(initial = TerminalThemeType.DRACULA)

    val fontSize by settingsManager.fontSizeFlow.collectAsState(initial = 14f)
    val fontFamily by settingsManager.fontFamilyFlow.collectAsState(initial = "Monospace")
    val tabSize by settingsManager.tabSizeFlow.collectAsState(initial = 4)
    val lineNumbers by settingsManager.lineNumbersFlow.collectAsState(initial = true)
    val autoIndent by settingsManager.autoIndentFlow.collectAsState(initial = true)
    val wordWrap by settingsManager.wordWrapFlow.collectAsState(initial = false)

    // Phase 27.3 — completion surfaces (master off = the whole feature is gone).
    val completionMaster by settingsManager.completionMasterFlow.collectAsState(initial = true)
    val completionGhost by settingsManager.completionGhostFlow.collectAsState(initial = true)
    val completionStrip by settingsManager.completionStripFlow.collectAsState(initial = true)
    val completionPanel by settingsManager.completionPanelFlow.collectAsState(initial = true)
    val completionDebounceMs by settingsManager.completionDebounceMsFlow.collectAsState(initial = 120)

    // Phase 28.2 — CodeC Keys (the dedicated in-app code keyboard; opt-in
    // until the device round flips the default).
    val codecKeysOn by settingsManager.codecKeysEnabledFlow.collectAsState(initial = true)
    val codecKeysHaptics by settingsManager.codecKeysHapticsFlow.collectAsState(initial = true)
    val codecKeysHeight by settingsManager.codecKeysHeightFlow.collectAsState(initial = 1f)

    val cStandard by settingsManager.cStandardFlow.collectAsState(initial = "C11")
    val warningLevel by settingsManager.warningLevelFlow.collectAsState(initial = "Standard")
    val optimizationLevel by settingsManager.optimizationLevelFlow.collectAsState(initial = "O0")
    val terminalFontSize by settingsManager.terminalFontSizeFlow.collectAsState(initial = 12f)
    val terminalFontFamily by settingsManager.terminalFontFamilyFlow.collectAsState(initial = "JetBrains Mono")
    val terminalExtraKeysMacros by settingsManager.terminalExtraKeysMacrosFlow.collectAsState(initial = "")
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

            // Phase 27.3 — phone-native autocomplete surfaces. The master
            // switch removes ALL completion chrome (ghost, chips, panel).
            SettingsSwitch(
                title = "Autocompletion",
                checked = completionMaster,
                onCheckedChange = { scope.launch { settingsManager.setCompletionMaster(it) } }
            )
            SettingsItem(
                title = "How suggestions appear",
                subtitle = "Suggestions never steal Enter and never complete by themselves. " +
                    "Ghost text = dimmed hint in the code (tap it, \"TAB ▸\" or \"→\" to accept, " +
                    "long-press TAB still indents). Chips = tap targets in the keys row " +
                    "(swipe down dismisses for the word). \"⌄ more\" opens the full list."
            )
            if (completionMaster) {
                SettingsSwitch(
                    title = "Inline ghost text",
                    checked = completionGhost,
                    onCheckedChange = { scope.launch { settingsManager.setCompletionGhost(it) } }
                )
                SettingsSwitch(
                    title = "Suggestion chips in the keys row",
                    checked = completionStrip,
                    onCheckedChange = { scope.launch { settingsManager.setCompletionStrip(it) } }
                )
                SettingsSwitch(
                    title = "\"⌄ more\" opens the full completion panel",
                    checked = completionPanel,
                    onCheckedChange = { scope.launch { settingsManager.setCompletionPanel(it) } }
                )
                SettingsDropdown(
                    title = "Suggestion delay",
                    selectedOption = "$completionDebounceMs ms",
                    options = listOf("120 ms", "240 ms"),
                    onOptionSelected = {
                        scope.launch {
                            settingsManager.setCompletionDebounceMs(it.split(" ")[0].toInt())
                        }
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // CODEC KEYS — Phase 28.2. The editor draws its own code
            // keyboard; while it is ON the system IME steps aside for the
            // editor surface (a run waiting for stdin always gets it back,
            // and leaving the editor restores it: exit condition 5 — "OFF →
            // system IME returns exactly as before"). DEFAULT ON per owner
            // round 2 — turn this off any time to go back to the strip + IME.
            SettingsSectionHeader("CodeC Keys")
            SettingsItem(
                title = "Dedicated in-app code keyboard",
                subtitle = "A data-driven code-QWERTY the app draws itself: flick up for digits/symbols, " +
                    "hold for popups, ⌫ hold-repeats, flick-up on ⌫ deletes a word. Suggestions ride above " +
                    "it; it is NOT a system IME and exists only inside the editor. Layout defaults are " +
                    "built-in; dev builds can override the rows with a layout JSON (Settings → Developer)."
            )
            SettingsSwitch(
                title = "CodeC Keys",
                checked = codecKeysOn,
                onCheckedChange = { scope.launch { settingsManager.setCodecKeysEnabled(it) } }
            )
            if (codecKeysOn) {
                SettingsSwitch(
                    title = "Haptic tick per key",
                    checked = codecKeysHaptics,
                    onCheckedChange = { scope.launch { settingsManager.setCodecKeysHaptics(it) } }
                )
                SettingsSlider(
                    title = "Key row height",
                    value = codecKeysHeight,
                    valueRange = 0.7f..1.3f,
                    steps = 0,
                    onValueChange = { v -> scope.launch { settingsManager.setCodecKeysHeight(v) } },
                    valueLabel = "${(codecKeysHeight * 100).roundToInt()}%"
                )
                Text(
                    text = "Preview (live — taps here type nowhere)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                )
                CodecKeyboard(
                    layout = KeyboardDefaults.codeQwerty().copy(heightScale = codecKeysHeight),
                    shift = ShiftState.OFF,
                    onShiftChange = {},
                    onLayerChange = {},
                    textFieldValue = TextFieldValue(""),
                    onValueChange = {},
                    haptics = false,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

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

            SettingsItem(
                title = "Compiler",
                subtitle = "C compiles with the built-in TCC by default — offline, instant, " +
                    "no download. C++ and advanced C11/C17 code need the full LLVM " +
                    "toolchain: install it from Packages (or run \"pkg install clang\" in " +
                    "the terminal) and RUN \u25b6 offers it automatically when a file needs it."
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
            SettingsDropdown(
                title = stringResource(com.codeci.ide.R.string.terminal_font_family),
                selectedOption = terminalFontFamily,
                options = listOf("JetBrains Mono", "Monospace", "Courier", "Sans Serif", "Serif"),
                onOptionSelected = { scope.launch { settingsManager.setTerminalFontFamily(it) } }
            )
            SettingsDropdown(
                title = stringResource(com.codeci.ide.R.string.terminal_theme),
                selectedOption = currentTerminalTheme.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " "),
                options = TerminalThemeType.values().map { it.name.lowercase().replaceFirstChar { char -> char.uppercase() }.replace("_", " ") },
                onOptionSelected = { option ->
                    val theme = TerminalThemeType.values().first { 
                        it.name.lowercase().replaceFirstChar { char -> char.uppercase() }.replace("_", " ") == option 
                    }
                    scope.launch { themeManager.setTerminalTheme(theme) }
                }
            )

            Box(modifier = Modifier.padding(16.dp)) {
                TerminalThemePreview(
                    terminalTheme = currentTerminalTheme,
                    fontFamily = terminalFontFamily,
                    fontSize = terminalFontSize
                )
            }

            SettingsItem(
                title = stringResource(com.codeci.ide.R.string.nav_terminal),
                subtitle = "In-app VT/ANSI terminal with a real PTY, built-in TCC compiler (cc), and signed CodeC package manager (pkg)."
            )

            var editingMacros by remember(terminalExtraKeysMacros) { mutableStateOf(terminalExtraKeysMacros) }
            var macrosSaved by remember { mutableStateOf(false) }

            SettingsSectionHeader("Terminal Extra-Keys & Shortcuts")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Custom Extra-Key Shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add custom shortcut buttons to the terminal key bar (e.g. 'pkg install nano', 'git status', 'make', 'cc').",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editingMacros,
                        onValueChange = {
                            editingMacros = it
                            macrosSaved = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("pkg install nano, git status, make") },
                        label = { Text("Extra Keys (comma-separated)") },
                        singleLine = false,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            editingMacros = if (editingMacros.isBlank()) "pkg install nano, git status, make" else "$editingMacros, pkg install nano"
                            macrosSaved = false
                        }) {
                            Text("+ ADD EXAMPLE")
                        }
                        TextButton(onClick = {
                            scope.launch {
                                settingsManager.setTerminalExtraKeysMacros(editingMacros)
                                macrosSaved = true
                                Toast.makeText(context, "Shortcuts saved ✓", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text(if (macrosSaved) "SAVED ✓" else "SAVE SHORTCUTS")
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // PACKAGE REPOSITORY & TRUST (Phase 4 Part 4.3)
            SettingsSectionHeader("Package Repository & Trust")

            var trustInfo by remember {
                mutableStateOf(ShellEnvironment.getRepositoryTrustInfo(context.filesDir))
            }
            var checkingRepo by remember { mutableStateOf(false) }
            var repoStatusMessage by remember { mutableStateOf<String?>(null) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (trustInfo.keyringInstalled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Trust Status",
                            tint = if (trustInfo.keyringInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (trustInfo.keyringInstalled) "CodeC Official Signed Channel" else "Keyring Missing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (trustInfo.keyringInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• Channel: ${trustInfo.channelName}\n" +
                            "• Repository: ${trustInfo.repositoryUrl}\n" +
                            "• Trust Model: OpenPGP gpgv (Fail-Closed)\n" +
                            "• Keyring: ${trustInfo.keyringName} (${if (trustInfo.keyringInstalled) "${trustInfo.keyringSize} bytes" else "missing"})\n" +
                            "• Signing Subkey: ${trustInfo.signingFingerprint.take(8)}...${trustInfo.signingFingerprint.takeLast(8)}\n" +
                            "• Userland: ${if (trustInfo.userlandInstalled) "Phase 3 (Installed, ${trustInfo.arch ?: "unknown"})" else "Not installed"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (repoStatusMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = repoStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    checkingRepo = true
                                    repoStatusMessage = "Checking repository signature…"
                                    val isOnline = ShellEnvironment.checkRepositoryOnline()
                                    trustInfo = ShellEnvironment.getRepositoryTrustInfo(context.filesDir)
                                    repoStatusMessage = if (isOnline) {
                                        "Repository online & InRelease reachable ✓"
                                    } else {
                                        "Repository unreachable (offline or network error)"
                                    }
                                    checkingRepo = false
                                }
                            },
                            enabled = !checkingRepo
                        ) {
                            Text(if (checkingRepo) "CHECKING…" else "CHECK REPOSITORY")
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // GITHUB ACCOUNT (Phase 13 — Git integration credentials)
            SettingsSectionHeader("GitHub Account")

            val gitStore = remember { GitCredentialsStore(context) }
            var gitHubUser by remember { mutableStateOf("") }
            var gitHubToken by remember { mutableStateOf("") }
            var gitAuthorName by remember { mutableStateOf("") }
            var gitAuthorEmail by remember { mutableStateOf("") }
            var tokenVisible by remember { mutableStateOf(false) }
            var gitSavedMessage by remember { mutableStateOf<String?>(null) }
            var gitLoaded by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                val stored = gitStore.stored()
                gitHubToken = stored.token
                gitHubUser = stored.username
                gitAuthorName = stored.authorName
                gitAuthorEmail = stored.authorEmail
                gitLoaded = true
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (gitHubToken.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = "GitHub Status",
                            tint = if (gitHubToken.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (gitHubToken.isNotBlank()) {
                                "Connected (${gitHubUser.ifBlank { "oauth2" }} · ••••${gitHubToken.takeLast(4)})"
                            } else {
                                "Not connected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (gitHubToken.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "A fine-grained Personal Access Token (repo contents read/write) enables push from the Source Control pane. The token stays in app-private storage — it is never written to repositories or logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Phase 17 follow-up — a one-tap path to GitHub's token
                    // page, so "no token" never means "go figure it out".
                    Text(
                        text = "Create a GitHub token ↗",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(GitErrors.TOKEN_HELP_URL))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            .padding(vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = gitHubToken,
                        onValueChange = { gitHubToken = it },
                        label = { Text("Personal Access Token") },
                        singleLine = true,
                        visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { tokenVisible = !tokenVisible }) {
                                Text(if (tokenVisible) "HIDE" else "SHOW")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gitHubUser,
                        onValueChange = { gitHubUser = it },
                        label = { Text("GitHub username (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gitAuthorName,
                            onValueChange = { gitAuthorName = it },
                            label = { Text("Commit name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = gitAuthorEmail,
                            onValueChange = { gitAuthorEmail = it },
                            label = { Text("Commit email") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (gitSavedMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = gitSavedMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (gitHubToken.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        gitStore.clearCredentials()
                                        gitHubToken = ""
                                        gitHubUser = ""
                                        gitSavedMessage = "GitHub account disconnected"
                                    }
                                },
                                enabled = gitLoaded
                            ) {
                                Text("DISCONNECT")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    gitStore.save(gitHubToken, gitHubUser, gitAuthorName, gitAuthorEmail)
                                    gitSavedMessage = "GitHub credentials saved ✓"
                                    Toast.makeText(context, "GitHub credentials saved ✓", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = gitLoaded
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SAVE")
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // APPEARANCE
            SettingsSectionHeader("Appearance")
            
            Text(
                text = "App Theme",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            // Phase 24.8 — "Auto (follow system)" is the first, default option.
            val appThemeOptions = listOf(AppThemeMode.SYSTEM, AppThemeMode.LIGHT, AppThemeMode.DARK)
            appThemeOptions.forEach { themeMode ->
                val label = when (themeMode) {
                    AppThemeMode.SYSTEM -> "Auto (follow system)"
                    AppThemeMode.LIGHT -> "Light"
                    AppThemeMode.DARK -> "Dark"
                }
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
                        text = label,
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
                title = "Terminal Theme",
                selectedOption = currentTerminalTheme.name.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " "),
                options = TerminalThemeType.values().map { it.name.lowercase().replaceFirstChar { char -> char.uppercase() }.replace("_", " ") },
                onOptionSelected = { option ->
                    val theme = TerminalThemeType.values().first { 
                        it.name.lowercase().replaceFirstChar { char -> char.uppercase() }.replace("_", " ") == option 
                    }
                    scope.launch { themeManager.setTerminalTheme(theme) }
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
            SettingsSectionHeader(stringResource(com.codeci.ide.R.string.storage))
            
            var storageGranted by remember {
                mutableStateOf(ShellEnvironment.hasStoragePermission(context))
            }
            val storageLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val granted = permissions.values.any { it }
                storageGranted = ShellEnvironment.hasStoragePermission(context) || granted
                if (storageGranted) {
                    val home = ShellEnvironment.homeDir(context.filesDir)
                    ShellEnvironment.setupStorageDirectory(home)
                    Toast.makeText(context, context.getString(com.codeci.ide.R.string.storage_setup_complete), Toast.LENGTH_SHORT).show()
                }
            }

            SettingsItem(
                title = stringResource(com.codeci.ide.R.string.terminal_storage_title),
                subtitle = if (storageGranted) {
                    stringResource(com.codeci.ide.R.string.storage_permission_granted) + " — " +
                        stringResource(com.codeci.ide.R.string.terminal_storage_subtitle)
                } else {
                    stringResource(com.codeci.ide.R.string.storage_permission_needed)
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = {
                    val home = ShellEnvironment.homeDir(context.filesDir)
                    ShellEnvironment.setupStorageDirectory(home)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        if (!android.os.Environment.isExternalStorageManager()) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val fallback = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(fallback)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open storage settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                            return@TextButton
                        }
                    }
                    storageLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }) {
                    Text(stringResource(com.codeci.ide.R.string.setup_storage_action))
                }
            }

            SettingsItem(
                title = stringResource(com.codeci.ide.R.string.projects_location),
                subtitle = context.getExternalFilesDir(null)?.absolutePath ?: "Internal Storage"
            )
            
            SettingsAction(
                title = stringResource(com.codeci.ide.R.string.clear_cache),
                actionText = stringResource(com.codeci.ide.R.string.clear),
                onClick = {
                    val cacheDir = File(context.cacheDir.absolutePath)
                    cacheDir.deleteRecursively()
                    Toast.makeText(context, context.getString(com.codeci.ide.R.string.cache_cleared), Toast.LENGTH_SHORT).show()
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
                subtitle = "1.3.14 (Beta)",
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
            // Phase 25.2 — LGPL-2.1 obligation checklist: sora-editor is used
            // as a binary Gradle dependency only (no source copied, no fork);
            // the attribution + license pointer live here.
            SettingsItem(
                title = "Open-source licenses",
                subtitle = "sora-editor © Rosemoe — LGPL-2.1 · github.com/Rosemoe/sora-editor"
            )
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
        "Present but could not start. Reinstall the app, or install clang from Packages."
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
        "Installed, permission granted. Tap CHECK BRIDGE to verify — CodeC can then use " +
            "Termux's clang as a fallback engine."
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

@Composable
fun TerminalThemePreview(terminalTheme: TerminalThemeType, fontFamily: String, fontSize: Float) {
    val colors = getTerminalTheme(terminalTheme)
    val font = when (fontFamily) {
        "JetBrains Mono" -> FontFamily(
            androidx.compose.ui.text.font.Font(com.codeci.ide.R.font.jetbrainsmono_medium)
        )
        "Courier" -> FontFamily.Monospace
        "Sans Serif" -> FontFamily.SansSerif
        "Serif" -> FontFamily.Serif
        else -> FontFamily.Monospace
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        val previewText = buildAnnotatedString {
            withStyle(SpanStyle(color = Color(0xFF50FA7B))) { append("codec@user") }
            withStyle(SpanStyle(color = colors.foreground)) { append(":") }
            withStyle(SpanStyle(color = Color(0xFF8BE9FD))) { append("~") }
            withStyle(SpanStyle(color = colors.foreground)) { append("$ cc -o hello hello.c\n") }
            withStyle(SpanStyle(color = Color(0xFF50FA7B))) { append("codec@user") }
            withStyle(SpanStyle(color = colors.foreground)) { append(":") }
            withStyle(SpanStyle(color = Color(0xFF8BE9FD))) { append("~") }
            withStyle(SpanStyle(color = colors.foreground)) { append("$ ./hello\n") }
            withStyle(SpanStyle(color = colors.foreground)) { append("Hello from CodeC terminal! ") }
            withStyle(SpanStyle(color = colors.cursor)) { append("█") }
        }

        Text(
            text = previewText,
            fontFamily = font,
            color = colors.foreground,
            fontSize = fontSize.sp
        )
    }
}
