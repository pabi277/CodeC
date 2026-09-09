package com.codeci.ide.ui.editor

/** The small set of state that determines whether find highlights are stale. */
data class FindDecorationKey(
    val visible: Boolean,
    val query: String,
    val options: FindOptions
)

/**
 * Phase 35.2 — find matching is not a per-keystroke decoration. The regular
 * current-line and bracket pass may run after a text edit, but find results
 * are refreshed only when this key changes (or when a caller explicitly
 * requests a refresh after a replacement).
 */
object DecorationDirtyPolicy {

    fun findNeedsRefresh(previous: FindDecorationKey?, current: FindDecorationKey): Boolean =
        previous != current
}
