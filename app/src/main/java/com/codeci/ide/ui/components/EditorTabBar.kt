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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import com.codeci.ide.R
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Space
import com.codeci.ide.ui.theme.CodecTokens.Radius

/** View model of one editor tab for the tab strip. */
data class EditorTabUi(
    val path: String,
    val name: String,
    val isDirty: Boolean
)

/**
 * Phase 68.1 — compact Spck-style tab strip: 40dp high, 8dp inner padding,
 * italic active, subtle underline, fixed dirty dot. Close menu only (6 rows);
 * sort lives in its own door (sort icon) for Spck parity, shortest.
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
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return
    var menuPath by remember { mutableStateOf<String?>(null) }
    val underlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val dirtyColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CodecTokens.space(Space.XXL + Space.S))
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = CodecTokens.space(Space.XXS)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val active = tab.path == activePath
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
                                    topLeft = Offset(0f, size.height - CodecTokens.space(Space.XXS).toPx()),
                                    size = Size(size.width, CodecTokens.space(Space.XXS).toPx())
                                )
                            }
                        }
                        .combinedClickable(
                            onClick = { onSelect(tab.path) },
                            onLongClick = { menuPath = tab.path }
                        )
                        .padding(horizontal = CodecTokens.space(Space.S)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileIconView(
                        name = tab.name,
                        isDirectory = false,
                        modifier = Modifier.padding(end = CodecTokens.space(Space.XS)).size(CodecTokens.space(Space.M)),
                        tint = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = tab.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontStyle = if (active) FontStyle.Italic else FontStyle.Normal,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (tab.isDirty) {
                        Spacer(Modifier.width(CodecTokens.space(Space.XS)))
                        Box(
                            modifier = Modifier
                                .size(CodecTokens.space(Space.XS + Space.XXS))
                                .background(dirtyColor, CircleShape)
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuPath == tab.path,
                    onDismissRequest = { menuPath = null }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_quick_close)) },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        onClick = {
                            menuPath = null
                            onClose(tab.path)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_close_others)) },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        onClick = {
                            menuPath = null
                            onCloseOthers(tab.path)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_close_unmodified)) },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        onClick = {
                            menuPath = null
                            onCloseUnmodified()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_close_all)) },
                        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
                        onClick = {
                            menuPath = null
                            onCloseAll()
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_copy_path)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuPath = null
                            onCopyPath(tab.path)
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tab_hide)) },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                        onClick = {
                            menuPath = null
                            onHideTabs()
                        }
                    )
                }
            }
        }
    }
}

internal object TabSortLabels {
    fun of(sort: com.codeci.ide.ui.editor.TabSort): Int = when (sort) {
        com.codeci.ide.ui.editor.TabSort.QUEUE -> com.codeci.ide.R.string.tab_sort_queue
        com.codeci.ide.ui.editor.TabSort.NAME -> com.codeci.ide.R.string.tab_sort_name
        com.codeci.ide.ui.editor.TabSort.EXTENSION -> com.codeci.ide.R.string.tab_sort_extension
        com.codeci.ide.ui.editor.TabSort.PATH -> com.codeci.ide.R.string.tab_sort_path
    }
}

@Composable
fun TabRowRevealStrip(
    onReveal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onReveal)
            .height(CodecTokens.space(Space.XXL + Space.S))
            .padding(horizontal = CodecTokens.space(Space.S)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = CodecTokens.space(Space.XL + Space.XS), height = CodecTokens.space(Space.XXS))
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(CodecTokens.radius(Radius.XS))
                    )
            )
            Spacer(Modifier.width(CodecTokens.space(Space.XS + Space.XXS)))
            Text(
                text = stringResource(R.string.tab_show),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
