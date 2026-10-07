package com.codeci.ide.ui.support

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Phase 97 — the app's single outbound door.
 *
 * CodeC has no in-app browser (a deliberate choice, `GitControlView.kt` says
 * the same for the GitHub screens), so every address the app offers opens in
 * the user's own browser through one helper. One home means one place to review
 * the app's whole outbound surface (`LearningLinks.ALL` is the list of
 * addresses; this is the how).
 *
 * The flags: `FLAG_ACTIVITY_NEW_TASK` because the caller may be a screen that is
 * not the task's root. The `runCatching`: a device with no browser at all must
 * never crash an IDE — the tap simply does nothing.
 */
object CodecLinks {

    /** Opens [url] in whatever the user has; silently gives up if nothing can. */
    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
