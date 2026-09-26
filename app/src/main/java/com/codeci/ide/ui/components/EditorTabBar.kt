package com.codeci.ide.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeci.ide.R
import com.codeci.ide.ui.components.FileIconView
import com.codeci.ide.ui.editor.TabSort
import com.codeci.ide.ui.editor.TabSortPolicy

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
 * (the mockups show none): long-press offers the tab menu, and the top-bar
 * overflow keeps "Close file".
 *
 * Phase 60 grew that menu to the spec's §2 list: the close family gains
 * *Close unmodified*, *Hide tabs* folds the row into [TabRowRevealStrip], and
 * the three sorts ([TabSort]) close the list behind their own divider. The
 * sorts are one-shot re-orders — the menu marks no choice, because after the
 * tap the order *is* the choice, and a tick would claim a mode that no longer
 * exists the moment a new file opens.
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
    onCloseUnmodified: () -> Unit = {},
    onHideTabs: () -> Unit = {},
    onSort: (TabSort) -> Unit = {},
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
                    FileIconView(
                        name = tab.name,
                        isDirectory = false,
                        modifier = Modifier.padding(end = 6.dp).size(16.dp),
                        tint = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    // Phase 60 — the spec's own row: close every tab that has
                    // nothing unsaved. The law lives in `TabClosePolicy`; this
                    // row is only its door.
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_close_unmodified)) },
                        onClick = {
                            menuPath = null
                            onCloseUnmodified()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_copy_path)) },
                        onClick = {
                            menuPath = null
                            onCopyPath(tab.path)
                        }
                    )
                    // Phase 60 — the view rows are a group of their own: what
                    // the row looks like, then how it is ordered.
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_hide)) },
                        onClick = {
                            menuPath = null
                            onHideTabs()
                        }
                    )
                    HorizontalDivider()
                    TabSortPolicy.MENU_ORDER.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(stringResource(TabSortLabels.of(sort))) },
                            onClick = {
                                menuPath = null
                                onSort(sort)
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Phase 60 — the tab row, folded away: the same reveal idiom Phase 32.1 gave
 * the bottom bar (a thin pill and one word), for the same reason. The row is
 * not gone, it is parked; a tap anywhere on this strip brings it back, and
 * bringing it back also un-parks the bottom bar, because both are one flag.
 *
 * The editor-menu cell at the row's right edge is deliberately NOT part of the
 * fold: undo, save, format and the rest live in that cell's list, and hiding
 * the tabs must never take the editor's actions with it.
 */
@Composable
fun TabRowRevealStrip(
    onReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onReveal)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.tab_show),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Phase 60 — [TabSort]'s label, in the menu's own terms. A `when` over every
 * entry rather than a lookup table, so adding a sort without a label is a
 * compile error here instead of a blank row on a device (`TabMenuWiringTest`
 * holds the other half: the menu walks [TabSortPolicy.MENU_ORDER]).
 */
internal object TabSortLabels {

    fun of(sort: TabSort): Int = when (sort) {
        TabSort.NAME -> R.string.tab_sort_name
        TabSort.EXTENSION -> R.string.tab_sort_extension
        TabSort.PATH -> R.string.tab_sort_path
    }
}
