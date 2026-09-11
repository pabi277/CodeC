package com.codeci.ide.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.settings.SettingsManager
import com.codeci.ide.ui.support.ExitSurvey
import com.codeci.ide.ui.support.FeedbackSectionCard
import kotlinx.coroutines.launch

/**
 * Phase 41 follow-up (owner, after device round 1: *"Can it be a separate
 * page?"*) — the Feedback & Support content moved from a Settings section
 * card to its own screen, reachable from Settings (OPEN) and from the exit
 * survey's SHARE EXPERIENCE. Same card, same pure decisions, same honest
 * disclosure — only the address changed, plus the exit-prompt switch that
 * lives here so the popup's off-switch sits next to the thing it controls.
 *
 * Round 2: the reply-to fields are gone entirely (the developer's contact
 * is hardcoded — `DeveloperContact`); the only knob on this screen is the
 * exit-prompt switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onNavigateBack: () -> Unit = {},
    /** The exit survey's star rating (0 = the screen was opened directly). */
    exitRating: Int = 0
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    val exitPromptEnabled by settingsManager.feedbackExitPromptEnabledFlow.collectAsState(initial = true)

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Feedback & Support") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            FeedbackSectionCard(exitRating = exitRating)

            // The exit survey's off-switch, next to the thing it controls.
            // Default ON (testing phase); the "no nag" law of PART_41_2 is
            // amended BY THE OWNER's request — and this switch is what
            // keeps the amendment honest.
            SettingsSwitch(
                title = "Ask for feedback on exit",
                checked = exitPromptEnabled,
                onCheckedChange = { scope.launch { settingsManager.setFeedbackExitPromptEnabled(it) } }
            )
            Text(
                "Testing phase: when you close the app (back at the main screen), " +
                    "CodeC asks how it went — rate it, tell us, or review it on GitHub " +
                    "(${ExitSurvey.REPO_URL.removePrefix("https://")}). Off = back closes " +
                    "the app directly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}
