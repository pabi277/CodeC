package com.codeci.ide.ui.modules

/**
 * Phase 71.1 — the 📌 on a package card.
 *
 * Owner (2026-09-29, device): *"Add package pin 📌 option"*. A pinned package
 * rises into a "Pinned" group at the top of the Packages list, so the three
 * tools you actually use are not two scrolls below the compilers.
 *
 * The choice was the chat's (the owner said "whatever you want to do it
 * better"): pin means *pin to top of the list*, not an apt version hold. It is
 * stored as one comma-separated string of catalog ids, most recently pinned
 * first — see `SettingsManager.pinnedPackagesFlow`. Pure Kotlin: every rule
 * here is host-tested.
 */
object PackagePins {

    /** A generous ceiling; the catalog itself is only a few dozen cards. */
    const val MAX_PINS = 24

    /**
     * The stored string as an ordered id list. Only ids the catalog still knows
     * survive, so a card removed in a later release cannot leave a dead pin,
     * and a hand-edited or corrupt value degrades to "nothing pinned".
     */
    fun parse(stored: String?, knownIds: Set<String>): List<String> =
        stored.orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it in knownIds }
            .distinct()
            .take(MAX_PINS)

    fun serialize(pins: List<String>): String = pins.distinct().take(MAX_PINS).joinToString(",")

    /** Pin → the front of the group; unpin → gone. Pinning the 25th drops the oldest. */
    fun toggle(pins: List<String>, id: String): List<String> =
        if (id in pins) pins - id else (listOf(id) + pins).take(MAX_PINS)

    fun isPinned(pins: List<String>, id: String): Boolean = id in pins

    /**
     * Splits [items] into (pinned in pin order, everything else in catalog
     * order). A pin whose card is not in [items] is skipped — the caller may
     * pass a filtered list (a search).
     */
    fun arrange(items: List<PackageItem>, pins: List<String>): Pair<List<PackageItem>, List<PackageItem>> {
        val byId = items.associateBy { it.id }
        val pinned = pins.mapNotNull { byId[it] }
        val pinnedIds = pinned.map { it.id }.toSet()
        return pinned to items.filter { it.id !in pinnedIds }
    }
}
