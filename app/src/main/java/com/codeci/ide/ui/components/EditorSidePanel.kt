package com.codeci.ide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.R
import com.codeci.ide.ui.editor.NavCell
import com.codeci.ide.ui.editor.ProjectSearch
import com.codeci.ide.ui.editor.RailPanel
import com.codeci.ide.ui.editor.RecentProjects
import com.codeci.ide.ui.editor.SidePanelPlan
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Space

/**
 * Phase 55 — **the side panel**, built to the reference card
 * (`docs/chat-phase54/PART_54_1_SHOTS.md`, from the seven
 * `docs/spck-ui/Screenshot_20260922_*.jpg` read 2026-09-22).
 *
 * The shell, exactly as the shots show it:
 *
 * ```text
 *   ┌──────────────────────────────────────────┐┬──────┐
 *   │ ◁ ▭ ⌕<> ⑂ ◯        ← rail, selected is  ││      │  a strip of the
 *   │                       underlined in white││  ▶   │  editor stays live
 *   │ NAVIGATION                …              ││      │  (the owner's own
 *   │ ┌────────┬────────┬────────┐             ││      │  requirement: play
 *   │ │Projects│ Editor │Settings│  ← 3 × 2    ││      │  keeps working)
 *   │ ├────────┼────────┼────────┤             ││      │
 *   │ │Terminal│Packages│ Guide  │             ││      │
 *   │ └────────┴────────┴────────┘             ││      │
 *   │ RECENT                                   ││      │
 *   │ 1st semester      18 minutes ago         ││      │
 *   └──────────────────────────────────────────┘┴──────┘
 * ```
 *
 * **What this file is not.** It is not the bottom bar (the owner kept it —
 * *“I don't think removing the full down ber is a good choice i think only
 * removing the project option is ok.”*), it is not a second home for the
 * Projects tab, and it carries no shop, no Upgrade, no Discover/My Labs/Change
 * Log. The fifth rail slot is the owner's reserved AI slot
 * (*“Reseserve it i have plan for ai i can use that”*) — drawn, dimmed, and not
 * tappable, so it implies no screen.
 *
 * **Placement.** [Placement] is the one visual decision this panel makes for
 * itself: it sits at the **start edge, over the editor**, leaving
 * `1 − SidePanelPlan.PANEL_WIDTH_FRACTION` of the width showing the editor.
 */
object Placement {
    /** The panel's own width fraction (the shot's ≈85 %). */
    const val FRACTION = SidePanelPlan.PANEL_WIDTH_FRACTION
}

/** The Search slot's live state (the field, the five glyphs, the hits). */
data class SearchPanelState(
    val query: String = "",
    val options: ProjectSearch.Options = ProjectSearch.Options(),
    val hits: List<ProjectSearch.Hit> = emptyList()
)

/** The Repository slot's live state. The empty state is the shot's. */
data class RepositoryPanelState(
    val hasRepository: Boolean = false,
    val branch: String? = null,
    val changeCount: Int = 0
)

/**
 * The panel. Every callback is supplied by the editor screen: this file draws
 * and reports, it never navigates or touches the disk on its own.
 */
@Composable
fun EditorSidePanel(
    panel: RailPanel,
    onSelectPanel: (RailPanel) -> Unit,
    modifier: Modifier = Modifier,
    navCard: List<List<NavCell>> = SidePanelPlan.CARD,
    selectedCell: NavCell? = SidePanelPlan.SELECTED_CELL,
    onNavCell: (NavCell) -> Unit = {},
    recent: List<RecentProjects.Row> = emptyList(),
    onRecentRow: (RecentProjects.Row) -> Unit = {},
    /** The Files slot's content: the existing project tree, slotted in. */
    files: @Composable () -> Unit = {},
    search: SearchPanelState = SearchPanelState(),
    onSearchQuery: (String) -> Unit = {},
    onSearchOptions: (ProjectSearch.Options) -> Unit = {},
    onSearchHit: (ProjectSearch.Hit) -> Unit = {},
    onClearSearch: () -> Unit = {},
    repository: RepositoryPanelState = RepositoryPanelState(),
    onInitializeRepository: () -> Unit = {},
    onOpenSourceControl: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(Placement.FRACTION),
        color = MaterialTheme.colorScheme.surface,
        // The panel is a sheet over the editor, so it needs an edge, not a
        // scrim: the shots show the strip beside it undimmed, and play has to
        // stay tappable through it.
        shadowElevation = CodecTokens.elevation(CodecTokens.Elevation.SHEET),
        tonalElevation = CodecTokens.elevation(CodecTokens.Elevation.FLAT)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Rail(panel = panel, onSelect = onSelectPanel)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            // The slot owns the height that is left under the rail: `weight`,
            // never a second `fillMaxSize` (a Column child asking for the whole
            // screen under a 48 dp rail is content below the fold).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (panel) {
                RailPanel.NAVIGATION -> NavigationSlot(
                    navCard = navCard,
                    selectedCell = selectedCell,
                    onNavCell = onNavCell,
                    recent = recent,
                    onRecentRow = onRecentRow
                )

                RailPanel.FILES -> FilesSlot(files = files)

                RailPanel.SEARCH -> SearchSlot(
                    state = search,
                    onQuery = onSearchQuery,
                    onOptions = onSearchOptions,
                    onHit = onSearchHit,
                    onClear = onClearSearch
                )

                RailPanel.REPOSITORY -> RepositorySlot(
                    state = repository,
                    onInitializeRepository = onInitializeRepository,
                    onOpenSourceControl = onOpenSourceControl
                )

                // The reserved slot has no panel: [Rail] never lets a tap reach it.
                RailPanel.RESERVED -> Unit
                }
            }
        }
    }
}

// ---- the rail --------------------------------------------------------------

@Composable
private fun Rail(panel: RailPanel, onSelect: (RailPanel) -> Unit) {
    val active = MaterialTheme.colorScheme.onSurface
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CodecTokens.space(Space.S))
            .padding(horizontal = CodecTokens.space(Space.XS)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SidePanelPlan.RAIL.forEach { slot ->
            val wired = SidePanelPlan.isWired(slot)
            val selected = wired && slot == panel
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = CodecTokens.icon(CodecTokens.MIN_TOUCH))
                    .clickable(enabled = wired) { onSelect(slot) }
                    .padding(top = CodecTokens.space(Space.S)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = railIcon(slot),
                    contentDescription = if (wired) slot.label else stringResource(R.string.panel_reserved),
                    tint = when {
                        selected -> active
                        // The reserved slot is visibly not a room yet.
                        !wired -> idle.copy(alpha = 0.35f)
                        else -> idle
                    },
                    modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.NAV))
                )
                Spacer(Modifier.height(CodecTokens.space(Space.S)))
                // The selected slot is underlined — the shot's one selection mark.
                Box(
                    modifier = Modifier
                        .width(CodecTokens.icon(CodecTokens.Icon.NAV))
                        .height(CodecTokens.space(Space.XXS))
                        .background(if (selected) active else Color.Transparent)
                )
            }
        }
    }
}

/**
 * The five slots' glyphs. Reuse first (Phase 50's icon law): the folder is the
 * drawer's own outline folder, the branch is the app's own git glyph.
 */
private fun railIcon(panel: RailPanel): ImageVector = when (panel) {
    RailPanel.NAVIGATION -> Icons.Filled.PlayArrow
    RailPanel.FILES -> SpckIcons.FolderLine
    RailPanel.SEARCH -> Icons.Filled.Search
    RailPanel.REPOSITORY -> SpckIcons.GitBranch
    RailPanel.RESERVED -> Icons.Filled.AutoFixHigh
}

// ---- navigation ------------------------------------------------------------

@Composable
private fun NavigationSlot(
    navCard: List<List<NavCell>>,
    selectedCell: NavCell?,
    onNavCell: (NavCell) -> Unit,
    recent: List<RecentProjects.Row>,
    onRecentRow: (RecentProjects.Row) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CodecTokens.space(Space.L))
    ) {
        SlotLabel(text = RailPanel.NAVIGATION.label)
        NavCardGrid(rows = navCard, selectedCell = selectedCell, onNavCell = onNavCell)
        Spacer(Modifier.height(CodecTokens.space(Space.XL)))
        SlotLabel(text = stringResource(R.string.panel_recent))
        if (recent.isEmpty()) {
            Text(
                text = stringResource(R.string.panel_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M))
                    )
            ) {
                recent.forEachIndexed { index, row ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                    }
                    RecentRow(row = row, onClick = { onRecentRow(row) })
                }
            }
        }
        Spacer(Modifier.height(CodecTokens.space(Space.XL)))
    }
}

/**
 * **One card, 3 columns × 2 rows** — the shape the phone shows and the drawing
 * got wrong. The selected cell wears its own raised, rounded tile.
 */
@Composable
private fun NavCardGrid(
    rows: List<List<NavCell>>,
    selectedCell: NavCell?,
    onNavCell: (NavCell) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M))
            )
            .padding(vertical = CodecTokens.space(Space.S))
    ) {
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { cell ->
                    NavCardCell(
                        cell = cell,
                        selected = cell == selectedCell,
                        onClick = { onNavCell(cell) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavCardCell(
    cell: NavCell,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .padding(CodecTokens.space(Space.XS))
            .clickable(onClick = onClick)
            .padding(vertical = CodecTokens.space(Space.S)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(CodecTokens.icon(CodecTokens.MIN_TOUCH))
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.S))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = cellIcon(cell),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.NAV))
            )
        }
        Spacer(Modifier.height(CodecTokens.space(Space.S)))
        Text(
            text = cell.label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun cellIcon(cell: NavCell): ImageVector = when (cell) {
    NavCell.PROJECTS -> Icons.Filled.Folder
    NavCell.EDITOR -> Icons.Filled.Code
    NavCell.SETTINGS -> Icons.Filled.Settings
    NavCell.TERMINAL -> Icons.Filled.Terminal
    NavCell.PACKAGES -> Icons.Filled.Download
    NavCell.GUIDE -> SpckIcons.BookLine
}

@Composable
private fun RecentRow(row: RecentProjects.Row, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = CodecTokens.space(Space.M),
                vertical = CodecTokens.space(Space.M)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(CodecTokens.space(Space.XXS)))
            Text(
                text = row.ageLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Only a project that really came from outside wears the badge.
        if (row.external) {
            Spacer(Modifier.width(CodecTokens.space(Space.S)))
            Text(
                text = stringResource(R.string.panel_recent_external),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---- files -----------------------------------------------------------------

@Composable
private fun FilesSlot(files: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        SlotLabel(text = RailPanel.FILES.label)
        // The tree itself: the screen passes the existing project tree in.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { files() }
    }
}

// ---- search ----------------------------------------------------------------

@Composable
private fun SearchSlot(
    state: SearchPanelState,
    onQuery: (String) -> Unit,
    onOptions: (ProjectSearch.Options) -> Unit,
    onHit: (ProjectSearch.Hit) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CodecTokens.space(Space.L))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlotLabel(text = RailPanel.SEARCH.label, modifier = Modifier.weight(1f))
            // The shot's five glyphs, as real options: regex, Aa, whole word,
            // and a clear. A glyph whose meaning is unknown is not drawn.
            OptionGlyph(label = ".*", selected = state.options.regex) {
                onOptions(state.options.copy(regex = !state.options.regex))
            }
            OptionGlyph(label = "Aa", selected = state.options.caseSensitive) {
                onOptions(state.options.copy(caseSensitive = !state.options.caseSensitive))
            }
            OptionGlyph(label = "Ab|", selected = state.options.wholeWord) {
                onOptions(state.options.copy(wholeWord = !state.options.wholeWord))
            }
            OptionGlyph(label = "⌫", selected = false) { onClear() }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.panel_search_placeholder)) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(CodecTokens.space(Space.L)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SlotLabel(
                text = stringResource(R.string.panel_search_results),
                modifier = Modifier.weight(1f)
            )
            if (state.hits.isNotEmpty()) {
                Text(
                    text = state.hits.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when {
            // Nothing typed: nothing at all. The shot's empty RESULTS.
            state.query.isBlank() -> Unit
            state.hits.isEmpty() -> Text(
                text = stringResource(R.string.panel_search_no_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                state.hits.forEach { hit ->
                    SearchHitRow(hit = hit, onClick = { onHit(hit) })
                }
            }
        }
    }
}

@Composable
private fun OptionGlyph(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = CodecTokens.space(Space.S), vertical = CodecTokens.space(Space.S))
    )
}

@Composable
private fun SearchHitRow(hit: ProjectSearch.Hit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = CodecTokens.space(Space.S))
    ) {
        Text(
            text = "${hit.relativePath}:${hit.line}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = hit.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---- repository ------------------------------------------------------------

@Composable
private fun RepositorySlot(
    state: RepositoryPanelState,
    onInitializeRepository: () -> Unit,
    onOpenSourceControl: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CodecTokens.space(Space.L))
    ) {
        SlotLabel(text = RailPanel.REPOSITORY.label)
        if (!state.hasRepository) {
            // The shot's empty state: one left-aligned sentence, then a centred
            // button. The sentence is the shot's; the button runs CodeC's own
            // git (never SPCK's engine, and never a screen the shots do not show).
            Text(
                text = stringResource(R.string.panel_repository_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(CodecTokens.space(Space.XL)))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = onInitializeRepository) {
                    Text(stringResource(R.string.panel_repository_init))
                }
            }
        } else {
            Text(
                text = state.branch ?: stringResource(R.string.panel_repository_no_branch),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(CodecTokens.space(Space.XS)))
            Text(
                text = stringResource(R.string.panel_repository_changes, state.changeCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(CodecTokens.space(Space.L)))
            TextButton(onClick = onOpenSourceControl) {
                Text(stringResource(R.string.panel_repository_open))
            }
        }
    }
}

// ---- shared ----------------------------------------------------------------

@Composable
private fun SlotLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = CodecTokens.space(Space.M))
    )
}
