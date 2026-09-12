package com.codeci.ide.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.terminal.InstallProgress
import com.codeci.ide.ui.terminal.SetupFacts
import com.codeci.ide.ui.terminal.SetupGatePolicy
import com.codeci.ide.ui.terminal.SetupStage

/**
 * Phase 44.1 — the setup bar: the surface that makes the one-time userland
 * download visible from EVERY tab (spec:
 * docs/chat-phase44/PART_44_1_VISIBLE_SETUP.md §2).
 *
 * Before this, the bootstrap download that starts at app launch printed only
 * into the terminal emulator's grid, so a tester on the Projects/Editor/
 * Packages tab never learned it was running, swiped the app away, and every
 * later `pkg install` failed with `pkg: not found`.
 *
 * Laws (all pinned by `SetupGatePolicyTest`):
 *  - it NEVER blocks the UI — the owner's row is about *knowing*, not waiting,
 *    and C works with no setup at all (TCC is in the APK);
 *  - it is not dismissible while the setup is in flight: dismissing it would
 *    recreate exactly the bug it exists to remove. Once the setup has settled
 *    (READY / FAILED / UNSUPPORTED) the ✕ appears;
 *  - the sentence comes from [SetupGatePolicy.barText] — the same vocabulary
 *    the Packages-tab refusal and the terminal's "don't close" bar use, so
 *    there is one truth in the whole app;
 *  - [note] (the boot repair's one-time report, `SetupNoticeBridge`) wins over
 *    the stage text and is always dismissible.
 */
@Composable
fun SetupBar(
    progress: InstallProgress,
    facts: SetupFacts,
    note: String? = null,
    onViewSetup: () -> Unit = {},
    onDismissNote: () -> Unit = {}
) {
    val stageText = SetupGatePolicy.barText(progress, facts)
    val text = note ?: stageText ?: return
    val inFlight = note == null && progress.inFlight
    val container = when {
        note != null -> MaterialTheme.colorScheme.tertiaryContainer
        progress.stage == SetupStage.FAILED -> MaterialTheme.colorScheme.errorContainer
        inFlight -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when {
        note != null -> MaterialTheme.colorScheme.onTertiaryContainer
        progress.stage == SetupStage.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        inFlight -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val icon: ImageVector = when {
        note != null -> Icons.Default.Warning
        progress.stage == SetupStage.FAILED -> Icons.Default.Warning
        progress.stage == SetupStage.UNSUPPORTED -> Icons.Default.Info
        else -> Icons.Default.Download
    }

    Surface(color = container, contentColor = onContainer) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp)
                )
                // The owner's row 1 in one tap: get to where the setup is.
                if (inFlight || progress.stage == SetupStage.FAILED) {
                    TextButton(onClick = onViewSetup) {
                        Text("VIEW", color = onContainer)
                    }
                }
                if (!inFlight) {
                    IconButton(onClick = onDismissNote) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = onContainer
                        )
                    }
                }
            }
            if (inFlight && progress.percent != null) {
                LinearProgressIndicator(
                    progress = { (progress.percent ?: 0) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    color = onContainer,
                    trackColor = onContainer.copy(alpha = 0.2f)
                )
            }
        }
    }
}
