package com.codeci.ide.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.components.HapticMoment
import com.codeci.ide.ui.components.PressableSurface
import com.codeci.ide.ui.components.SkeletonPackageRow
import com.codeci.ide.ui.components.rememberCodecHaptics
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecMotion
import com.codeci.ide.ui.theme.CodecTokens.Radius
import com.codeci.ide.ui.theme.CodecTokens.Space
import com.codeci.ide.ui.theme.CodecType
import com.codeci.ide.ui.theme.rememberMotionSpecs
import com.codeci.ide.R
import com.codeci.ide.ui.guide.GuideAnchor
import com.codeci.ide.ui.guide.GuideAnchors
import com.codeci.ide.ui.modules.InstallFacts
import com.codeci.ide.ui.modules.InstallLabel
import com.codeci.ide.ui.modules.InstallMoment
import com.codeci.ide.ui.modules.PackageCatalog
import com.codeci.ide.ui.modules.PackageItem
import com.codeci.ide.ui.modules.PackageSection
import com.codeci.ide.ui.modules.PkgState
import com.codeci.ide.ui.modules.QuickAction
import com.codeci.ide.ui.services.EmbeddedCompiler
import com.codeci.ide.ui.terminal.SetupAction
import kotlinx.coroutines.delay
import com.codeci.ide.ui.terminal.SetupGatePolicy
import com.codeci.ide.ui.terminal.ShellEnvironment
import com.codeci.ide.ui.viewmodels.TerminalViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    modifier: Modifier = Modifier,
    terminalViewModel: TerminalViewModel = activityTerminalViewModel(),
    onNavigateToTerminal: () -> Unit = {}
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var customCommand by remember { mutableStateOf("") }
    // Phase 52.2 — even the static catalog gets one honest first-paint branch;
    // its six rows match the loaded card rhythm instead of flashing a blank.
    var packageListReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.yield()
        packageListReady = true
    }
    // Phase 44.1 — the Packages tab stops lying. Every row here fires
    // `pkg …` into the terminal shell; while the one-time userland setup has
    // not produced a working `bin/pkg`, that produced a bare
    // "pkg: not found" — the owner's exact report. One gate
    // (SetupGatePolicy) now decides, one sentence explains, and the button
    // becomes VIEW SETUP instead of queueing a command that cannot work.
    val setupFacts by terminalViewModel.setupFacts.collectAsState()
    val installVerdict = remember(setupFacts) {
        SetupGatePolicy.can(SetupAction.INSTALL_PACKAGE, setupFacts)
    }
    val setupRefusal = installVerdict.message?.takeIf { !installVerdict.allowed }
    val prefixDir = remember(context) { ShellEnvironment.prefixDir(context.filesDir) }
    val runGated: (String, String) -> Unit = { command, label ->
        // A command that could actually run (CodeC's own `cc`, a compiled
        // executable, anything present in the userland or in /system/bin) is
        // never gated; only one that needs the missing `bin/pkg` is.
        val action = SetupGatePolicy.actionForCommand(command, prefixDir)
        val verdict = SetupGatePolicy.can(action, setupFacts)
        if (verdict.allowed) {
            Toast.makeText(context, "Running: $label", Toast.LENGTH_SHORT).show()
            terminalViewModel.sendCommand(command)
            onNavigateToTerminal()
        } else {
            // Refused out loud, never queued silently: a `pkg install` that
            // fires 40 s later with no visible cause is the same surprise this
            // phase exists to remove.
            Toast.makeText(
                context,
                verdict.message ?: NOT_READY,
                Toast.LENGTH_LONG
            ).show()
        }
    }
    // Phase 33.2 — the "Unix tools" section is collapsed by default; a tap on
    // its header expands it. The language section is always open.
    var unixToolsExpanded by remember { mutableStateOf(false) }

    val filteredPackages = remember(searchQuery) {
        PackageCatalog.ALL_PACKAGES.filter { item ->
            val query = searchQuery.trim().lowercase()
            query.isEmpty() ||
                item.name.lowercase().contains(query) ||
                item.id.lowercase().contains(query) ||
                item.binary.lowercase().contains(query) ||
                item.description.lowercase().contains(query) ||
                item.installCommand.lowercase().contains(query)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text("Packages & Tools", fontWeight = FontWeight.Bold)
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = CodecTokens.space(Space.L)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.L))
        ) {
            // Phase 44.1 — the setup notice, once, at the top: what is
            // happening, and the tap that gets the user to where it happens.
            if (setupRefusal != null) {
                item(key = "setup_gate") {
                    SetupGateCard(
                        message = setupRefusal,
                        onViewSetup = onNavigateToTerminal
                    )
                }
            }
            // SEARCH BAR
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search packages or install commands…") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(CodecTokens.radius(Radius.M))
                )
            }

            // PACKAGES — Phase 33.2: "Languages & IntelliSense" is always open
            // and "Unix tools" is collapsed by default (tap the header to
            // expand). A typed search flattens both sections so a name is
            // found regardless of which section it lives in.
            if (!packageListReady) {
                items(
                    count = PackageCatalog.ALL_PACKAGES.size,
                    key = { "package_skeleton_$it" },
                ) {
                    SkeletonPackageRow()
                }
            } else if (filteredPackages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CodecTokens.space(Space.XXL)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No packages match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (searchQuery.isNotBlank()) {
                items(filteredPackages, key = { it.id }) { item ->
                    PackageCardRow(
                        item = item,
                        context = context,
                        terminalViewModel = terminalViewModel,
                        onNavigateToTerminal = onNavigateToTerminal,
                        setupRefusal = setupRefusal,
                    )
                }
            } else {
                PackageSection.ordered.forEach { section ->
                    val sectionItems = filteredPackages.filter { PackageCatalog.sectionOf(it) == section }
                    if (sectionItems.isEmpty()) return@forEach
                    val expanded = section == PackageSection.LANGUAGES_INTELLISENSE || unixToolsExpanded
                    item(key = "section_${section.name}") {
                        PackageSectionHeader(
                            title = section.title,
                            expanded = expanded,
                            collapsible = section == PackageSection.UNIX_TOOLS,
                            onToggle = { unixToolsExpanded = !unixToolsExpanded },
                        )
                    }
                    if (expanded) {
                        // Phase 45.2 — the Packages surface gets ONE coach mark,
                        // on the first card of the first section: "adding a
                        // language downloads once" is Phase 44's teaching moment
                        // at the point of action. Only a card that is really laid
                        // out publishes an anchor, so a collapsed or empty
                        // section produces no mark (and marks nothing seen).
                        val isFirstSection = section == PackageSection.ordered.first()
                        itemsIndexed(sectionItems, key = { _, item -> item.id }) { index, item ->
                            PackageCardRow(
                                item = item,
                                context = context,
                                terminalViewModel = terminalViewModel,
                                onNavigateToTerminal = onNavigateToTerminal,
                                setupRefusal = setupRefusal,
                                guideAnchorId = if (isFirstSection && index == 0) {
                                    GuideAnchors.PACKAGES_CARD
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }

            // QUICK SYSTEM ACTIONS
            item {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(CodecTokens.space(Space.S)))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
                ) {
                    PackageCatalog.QUICK_ACTIONS.forEach { action ->
                        QuickActionChip(
                            action = action,
                            onClick = {
                                runGated(action.command, action.command)
                            }
                        )
                    }
                }
            }

            // CUSTOM COMMAND RUNNER
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(CodecTokens.radius(Radius.M))
                ) {
                    Column(modifier = Modifier.padding(CodecTokens.space(Space.L))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION))
                            )
                            Spacer(modifier = Modifier.width(CodecTokens.space(Space.S)))
                            Text(
                                text = "Run Custom Command",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(CodecTokens.space(Space.S)))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customCommand,
                                onValueChange = { customCommand = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("e.g. pkg install -y git, cc main.c") },
                                singleLine = true,
                                shape = RoundedCornerShape(CodecTokens.radius(Radius.S))
                            )
                            Spacer(modifier = Modifier.width(CodecTokens.space(Space.S)))
                            Button(
                                onClick = {
                                    if (customCommand.isNotBlank()) {
                                        val cmd = customCommand.trim()
                                        runGated(cmd, cmd)
                                    }
                                },
                                enabled = customCommand.isNotBlank(),
                                shape = RoundedCornerShape(CodecTokens.radius(Radius.S))
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Run", modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE)))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(CodecTokens.space(Space.XXL)))
            }
        }
    }
}

/**
 * One package card inside a hub section, with the installed check and the
 * terminal hand-off wired. Shared by the sectioned list and the search-flat
 * list so both behave identically (Phase 33.2).
 */
@Composable
private fun PackageCardRow(
    item: PackageItem,
    context: Context,
    terminalViewModel: TerminalViewModel,
    onNavigateToTerminal: () -> Unit,
    setupRefusal: String? = null,
    /** Phase 45.2 — non-null on the one card a coach mark may spotlight. */
    guideAnchorId: String? = null,
) {
    val haptics = rememberCodecHaptics()
    val motion = rememberMotionSpecs()
    // Phase 51.3 — the row's own state, now observable. Before this it was a
    // one-shot `remember(item.id) { checkIsInstalled(...) }`: a finished install
    // could never be seen by the row that started it, which is why the install
    // "ended in the terminal". `installedNow` re-reads the disk only while the
    // user's own install is in flight (never as a background poll for a
    // screenful of rows), and the pure InstallMoment policy decides when that
    // read is the genuine transition worth marking — once, never on a
    // re-render, never for a package that was already installed.
    val firstCheck = remember(item.id) { checkIsInstalled(context, item) }
    var installedNow by remember(item.id) { mutableStateOf(firstCheck) }
    var installRequested by remember(item.id) { mutableStateOf(false) }
    var celebrated by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id, installRequested, installedNow) {
        if (!installRequested || installedNow) return@LaunchedEffect
        var observed = PkgState.NOT_INSTALLED
        while (!installedNow) {
            delay(1_500)
            val now = if (checkIsInstalled(context, item)) PkgState.INSTALLED else PkgState.NOT_INSTALLED
            if (InstallMoment.celebrateOnFinish(observed, now) && !celebrated) {
                celebrated = true
                haptics.perform(HapticMoment.INSTALL_FINISHED)
                Toast.makeText(
                    context,
                    context.getString(R.string.install_finished, item.name),
                    Toast.LENGTH_LONG,
                ).show()
            }
            observed = now
            installedNow = now == PkgState.INSTALLED
        }
    }
    val isInstalled = installedNow
    // Phase 44.1 — a built-in package (the APK's own TCC `cc`) needs no
    // userland at all, so the setup gate never applies to it: C is never gated.
    val gated = setupRefusal != null && !item.isBuiltIn
    // Capability, not optimism, in both directions: `pkg install` and
    // `pkg uninstall` genuinely need the missing `bin/pkg`, but a binary that
    // is ALREADY in the prefix runs fine — so RUN stays live for an installed
    // package even while the setup gate is up.
    val runBlocked = gated && !isInstalled
    // Phase 51.3 — one policy decides what the row's primary action says at each
    // transition (INSTALL / INSTALLING / OPEN / UPDATE / RETRY); the copy itself
    // stays in strings.xml.
    val installFacts = InstallFacts(
        state = when {
            isInstalled -> PkgState.INSTALLED
            installRequested -> PkgState.INSTALLING
            else -> PkgState.NOT_INSTALLED
        },
        running = installRequested,
        progress = null,
        // Failure is the terminal's to report (Phase 44's law): the row never
        // guesses. `refuse()` below already names the place to look.
        failed = false,
    )
    val installLabel = InstallMoment.labelFor(installFacts)
    val installLabelText = when (installLabel) {
        InstallLabel.INSTALL -> stringResource(R.string.install_label_install)
        InstallLabel.INSTALLING -> stringResource(R.string.install_label_installing)
        InstallLabel.OPEN -> stringResource(R.string.install_label_open)
        InstallLabel.UPDATE -> stringResource(R.string.install_label_update)
        InstallLabel.RETRY -> stringResource(R.string.install_label_retry)
    }
    val refuse: () -> Unit = {
        Toast.makeText(context, setupRefusal ?: NOT_READY, Toast.LENGTH_LONG).show()
        onNavigateToTerminal()
    }
    PackageItemCard(
        item = item,
        guideAnchorId = guideAnchorId,
        isInstalled = isInstalled,
        installLabelText = installLabelText,
        installInFlight = installLabel == InstallLabel.INSTALLING,
        crossfadeSpec = motion.floatOrSnap(CodecMotion.crossfadeSpec),
        setupRefusal = setupRefusal?.takeIf { gated && !isInstalled },
        packageActionsBlocked = gated,
        onViewSetup = onNavigateToTerminal,
        onInstall = {
            if (gated) {
                refuse()
            } else {
                // The moment the user's own install starts — the only state in
                // which the row polls the disk for the finish below.
                installRequested = true
                celebrated = false
                Toast.makeText(context, "Installing ${item.name}…", Toast.LENGTH_SHORT).show()
                terminalViewModel.sendCommand(item.installCommand)
                onNavigateToTerminal()
            }
        },
        onRun = {
            if (runBlocked) {
                refuse()
            } else {
                Toast.makeText(context, "Launching ${item.name}…", Toast.LENGTH_SHORT).show()
                terminalViewModel.sendCommand(item.runCommand)
                onNavigateToTerminal()
            }
        },
        onUninstall = {
            if (gated) {
                refuse()
            } else {
                Toast.makeText(context, "Uninstalling ${item.name}…", Toast.LENGTH_SHORT).show()
                terminalViewModel.sendCommand("pkg uninstall -y ${item.id}")
                onNavigateToTerminal()
            }
        },
        onCopyCommand = { cmd ->
            copyToClipboard(context, cmd)
            Toast.makeText(context, "Copied: $cmd", Toast.LENGTH_SHORT).show()
        },
    )
}

/**
 * Phase 44.1 — the one place the Packages tab explains why nothing can be
 * installed yet. The sentence is [SetupGatePolicy]'s, i.e. the same one the
 * setup bar and the terminal show; the button takes the user to the setup
 * instead of firing a command into a prefix that has no `pkg`.
 */
@Composable
private fun SetupGateCard(
    message: String,
    onViewSetup: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CodecTokens.radius(Radius.M)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CodecTokens.space(Space.L), end = CodecTokens.space(Space.S), top = CodecTokens.space(Space.M), bottom = CodecTokens.space(Space.M)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION))
            )
            Spacer(modifier = Modifier.width(CodecTokens.space(Space.M)))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onViewSetup) {
                Text("VIEW SETUP")
            }
        }
    }
}

/**
 * Phase 33.2 — a hub section header. The language section is a plain label;
 * the "Unix tools" section carries a collapse/expand chevron.
 */
@Composable
private fun PackageSectionHeader(
    title: String,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
) {
    // Phase 51.4 — a press needs an edge to happen inside. A collapsible
    // header is a real surface, so it gets the phase's one containment rule
    // (radius + press state + the touch floor); a plain label keeps none of it
    // (`contained = collapsible`) and stays the flat line it was.
    PressableSurface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CodecTokens.radius(Radius.S)),
        containerColor = Color.Transparent,
        contentPadding = PaddingValues(vertical = CodecTokens.space(Space.S)),
        contained = collapsible,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (collapsible) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    action: QuickAction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clickable { onClick() }
            .clip(RoundedCornerShape(CodecTokens.radius(Radius.S))),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = CodecTokens.space(Space.M), vertical = CodecTokens.space(Space.S)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = action.title,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE))
            )
            Spacer(modifier = Modifier.width(CodecTokens.space(Space.S)))
            Column {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = action.subtitle,
                    // Phase 50.3 — the caption role (11sp) instead of a 10sp
                    // literal: one step up, same place in the hierarchy.
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun PackageItemCard(
    item: PackageItem,
    /** Phase 45.2 — the coach-mark anchor id, or null for an ordinary card. */
    guideAnchorId: String? = null,
    isInstalled: Boolean,
    /** Phase 51.3 — the word the primary action wears (InstallMoment). */
    installLabelText: String,
    /** Phase 51.3 — true while the user's own install is in flight. */
    installInFlight: Boolean = false,
    /** Phase 51.3 — the shared spec for the badge's state change (50.4). */
    crossfadeSpec: androidx.compose.animation.core.FiniteAnimationSpec<Float>,
    onInstall: () -> Unit,
    onRun: () -> Unit,
    onUninstall: () -> Unit,
    onCopyCommand: (String) -> Unit,
    setupRefusal: String? = null,
    packageActionsBlocked: Boolean = false,
    onViewSetup: () -> Unit = {}
) {
    // Phase 45.2 — the anchored card publishes its window rect while it is laid
    // out; the pure CoachMarkPlan decides whether a mark may use it.
    // Phase 45 round 4 — and it publishes the click of the button the card is
    // REALLY showing, so the tour's eighth beat is one tap that starts the
    // one-time download instead of a tap that only dismisses the box:
    //  - not installed → INSTALL (`onInstall`, which already carries the
    //    Phase 44.1 setup gate, so performing it from the overlay cannot
    //    bypass that);
    //  - not installed and the userland is not usable → the card shows VIEW
    //    SETUP, so that is the click published;
    //  - already installed → the card's buttons are RUN / UNINSTALL / REINSTALL
    //    and none of them is what the box is teaching, so NO click is published:
    //    the tap is left to the card (and still advances the tour). Publishing
    //    `onInstall` here would turn a tap on a highlighted card into a
    //    REINSTALL the user never asked for.
    val cardClick: (() -> Unit)? = when {
        isInstalled -> null
        setupRefusal != null -> onViewSetup
        else -> onInstall
    }
    val cardModifier = if (guideAnchorId != null) {
        Modifier.fillMaxWidth()
            .then(GuideAnchor.modifier(guideAnchorId, onClick = cardClick))
    } else {
        Modifier.fillMaxWidth()
    }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(CodecTokens.radius(Radius.M)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = CodecTokens.elevation(CodecTokens.Elevation.RAISED))
    ) {
        Column(modifier = Modifier.padding(CodecTokens.space(Space.L))) {
            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${item.category.title} · ${item.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // STATUS BADGE — Phase 51.3: the INSTALLED badge does not
                // appear, it arrives, on the shared crossfade spec (and snaps
                // when the platform's animations are off). A state change the
                // user waited minutes for deserves to be seen.
                Crossfade(
                    targetState = isInstalled,
                    animationSpec = crossfadeSpec,
                    label = "installBadge",
                ) { installed ->
                    if (installed) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(CodecTokens.radius(Radius.S)))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = CodecTokens.space(Space.S), vertical = CodecTokens.space(Space.XS))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Installed",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE))
                                )
                                Spacer(modifier = Modifier.width(CodecTokens.space(Space.XS)))
                                Text(
                                    text = if (item.isBuiltIn) "BUILT-IN" else "INSTALLED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(CodecTokens.radius(Radius.S)))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = CodecTokens.space(Space.S), vertical = CodecTokens.space(Space.XS))
                        ) {
                            Text(
                                text = "AVAILABLE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(CodecTokens.space(Space.S)))

            // DESCRIPTION
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(CodecTokens.space(Space.M)))

            // COMMAND SNIPPET BOX
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CodecTokens.radius(Radius.S)))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(CodecTokens.radius(Radius.S)))
                    .padding(horizontal = CodecTokens.space(Space.M), vertical = CodecTokens.space(Space.S))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$ ${item.installCommand}",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = CodecType.codeFamily,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onCopyCommand(item.installCommand) },
                        modifier = Modifier.size(CodecTokens.space(CodecTokens.MIN_TOUCH))
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Command",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(CodecTokens.space(Space.M)))

            // ACTIONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isInstalled) {
                    // Phase 44.1 — UNINSTALL/REINSTALL are `pkg` transactions:
                    // they disappear while the setup gate is up rather than
                    // failing with "pkg: not found". RUN stays: the binary is
                    // already in the prefix.
                    if (!item.isBuiltIn && !packageActionsBlocked) {
                        TextButton(onClick = onUninstall) {
                            Text("UNINSTALL", color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.width(CodecTokens.space(Space.S)))
                        OutlinedButton(onClick = onInstall) {
                            Text("REINSTALL")
                        }
                        Spacer(modifier = Modifier.width(CodecTokens.space(Space.S)))
                    }
                    Button(onClick = onRun) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE))
                        )
                        Spacer(modifier = Modifier.width(CodecTokens.space(Space.XS)))
                        Text(if (item.isBuiltIn) "RUN CC" else "RUN")
                    }
                } else if (setupRefusal != null) {
                    // Phase 44.1 — no `pkg` command is fired, silently queued
                    // or pretended to work: the row says where the setup is.
                    OutlinedButton(onClick = onViewSetup) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE))
                        )
                        Spacer(modifier = Modifier.width(CodecTokens.space(Space.XS)))
                        Text("VIEW SETUP")
                    }
                } else {
                    Button(onClick = onInstall, enabled = !installInFlight) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE))
                        )
                        Spacer(modifier = Modifier.width(CodecTokens.space(Space.XS)))
                        Text(installLabelText)
                    }
                }
            }
        }
    }
}

private fun checkIsInstalled(context: Context, item: PackageItem): Boolean {
    if (item.isBuiltIn) {
        return EmbeddedCompiler.isAvailable(context)
    }
    val filesDir = context.filesDir
    val binDir = File(File(filesDir, "usr"), "bin")
    val binaryFile = File(binDir, item.binary)
    if (binaryFile.exists() && binaryFile.canExecute()) {
        return true
    }
    val dpkgStatus = File(File(filesDir, "usr"), "var/lib/dpkg/status")
    if (dpkgStatus.exists()) {
        try {
            val text = dpkgStatus.readText()
            if (text.contains("Package: ${item.id}\n") && text.contains("Status: install ok installed")) {
                return true
            }
        } catch (_: Exception) {
        }
    }
    return false
}

/** Fallback for a refusal whose sentence is somehow missing (never expected). */
private const val NOT_READY = "CodeC's Linux tools aren't ready yet."

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Command", text)
    clipboard?.setPrimaryClip(clip)
}
