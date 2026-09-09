package com.codeci.ide.ui.services

import android.content.Context
import android.content.Intent
import android.net.Uri

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
}
