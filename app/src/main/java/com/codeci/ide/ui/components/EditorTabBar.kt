package com.codeci.ide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeci.ide.R

/** View model of one editor tab for the tab strip. */
data class EditorTabUi(
    val path: String,
    val name: String,
    val isDirty: Boolean
)

/**
 * Phase 16 (mockup-exact) tab strip: horizontally scrollable file tabs in the
 * top bar title slot — plain bold/regular labels, the active one with a 3dp
 * accent underline on the bar's bottom edge and a ● dirty dot. No per-tab ✕
 * (the mockups show none): long-press offers Close tab / Close others /
 * Close all / Copy path, and the top-bar overflow keeps "Close file".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorTabBar(
    tabs: List<EditorTabUi>,
    activePath: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onCloseOthers: (String) -> Unit = {},
    onCloseAll: () -> Unit = {},
    onCopyPath: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    var menuPath by remember { mutableStateOf<String?>(null) }
    val underlineColor = MaterialTheme.colorScheme.primary
    // fillMaxHeight stretches the strip to the app-bar's full height so the
    // 3dp underline lands on the bar's bottom edge (mockup-exact), while the
    // tab labels stay vertically centered.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = tab.path == activePath
            // Full-height tab box: the label stays centered while the 3dp
            // accent underline is anchored to the app bar's bottom edge.
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .drawBehind {
                            if (active) {
                                drawRect(
                                    color = underlineColor,
                                    topLeft = Offset(0f, size.height - 3.dp.toPx()),
                                    size = Size(size.width, 3.dp.toPx())
                                )
                            }
                        }
                        .combinedClickable(
                            onClick = { onSelect(tab.path) },
                            onLongClick = { menuPath = tab.path }
                        )
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tab.name + if (tab.isDirty) " ●" else "",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DropdownMenu(
                    expanded = menuPath == tab.path,
                    onDismissRequest = { menuPath = null }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.close_tab)) },
                        onClick = {
                            menuPath = null
                            onClose(tab.path)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_close_others)) },
                        onClick = {
                            menuPath = null
                            onCloseOthers(tab.path)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_close_all)) },
                        onClick = {
                            menuPath = null
                            onCloseAll()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_copy_path)) },
                        onClick = {
                            menuPath = null
                            onCopyPath(tab.path)
                        }
                    )
                }
            }
        }
    }
}
