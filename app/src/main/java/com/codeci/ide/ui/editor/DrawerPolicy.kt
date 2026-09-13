package com.codeci.ide.ui.editor

/**
 * Phase 47.1 — the drawer's close law and the in-drawer project list
 * (PART_47_1). Both pure, host-tested ([DrawerPolicyTest],
 * [com.codeci.ide.DrawerProjectListTest]).
 *
 * Owner rows: *"the file ber can't close without opening any file"* (there was
 * no affordance whose only job was "close this") and *"I can switch project but
 * can't directly open folder"* (a dialog titled Open-folder that cannot open
 * a folder — retired; its list now lives IN the drawer, the owner's chosen
 * shape).
 */
enum class DrawerCloseReason {
    /** The ✕ in the drawer header. Closes and does nothing else. */
    CLOSE_BUTTON,

    /** The scrim tap. Material3's own affordance, same result. */
    SCRIM,

    /**
     * The system back gesture/button. In this phase the editor's own
     * `BackHandler` closes the drawer; Phase 49's `BackRouter` later owns the
     * decision (precedence: unsaved → drawer → …) without changing it.
     */
    BACK,

    /** Tapping a file row opened the file; the drawer leaves with it. */
    FILE_OPENED,

    /** A project (or Single files) was picked from the in-drawer list. */
    PROJECT_SWITCHED
}

object DrawerPolicy {

    /**
     * Every close reason closes an OPEN drawer; a close when already closed is
     * a no-op (the "double animation" bug class — never two `close()` calls).
     */
    fun shouldClose(reason: DrawerCloseReason, drawerOpen: Boolean): Boolean = drawerOpen

    /**
     * The one close that ALSO changes the editor's context. Nothing else may
     * switch projects as a side effect of closing (and ✕ in particular must
     * never open a dialog or navigate — PART_47_1 exit 1).
     */
    fun closesAndSwitches(reason: DrawerCloseReason): Boolean =
        reason == DrawerCloseReason.PROJECT_SWITCHED
}

/**
 * The PROJECTS section's rows, in the exact order the retiring Open-folder
 * dialog listed its contexts: **Single files first** (the null context), then
 * every project alphabetically (case-insensitive). The current context is
 * marked so the user can see where they are before they tap.
 */
object DrawerProjectList {

    /** The label for the scratch context — the retired dialog's own words. */
    const val SINGLE_FILES_LABEL = "Single files"

    /** The dialog's empty-projects copy, verbatim — it is already written. */
    const val EMPTY_PROJECTS_COPY =
        "No projects yet — create one in the Projects tab, or keep working with single files here."

    data class Row(
        /** null = the Single files (scratch) context. */
        val contextName: String?,
        val label: String,
        val isCurrent: Boolean
    )

    /** Single files → projects alphabetically; the current context marked. */
    fun build(projectNames: List<String>, currentContext: String?): List<Row> = buildList {
        add(Row(null, SINGLE_FILES_LABEL, currentContext == null))
        projectNames
            .sortedBy { it.lowercase() }
            .forEach { name -> add(Row(name, name, currentContext == name)) }
    }
}
