package com.codeci.ide.ui.projects

/**
 * Phase 51.3 — the hub's third state, and why it is not a blank frame.
 *
 * The evidence (2026-09-21, `main` @ `0f1b650`): `FileManagerScreen.kt`
 * crossfades between `EmptyProjectsState` (`:1219`) and the loaded list
 * (`:1246`), and `FileManagerViewModel.hubEntries` starts as `emptyList()`
 * (`:48-49`). On a cold open with many projects, "still reading" and "you have
 * no projects" therefore render **the same screen** — the empty state — for as
 * long as the disk takes, which is exactly the moment a user concludes their
 * projects are gone.
 *
 * [HubListPolicy] adds the third branch and keeps it honest:
 *
 * - `LOADING` only while a hub read is genuinely in flight and nothing has been
 *   read yet ([HubListFacts.loadedOnce] — set by the ViewModel when a read
 *   finishes), never for the busy flags of *other* work (clone, delete, export),
 *   which is what `isBusy` alone would have meant;
 * - the skeleton shows the same number of rows the list had last time (floor
 *   [SKELETON_ROWS]), because a placeholder count that differs from the real
 *   count makes the scroll position jump when the names arrive — the Phase 36
 *   class of bug (`SkeletonStabilityTest` pins it).
 *
 * Pure Kotlin: `HubSurfaceTest` pins that the screen really renders the branch.
 */
data class HubListFacts(
    /** Entries the screen has in hand right now. */
    val entryCount: Int = 0,
    /**
     * At least one hub read has finished in this session. The whole point of
     * the third state: before the first read lands, "zero entries" means "we do
     * not know yet", not "you have no projects" — and only this flag can tell
     * the two apart. Deliberately not `isBusy`, which is also true for clone,
     * delete and export, and would draw a skeleton over loaded projects.
     */
    val loadedOnce: Boolean = false,
)

/** The three states the hub's list area can be in. */
enum class HubListBranch { LOADING, EMPTY, LIST }

object HubListPolicy {

    /** Placeholder rows shown when there is no previous count to honour. */
    const val SKELETON_ROWS = 3

    fun branchFor(facts: HubListFacts): HubListBranch = when {
        facts.entryCount > 0 -> HubListBranch.LIST
        facts.loadedOnce -> HubListBranch.EMPTY
        else -> HubListBranch.LOADING
    }

    /**
     * The row count the surface must declare for these facts. The skeleton and
     * the loaded list answer from the same function, so the transition can
     * never change the list's shape by more than the data itself.
     */
    fun rowCount(facts: HubListFacts, lastKnownCount: Int = 0): Int =
        when (branchFor(facts)) {
            HubListBranch.LIST -> facts.entryCount
            HubListBranch.LOADING -> maxOf(lastKnownCount, SKELETON_ROWS)
            HubListBranch.EMPTY -> 0
        }

    /** True when the branch is a placeholder that must not be interactive. */
    fun isPlaceholder(branch: HubListBranch): Boolean = branch == HubListBranch.LOADING
}
