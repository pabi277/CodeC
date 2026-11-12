package com.codeci.ide.ui.terminal

/**
 * Phase 104.3 — session facts shown to and acted on by the user (pure).
 */
object SessionFacts {

    /**
     * The notice printed when the shell process ends. [code] is the exit status, or
     * null when it is not known. Enter restarts the session (see [restartsOnInput]).
     */
    fun exitNotice(code: Int?): String =
        if (code != null) {
            "Process completed (code $code) \u2014 press Enter"
        } else {
            "Process completed \u2014 press Enter"
        }

    /**
     * A dead session restarts on Enter. [dead] is true only once the shell has
     * really ended (or failed to start), never while it is starting.
     */
    fun restartsOnInput(dead: Boolean, input: String): Boolean =
        dead && (input.contains('\r') || input.contains('\n'))

    /**
     * A new tab inherits the previous tab's directory when that directory is
     * readable as an absolute path; otherwise it falls back to the default.
     */
    fun inheritedDirectory(currentDir: String?): String? =
        currentDir?.takeIf { it.startsWith("/") }
}
