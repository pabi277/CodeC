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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
 * that takes commands, a Network table, a Resources list, an Elements tree and
 * a Settings page.
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
    val resizeLabel = stringResource(R.string.preview_resize)
    val maximum = PreviewToolsPolicy.panelHeight(Float.MAX_VALUE, availableHeight)
    val minimum = PreviewToolsPolicy.panelHeight(0f, availableHeight)
    Column(
        modifier
            .height(height.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // Short panels (the keyboard is up) give their rows to the list and the
        // input line first: the drag handle is the first thing to go, never the
        // line the user is typing into.
        if (height >= 200f) {
            Box(
                Modifier.fillMaxWidth().height(48.dp)
                    .semantics {
                        contentDescription = resizeLabel
                        progressBarRangeInfo = ProgressBarRangeInfo(height, minimum..maximum)
                        setProgress { onHeight(it); true }
                    }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta -> onResize(-delta / density) }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                HorizontalDivider(Modifier.width(44.dp), thickness = 4.dp)
            }
        }
        PreviewTabStrip(tab = tab, onTab = onTab, onClose = onClose)
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

/** The strip the shots show: five equal tabs over one hairline, plus Close. */
@Composable
private fun PreviewTabStrip(tab: PreviewToolTab, onTab: (PreviewToolTab) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            PreviewToolTab.entries.forEach { choice ->
                val selected = tab == choice
                Column(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable { onTab(choice) }
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(tabLabel(choice)),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .width(56.dp)
                            .height(2.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                    )
                }
            }
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.preview_close)) }
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

/** The filter row (All · Log · Info · Warning · Error), copy and clear. */
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
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            val all = levels.size == PreviewLevel.entries.size
            FilterChip(
                selected = all,
                onClick = {
                    onLevels(
                        if (all) emptySet() else PreviewLevel.entries.toSet()
                    )
                },
                label = { Text(stringResource(R.string.preview_level_all)) },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            PreviewLevel.entries.forEach { level ->
                FilterChip(
                    selected = level in levels,
                    onClick = {
                        onLevels(if (level in levels) levels - level else levels + level)
                    },
                    label = { Text(stringResource(levelLabel(level))) },
                    modifier = Modifier.padding(horizontal = 4.dp),
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
    val execute = {
        val line = input.trim()
        if (line.isNotEmpty()) {
            onCommand(line)
            input = ""
        }
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
            Text(
                stringResource(R.string.preview_console_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(visible) { entry ->
                    Text(
                        text = entry.message,
                        fontFamily = CodecType.codeFamily,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        // The shots' Cancel · Execute bar: it appears with the keyboard, so the
        // console never spends a permanent row on two buttons nobody can press
        // while the caret is elsewhere.
        if (focused) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        input = ""
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.preview_console_cancel)) }
                TextButton(
                    onClick = { execute() },
                    enabled = input.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.preview_console_execute)) }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.take(PreviewConsolePolicy.COMMAND_LIMIT) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(
                fontFamily = CodecType.codeFamily,
                fontSize = 13.sp,
            ),
            placeholder = { Text(stringResource(R.string.preview_console_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { execute() }),
        )
    }
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
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.preview_elements_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.preview_elements_refresh),
                )
            }
        }
        if (tree.isEmpty()) {
            Text(
                stringResource(R.string.preview_elements_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
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
                            .padding(horizontal = 8.dp),
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
            Column(Modifier.fillMaxWidth().heightIn(max = 168.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.preview_elements_details),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                )
                Text(
                    text = buildString {
                        append(details.tag)
                        if (details.box.isNotBlank()) append("  ").append(details.box)
                    },
                    fontFamily = CodecType.codeFamily,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                details.attributes.forEach { attribute ->
                    Text(
                        text = "${attribute.name}=\"${attribute.value}\"",
                        fontFamily = CodecType.codeFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                if (details.text.isNotBlank()) {
                    Text(
                        text = details.text,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { onToggleHighlight(selected) }) {
                    Text(
                        stringResource(
                            if (highlighted == selected) R.string.preview_elements_highlight_off
                            else R.string.preview_elements_highlight
                        )
                    )
                }
                TextButton(onClick = {
                    selectedNode?.let { onCopyText(PreviewInspectorPolicy.selector(it)) }
                }) {
                    Text(stringResource(R.string.preview_elements_copy_selector))
                }
                TextButton(onClick = { onCopyHtml(selected) }) {
                    Text(stringResource(R.string.preview_elements_copy_html))
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.preview_network_limits),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            IconButton(onClick = onClear) {
                Icon(
                    SpckIcons.ClearCircle,
                    contentDescription = stringResource(R.string.preview_console_clear),
                )
            }
        }
        PreviewTableHeader()
        if (requests.isEmpty()) {
            Text(
                stringResource(R.string.preview_network_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(requests) { request ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PreviewCell(request.address, weight = 1f)
                        PreviewCell(request.method, width = 56.dp)
                        PreviewCell(PreviewToolsPolicy.statusLabel(null), width = 48.dp)
                        PreviewCell(PreviewToolsPolicy.typeLabel(request.type), width = 56.dp)
                        PreviewCell(PreviewToolsPolicy.sizeLabel(request.size), width = 64.dp)
                        PreviewCell(PreviewToolsPolicy.timeLabel(request.time), width = 64.dp)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PreviewTableHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        PreviewHeaderCell(stringResource(R.string.preview_network_name), weight = 1f)
        PreviewHeaderCell(stringResource(R.string.preview_network_method), width = 56.dp)
        PreviewHeaderCell(stringResource(R.string.preview_network_status), width = 48.dp)
        PreviewHeaderCell(stringResource(R.string.preview_network_type), width = 56.dp)
        PreviewHeaderCell(stringResource(R.string.preview_network_size), width = 64.dp)
        PreviewHeaderCell(stringResource(R.string.preview_network_time), width = 64.dp)
    }
    HorizontalDivider()
}

@Composable
private fun RowScope.PreviewHeaderCell(text: String, width: androidx.compose.ui.unit.Dp? = null, weight: Float? = null) {
    val modifier = when {
        weight != null -> Modifier.weight(weight)
        width != null -> Modifier.width(width)
        else -> Modifier
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun RowScope.PreviewCell(text: String, width: androidx.compose.ui.unit.Dp? = null, weight: Float? = null) {
    val modifier = when {
        weight != null -> Modifier.weight(weight)
        width != null -> Modifier.width(width)
        else -> Modifier
    }
    Text(
        text = text,
        fontFamily = CodecType.codeFamily,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.preview_resources_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.preview_resources_refresh),
                )
            }
        }
        if (resources.isEmpty()) {
            Text(
                stringResource(R.string.preview_resources_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(resources) { resource ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = resource.type,
                            fontFamily = CodecType.codeFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.width(56.dp),
                        )
                        Text(
                            text = resource.address,
                            fontFamily = CodecType.codeFamily,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = PreviewToolsPolicy.sizeLabel(resource.size),
                            fontFamily = CodecType.codeFamily,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.width(64.dp),
                        )
                        Text(
                            text = PreviewToolsPolicy.timeLabel(resource.time),
                            fontFamily = CodecType.codeFamily,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.width(56.dp),
                        )
                        IconButton(onClick = { onCopyText(resource.address) }) {
                            Icon(SpckIcons.Copy, contentDescription = copyLabel)
                        }
                    }
                    HorizontalDivider()
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
            Column(Modifier.weight(1f)) {
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
        PreviewSettingRow(
            label = stringResource(R.string.preview_settings_clear_cache),
            value = stringResource(R.string.preview_settings_clear_cache_note),
            onClick = onClearCache,
        )
        HorizontalDivider()
    }
}

@Composable
private fun PreviewSettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            fontFamily = CodecType.codeFamily,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
