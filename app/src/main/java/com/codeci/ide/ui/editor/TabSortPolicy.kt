package com.codeci.ide.ui.editor

/**
 * Phase 60 — the tab menu's sorts (spec §2), as pure ordering over [EditorTab].
 *
 * The spec asks for alphabetical / by-extension / by-path. Those are *orders*,
 * not flags: applying one physically re-orders the open tabs, so everything
 * that reads the tab list afterwards agrees with what the strip shows — the
 * strip itself, Ctrl+Tab (`EditorViewModel.nextTab`), the tab a close falls
 * back to, and the eviction pick (`trimTabs`). A view-only sort would have made
 * the visible order and the Ctrl+Tab order disagree, which is why this policy
 * returns a new list for the caller to keep rather than a display projection.
 *
 * Every comparison is **total and case-insensitive**, with the lower-case
 * comparison first and the exact spelling as the last tiebreak — so two files
 * that differ only in case still order the same way on every device, and the
 * sort is deterministic rather than merely stable.
 */
enum class TabSort {
    /** `a.kt` before `B.kt` (case-insensitive A→Z), then by full path. */
    NAME,

    /** By extension (`.java`, `.js`, `.kt` …), then by name, then by path. */
    EXTENSION,

    /** By the tab's project-relative path — folders group their files. */
    PATH,
}

object TabSortPolicy {

    /**
     * The three rows of the tab menu's sort group, in the order they are shown.
     * The menu walks this list, and `TabMenuWiringTest` pins that every enum
     * entry has a label — so a fourth sort cannot ship as a blank row.
     */
    val MENU_ORDER: List<TabSort> = listOf(TabSort.NAME, TabSort.EXTENSION, TabSort.PATH)

    /**
     * The extension a name sorts under: the text after the **last** dot, or
     * `""` when there is none. A leading dot names a hidden file
     * (`.gitignore`), not an extension — the same reading a file manager's
     * "type" column uses, and the reason `dot <= 0` is the test rather than
     * `dot < 0`.
     */
    fun extensionOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0) "" else fileName.substring(dot + 1)
    }

    /** [tabs] in [sort]'s order. The input list is never mutated. */
    fun sort(tabs: List<EditorTab>, sort: TabSort): List<EditorTab> = when (sort) {
        TabSort.NAME -> tabs.sortedWith(
            compareBy(
                { it.displayName.lowercase() },
                { it.relativePath.lowercase() },
                { it.relativePath },
            )
        )

        TabSort.EXTENSION -> tabs.sortedWith(
            compareBy(
                // Extensionless files first: their key is the empty string,
                // which is where `substringAfterLast`'s "no extension" reads
                // honestly rather than as a made-up extension.
                { extensionOf(it.displayName).lowercase() },
                { it.displayName.lowercase() },
                { it.relativePath.lowercase() },
                { it.relativePath },
            )
        )

        TabSort.PATH -> tabs.sortedWith(
            compareBy({ it.relativePath.lowercase() }, { it.relativePath })
        )
    }
}
