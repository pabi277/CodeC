package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import com.codeci.ide.R
import com.codeci.ide.ui.services.*
import com.codeci.ide.ui.theme.CodecType

/** Bounded by the caller's available height, including IME and other chrome. */
@Composable
fun PreviewToolsPanel(
    console: List<PreviewConsoleEntry>,
    network: List<PreviewRequest>,
    tab: PreviewToolTab,
    levels: Set<PreviewLevel>,
    height: Float,
    availableHeight: Float,
    onTab: (PreviewToolTab) -> Unit,
    onLevel: (PreviewLevel) -> Unit,
    onResize: (Float) -> Unit,
    onHeight: (Float) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
    val resizeLabel = stringResource(R.string.preview_resize)
    val maximum = PreviewToolsPolicy.panelHeight(Float.MAX_VALUE, availableHeight)
    val minimum = PreviewToolsPolicy.panelHeight(0f, availableHeight)
    val filtered = PreviewToolsPolicy.visible(console, levels)
    val lines = if (tab == PreviewToolTab.CONSOLE) filtered.map {
        "${it.level.name.lowercase()}${if (it.line > 0) " [${it.line}]" else ""}: ${it.message}"
    } else network.map {
        "${it.method} ${if (it.mainFrame) "[document]" else "[resource]"} ${it.address}"
    }
    // One lazy scroll owner: controls remain reachable on short screens,
    // and 200 potentially long messages do not all get composed off-screen.
    LazyColumn(modifier.height(height.dp).background(MaterialTheme.colorScheme.surfaceContainer)) {
        item {
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
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                PreviewToolTab.entries.forEach { choice ->
                    FilterChip(
                        selected = tab == choice,
                        onClick = { onTab(choice) },
                        label = { Text(stringResource(if (choice == PreviewToolTab.CONSOLE)
                            R.string.preview_console else R.string.preview_network)) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                TextButton(onClick = onClear) { Text(stringResource(R.string.preview_clear)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.preview_close)) }
            }
        }
        item {
            if (tab == PreviewToolTab.CONSOLE) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    PreviewLevel.entries.forEach { level ->
                        FilterChip(
                            selected = level in levels,
                            onClick = { onLevel(level) },
                            label = { Text(level.name.lowercase()) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            } else {
                Text(stringResource(R.string.preview_network_limits),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
        if (lines.isEmpty()) {
            item {
                Text(stringResource(if (tab == PreviewToolTab.CONSOLE)
                    R.string.preview_console_empty else R.string.preview_network_empty),
                    modifier = Modifier.padding(12.dp))
            }
        } else {
            items(lines) { line ->
                Text(line, fontFamily = CodecType.codeFamily,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
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
