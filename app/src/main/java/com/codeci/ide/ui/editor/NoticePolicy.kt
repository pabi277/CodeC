package com.codeci.ide.ui.editor

/**
 * Phase 57.3 — the short messages the shots show as a pill.
 *
 * The reference (`docs/spck-ui` 122157) floats one pill above the bottom of the
 * editor: a rounded dark surface, one icon, one bold line — “Refreshed Files”.
 * The roadmap's job for this part is the *surface*, and the two messages the
 * shots (and the app's own dead ends) name:
 *
 *  - **Refreshed Files** — the tree re-read the disk.
 *  - **Error opening file.** — a file the user tapped could not be opened.
 *
 * The last one is the reason this part exists at all: on this checkout the
 * failure paths in `EditorViewModel.openProjectFile` were **silent**
 * (`?: return`, three of them), so a tap on an unreadable file did nothing at
 * all — not a dialog, not a dump, *nothing*. A dead tap is the one thing the
 * chrome law of this series does not allow.
 *
 * **And the other half is the no-nag law.** Every automatic refresh stays
 * silent (opening the drawer, saving, creating, deleting, renaming, the git
 * re-read); only the refresh the user *asked for* — the tree toolbar's own
 * Refresh — earns a pill. That is the same rule the save snackbar already
 * obeys (*“a save the user did not ask for does not deserve an interruption”*,
 * 41/42/45), so it lives here as a decision instead of a comment.
 *
 * Pure Kotlin on purpose: which message appears, and when, is testable without
 * a device; the pill's own look is one composable.
 */
enum class NoticeKind {
    /** The user's own Refresh finished re-reading the tree. */
    FILES_REFRESHED,

    /** A file the user tapped could not be opened. */
    FILE_OPEN_FAILED,

    /**
     * Phase 58.2 — a RUN ▶ needed a download and the one-time Linux setup is
     * not in a state where that download can be trusted yet.
     */
    USERLAND_NOT_READY,
}

object NoticePolicy {

    /**
     * How long a pill stays before it takes itself away. Short: it confirming
     * or explaining something the user just did, never a thing to read twice.
     */
    const val AUTO_DISMISS_MS = 2_400L

    /**
     * A refresh is silent unless the user asked for it (the no-nag law).
     *
     * @param userAsked true only for the tree toolbar's own Refresh control.
     */
    fun refreshNotice(userAsked: Boolean): NoticeKind? =
        if (userAsked) NoticeKind.FILES_REFRESHED else null

    /**
     * The open-failure message. `null` when there is no name to speak about —
     * the chrome never says something about nothing, and an empty name would
     * render an empty sentence.
     */
    fun openFailureNotice(fileName: String?): NoticeKind? =
        if (fileName.isNullOrBlank()) null else NoticeKind.FILE_OPEN_FAILED

    /**
     * Phase 58.2 — the one warning of the whole userland story.
     *
     * The owner's row: *“Userland installs silently; one warning when a run
     * needs a download before userland is ready.”* The permanent strip is
     * retired (58.2 removed it, and with it the first-run divert to a locked
     * Terminal), so this is the ONLY place the app speaks about the setup while
     * the user is working: the moment a RUN ▶ asks for a package it cannot
     * install yet. One sentence, at the point of use, then the user is on their
     * own again — not “hang tight”, not “don't close”, nothing to watch.
     *
     * @param packageInstallAllowed `SetupGatePolicy.can(INSTALL_PACKAGE,
     *   facts)` — the same verdict `confirmInstall` has always obeyed, so the
     *   warning and the refusal can never disagree.
     */
    fun userlandWarning(packageInstallAllowed: Boolean): NoticeKind? =
        if (packageInstallAllowed) null else NoticeKind.USERLAND_NOT_READY
}
