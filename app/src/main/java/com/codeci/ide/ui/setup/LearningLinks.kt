package com.codeci.ide.ui.setup

/**
 * Phase 97 — the app's doors to the website, in one place.
 *
 * The owner asked for the learning side of the site to be reachable from
 * inside the app (2026-10-08: *"add this somewhere the learning of the direct
 * link of the website"*). The course already exists and is published under the
 * project's own licence (`website/learn/`, 19 chapters, CC BY 4.0 prose + MIT
 * examples); what was missing was a door.
 *
 * Three rules ride with these constants:
 *
 * 1. **Every URL is here**, so `LearningLinksTest` can assert them all at once
 *    (https only, canonical host, no tracking parameters) and so a reviewer can
 *    see the app's whole outbound surface in one screen of code.
 * 2. **Leaving the app is said out loud.** [LEARN_SUBTITLE] states that the page
 *    opens in the browser and nothing about the user is sent — the same promise
 *    `DATA_AND_PRIVACY.md` carries, which is why `LearningLinksTest` also reads
 *    that document and fails if the sentence is removed from it.
 * 3. **Android-free** (`rule.md` §4.4): the tools that open a URL live in the
 *    screens that already own `Intent`; this file is only the addresses and the
 *    words, so it is host-testable.
 */
object LearningLinks {

    /** The site's canonical origin (`website/sitemap.xml`). */
    const val SITE_URL = "https://pabi277.github.io/CodeC/"

    /**
     * The 19-chapter course. This is the *learning* link the owner asked for;
     * it is the flow's S7 door, a Settings -> About row, and the hub's reading
     * line, all pointing at this one constant.
     */
    const val LEARN_URL = "https://pabi277.github.io/CodeC/learn/"

    /** The FAQ page — the audit found the app never mentions it either. */
    const val FAQ_URL = "https://pabi277.github.io/CodeC/guides/faq.html"

    /** Every outbound address the app offers, for the test and for review. */
    val ALL: List<String> = listOf(SITE_URL, LEARN_URL, FAQ_URL)

    // ---- Words the surfaces share ----

    const val LEARN_TITLE = "Learn to code — 19 short chapters"
    const val LEARN_SUBTITLE =
        "The CodeC course on the website. Opens in your browser; nothing about you is sent."

    const val FAQ_TITLE = "Common questions"

    /** The hub's quiet reading line (never a tile, never a nudge). */
    const val HUB_LEARN_LINE = "Prefer to read first?"

    /** The flow's S7 door label, kept shorter than the Settings row. */
    const val FLOW_DOOR_LABEL = "Learn: 19 short chapters"
}
