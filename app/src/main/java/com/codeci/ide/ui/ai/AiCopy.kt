package com.codeci.ide.ui.ai

import com.codeci.ide.ui.projects.AiUndoSummary

/**
 * Phase 76 — every sentence the AI panel shows, in one pure place so tests
 * can pin the disclosures the owner's decisions require (`AiPolicyTest`):
 * O1's own-key/terms text and 18+ checkbox, D2's free-tier note, D1's
 * approval-gated line. Google's terms are linked, quoted by name, never
 * interpreted.
 */
object AiCopy {

    const val TITLE = "AI helper · Gemini"
    // Phase 79 (owner D1 amendment): diff-approved project file edits are now
    // allowed; the helper still never changes files without approval and never
    // runs anything.
    const val READ_ONLY =
        "Explains code and errors, and can propose file edits for your review. " +
            "It never changes files without your approval and never runs anything."

    const val NEEDS_PROJECT = "Open a project to use the AI helper. It works only inside CodeC projects, not in single-file mode."

    // ---- key setup (O1) ---------------------------------------------------

    const val SETUP_INTRO =
        "CodeC sends requests to Google using your own Gemini API key. " +
            "Your use is covered by your own agreement with Google."
    const val GET_KEY = "Get a key in Google AI Studio"
    const val GET_KEY_URL = "https://aistudio.google.com/apikey"
    const val TERMS_LINK = "Gemini API Additional Terms"
    const val TERMS_URL = "https://ai.google.dev/gemini-api/terms"
    const val POLICY_LINK = "Generative AI Prohibited Use Policy"
    const val POLICY_URL = "https://policies.google.com/terms/generative-ai/use-policy"
    const val REGION_NOTE =
        "Google limits where the Gemini API may be used. In the EEA, Switzerland and the UK only paid keys are permitted."
    const val CONFIRM = "I am 18 or older and I accept Google's Gemini API terms for my key."
    const val KEY_LABEL = "Gemini API key"
    const val KEY_STORAGE_NOTE =
        "The key is encrypted on this phone (Android Keystore), never backed up, and sent only to Google."
    const val MODEL_LABEL = "Model"
    const val SAVE_KEY = "Save key"

    // ---- data-use note (D2), shown at setup AND in every preview ----------

    const val FREE_TIER_NOTE =
        "On Google's free tier, Google may use what you send to improve its products, and human reviewers may read it. " +
            "Don't send secrets or personal data. On paid keys Google keeps requests for a limited time to detect abuse."

    // ---- asking -----------------------------------------------------------

    const val QUESTION_LABEL = "Your question (optional)"
    const val EXPLAIN_SELECTION = "Explain selection"
    const val EXPLAIN_ERROR = "Explain last error"
    const val PREVIEW_TITLE = "Check before sending"
    const val SEND = "Send"
    const val CANCEL = "Cancel"
    const val STOP = "Stop"
    const val COPY = "Copy answer"
    const val COPIED = "Answer copied"
    const val NEW_QUESTION = "New question"
    const val TRY_AGAIN = "Try again"
    const val WRONG_NOTE = "AI answers can be wrong. Nothing in your project was changed."
    const val NOT_SAVED_NOTE = "This conversation is not saved. It disappears when you close it or switch projects."

    fun previewHeader(model: String, chars: Int): String =
        "Will be sent to Google Gemini ($model) · $chars characters. Only the text below leaves your phone."

    const val UNSAVED_NOTE = "The selection includes unsaved edits."

    // ---- Phase 77: the floating button and the chat sheet ------------------

    const val BUBBLE_DESCRIPTION = "AI chat"
    const val BUBBLE_SELECTION_DESCRIPTION = "AI chat, code selected"
    const val HIDE_BUBBLE = "Hide AI button"
    const val MOVE_BUBBLE = "Move to other side"
    const val BUBBLE_HIDDEN_NOTE = "AI button hidden. Turn it back on in the AI tab of the side panel."
    const val SHOW_BUBBLE = "Show AI button"
    const val SHOW_BUBBLE_NOTE = "A small button over the code. Drag it anywhere along either side; press and hold it to hide or move it."
    const val OPEN_CHAT = "Open AI chat"
    const val EXPLAIN_WITH_AI = "Explain with AI"
    const val EXPAND = "Expand chat"
    const val MINIMIZE = "Minimize chat"
    const val DRAG_HANDLE = "Drag to resize chat"
    const val QUESTION_PLACEHOLDER = "Ask about the code…"
    const val SEND_QUESTION = "Preview question"
    const val YOU = "You"
    const val AI = "AI"
    // Phase 78 device round 1: this hint used to say only "Select code in the
    // editor, then ask", which stopped being true once a question could be
    // asked about the whole project with nothing selected.
    const val SELECTION_HINT =
        "Select code and ask about it, or just type a question and ask about the whole " +
            "project. You always see what will be sent before it leaves your phone."

    fun sheetTitle(model: String): String = "AI · $model"

    /** The user's side of the exchange, from the prompt the preview was built from. */
    fun youLine(source: AiSource, fileLabel: String, question: String): String {
        val label = when (source) {
            AiSource.SELECTION -> EXPLAIN_SELECTION
            AiSource.RUN_OUTPUT -> EXPLAIN_ERROR
            AiSource.PROJECT -> ASK_PROJECT
            AiSource.PROPOSE_EDITS -> PROPOSE_EDITS
        }
        val head = label + " · " + fileLabel
        return if (question.isBlank()) head else head + "\n" + question.trim()
    }

    // ---- Phase 78 (Level 2): whole-project context ------------------------

    /** The third chip in the sheet, beside Explain selection / Explain last error. */
    const val ASK_PROJECT = "Ask about the project"

    // ---- Phase 79 (Level 3): proposed edits, review, and undo -------------

    /** The fourth chip in the sheet: ask for reviewable file edits. */
    const val PROPOSE_EDITS = "Propose edits"
    const val EDIT_QUESTION_REQUIRED =
        "Type what you want to change first, then tap Propose edits."

    const val PROPOSAL_TITLE = "Proposed file changes"
    const val APPLY_SELECTED = "Apply selected"
    const val APPLYING = "Applying changes…"
    const val REJECT_CHANGES = "Reject changes"
    const val REBUILD_PROPOSAL = "Rebuild proposal"
    const val PROPOSAL_REJECTED = "Proposed changes rejected. No project files were touched."

    const val UNDO_TITLE = "Last AI file change"
    const val UNDO_CHANGES = "Undo AI changes"
    const val UNDO_SCOPE_NOTE =
        "Undo restores these files to their exact contents before this AI change. " +
            "It does not undo terminal commands, package installs, Git actions, or edits you made afterward."
    const val UNDO_FORCE_CONFIRM = "Restore pre-AI files anyway"
    const val UNDO_KEEP_MINE = "Keep my edits"

    fun applyButtonLabel(selectedCount: Int): String =
        if (selectedCount <= 0) APPLY_SELECTED else "$APPLY_SELECTED ($selectedCount)"

    fun proposalSummaryLine(selected: Int, total: Int, added: Int, removed: Int): String =
        "$selected of $total " + (if (total == 1) "file" else "files") +
            " selected · +$added -$removed lines"

    fun opBadge(op: AiEditOp): String = when (op) {
        AiEditOp.MODIFY -> "Modify"
        AiEditOp.CREATE -> "Create"
        AiEditOp.DELETE -> "Delete"
    }

    fun applyConflictMessage(paths: List<String>): String =
        "You edited ${paths.joinToString(", ")} after this proposal was created. " +
            "Your edits were not overwritten. Rebuild the proposal or reject these changes."

    fun undoConflictMessage(paths: List<String>): String =
        "You edited ${paths.joinToString(", ")} after this AI change was applied. " +
            "Undoing will replace your newer edits in " +
            (if (paths.size == 1) "that file." else "those files.")

    fun undoSummaryLine(summary: AiUndoSummary): String {
        val count = summary.files.size
        val names = summary.files.joinToString(", ") { "${opBadge(it.op)}: ${it.path}" }
        return "Applied to $count " + (if (count == 1) "file" else "files") + " ($names)."
    }

    fun appliedNotice(count: Int): String =
        "Applied AI changes to $count " + (if (count == 1) "file." else "files.")

    fun undoneNotice(count: Int): String =
        "Restored $count " + (if (count == 1) "file" else "files") + " to before the last AI change."

    // ---- Phase 80 (Level 4): the agent surface ----------------------------

    /** The preview's note when the task will be run by the agent, not one shot. */
    fun agentPreviewNote(): String =
        "After you tap Send, the AI may read this project — list_files, search_project and read_file — " +
            "one step at a time, shown below as it happens (up to ${AiAgentLimits.MAX_TOOL_CALLS} reads). " +
            "It may ask to run the project (up to ${AiAgentLimits.MAX_RUNS} times); nothing runs until you tap Run. " +
            "Nothing in your project is changed without the diff review, and Stop is always available."

    /**
     * The agent preview's own header: a task, not a file list. The map's own
     * sentence (how many files it names) is rendered separately, from
     * [AiProjectSummary.mapLine], so the preview cannot overstate what was sent.
     */
    fun agentPreviewHeader(projectName: String, chars: Int): String =
        "Agent task · whole-project map from $projectName · $chars characters."

    /** The activity timeline's heading. */
    const val AGENT_ACTIVITY = "What the AI did"

    /** One row's counter, e.g. `3 of 12 steps · 1 of 2 runs`. */
    fun agentUsageLine(turns: Int, toolCalls: Int, runs: Int): String =
        "$turns of ${AiAgentLimits.MAX_TURNS} steps · $toolCalls of ${AiAgentLimits.MAX_TOOL_CALLS} reads · " +
            "$runs of ${AiAgentLimits.MAX_RUNS} runs"

    /** The running label while the agent works. */
    fun agentWorkingLine(steps: Int): String =
        "Working on it" + (if (steps > 0) " — $steps steps done" else "") + "…"

    /** The run approval card. */
    const val AGENT_RUN_TITLE = "The AI wants to run the project"
    const val AGENT_RUN_APPROVE = "Run"
    const val AGENT_RUN_SKIP = "Skip"
    const val AGENT_RUN_RUNNING = "Running the project… The output is in the Output panel."
    const val AGENT_RUN_SKIPPED_NOTE = "Run skipped. The AI was told."
    const val AGENT_RUN_NOT_STARTED =
        "The run did not start (the project asked something first, or has no run profile). The AI was told."

    /** Step titles for the timeline. Every sentence the agent surface shows. */
    const val AGENT_STEP_ANSWER = "AI step"
    const val AGENT_STEP_MALFORMED = "Tool block could not be read"
    const val AGENT_RUN_APPROVED = "You approved the run"
    fun agentRunApproved(target: String?): String =
        AGENT_RUN_APPROVED + (if (target.isNullOrBlank()) "" else " ($target)")
    const val AGENT_RUN_SKIPPED = "Run skipped by the user"
    const val AGENT_RUN_SKIPPED_MODEL =
        "The user skipped the run. Continue without it; do not ask to run again unless nothing else can answer the task."
    const val AGENT_RUN_NOT_STARTED_TITLE = "Run did not start"
    const val AGENT_RUN_FINISHED = "Run finished"
    fun agentStepAnswer(number: Int): String = "$AGENT_STEP_ANSWER $number"
    fun agentStepDenied(title: String, reason: String): String = "Refused $title — $reason"

    /** Shown when the task ends because of a cap or the user. */
    fun agentStopped(reason: AiAgentStopReason): String = AiAgentLimits.stopSentence(reason)

    /** Shown in the sheet while the project is being read. */
    const val GATHERING = "Reading the project…"

    /** The preview's heading for a project request. */
    const val PROJECT_PREVIEW_TITLE = "Files that will be sent"

    fun projectPreviewHeader(projectName: String, files: Int, chars: Int): String =
        "From project $projectName · $files " +
            (if (files == 1) "file" else "files") + " · $chars characters."

    /** Why a project question could not be built — one short line each. */
    const val NO_PROJECT_FILES =
        "This project has no code or text file to send. Open a project with source files."
    const val PROJECT_TOO_LARGE =
        "This project is too large to fit one question. Open the file you mean and use Explain selection."

    // The user's choice for opening chat while the Output panel is open (owner: both stay).
    const val VARIANT_TITLE = "When the Output panel is open, AI chat"
    const val VARIANT_A = "Replaces the Output panel (bottom half)"
    const val VARIANT_B = "Opens full screen"
    const val VARIANT_NOTE = "Your Output panel is not closed or changed either way; it comes back when the chat closes."

    // ---- settings ---------------------------------------------------------

    const val SETTINGS = "AI settings"
    const val SAVE_MODEL = "Save model"
    const val TEST = "Test connection"
    const val TEST_NOTE = "Sends a short test message to Google. No code is sent."
    const val TEST_OK = "Connected. Gemini answered."
    const val DELETE_KEY = "Delete key"
    const val DELETE_NOTE = "Deleting the key also clears your terms confirmation."

    // ---- problems ---------------------------------------------------------

    const val SETUP_INCOMPLETE = "Enter a key and tick the confirmation first."
    const val MODEL_INVALID = "Model names use lowercase letters, digits, dots and dashes (for example gemini-3-flash-preview)."
    const val SAVE_FAILED = "Couldn't save securely on this phone. Try again."
    const val KEY_UNREADABLE = "Your saved key can't be read anymore. Please enter it again."

    fun problem(p: AiContextProblem): String = when (p) {
        AiContextProblem.NO_SELECTION -> "Select some code in the editor first, then tap Explain selection."
        AiContextProblem.SELECTION_TOO_LONG ->
            "The selection is too long (max ${AiLimits.MAX_CONTEXT_CHARS} characters). Select a smaller part."
        AiContextProblem.NO_FAILED_RUN -> "There is no failed run to explain. Run your code first."
        AiContextProblem.QUESTION_TOO_LONG -> "Your question is too long (max ${AiLimits.MAX_QUESTION_CHARS} characters)."
        AiContextProblem.NO_PROJECT_FILES -> NO_PROJECT_FILES
        AiContextProblem.PROJECT_TOO_LARGE -> PROJECT_TOO_LARGE
        AiContextProblem.EMPTY_EDIT_QUESTION -> EDIT_QUESTION_REQUIRED
    }
}
