package com.codeci.ide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
 * Phase 9 tab strip, re-skinned for Phase 16 Spck style: the active tab gets
 * a bold label + accent underline drawn behind the row (no chip fill, which
 * survives the scrollable Row's infinite width constraints), dirty tabs carry
 * a ●, every tab past the first keeps its ✕, and a long-press opens
 * Close others / Close all / Copy path — Spck's tab menu, kept compact.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = tab.path == activePath
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .drawBehind {
                            if (active) {
                                val inset = 8.dp.toPx()
                                drawRect(
                                    color = underlineColor,
                                    topLeft = Offset(inset, size.height - 2.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(
                                        (size.width - inset * 2f).coerceAtLeast(0f),
                                        2.dp.toPx()
                                    )
                                )
                            }
                        }
                        .combinedClickable(
                            onClick = { onSelect(tab.path) },
                            onLongClick = { menuPath = tab.path }
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tab.name + if (tab.isDirty) " ●" else "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (tabs.size > 1) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onClose(tab.path) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close_tab),
                                modifier = Modifier.size(14.dp),
                                tint = if (active) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = menuPath == tab.path,
                    onDismissRequest = { menuPath = null }
                ) {
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
