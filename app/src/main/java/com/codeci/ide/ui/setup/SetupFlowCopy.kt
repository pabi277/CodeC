package com.codeci.ide.ui.setup

/**
 * Phase 97 — every user-visible word of the setup flow, in one place.
 *
 * Same pattern as [com.codeci.ide.ui.ai.AiCopy]: copy as Kotlin constants so
 * `SetupFlowCopyTest` can assert the house rules mechanically —
 *
 * - no sentence over [MAX_WORDS] words,
 * - no banned word ([BANNED_WORDS]),
 * - no marketing, no "premium", no "unlock", no scores,
 * - ASCII only, so no emoji can creep in and render differently per OEM.
 *
 * The voice is the app's existing one: present tense, active, literal
 * (`strings.xml`'s `git_install_explainer`, `no_projects_hint`,
 * `terminal_intro_ready` were the model).
 */
object SetupFlowCopy {

    /**
     * Subtitles are the flow's spine: one line, and never an essay.
     *
     * 12 is the design record's own ceiling
     * (`docs/research/ONBOARDING_PERSONALISATION_DESIGN_20261008.md`: *"subtitles
     * <=12 words"*), enforced here rather than merely restated. The longest line
     * the flow actually ships is 10 words, so the budget has headroom and still
     * refuses a paragraph from creeping into a subtitle.
     */
    const val MAX_WORDS = 12

    /**
     * Words the flow must never contain, matched on word boundaries (so
     * "expected" is not an "xp" and "package" is not an "age").
     *
     * The four ability words are here because the flow does not label people:
     * Pydroid asks "What's your Python level?" and changes nothing; the
     * switches on S5 change four real settings instead. `level` on its own is
     * *not* banned - it is the honest difficulty of a shipped C template
     * (`Template.difficulty`, 1-3). `userland` is here because the audit's
     * glossary replaced it with "Linux tools"; the rest because CodeC is not a
     * store and the flow is not a game.
     */
    val BANNED_WORDS: List<String> = listOf(
        "userland", "premium", "upgrade", "unlock", "credits", "ad-free",
        "leaderboard", "streak", "score", "experience points",
        "beginner", "intermediate", "advanced", "expert",
    )

    // ---- S1 · Set up your workspace ----

    const val WELCOME_TITLE = "Set up your workspace"
    const val WELCOME_SUB = "Three things and you are coding."
    const val WELCOME_TIME = "About 40 seconds."
    const val WELCOME_PLAN_1 = "Pick your first project"
    const val WELCOME_PLAN_1_DETAIL = "A game, C, Python or a web page"
    const val WELCOME_PLAN_2 = "Make the editor yours"
    const val WELCOME_PLAN_2_DETAIL = "Size, theme, and how errors read"
    const val WELCOME_PLAN_3 = "Watch CodeC build it"
    const val WELCOME_PLAN_3_DETAIL = "Then press Run to see it work"

    // ---- S2 · What do you want to make first? ----

    const val PICK_TITLE = "What do you want to make first?"
    const val PICK_SUB = "Pick one. CodeC creates it and opens it."

    const val PICK_ARCADE = "CodeC Arcade"
    const val PICK_ARCADE_SUB = "A finished game you can play, then take apart."
    const val PICK_ARCADE_COST = "Three games inside"
    const val PICK_ARCADE_SAMPLE = "{ \"title\": \"Snake\" }"

    const val PICK_C = "C"
    const val PICK_C_SUB = "The classic first program."
    const val PICK_C_COST = "Built-in compiler · works offline"
    const val PICK_C_SAMPLE = "#include <stdio.h>"

    const val PICK_PYTHON = "Python"
    const val PICK_PYTHON_SUB = "Scripts and small tools."
    const val PICK_PYTHON_COST = "Downloads once · about 40 MB"
    const val PICK_PYTHON_SAMPLE = "print(\"Hello!\")"

    const val PICK_WEB = "Web page"
    const val PICK_WEB_SUB = "A page you can preview as you type."
    const val PICK_WEB_COST = "Live preview · works offline"
    const val PICK_WEB_SAMPLE = "<!DOCTYPE html>"

    const val PICK_TEMPLATES_DOOR = "More C templates with their level notes"

    // ---- S3 · Name your project ----

    const val NAME_TITLE = "Name your project"
    const val NAME_SUB = "This is the folder you will see in Projects."
    const val NAME_START_FROM = "Start from"
    const val NAME_VARIANT_LEVEL_PREFIX = "Level "
    /** The field's own errors, from the app's validator verdicts. */
    const val NAME_ERROR_EMPTY = "Type a name to continue"
    const val NAME_ERROR_INVALID = "That name has characters a folder cannot use"
    const val NAME_ERROR_TAKEN = "A project already has this name"
    const val NAME_HINT_CHIP_1 = "My First Program"
    const val NAME_HINT_CHIP_2 = "Playground"
    const val NAME_HINT_CHIP_3 = "Test Run"

    // ---- S4 · Make the editor yours ----

    const val LOOKS_TITLE = "Make the editor yours"
    const val LOOKS_SUB = "These change right here. Settings keeps them too."
    const val LOOKS_SIZE = "Text size"
    const val LOOKS_SIZE_DETAIL = "Code and terminal"
    const val LOOKS_THEME = "Theme"
    const val LOOKS_THEME_DETAIL = "Your whole app"

    // ---- S5 · How CodeC helps you ----

    const val HELPS_TITLE = "How CodeC helps you"
    const val HELPS_SUB = "Everything here is changeable later."
    const val HELPS_PLAIN = "When a build fails"
    const val HELPS_PLAIN_DETAIL = "The plain sentence sits above the real compiler output."
    const val HELPS_PLAIN_ON = "Plain words"
    const val HELPS_PLAIN_OFF = "Raw text"
    const val HELPS_PLAIN_SAMPLE = "The statement on line 4 has no ending."
    const val HELPS_PLAIN_RAW = "main.c:4:18: error: expected ';' after expression"
    const val HELPS_HINTS = "Typing hints"
    const val HELPS_HINTS_DETAIL = "Grey suggestions, Tab accepts"
    const val HELPS_LINES = "Line numbers"
    const val HELPS_LINES_DETAIL = "Error lines point at numbers"
    const val HELPS_WRAP = "Word wrap long lines"
    const val HELPS_WRAP_DETAIL = "Nothing scrolls sideways"
    const val HELPS_AI = "The AI helper stays off until you ask for it."

    // ---- S6 · Building your project ----

    const val BUILD_TITLE_PREFIX = "Building "
    const val BUILD_SUB = "These are the files as they are written."
    const val BUILD_DOWNLOAD_PREFIX = "Downloading "
    const val BUILD_DOWNLOAD_LINE = "about 40 MB, once"
    const val BUILD_ESCAPE = "Use C instead"
    const val BUILD_NO_DOWNLOAD = "Arcade, C and web pages need no download."
    const val BUILD_FAILED = "CodeC could not create the project."
    const val BUILD_FAILED_ACTION = "Open Projects"
    const val BUILD_FAILED_DETAIL = "You can make the project there instead"

    // ---- S7 · You are all set ----

    const val READY_TITLE = "You are set up"
    const val READY_CHIP_SIZE_PREFIX = "Text: "
    /** The receipt's chip line is composed from these, so no fragment hides. */
    const val READY_CHIP_SEPARATOR = " · "
    const val READY_CHIP_PLAIN_WORDS = "plain words"
    const val READY_CHIP_HINTS = "hints on"
    const val READY_SETTINGS_LINE = "Everything here lives in Settings."
    const val READY_FAQ_DOOR_DETAIL = "Answers about compiling, storage and the AI"

    /**
     * The editor mirror's sample lines, one per language the flow can start.
     * They are code, not prose, which is exactly why they live here: the
     * "never draw what the app can show" law means the mirror shows the real
     * first line of the file the user is about to get.
     */
    const val MIRROR_C = "printf(\"Hello!\\n\");"
    const val MIRROR_PYTHON = "print(\"Hello!\")"
    const val MIRROR_WEB = "const speed = 6;"

    // ---- Buttons, shared ----

    const val BUTTON_START = "Let us go"
    const val BUTTON_CONTINUE = "Continue"
    const val BUTTON_FINISH = "Start coding"
    const val BUTTON_BACK = "Back"
    /** TalkBack's words for a selected option; the app's existing phrasing. */
    const val A11Y_SELECTED = "Selected"
    const val A11Y_NOT_SELECTED = "Not selected"
    const val SKIP_LABEL = "Skip setup — start with the sample game"
    const val SKIP_BUSY = "Setting up"

    /**
     * Every sentence the flow shows, in one list, so the rules that apply to
     * *copy* (banned words, ASCII-only, no jargon that was retired) are checked
     * against the whole surface rather than against the strings someone
     * remembered to add. A new string belongs in this list in the same change
     * that introduces it — `SetupFlowCopyTest` iterates this and nothing else.
     */
    val ALL_COPY: List<String> = listOf(
        WELCOME_TITLE, WELCOME_SUB, WELCOME_TIME,
        WELCOME_PLAN_1, WELCOME_PLAN_1_DETAIL,
        WELCOME_PLAN_2, WELCOME_PLAN_2_DETAIL,
        WELCOME_PLAN_3, WELCOME_PLAN_3_DETAIL,
        PICK_TITLE, PICK_SUB,
        PICK_ARCADE, PICK_ARCADE_SUB, PICK_ARCADE_COST, PICK_ARCADE_SAMPLE,
        PICK_C, PICK_C_SUB, PICK_C_COST, PICK_C_SAMPLE,
        PICK_PYTHON, PICK_PYTHON_SUB, PICK_PYTHON_COST, PICK_PYTHON_SAMPLE,
        PICK_WEB, PICK_WEB_SUB, PICK_WEB_COST, PICK_WEB_SAMPLE,
        PICK_TEMPLATES_DOOR,
        NAME_TITLE, NAME_SUB, NAME_START_FROM, NAME_VARIANT_LEVEL_PREFIX,
        NAME_ERROR_EMPTY, NAME_ERROR_INVALID, NAME_ERROR_TAKEN,
        NAME_HINT_CHIP_1, NAME_HINT_CHIP_2, NAME_HINT_CHIP_3,
        LOOKS_TITLE, LOOKS_SUB, LOOKS_SIZE, LOOKS_SIZE_DETAIL, LOOKS_THEME, LOOKS_THEME_DETAIL,
        HELPS_TITLE, HELPS_SUB, HELPS_PLAIN, HELPS_PLAIN_DETAIL, HELPS_PLAIN_ON, HELPS_PLAIN_OFF,
        HELPS_PLAIN_SAMPLE, HELPS_PLAIN_RAW, HELPS_HINTS, HELPS_HINTS_DETAIL,
        HELPS_LINES, HELPS_LINES_DETAIL, HELPS_WRAP, HELPS_WRAP_DETAIL, HELPS_AI,
        BUILD_TITLE_PREFIX, BUILD_SUB, BUILD_DOWNLOAD_PREFIX, BUILD_DOWNLOAD_LINE,
        BUILD_ESCAPE, BUILD_NO_DOWNLOAD, BUILD_FAILED, BUILD_FAILED_ACTION, BUILD_FAILED_DETAIL,
        A11Y_SELECTED, A11Y_NOT_SELECTED,
        READY_TITLE, READY_CHIP_SIZE_PREFIX, READY_CHIP_SEPARATOR, READY_CHIP_PLAIN_WORDS,
        READY_CHIP_HINTS, READY_SETTINGS_LINE, READY_FAQ_DOOR_DETAIL,
        BUTTON_START, BUTTON_CONTINUE, BUTTON_FINISH, BUTTON_BACK, SKIP_LABEL, SKIP_BUSY,
        LearningLinks.LEARN_TITLE, LearningLinks.LEARN_SUBTITLE, LearningLinks.FAQ_TITLE,
        LearningLinks.HUB_LEARN_LINE, LearningLinks.FLOW_DOOR_LABEL,
    )

    /**
     * Every one-sentence body the flow shows, for the word-count rule. The
     * lists are the assertion surface, so a new sentence cannot skip the test
     * by being written somewhere else.
     */
    val allSubtitles: List<String> = listOf(
        WELCOME_SUB, WELCOME_TIME,
        WELCOME_PLAN_1_DETAIL, WELCOME_PLAN_2_DETAIL, WELCOME_PLAN_3_DETAIL,
        PICK_SUB,
        PICK_ARCADE_SUB, PICK_ARCADE_COST,
        PICK_C_SUB, PICK_C_COST,
        PICK_PYTHON_SUB, PICK_PYTHON_COST,
        PICK_WEB_SUB, PICK_WEB_COST,
        PICK_TEMPLATES_DOOR,
        NAME_SUB,
        LOOKS_SUB, LOOKS_SIZE_DETAIL, LOOKS_THEME_DETAIL,
        HELPS_SUB, HELPS_PLAIN_DETAIL, HELPS_HINTS_DETAIL, HELPS_LINES_DETAIL,
        HELPS_WRAP_DETAIL, HELPS_AI,
        BUILD_SUB, BUILD_DOWNLOAD_LINE, BUILD_NO_DOWNLOAD,
        BUILD_FAILED, BUILD_FAILED_DETAIL, NAME_ERROR_EMPTY, NAME_ERROR_INVALID, NAME_ERROR_TAKEN,
        READY_SETTINGS_LINE, READY_FAQ_DOOR_DETAIL,
        MIRROR_C, MIRROR_PYTHON, MIRROR_WEB,
    )
}
