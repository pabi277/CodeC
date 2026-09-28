package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.R
import com.codeci.ide.ui.services.*
import com.codeci.ide.ui.theme.CodecType

/**
 * The preview's diagnostics panel — Phase 61's two-chip strip, grown on
 * 2026-09-28 into the five tabs the owner's own SPCK shots show
 * (**Console · Elements · Network · Resources · Settings**), with a console
 * that takes commands, a Network list, a Resources list, an Elements tree and
 * a Settings page.
 *
 * Phone pass, later the same day (owner: *"make everything from this phase
 * phone friendly"*). Measured on a 411 × 656 dp page area at the old 240 dp
 * default, the Console tab spent 209 dp on its own rows — a 48 dp drag handle,
 * the 49 dp strip, the 48 dp filter row and a 64 dp outlined text field — and
 * showed about one line of output; with the keyboard up the Cancel · Execute
 * row pushed the output to nothing. Now: the strip itself is the drag handle
 * (a 4 dp pill above it), the command line is one 48 dp row with Cancel ·
 * Execute inside it, console lines are coloured by level and follow the newest
 * entry, Network and Resources are two-line rows instead of a six-column table,
 * and the long notes collapse to one line once there are rows. The panel's
 * default height is half the page area ([PreviewToolsPolicy.DEFAULT_FRACTION]).
 *
 * Everything here is presentation. The scripts (console command, DOM walk,
 * resource read, viewport ask) are built by the pure policies and run by
 * [PreviewWebView]; the panel receives plain data and reports plain callbacks,
 * which is why a Robolectric test can drive it without a WebView.
 *
 * Bounded by the caller's available height, including IME and other chrome.
 */
@Composable
fun PreviewToolsPanel(
    console: List<PreviewConsoleEntry>,
    network: List<PreviewRequest>,
    resources: List<PreviewResource>,
    domTree: List<PreviewDomNode>,
    details: PreviewNodeDetails?,
    selectedNode: Int,
    highlighted: Int,
    viewport: PreviewViewport,
    fitToPhone: Boolean,
    zoomPercent: Int,
    resolution: PreviewResolution,
    tab: PreviewToolTab,
    levels: Set<PreviewLevel>,
    height: Float,
    availableHeight: Float,
    onTab: (PreviewToolTab) -> Unit,
    onLevels: (Set<PreviewLevel>) -> Unit,
    onResize: (Float) -> Unit,
    onHeight: (Float) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    onCommand: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onSelectNode: (Int) -> Unit,
    onToggleHighlight: (Int) -> Unit,
    onCopyHtml: (Int) -> Unit,
    onRefreshElements: () -> Unit,
    onRefreshResources: () -> Unit,
    onZoom: () -> Unit,
    onResolution: () -> Unit,
    onFitToPhone: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val focusManager = LocalFocusManager.current
    val resizeLabel = stringResource(R.string.preview_resize)
    val maximum = PreviewToolsPolicy.panelHeight(Float.MAX_VALUE, availableHeight)
    val minimum = PreviewToolsPolicy.panelHeight(0f, availableHeight)
    Column(
        modifier
            .height(height.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // The strip is the handle: dragging anywhere on it (or the pill above
        // it) resizes the panel, a tap still switches tabs. A separate 48 dp
        // handle row cost the console a fifth of its default height on a phone.
        Column(
            Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = resizeLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(height, minimum..maximum)
                    setProgress { onHeight(it); true }
                }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> onResize(-delta / density) }
                ),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                )
            }
            // Closing the panel, or leaving the console tab, disposes the
            // command line — the only focusable field here. Releasing focus in
            // the same tap is what takes the keyboard down with it: 2026-09-28,
            // the screen reserves the keyboard's inset only while this panel is
            // open, so a keyboard left up over a closed panel would draw the
            // page underneath it.
            PreviewTabStrip(
                tab = tab,
                onTab = { focusManager.clearFocus(force = true); onTab(it) },
                onClose = { focusManager.clearFocus(force = true); onClose() },
            )
        }
        when (tab) {
            PreviewToolTab.CONSOLE -> PreviewConsoleTab(
                console = console,
                levels = levels,
                onLevels = onLevels,
                onClear = onClear,
                onCopyText = onCopyText,
                onCommand = onCommand,
                modifier = Modifier.weight(1f),
            )
            PreviewToolTab.ELEMENTS -> PreviewElementsTab(
                tree = domTree,
                details = details,
                selected = selectedNode,
                highlighted = highlighted,
                onSelect = onSelectNode,
                onToggleHighlight = onToggleHighlight,
                onCopyText = onCopyText,
                onCopyHtml = onCopyHtml,
                onRefresh = onRefreshElements,
                modifier = Modifier.weight(1f),
            )
            PreviewToolTab.NETWORK -> PreviewNetworkTab(
                requests = network,
                onClear = onClear,
                modifier = Modifier.weight(1f),
            )
            PreviewToolTab.RESOURCES -> PreviewResourcesTab(
                resources = resources,
                onRefresh = onRefreshResources,
                onCopyText = onCopyText,
                modifier = Modifier.weight(1f),
            )
            PreviewToolTab.SETTINGS -> PreviewSettingsTab(
                viewport = viewport,
                fitToPhone = fitToPhone,
                zoomPercent = zoomPercent,
                resolution = resolution,
                onZoom = onZoom,
                onResolution = onResolution,
                onFitToPhone = onFitToPhone,
                onClearCache = onClearCache,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The strip the shots show: five tabs over one hairline, plus Close. Phone
 * pass: labels at labelMedium with 10 dp padding so all five fit a 411 dp
 * phone beside a 48 dp × (at the default font scale — larger scales scroll),
 * and the underline spans its own tab instead of a fixed 56 dp.
 */
@Composable
private fun PreviewTabStrip(tab: PreviewToolTab, onTab: (PreviewToolTab) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            PreviewToolTab.entries.forEach { choice ->
                val selected = tab == choice
                Column(
                    Modifier
                        .width(IntrinsicSize.Max)
                        .heightIn(min = 48.dp)
                        .clickable { onTab(choice) }
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(tabLabel(choice)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                    )
                }
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.preview_close))
        }
    }
    HorizontalDivider()
}

private fun tabLabel(tab: PreviewToolTab): Int = when (tab) {
    PreviewToolTab.CONSOLE -> R.string.preview_console
    PreviewToolTab.ELEMENTS -> R.string.preview_elements
    PreviewToolTab.NETWORK -> R.string.preview_network
    PreviewToolTab.RESOURCES -> R.string.preview_resources
    PreviewToolTab.SETTINGS -> R.string.preview_settings
}

/**
 * The filter row (All · Log · Info · Warning · Error), copy and clear. Chip
 * labels at labelMedium with 2 dp gaps: five chips and two 48 dp icons then
 * fit a 411 dp phone; larger font scales scroll the chips, the icons stay.
 */
@Composable
private fun PreviewConsoleFilters(
    levels: Set<PreviewLevel>,
    onLevels: (Set<PreviewLevel>) -> Unit,
    onClear: () -> Unit,
    onCopyText: (String) -> Unit,
    lines: List<String>,
) {
    val clearLabel = stringResource(R.string.preview_console_clear)
    val copyLabel = stringResource(R.string.preview_console_copy)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val all = levels.size == PreviewLevel.entries.size
            PreviewLevelChip(
                selected = all,
                label = stringResource(R.string.preview_level_all),
                onClick = { onLevels(if (all) emptySet() else PreviewLevel.entries.toSet()) },
            )
            PreviewLevel.entries.forEach { level ->
                PreviewLevelChip(
                    selected = level in levels,
                    label = stringResource(levelLabel(level)),
                    onClick = { onLevels(if (level in levels) levels - level else levels + level) },
                )
            }
        }
        IconButton(onClick = onClear) {
            Icon(SpckIcons.ClearCircle, contentDescription = clearLabel)
        }
        IconButton(onClick = { onCopyText(lines.joinToString("\n")) }) {
            Icon(SpckIcons.Copy, contentDescription = copyLabel)
        }
    }
}

@Composable
private fun PreviewLevelChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

private fun levelLabel(level: PreviewLevel): Int = when (level) {
    PreviewLevel.LOG -> R.string.preview_level_log
    PreviewLevel.INFO -> R.string.preview_level_info
    PreviewLevel.WARN -> R.string.preview_level_warn
    PreviewLevel.ERROR -> R.string.preview_level_error
}

@Composable
private fun PreviewConsoleTab(
    console: List<PreviewConsoleEntry>,
    levels: Set<PreviewLevel>,
    onLevels: (Set<PreviewLevel>) -> Unit,
    onClear: () -> Unit,
    onCopyText: (String) -> Unit,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = PreviewToolsPolicy.visible(console, levels)
    var input by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val execute = {
        val line = input.trim()
        if (line.isNotEmpty()) {
            onCommand(line)
            input = ""
        }
    }
    // A console follows its newest line — on a phone the list shows a handful
    // of lines, so a result that lands below the fold would look like no
    // result at all.
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.scrollToItem(visible.lastIndex)
    }
    Column(modifier) {
        PreviewConsoleFilters(
            levels = levels,
            onLevels = onLevels,
            onClear = onClear,
            onCopyText = onCopyText,
            lines = visible.map { it.message },
        )
        if (visible.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    stringResource(R.string.preview_console_empty),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).testTag("preview_console_lines")) {
                items(visible) { entry -> PreviewConsoleLine(entry) }
            }
        }
        PreviewCommandLine(
            input = input,
            focused = focused,
            onInputChange = { input = it.take(PreviewConsolePolicy.COMMAND_LIMIT) },
            onFocus = { focused = it },
            onCancel = {
                input = ""
                focusManager.clearFocus()
            },
            execute = execute,
        )
    }
}

/**
 * One console line, coloured by its level the way every browser console is:
 * errors in the theme's error colour on a faint error tint, warnings on the
 * tertiary tint, info in the primary colour, plain log in the surface's text.
 * A hairline separates entries so wrapped lines still read as one entry each.
 */
@Composable
private fun PreviewConsoleLine(entry: PreviewConsoleEntry) {
    val scheme = MaterialTheme.colorScheme
    val (color, tint) = when (entry.level) {
        PreviewLevel.ERROR -> scheme.error to scheme.errorContainer.copy(alpha = .35f)
        PreviewLevel.WARN -> scheme.tertiary to scheme.tertiaryContainer.copy(alpha = .35f)
        PreviewLevel.INFO -> scheme.primary to Color.Transparent
        PreviewLevel.LOG -> scheme.onSurface to Color.Transparent
    }
    Text(
        text = entry.message,
        fontFamily = CodecType.codeFamily,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .4f))
}

/**
 * The shots' command line as one 48 dp row: a prompt glyph, the field, and the
 * shots' Cancel · Execute *inside* the row while the caret is there (or a line
 * is pending) — never a second row, which on a phone was the row that pushed
 * the output out of a short panel. Enter is the keyboard's Send action.
 */
@Composable
private fun PreviewCommandLine(
    input: String,
    focused: Boolean,
    onInputChange: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onCancel: () -> Unit,
    execute: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    HorizontalDivider()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(scheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "›",
            fontFamily = CodecType.codeFamily,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocus(it.isFocused) },
            textStyle = TextStyle(
                fontFamily = CodecType.codeFamily,
                fontSize = 13.sp,
                color = scheme.onSurface,
            ),
            cursorBrush = SolidColor(scheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { execute() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (input.isEmpty()) {
                        Text(
                            text = stringResource(R.string.preview_console_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (focused || input.isNotBlank()) {
            TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.preview_console_cancel), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                onClick = execute,
                enabled = input.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(stringResource(R.string.preview_console_execute), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * A tab's note row: the whole note while the tab is empty (it is then the
 * explanation of the empty state), one ellipsised line once there are rows —
 * the Network note alone wrapped to some 90 dp on a phone, above a table that
 * then had ~30 dp left. Tap the line to read it in full again.
 */
@Composable
private fun PreviewNoteRow(note: String, hasRows: Boolean, trailing: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val full = !hasRows || expanded
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (full) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = hasRows) { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        trailing()
    }
}

/** A phone row: the file on the first line, what is known about it on the second. */
@Composable
private fun PreviewTwoLineRow(name: String, summary: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = name,
                fontFamily = CodecType.codeFamily,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing() else Spacer(Modifier.width(8.dp))
    }
    HorizontalDivider()
}

@Composable
private fun PreviewElementsTab(
    tree: List<PreviewDomNode>,
    details: PreviewNodeDetails?,
    selected: Int,
    highlighted: Int,
    onSelect: (Int) -> Unit,
    onToggleHighlight: (Int) -> Unit,
    onCopyText: (String) -> Unit,
    onCopyHtml: (Int) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedNode = tree.firstOrNull { it.index == selected }
    // A fresh selection opens its details (that is what the hint promises);
    // a tap on the details header folds them to one line so the tree gets the
    // panel back. While open, tree and details split the tab evenly.
    var detailsOpen by remember { mutableStateOf(true) }
    LaunchedEffect(selected) { detailsOpen = true }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.preview_elements_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.preview_elements_refresh),
                )
            }
        }
        if (tree.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    stringResource(R.string.preview_elements_empty),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(tree) { node ->
                    val isSelected = node.index == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(node.index) }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                                else Color.Transparent
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width((node.depth * 10).dp))
                        Text(
                            text = PreviewInspectorPolicy.selector(node),
                            fontFamily = CodecType.codeFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (highlighted == node.index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (node.text.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = node.text,
                                fontFamily = CodecType.codeFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (details != null && selected >= 0) {
            HorizontalDivider()
            PreviewElementDetails(
                details = details,
                open = detailsOpen,
                highlightedHere = highlighted == selected,
                onToggleOpen = { detailsOpen = !detailsOpen },
                onToggleHighlight = { onToggleHighlight(selected) },
                onCopySelector = { selectedNode?.let { onCopyText(PreviewInspectorPolicy.selector(it)) } },
                onCopyHtml = { onCopyHtml(selected) },
                modifier = if (detailsOpen) Modifier.weight(1f) else Modifier,
            )
        }
    }
}

/**
 * The selected element on a phone: one 48 dp header row — tag and box, the
 * highlight eye, a ⋮ with Copy selector / Copy HTML — and, while open, the
 * attributes and text under it. The three text buttons this replaced took a
 * whole scrolling row under a 168 dp block that swallowed the tree.
 */
@Composable
private fun PreviewElementDetails(
    details: PreviewNodeDetails,
    open: Boolean,
    highlightedHere: Boolean,
    onToggleOpen: () -> Unit,
    onToggleHighlight: () -> Unit,
    onCopySelector: () -> Unit,
    onCopyHtml: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggleOpen)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.preview_elements_details),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(details.tag)
                        if (details.box.isNotBlank()) append("  ").append(details.box)
                    },
                    fontFamily = CodecType.codeFamily,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleHighlight) {
                Icon(
                    if (highlightedHere) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(
                        if (highlightedHere) R.string.preview_elements_highlight_off
                        else R.string.preview_elements_highlight
                    ),
                    tint = if (highlightedHere) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preview_elements_copy_selector)) },
                        onClick = { menuOpen = false; onCopySelector() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.preview_elements_copy_html)) },
                        onClick = { menuOpen = false; onCopyHtml() },
                    )
                }
            }
        }
        if (open) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
            ) {
                details.attributes.forEach { attribute ->
                    Text(
                        text = "${attribute.name}=\"${attribute.value}\"",
                        fontFamily = CodecType.codeFamily,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
                    )
                }
                if (details.text.isNotBlank()) {
                    Text(
                        text = details.text,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewNetworkTab(
    requests: List<PreviewRequest>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        PreviewNoteRow(
            note = stringResource(R.string.preview_network_limits),
            hasRows = requests.isNotEmpty(),
        ) {
            IconButton(onClick = onClear) {
                Icon(
                    SpckIcons.ClearCircle,
                    contentDescription = stringResource(R.string.preview_network_clear),
                )
            }
        }
        if (requests.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    stringResource(R.string.preview_network_empty),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(requests) { request ->
                    PreviewTwoLineRow(
                        name = PreviewToolsPolicy.nameLabel(request.address),
                        summary = PreviewToolsPolicy.requestSummary(request),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewResourcesTab(
    resources: List<PreviewResource>,
    onRefresh: () -> Unit,
    onCopyText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copyLabel = stringResource(R.string.preview_resources_copy)
    Column(modifier) {
        PreviewNoteRow(
            note = stringResource(R.string.preview_resources_note),
            hasRows = resources.isNotEmpty(),
        ) {
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.preview_resources_refresh),
                )
            }
        }
        if (resources.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    stringResource(R.string.preview_resources_empty),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(resources) { resource ->
                    PreviewTwoLineRow(
                        name = PreviewToolsPolicy.nameLabel(resource.address),
                        summary = PreviewToolsPolicy.resourceSummary(resource),
                    ) {
                        IconButton(onClick = { onCopyText(resource.address) }) {
                            Icon(SpckIcons.Copy, contentDescription = copyLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewSettingsTab(
    viewport: PreviewViewport,
    fitToPhone: Boolean,
    zoomPercent: Int,
    resolution: PreviewResolution,
    onZoom: () -> Unit,
    onResolution: () -> Unit,
    onFitToPhone: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolutionLabel = if (resolution == PreviewResolution.DEVICE) {
        stringResource(R.string.preview_device)
    } else {
        "${resolution.widthDp} × ${resolution.heightDp} dp"
    }
    val viewportNote = when (viewport) {
        PreviewViewport.ADDED -> stringResource(R.string.preview_settings_viewport_added)
        PreviewViewport.AUTHORED -> stringResource(R.string.preview_settings_viewport_authored)
        PreviewViewport.DISABLED -> stringResource(R.string.preview_settings_viewport_disabled)
        PreviewViewport.UNKNOWN -> stringResource(R.string.preview_settings_viewport_unknown)
    }
    Column(modifier.verticalScroll(rememberScrollState())) {
        PreviewSettingRow(
            label = stringResource(R.string.preview_zoom),
            value = "$zoomPercent%",
            onClick = onZoom,
        )
        HorizontalDivider()
        PreviewSettingRow(
            label = stringResource(R.string.preview_resolution),
            value = resolutionLabel,
            onClick = onResolution,
        )
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    stringResource(R.string.preview_settings_fit),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    viewportNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = fitToPhone, onCheckedChange = onFitToPhone)
        }
        HorizontalDivider()
        // The cache note sits under its label, not beside it: a two-line value
        // at the trailing edge fought the label for a phone's width.
        PreviewSettingRow(
            label = stringResource(R.string.preview_settings_clear_cache),
            note = stringResource(R.string.preview_settings_clear_cache_note),
            onClick = onClearCache,
        )
        HorizontalDivider()
    }
}

@Composable
private fun PreviewSettingRow(
    label: String,
    onClick: () -> Unit,
    value: String? = null,
    note: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Text(
                value,
                fontFamily = CodecType.codeFamily,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
fun PreviewZoomDialog(onDismiss: () -> Unit, onZoom: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preview_zoom)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.preview_zoom_note))
                PreviewToolsPolicy.zoomPresets.forEach { percent ->
                    TextButton(onClick = { onZoom(percent); onDismiss() }) { Text("$percent%") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.preview_close)) } },
    )
}

@Composable
fun PreviewResolutionDialog(
    selected: PreviewResolution,
    onDismiss: () -> Unit,
    onResolution: (PreviewResolution) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.preview_resolution)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.preview_resolution_note))
                PreviewResolution.entries.forEach { resolution ->
                    val label = if (resolution == PreviewResolution.DEVICE)
                        stringResource(R.string.preview_device) else
                        "${resolution.widthDp} × ${resolution.heightDp} dp"
                    TextButton(onClick = { onResolution(resolution); onDismiss() }) {
                        Text(if (selected == resolution) "✓ $label" else label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.preview_close)) } },
    )
}
