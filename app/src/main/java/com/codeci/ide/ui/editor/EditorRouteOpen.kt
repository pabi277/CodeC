package com.codeci.ide.ui.editor

/**
 * Phase 69.4 — whether the editor should open the file its ROUTE names.
 *
 * Owner (2026-09-28, verbatim): *"If i run a file but it is not in 1st of the
 * editor and back from preview it again opens the 1st file on the editor not
 * the file i opened"*.
 *
 * The route carries the file the editor was ENTERED with (`editor?projectName=
 * …&fileName=…`), and opening a file from the drawer does not navigate — it
 * just switches tab. Coming back from the Web Preview (or Terminal, Settings,
 * or a bottom-bar tab tap) re-composes the editor screen, and its open effect
 * ran again with the ROUTE's file, so the tab the user was actually on was
 * replaced by the file they had entered with — the "1st file" of the report.
 *
 * The rule is one sentence, kept pure so it is host-tested: **a route is
 * opened once per editor session**. The session is the ViewModel's life, which
 * is exactly what "the tabs are still there" means — and it is the one thing
 * that dies when a re-open is genuinely needed (a new back-stack entry, a
 * rename, a deep link, a cold start).
 */
object EditorRouteOpen {

    /** Paths and project names cannot contain a NUL, so it separates safely. */
    private const val SEP = "\u0000"

    /** The one key a route carries: project + file + peek flag. */
    fun key(projectName: String?, fileName: String?, singleFile: Boolean): String =
        projectName.orEmpty() + SEP + fileName.orEmpty() + SEP + if (singleFile) "1" else "0"

    /**
     * True when this route has NOT been opened in the current session yet —
     * a fresh session ([openedRouteKey] == null) always opens.
     */
    fun shouldOpen(routeKey: String, openedRouteKey: String?): Boolean = openedRouteKey != routeKey
}
