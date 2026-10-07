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

    /** Subtitles are the flow's spine: one line, and never an essay. */
    const val MAX_WORDS = 16

    /**
     * Words the flow must never contain. `level` is here because the flow does
     * not label people; `userland` because the glossary in the audit replaced
     * it with "Linux tools"; the rest because CodeC is not a store.
     */
    val BANNED_WORDS: List<String> = listOf(
        "userland", "premium", "upgrade", "unlock", "credits", "ad-free", "level",
        "leaderboard", "streak", "xp", "score",
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

    // ---- S7 · You are all set ----

    const val READY_TITLE = "You are set up"
    const val READY_CHIP_SIZE_PREFIX = "Text: "
    const val READY_SETTINGS_LINE = "Everything here lives in Settings."
    const val READY_FAQ_DOOR_DETAIL = "Answers about compiling, storage and the AI"

    // ---- Buttons, shared ----

    const val BUTTON_START = "Let us go"
    const val BUTTON_CONTINUE = "Continue"
    const val BUTTON_FINISH = "Start coding"
    const val BUTTON_BACK = "Back"
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
        NAME_TITLE, NAME_SUB, NAME_START_FROM,
        NAME_HINT_CHIP_1, NAME_HINT_CHIP_2, NAME_HINT_CHIP_3,
        LOOKS_TITLE, LOOKS_SUB, LOOKS_SIZE, LOOKS_SIZE_DETAIL, LOOKS_THEME, LOOKS_THEME_DETAIL,
        HELPS_TITLE, HELPS_SUB, HELPS_PLAIN, HELPS_PLAIN_DETAIL, HELPS_PLAIN_ON, HELPS_PLAIN_OFF,
        HELPS_PLAIN_SAMPLE, HELPS_PLAIN_RAW, HELPS_HINTS, HELPS_HINTS_DETAIL,
        HELPS_LINES, HELPS_LINES_DETAIL, HELPS_WRAP, HELPS_WRAP_DETAIL, HELPS_AI,
        BUILD_TITLE_PREFIX, BUILD_SUB, BUILD_DOWNLOAD_PREFIX, BUILD_DOWNLOAD_LINE,
        BUILD_ESCAPE, BUILD_NO_DOWNLOAD,
        READY_TITLE, READY_CHIP_SIZE_PREFIX, READY_SETTINGS_LINE, READY_FAQ_DOOR_DETAIL,
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
        READY_SETTINGS_LINE, READY_FAQ_DOOR_DETAIL,
    )
}
