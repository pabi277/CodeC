package com.codeci.ide.ui.projects

/**
 * Phase 59.2 — the project's own mark: two initials and one seat in the app's palette.
 *
 * The spec's §1 asks for an *auto-generated, distinct logo per project*; until this phase the
 * hub's leading square was **kind**-derived (every C project looked the same). The mark is now
 * derived from the project's **name**, so two projects of the same kind are told apart at a
 * glance, and the same project always wears the same mark.
 *
 * Everything deciding the mark is here and pure: no Compose, no `android.*`, no colour — the
 * seat is a *name* for a palette seat, and `ProjectIconView` maps it to `CodecPalette.TILE_*`.
 * That split is deliberate: the policy is host-testable, and the wiring (`ProjectMarkWiringTest`)
 * is what proves every seat really reaches a colour.
 */

/**
 * The palette seats a mark may take. These are the app's **existing** project-tile colours
 * (`CodecPalette.TILE_*`), whose white-on-tile contrast Phase 50.1 measured and fixed — a mark
 * never invents a colour, it picks one of the five the app already proved.
 */
enum class MarkSeat { ORANGE, BLUE, VIOLET, GREEN, GRAY }

/**
 * One project's mark. [initials] is never empty — see [ProjectMarks.mark].
 *
 * Named after the pattern it sits beside (`ProjectsHub` holds `ProjectHubEntry`): the data is the
 * mark, `ProjectMarks` is what decides it.
 */
data class ProjectMark(val initials: String, val seat: MarkSeat)

object ProjectMarks {

    /** Marks are one or two characters — a tile, not a word. */
    const val MAX_INITIALS = 2

    /** What a name with no letters or digits at all wears (`...`, `___`, `   `). */
    const val FALLBACK_INITIALS = "?"

    /**
     * The mark for [name].
     *
     * [initials] falls back to [FALLBACK_INITIALS] so the view never has to invent one, and the
     * seat is always answerable — even for an empty name.
     */
    fun mark(name: String): ProjectMark =
        ProjectMark(initials = initials(name).ifEmpty { FALLBACK_INITIALS }, seat = seat(name))

    /**
     * Up to [MAX_INITIALS] characters taken from the name's own words.
     *
     * The rule, in full:
     *  - the name is split on every run of characters that is neither a letter nor a digit
     *    (Unicode-aware, so a non-Latin project name keeps its own letters), and empty words are
     *    dropped: `todo-app` → `todo`, `app`;
     *  - **two or more words** → the first character of the first two (`todo-app` → `TA`);
     *  - **one word** → its first two characters (`snake` → `SN`), or its only character;
     *  - no letter or digit anywhere → `""` (and [mark] turns that into `?`).
     *
     * Deliberately **no camel-case splitting**: a name's own separators are the rule, so `myApp`
     * is `MY` rather than `MA`. One rule a user can predict beats two that occasionally differ.
     */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        if (words.size >= 2) {
            return (firstChar(words[0]) + firstChar(words[1])).take(MAX_INITIALS)
        }
        val only = words[0]
        return only.take(MAX_INITIALS).map { it.uppercaseChar() }.joinToString("")
    }

    /**
     * The seat this name wears, from a hash of the name.
     *
     * The hash is **FNV-1a 32-bit**, written out here rather than `String.hashCode()`, for the
     * reason the offline rule exists everywhere else in this app: the seat must be the same on
     * every device, in every build, forever. Case and surrounding space are folded so
     * `"Snake "` and `"snake"` wear one mark — they could not be two projects on a case-blind
     * filesystem anyway.
     */
    fun seat(name: String): MarkSeat {
        val seats = MarkSeat.values()
        val index = Math.floorMod(fnv1a32(name.trim().lowercase()), seats.size)
        return seats[index]
    }

    /** FNV-1a over UTF-16 code units; `Int` overflow is the algorithm's own wraparound. */
    private fun fnv1a32(text: String): Int {
        var hash = FNV_OFFSET_BASIS
        for (ch in text) {
            hash = hash xor ch.code
            hash *= FNV_PRIME
        }
        return hash
    }

    private fun firstChar(word: String): String = word.first().uppercaseChar().toString()

    private const val FNV_OFFSET_BASIS = -2128831035 // 2166136261u as a signed Int
    private const val FNV_PRIME = 16777619
}
