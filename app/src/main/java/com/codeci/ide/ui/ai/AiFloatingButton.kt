package com.codeci.ide.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.theme.CodecTokens
import kotlin.math.roundToInt

/**
 * Phase 77.1 — the draggable AI button. Drawn inside the editor's **code
 * area** only (the caller places it in that box), so the tab strip, the
 * coding row, the keyboard and the bottom bar can never be under it.
 *
 * Only placement and gestures live here; whether it shows at all is
 * [AiBubblePolicy.visible]'s decision, made by the caller, and where it rests
 * is [AiBubblePolicy]'s. One tap → [onTap] (the caller opens the sheet);
 * press-and-hold → Hide / Move to other side; a drag moves it live and, on
 * release, snaps to the nearer side ([onMoved]).
 *
 * No pulse, no tooltip, no animation (the no-nag law). A small dot shows when
 * code is selected — a fact, not a prompt.
 */
@Composable
fun AiFloatingButton(
    position: AiBubblePosition,
    hasSelection: Boolean,
    onTap: () -> Unit,
    onHide: () -> Unit,
    onMoved: (AiBubblePosition) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        val touchPx = with(density) { AiBubblePolicy.TOUCH_DP.dp.toPx() }
        val marginPx = with(density) { AiBubblePolicy.EDGE_MARGIN_DP.dp.toPx() }
        val rest = AiBubblePolicy.offsetPx(position, areaW, areaH, null, touchPx, marginPx)

        // Top-left while a finger is dragging it; null at rest.
        var dragging by remember { mutableStateOf<Offset?>(null) }
        var menuOpen by remember { mutableStateOf(false) }
        val shown = dragging ?: Offset(rest.first, rest.second)
        val description = if (hasSelection) AiCopy.BUBBLE_SELECTION_DESCRIPTION else AiCopy.BUBBLE_DESCRIPTION

        Box(
            modifier = Modifier
                .offset { IntOffset(shown.x.roundToInt(), shown.y.roundToInt()) }
                .size(AiBubblePolicy.TOUCH_DP.dp)
                .pointerInput(position, areaW, areaH) {
                    detectDragGestures(
                        onDragStart = { dragging = Offset(rest.first, rest.second) },
                        onDrag = { change, amount ->
                            change.consume()
                            val now = dragging ?: Offset(rest.first, rest.second)
                            dragging = Offset(
                                (now.x + amount.x).coerceIn(0f, (areaW - touchPx).coerceAtLeast(0f)),
                                (now.y + amount.y).coerceIn(0f, (areaH - touchPx).coerceAtLeast(0f))
                            )
                        },
                        onDragEnd = {
                            dragging?.let { top ->
                                onMoved(AiBubblePolicy.snap(top.x + touchPx / 2f, top.y + touchPx / 2f, areaW, areaH))
                            }
                            dragging = null
                        },
                        onDragCancel = { dragging = null }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onTap() }, onLongPress = { menuOpen = true })
                }
                .semantics {
                    contentDescription = description
                    role = Role.Button
                    onClick { onTap(); true }
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = CodecTokens.elevation(CodecTokens.Elevation.CARD),
                modifier = Modifier.size(AiBubblePolicy.VISUAL_DP.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.NAV))
                    )
                }
            }
            if (hasSelection) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(CodecTokens.space(CodecTokens.Space.S))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(AiCopy.HIDE_BUBBLE) },
                    onClick = { menuOpen = false; onHide() }
                )
                DropdownMenuItem(
                    text = { Text(AiCopy.MOVE_BUBBLE) },
                    onClick = { menuOpen = false; onMoved(position.otherSide()) }
                )
            }
        }
    }
}
