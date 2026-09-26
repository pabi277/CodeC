package com.codeci.ide.ui.editor

/**
 * Phase 60 — "Close unmodified" (spec §2): which tabs that row may take.
 *
 * Two laws, and they are the whole policy:
 *
 * 1. **The active tab is never taken.** The editor always keeps a buffer
 *    alive — `closeTab` refuses the last one — so with every tab clean the
 *    active tab is the one that stays. That also makes the row safe to tap
 *    twice: the second tap finds nothing to take.
 * 2. **Only clean tabs are taken**, and cleanness is decided by the caller:
 *    the active tab's buffer stash is deliberately stale between boundaries
 *    (Phase 22.5), so its dirtiness comes from the live flag while every other
 *    tab's comes from its own buffer. A dirty buffer is never dropped.
 *
 * Pure on purpose: the decision is host-testable, and the ViewModel's job is
 * reduced to reading the two facts and calling `closeTab` for each answer.
 */
object TabClosePolicy {

    /**
     * The paths "Close unmodified" closes, given the CLEAN tabs (in strip
     * order) and the [activePath]. Callers pass every clean tab — including
     * the active one when it is clean; filtering the active out happens here
     * so no caller can forget it.
     */
    fun unmodifiedTargets(cleanPaths: List<String>, activePath: String?): List<String> =
        cleanPaths.filterNot { it == activePath }
}
