package com.codeci.ide.ui.support

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

/**
 * Phase 41 follow-up (owner, after device round 1) — the "sweet request"
 * shown when the user presses BACK at a root destination (closing the app).
 * Rate (stars) / share the experience / report a bug / give a review —
 * and **tap again to exit**.
 *
 * The exit semantics: this dialog's [onDismissRequest] IS the exit (the
 * second back press closes the app — the classic double-back pattern), and
 * outside taps are disabled so an accidental tap never closes the app.
 * NOT NOW stays, EXIT closes.
 *
 * The stars travel ONLY inside the report the user then reviews and sends
 * from the Feedback screen — nothing is uploaded, stored, or counted. If
 * the user just rates and leaves, the rating goes nowhere (honest, and the
 * copy says so).
 */
@Composable
fun ExitFeedbackDialog(
    onShareExperience: (rating: Int) -> Unit,
    onReview: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    var rating by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onExit, // "tap again to exit": back = the second press
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text("Enjoying CodeC? 💚") },
        text = {
            Column {
                Text(
                    "You're closing the app. During testing, every word helps — " +
                        "tell us what you saw, or report a bug.",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (star in 1..ExitSurvey.MAX_STARS) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "$star of ${ExitSurvey.MAX_STARS}",
                            tint = if (star <= rating) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { rating = star }
                        )
                    }
                }
                if (ExitSurvey.hasRating(rating)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${ExitSurvey.stars(rating)} — it rides along only in the feedback you send",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onShareExperience(rating) }) {
                Text(if (ExitSurvey.hasRating(rating)) "SHARE EXPERIENCE" else "TELL US / REPORT A BUG")
            }
        },
        dismissButton = {
            Column(horizontalAlignment = Alignment.End) {
                OutlinedButton(onClick = onReview) {
                    Text("GIVE A REVIEW 💚")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    TextButton(onClick = onDismiss) { Text("NOT NOW") }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onExit) {
                        Text("EXIT", fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "tap back again to exit",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
