package com.codeci.ide.ui.editor

/**
 * Phase 51.2 — what the editor says when it has no file of its own to show.
 *
 * The plan's premise was *"with no file open the tab bar collapses and the user
 * is looking at an empty frame"* (`docs/chat-phase51/PART_51_2`, evidence read
 * on `62cfe7b`). Implementation round 1 checked that premise against the code
 * and found it **half right**, and the half that is wrong matters:
 *
 * - `EditorTabBar` really does return early (`EditorTabBar.kt:61`) and the top
 *   bar falls back to the bare file name (`EditorScreen.kt:1247-1252`);
 * - but the code view is never blank: `EditorViewModel.INITIAL_CODE` is a
 *   Hello-World `main.c` (`EditorViewModel.kt:281-289`) and the VM keeps one
 *   buffer alive on purpose (`closeTab` returns when `tabs.size <= 1`), so
 *   "no tabs" means **scratch mode**, not an empty frame.
 *
 * So the honest deliverable is not a full-screen empty state covering a buffer
 * the user can already run — it is the thing that is genuinely missing in that
 * state: **the chrome never says that this buffer is a scratch file and not a
 * project file, and offers no way back into one.** One sentence and exactly one
 * action, in chrome above the code view.
 *
 * Pure: facts in, the decision out. No copy lives here — the message and the
 * button label come from `strings.xml` (the screen maps the enums), so the
 * strings stay in the repo's one place for user-visible text and a copy pin is
 * a `strings.xml` read instead of a Kotlin literal.
 *
 * The wiring passes `EditorLaunchState.load(...) != null` as
 * [EditorEmptyFacts.lastFileAvailable], so the action can never invent a second
 * resume path: 52.1 owns resume, and this reuses the one source of it.
 */
data class EditorEmptyFacts(
    /** Open project-file tabs. `0` = scratch mode (the tab bar is collapsed). */
    val openTabs: Int = 0,
    /** A project context is loaded — the drawer is the way around, not a chip. */
    val projectOpen: Boolean = false,
    /** `EditorLaunchState.load(...)` returned a real, still-existing file. */
    val lastFileAvailable: Boolean = false,
)

/** The state the chip describes (one today; a named enum keeps it extensible). */
enum class EditorEmptyMessage { SCRATCH_FILE }

/** What the chip's single action does. */
enum class EditorEmptyAction { OPEN_LAST_FILE, BROWSE_PROJECTS }

data class EditorEmptyChrome(
    val message: EditorEmptyMessage,
    val action: EditorEmptyAction,
)

object EditorEmptyState {

    /**
     * The chip for these facts, or `null` when there is nothing to say: with a
     * tab open the surface speaks for itself, and inside a project the drawer
     * already lists the files.
     */
    fun chromeFor(facts: EditorEmptyFacts): EditorEmptyChrome? {
        if (facts.openTabs > 0) return null
        if (facts.projectOpen) return null
        return EditorEmptyChrome(
            message = EditorEmptyMessage.SCRATCH_FILE,
            action = if (facts.lastFileAvailable) {
                EditorEmptyAction.OPEN_LAST_FILE
            } else {
                EditorEmptyAction.BROWSE_PROJECTS
            },
        )
    }

    /** Exactly one action per state — the law the exit condition names. */
    fun actionCount(): Int = EditorEmptyAction.entries.size
}
