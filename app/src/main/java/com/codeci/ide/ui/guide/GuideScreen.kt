package com.codeci.ide.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Phase 45.1 — the five-slide first-run guide, and the same screen the three
 * "view the guide again" doors open.
 *
 * It is a plain [Column], not a pager and not a dialog: the content lives in the
 * pure [GuidePlan] (host-tested, vocabulary-pinned), this file only renders it
 * and reports the two user actions that end it. Both of them — SKIP on any slide
 * and the last slide's START CODING — call [onFinished], which is the ONLY way
 * `guide_completed` is ever written (no timer, no implicit dismissal: the
 * no-nag law says a guide the user cannot leave in one tap is a wall).
 *
 * Back is SKIP. A first-run screen that traps the user with the back button
 * teaches them to force-close the app instead.
 *
 * The slide index survives a rotation or a process death through
 * [rememberSaveable], sanitised by [GuidePlan.resume] so an index persisted by a
 * build with more slides still lands on a real one.
 */
@Composable
fun GuideScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var savedIndex by rememberSaveable { mutableStateOf(0) }
    val index = GuidePlan.resume(savedIndex)
    val slide = GuidePlan.at(index) ?: GuidePlan.slides.last()

    BackHandler { onFinished() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Guide \u00B7 ${index + 1} of ${GuidePlan.slides.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // SKIP on EVERY slide, the first one included.
            if (GuidePlan.canSkip(index)) {
                TextButton(onClick = onFinished) {
                    Text("SKIP")
                }
            }
        }
        LinearProgressIndicator(
            progress = { GuidePlan.progress(index) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        // The copy column scrolls on its own: a 5" screen at the largest font
        // scale must still reach the button (exit condition 5 of 45.1).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            // The only art in the app (Phase 38.1's mark, the same drawing as
            // the launcher icon): identity without a per-slide illustration.
            Image(
                painter = painterResource(com.codeci.ide.R.drawable.app_mark),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = slide.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = slide.body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = {
                if (GuidePlan.isLast(index)) onFinished() else savedIndex = GuidePlan.next(index)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(slide.actionLabel)
        }
        Spacer(Modifier.height(8.dp))
    }
}
