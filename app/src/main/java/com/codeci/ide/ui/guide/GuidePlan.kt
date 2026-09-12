package com.codeci.ide.ui.guide

/**
 * Phase 45.1 — the first-run guide's PLAN: pure data and pure rules, no
 * Compose and no Android imports, so every claim below is host-testable
 * (`GuidePlanTest`) and lint cannot find an API level in it.
 *
 * Owner row (verbatim): *"It has 0 guide features to give the user a real
 * knowledge how to use the app, user don't know where should they change the
 * project or file and the tap to the open down side of the keyboard"* →
 * owner's solution: *"Set a step by step user guide after opening the app 1st
 * time with a open view again[ing]"*.
 *
 * Two laws shape this file:
 *  - **the no-nag law** (`ui/support/ExitSurvey.kt` states it): one-time,
 *    skippable with a single tap from the FIRST slide, re-openable by the user,
 *    and nothing returns after dismissal unless asked;
 *  - **the copy is pinned to the product**: a slide may only name a control,
 *    tab or command that really exists, and [GuideVocabulary] is what makes
 *    that checkable instead of a promise (the same idea as
 *    `docs/chat-phase38/SETTINGS_AUDIT.md` + `SettingsAuditTest`).
 */

/** One slide: a single idea, a title, ≤ [GuidePlan.MAX_BODY_WORDS] words, one button. */
data class GuideSlide(
    val id: String,
    val title: String,
    val body: String,
    val actionLabel: String
)

/**
 * A word the guide is allowed to name, plus the file and substring that prove
 * the word is real. `GuidePlanTest` reads the REAL repo tree (`RepoFiles`) and
 * fails when a proof's needle is gone — i.e. when the product changed under the
 * copy.
 */
data class GuideTermProof(val term: String, val path: String, val needle: String)

object GuideVocabulary {

    /** Control symbols the guide may draw with. Each is a real on-screen glyph. */
    private val SYMBOLS = charArrayOf('\u2630', '\u25B6', '\u22EE', '\u2B07') // ☰ ▶ ⋮ ⬇

    private val SYMBOL_REGEX = Regex("[\u2630\u25B6\u22EE\u2B07]")
    private val BACKTICK_REGEX = Regex("`([^`]+)`")
    private val SENTENCE_SPLIT = Regex("(?<=[.!?])\\s+")
    private val WHITESPACE = Regex("\\s+")
    private val EDGE_PUNCTUATION = charArrayOf(
        '.', ',', ';', ':', '!', '?', '(', ')', '[', ']', '"', '\'', '\u2014', '\u2013'
    )

    /**
     * The product nouns a slide names: control symbols, `backticked` spans,
     * capitalised words that are NOT the first word of their sentence, and
     * ALL-CAPS words anywhere (RUN). Everything extracted must have a proof in
     * [proofs] — an invented feature name ("Super Compile") has none, and a
     * renamed one loses its needle, so the copy cannot drift silently.
     *
     * Deliberate limits, both recorded in PART_45_1: single letters are skipped
     * ("C works offline" is pinned by the C-never-gated tests instead, and a
     * one-letter token is a false-positive machine), and lowercase shell
     * commands are pinned by [GuidePlan.commandProofs] rather than by
     * extraction, because prose cannot tell `git` from the word "git".
     */
    fun candidateTerms(text: String): List<String> {
        val found = LinkedHashSet<String>()
        SYMBOL_REGEX.findAll(text).forEach { found += it.value }
        BACKTICK_REGEX.findAll(text).forEach { found += it.groupValues[1].trim() }
        for (sentence in text.split(SENTENCE_SPLIT)) {
            val words = sentence.split(WHITESPACE).filter { it.isNotBlank() }
            words.forEachIndexed { index, raw ->
                val word = raw.trim(*EDGE_PUNCTUATION)
                if (word.length < 2) return@forEachIndexed
                if (word.any { it in SYMBOLS }) {
                    SYMBOL_REGEX.findAll(word).forEach { found += it.value }
                    return@forEachIndexed
                }
                val allCaps = word.all { it.isUpperCase() }
                val capitalised = word[0].isUpperCase() && word.any { it.isLowerCase() }
                if (allCaps || (index > 0 && capitalised)) found += word
            }
        }
        return found.toList()
    }

    /**
     * Every proof the guide's copy needs. A term with no entry here is a
     * `GuidePlanTest` failure ("the guide names something the product does not
     * have"), and an entry whose needle vanished from [path] is a failure too
     * ("the product moved; fix the copy or the proof").
     */
    val proofs: List<GuideTermProof> = listOf(
        // The editor's ☰ (drawer) and RUN ▶ — the two controls the owner's row
        // says users cannot find.
        GuideTermProof(
            term = "\u2630",
            path = "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt",
            needle = "Icons.Default.Menu"
        ),
        GuideTermProof(
            term = "\u25B6",
            path = "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt",
            needle = "Icons.Default.PlayArrow"
        ),
        GuideTermProof(
            term = "\u22EE",
            path = "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt",
            needle = "Icons.Default.MoreVert"
        ),
        // RUN is a real label, not a description of one.
        GuideTermProof(
            term = "RUN",
            path = "app/src/main/res/values/strings.xml",
            needle = "<string name=\"run\">RUN</string>"
        ),
        // The tabs the guide names, from the one place they are declared.
        GuideTermProof(
            term = "Terminal",
            path = "app/src/main/java/com/codeci/ide/ui/navigation/Screen.kt",
            needle = "\"Terminal\""
        ),
        GuideTermProof(
            term = "Projects",
            path = "app/src/main/java/com/codeci/ide/ui/navigation/Screen.kt",
            needle = "\"Projects\""
        ),
        // The hub card's overflow action, verbatim from strings.xml.
        GuideTermProof(
            term = "Open",
            path = "app/src/main/res/values/strings.xml",
            needle = "<string name=\"hub_open_action\">Open</string>"
        ),
        // What the one-time download actually fetches.
        GuideTermProof(
            term = "Python",
            path = "app/src/main/java/com/codeci/ide/ui/modules/ModuleCatalog.kt",
            needle = "name = \"Python 3\""
        ),
        GuideTermProof(
            term = "Node",
            path = "app/src/main/java/com/codeci/ide/ui/modules/ModuleCatalog.kt",
            needle = "name = \"Node.js\""
        ),
        GuideTermProof(
            term = "Linux",
            path = "app/src/main/java/com/codeci/ide/ui/terminal/TerminalUx.kt",
            needle = "Linux tools"
        ),
        GuideTermProof(
            term = "CodeC",
            path = "app/src/main/res/values/strings.xml",
            needle = "<string name=\"app_name\">CodeC IDE</string>"
        )
    )

    private val byTerm: Map<String, GuideTermProof> = proofs.associateBy { it.term }

    fun proofFor(term: String): GuideTermProof? = byTerm[term]

    /** Terms named by [slides] that have no proof at all. */
    fun unprovenTerms(slides: List<GuideSlide>): List<String> = slides
        .flatMap { candidateTerms(it.title) + candidateTerms(it.body) }
        .distinct()
        .filter { proofFor(it) == null }

    /**
     * Proofs nothing in [slides] uses any more (stale entries to delete).
     * Substring-based on purpose: [candidateTerms] skips a sentence's first
     * word, so a term like "Python" can be named by the copy and still not be
     * *extracted* — "unused" must mean "the copy does not mention it at all".
     */
    fun unusedProofs(slides: List<GuideSlide>): List<String> {
        val copy = slides.joinToString("\n") { it.title + "\n" + it.body }
        return proofs.map { it.term }.filter { term -> !copy.contains(term) }
    }
}

object GuidePlan {

    /** The owner's "step by step": five slides, in the order the user meets them. */
    const val MAX_BODY_WORDS = 22
    const val MAX_TITLE_CHARS = 34
    const val MAX_BODY_CHARS = 130

    val slides: List<GuideSlide> = listOf(
        // Slide 1 exists because the owner's row said users cannot find where to
        // change project or file: the switcher is the project name in the ☰
        // drawer header (EditorProjectDrawer), behind a button a new user has no
        // reason to open.
        GuideSlide(
            id = "files",
            title = "Your files live in the \u2630 menu",
            body = "Tap \u2630 in the editor for the file tree. Tap the project name at the top to switch projects.",
            actionLabel = "GOT IT"
        ),
        // Slide 2 is the reason the app is worth opening: C needs no setup.
        GuideSlide(
            id = "run",
            title = "RUN \u25B6 compiles and runs",
            body = "Output appears at the bottom of the same screen. C works offline \u2014 no setup, no download.",
            actionLabel = "GOT IT"
        ),
        // Slide 3 is Phase 44's teaching moment: without it the setup bar looks
        // like an error and the download looks like it will never end.
        GuideSlide(
            id = "download",
            title = "One download, one time",
            body = "Python, Node and the Linux tools download once on first use. Keep CodeC open while it finishes.",
            actionLabel = "GOT IT"
        ),
        GuideSlide(
            id = "terminal",
            title = "A real terminal",
            body = "The Terminal tab is a Linux shell: pkg install, git, cc. Its status chip tells you what it is doing.",
            actionLabel = "GOT IT"
        ),
        // Slide 5 is the Projects-hub distinction (and Phase 46's subject).
        GuideSlide(
            id = "projects",
            title = "Projects vs single files",
            body = "In Projects, tap a file to edit it. Tap a card, or its \u22EE \u2192 Open, for the whole project.",
            actionLabel = "START CODING"
        )
    )

    /** The index that means "finished" (one past the last slide). */
    val doneIndex: Int get() = slides.size

    fun at(index: Int): GuideSlide? = slides.getOrNull(index)

    /**
     * SKIP exists on EVERY slide, the first one included (the no-nag law: a
     * guide the user cannot leave on tap one is a wall). An index with no slide
     * has nothing to skip.
     */
    fun canSkip(index: Int): Boolean = index in 0 until slides.size

    fun isLast(index: Int): Boolean = index == slides.lastIndex

    fun isDone(index: Int): Boolean = index >= slides.size

    /** Advance; the last slide's action finishes the guide instead of wrapping. */
    fun next(index: Int): Int = if (index < slides.size) index + 1 else slides.size

    /**
     * A persisted index resumes, never restarts and never overruns: an out of
     * range value (a shorter guide after an update, a corrupt preference) lands
     * on a real slide.
     */
    fun resume(persistedIndex: Int): Int = persistedIndex.coerceIn(0, slides.size - 1)

    /** 0f..1f for the progress bar; a finished guide reads full. */
    fun progress(index: Int): Float =
        ((index + 1).coerceIn(0, slides.size)).toFloat() / slides.size.toFloat()

    /** Words that count towards [MAX_BODY_WORDS]: tokens carrying a letter or digit. */
    fun wordCount(text: String): Int = text
        .split(Regex("\\s+"))
        .count { token -> token.any { it.isLetterOrDigit() } }

    /**
     * Shell commands the guide names, each pinned to the code that really runs
     * it. The check is bidirectional: every command here must appear in some
     * slide body (so this list cannot go stale), and every proof needle must
     * exist in the real source (so the copy cannot name a command the product
     * stopped shipping).
     */
    val commands: List<String> = listOf("pkg install", "git", "cc")

    val commandProofs: List<GuideTermProof> = listOf(
        GuideTermProof(
            term = "pkg install",
            path = "app/src/main/java/com/codeci/ide/ui/modules/ModuleCatalog.kt",
            needle = "installCommand = \"pkg install -y nodejs\""
        ),
        GuideTermProof(
            term = "git",
            path = "app/src/main/java/com/codeci/ide/ui/modules/ModuleCatalog.kt",
            needle = "binary = \"git\""
        ),
        GuideTermProof(
            term = "cc",
            path = "app/src/main/java/com/codeci/ide/ui/modules/ModuleCatalog.kt",
            needle = "runCommand = \"cc\""
        )
    )

    fun slidesNaming(command: String): List<GuideSlide> = slides.filter { command in it.body }
}
