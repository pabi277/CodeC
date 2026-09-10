package com.codeci.ide.ui.services

/**
 * Phase 39.1 — live-stamp registry so GC never deletes a run that is still
 * executing. Process-wide; written by CompilerService around a compile,
 * read by TempGc call sites (MainActivity cold-start, Settings Clear).
 * Thread-safe.
 */
object LiveRunStamps {
    private val lock = Any()
    private val stamps = linkedSetOf<Long>()

    fun add(stamp: Long) {
        synchronized(lock) { stamps += stamp }
    }

    fun remove(stamp: Long) {
        synchronized(lock) { stamps -= stamp }
    }

    fun snapshot(): Set<Long> = synchronized(lock) { stamps.toSet() }

    fun clear() {
        synchronized(lock) { stamps.clear() }
    }
}
