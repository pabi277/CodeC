package com.codeci.ide.ui.crash

import java.io.File

/**
 * Phase 41.2 — the ONE reader of the crash record file that
 * `MainActivity.installCrashLog()` writes (`filesDir/crash-log.txt`).
 *
 * The read was inside `CrashReportOverlay` until Phase 41's feedback section
 * needed the same bytes; it is extracted here so the overlay and the report
 * CANNOT drift apart (one sink, one reader list — a second crash store is
 * how a crash gets reported from the wrong build).
 *
 * The semantics are the ones the overlay established (and the Phase 29
 * device round paid for): records accumulate in the file, and the NEWEST
 * record is taken FROM ITS `==== ` HEADER — never a byte tail of the whole
 * file, because a tail window cuts off the exception line, i.e. the
 * diagnosis, exactly when it is needed most. The writer caps each record
 * and bounds the file at 60 KB, so this read stays small; the 9 000-char
 * display cap is the overlay's original window.
 */
object CrashLog {

    const val FILE_NAME = "crash-log.txt"

    /** The newest crash record, or null when there is nothing to show. */
    fun newestRecord(filesDir: File): String? = runCatching {
        val file = File(filesDir, FILE_NAME)
        if (!file.isFile || file.length() == 0L) return@runCatching null
        val text = file.readText()
        val start = text.lastIndexOf("\n==== ").let { if (it >= 0) it + 1 else 0 }
        text.substring(start).take(9_000).takeIf { it.isNotBlank() }
    }.getOrNull()
}
