package com.codeci.ide.ui.services

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * The app's **one** "show this URL in the user's browser" path
 * (Phase 37 follow-up; the Terminal used to own a copy of this plumbing).
 *
 * It returns a Boolean instead of deciding what a failure *means*, so the
 * caller can degrade honestly: the share row falls back to copying the link,
 * the Terminal just says it could not open it. No in-app browser is invented,
 * and no second `ACTION_VIEW` block exists in the codebase.
 */
object OpenInBrowser {

    /** True when a browser (or another handler) took the URL. */
    fun open(context: Context, url: String?): Boolean {
        if (!ShareActions.isHttpUrl(url)) return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    /**
     * Phase 41 — the shared "open, and if nothing can take it, do not lose
     * the content" policy. The share rows (37) and the feedback channels
     * (41) both route through this helper instead of each keeping its own
     * copy of the three lines: launch via [open]; on failure copy
     * [copyInstead] (which may be the URL itself, or the full report when
     * the URL was only its carrier) and say so. A tap is never a dead end.
     *
     * @return true when something opened; false when the fallback fired.
     */
    fun openOrCopy(
        context: Context,
        url: String?,
        clipboardLabel: String,
        copyInstead: String,
        failureMessage: String
    ): Boolean {
        if (open(context, url)) return true
        val copied = runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(clipboardLabel, copyInstead))
            true
        }.getOrDefault(false)
        Toast.makeText(
            context,
            if (copied) failureMessage else "Could not open the link — and copying failed too",
            Toast.LENGTH_LONG
        ).show()
        return false
    }
}
