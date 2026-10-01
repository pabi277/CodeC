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
     * Phase 77.2 — the AI chat sheet (HALF or FULL) is up over the editor. It
     * is a surface, not a route, so Back must put it away before anything
     * underneath (an unsaved-changes prompt about the file is not what a
     * press on a chat sheet means).
     */
    val aiSheetOpen: Boolean = false,
    /** The first-run intro page, or null outside that screen. */
    val firstRunIntroPage: Int? = null,
    /**
     * The soft keyboard is up: back is the user closing it — the platform
     * already does that, and the router must not eat it (rows 7 and 9).
     */
    val keyboardVisible: Boolean = false,
    /** The preview WebView has a previous page in its own history. */
    val webViewCanGoBack: Boolean = false,
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
    CloseFindBar, CollapseOutputPanel, PreviousIntroPage,
    GoBackInWebView, PopRoute, ShowExitPrompt, ExitApp,
    /** Phase 77.2 — one step down: FULL → HALF → the bubble (`AiSheetPolicy.next(BACK)`). */
    CollapseAiSheet,
    None
}

object BackRouter {

    /**
     * The precedence table, in the order the rows are written and tested
     * (PART_49_1; `BackRouterTest` walks exactly these pairs):
     *
     * ```text
     * 0  aiSheetOpen && !editorDrawerOpen && !keyboardVisible
     *                                          -> CollapseAiSheet   (Phase 77.2)
     * 1  unsavedChanges                        -> ShowUnsavedDialog
     * 2  editorDrawerOpen                      -> CloseEditorDrawer
     * 3  hubProjectOpen                        -> CloseHubProject
     * 4  sheetOrDialogOpen                     -> None
     * 5  firstRunIntroPage > 0                 -> PreviousIntroPage
     * 6  findBarOpen                           -> CloseFindBar
     * 7  outputPanelExpanded && !keyboardVisible -> CollapseOutputPanel
     * 8  exitPromptVisible                     -> ExitApp
     * 9  keyboardVisible                       -> None
     * 10 webViewCanGoBack                      -> GoBackInWebView
     * 11 canPopRoute                           -> PopRoute
     * 12 atRootDestination                     -> ShowExitPrompt (or ExitApp
     *       when the switch is off / safe mode)
     * 13 else                                  -> None
     * ```
     *
     * Deliberate choices, kept from the spec and extended for onboarding:
     * - **Row 0 is the AI chat sheet (Phase 77.2) and sits ABOVE row 1.** The
     *   sheet is an overlay on the editor: Back closes the topmost thing you
     *   can see. Two guards: an open drawer is on top of it (row 2 answers),
     *   and with the keyboard up Back is the user closing it — the platform
     *   owns that press, exactly as rows 7 and 9 already say; the next Back
     *   steps the sheet down. Rows 1-13 are unchanged.
     * - **`sheetOrDialogOpen -> None` sits AFTER the drawer/hub rows.** A
     *   Material3 sheet's own handler lives in its own window and wins the
     *   dispatch anyway; returning `None` is what lets it work, and if its
     *   handler is ever missing we want to SEE that, not paper over it.
     * - **Row 5 moves through the onboarding pages only.** The first page
     *   leaves back with the platform, and the privacy dialog keeps its own
     *   back handling.
     * - **Rows 7 and 9 leave the keyboard to the platform.** The output
     *   panel cannot collapse while the keyboard is up, and a preview's
     *   system Back never consumes the press meant to close the IME.
     * - **Row 10 is the WebView's own history.** The preview supplies the
     *   live `canGoBack()` fact, so an in-page Back wins over popping the
     *   preview route.
     * - **Rows 11-12 are navigation/root actions.** `MainActivity` supplies
     *   the real route/root/exit facts; WebPreview sets `canPopRoute` only
     *   because its `onNavigateBack` callback explicitly pops that route.
     *   Other screen-local handlers leave the navigation fields at their
     *   defaults and cannot pop or exit behind their own screen. (The
     *   defaults make `exitPromptEnabled` true so a state that reaches row 12
     *   with the field unfilled still answers honestly; the root always
     *   passes the real switch.)
     */
    fun decide(s: BackState): BackAction = when {
        s.aiSheetOpen && !s.editorDrawerOpen && !s.keyboardVisible -> BackAction.CollapseAiSheet
        s.unsavedChanges -> BackAction.ShowUnsavedDialog
        s.editorDrawerOpen -> BackAction.CloseEditorDrawer
        s.hubProjectOpen -> BackAction.CloseHubProject
        s.sheetOrDialogOpen -> BackAction.None
        s.firstRunIntroPage?.let { it > 0 } == true -> BackAction.PreviousIntroPage
        s.findBarOpen -> BackAction.CloseFindBar
        s.outputPanelExpanded && !s.keyboardVisible -> BackAction.CollapseOutputPanel
        s.exitPromptVisible -> BackAction.ExitApp
        s.keyboardVisible -> BackAction.None
        s.webViewCanGoBack -> BackAction.GoBackInWebView
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
