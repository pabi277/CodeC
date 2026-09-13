package com.codeci.ide.ui.navigation

/**
 * Phase 49 — back does the obvious thing, everywhere (PART_49_1, PART_49_2).
 *
 * Owner rows: *"After clicking 3 ber if user use back botton it will [close]
 * the file view and show the editor not full app close"* · *"My phone showing
 * the option when try to close not now option but in most phone no option
 * like not now or exit"* · *"The back botton all screen behavior please
 * recheck and refine"*.
 *
 * ONE pure precedence table for every back press in the app. Before 49 there
 * were two ad-hoc `BackHandler`s (`MainActivity`'s root, the editor's
 * dirty-buffer ask) and a drawer handler from 47.1 — and everything else was
 * library default, so nobody could SAY what back does on any screen. Now a
 * screen fills the fields it owns (everything else stays at its default) and
 * performs the one action the table returns; `BackHandlerWiringTest` keeps
 * every handler in the app routed through [BackRouter.decide], and
 * `BackRouterTest` pins the precedence pairs.
 *
 * `None` means "let the library own it" (a Material3 sheet or a dropdown
 * keeps its own back handling) — never "do nothing".
 *
 * Deliberate deviation from the written spec (PART_49_1's table row 2): the
 * spec's `coachMarkVisible -> CloseCoachMark` row is NOT built, and the
 * `BackState`/`BackAction` members for it do not exist. The guided tour was
 * rebuilt under the owner's own instruction in Phase 45 rounds 2-3 as ONE
 * unbreakable flow (*"I want a full process 1st to last without skip
 * anything in this"*), where Back navigates normally and the tour resumes at
 * the same beat, unspent — pinned by `GuideWiringTest` ("Back must not end
 * the tour"). A close-the-mark row would contradict that later, owner-given
 * law; the audit row is corrected in PART_49_1's implementation record.
 */
data class BackState(
    /** The active buffer has unsaved edits (the editor's dirty flag). */
    val unsavedChanges: Boolean = false,
    /**
     * The editor's ☰ drawer is open or OPENING (`drawerState.targetValue ==
     * Open` — PART_49_1 H2: a press inside the ~200 ms open animation still
     * closes it; `currentValue` flips only at the animation's end).
     */
    val editorDrawerOpen: Boolean = false,
    /**
     * The Projects hub's file tree is showing (`activeProject != null`).
     * ViewModel state, not a navigation entry — before 49 back here fell
     * through to the root and EXITED the app (owner 4.iv, hub half).
     */
    val hubProjectOpen: Boolean = false,
    /** A sheet, dialog or dropdown is open — that surface owns back. */
    val sheetOrDialogOpen: Boolean = false,
    /** The editor's find/replace bar is visible. */
    val findBarOpen: Boolean = false,
    /** The editor's output panel is expanded. */
    val outputPanelExpanded: Boolean = false,
    /**
     * The soft keyboard is up: back is the user closing it — the platform
     * already does that, and the router must not eat it (the row-6 guard).
     */
    val keyboardVisible: Boolean = false,
    /** A navigation entry exists below the current one. */
    val canPopRoute: Boolean = false,
    /** The current route is one of the five tab roots ([BackRouter.isRoot]). */
    val atRootDestination: Boolean = false,
    /** The exit-prompt switch (`feedback_exit_prompt_enabled`, default ON). */
    val exitPromptEnabled: Boolean = true,
    /** The exit dialog is on screen — the second press is the app's exit. */
    val exitPromptVisible: Boolean = false,
    /** Safe mode (Phase 42.3): no prompt, no ceremony — back exits. */
    val safeMode: Boolean = false
)

enum class BackAction {
    ShowUnsavedDialog, CloseEditorDrawer, CloseHubProject,
    CloseFindBar, CollapseOutputPanel, PopRoute, ShowExitPrompt, ExitApp, None
}

object BackRouter {

    /**
     * The precedence table, in the order the rows are written and tested
     * (PART_49_1; `BackRouterTest` walks exactly these pairs):
     *
     * ```text
     * 1  unsavedChanges                        -> ShowUnsavedDialog
     * 2  editorDrawerOpen                      -> CloseEditorDrawer
     * 3  hubProjectOpen                        -> CloseHubProject
     * 4  sheetOrDialogOpen                     -> None
     * 5  findBarOpen                           -> CloseFindBar
     * 6  outputPanelExpanded && !keyboardVisible -> CollapseOutputPanel
     * 7  exitPromptVisible                     -> ExitApp
     * 8  canPopRoute                           -> PopRoute
     * 9  atRootDestination                     -> ShowExitPrompt (or ExitApp
     *       when the switch is off / safe mode)
     * 10 else                                  -> None
     * ```
     *
     * Three deliberate choices, kept from the spec:
     * - **`sheetOrDialogOpen -> None` sits AFTER the drawer/hub rows.** A
     *   Material3 sheet's own handler lives in its own window and wins the
     *   dispatch anyway; returning `None` is what lets it work, and if its
     *   handler is ever missing we want to SEE that, not paper over it.
     * - **Row 6 is guarded by `!keyboardVisible`.** With the keyboard up,
     *   back is the user closing the keyboard — the platform already does
     *   that and the router must not eat it.
     * - **Rows 7-9 belong to the ROOT handler only.** Only `MainActivity`
     *   fills `canPopRoute` / `atRootDestination` / `exitPrompt*` /
     *   `safeMode`; a screen-local handler constructs the state with those
     *   defaults (false / true / false / false) and can therefore never pop
     *   navigation or exit the app behind its own screen's back. (The
     *   defaults make `exitPromptEnabled` true so a state that reaches row 9
     *   with the field unfilled still answers honestly; the root always
     *   passes the real switch.)
     */
    fun decide(s: BackState): BackAction = when {
        s.unsavedChanges -> BackAction.ShowUnsavedDialog
        s.editorDrawerOpen -> BackAction.CloseEditorDrawer
        s.hubProjectOpen -> BackAction.CloseHubProject
        s.sheetOrDialogOpen -> BackAction.None
        s.findBarOpen -> BackAction.CloseFindBar
        s.outputPanelExpanded && !s.keyboardVisible -> BackAction.CollapseOutputPanel
        s.exitPromptVisible -> BackAction.ExitApp
        s.canPopRoute -> BackAction.PopRoute
        s.atRootDestination ->
            if (s.safeMode || !s.exitPromptEnabled) BackAction.ExitApp
            else BackAction.ShowExitPrompt
        else -> BackAction.None
    }

    /**
     * 49.2 — "am I at a root tab", decided from the ROUTE and not from
     * `popBackStack()`'s return value, so the exit prompt behaves the same
     * on every device (PART_49_2's causes A and B die here: a tab-tapped
     * stack and a per-install start destination can no longer hide it).
     *
     * Takes ROUTE PATTERN strings — the app passes `screens.map { it.route }`
     * — and compares the segment before any query: parameterised routes
     * (`editor?projectName={projectName}&…`, `terminal?cmd={cmd}&…`) report
     * their PATTERN as the destination route, and a substring `startsWith`
     * would be the trap that bit the 45.2 replay idiom. Kept on plain
     * strings so this file stays a pure, compose-free policy (host-tested
     * with no shims).
     */
    fun isRoot(route: String?, rootRoutePatterns: Collection<String>): Boolean {
        if (route == null) return false
        val base = route.substringBefore('?')
        return rootRoutePatterns.any { it.substringBefore('?') == base }
    }
}
