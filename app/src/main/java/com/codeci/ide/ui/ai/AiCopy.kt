package com.codeci.ide.ui.ai

/**
 * Phase 76 — every sentence the AI panel shows, in one pure place so tests
 * can pin the disclosures the owner's decisions require (`AiPolicyTest`):
 * O1's own-key/terms text and 18+ checkbox, D2's free-tier note, D1's
 * read-only line. Google's terms are linked, quoted by name, never
 * interpreted.
 */
object AiCopy {

    const val TITLE = "AI helper · Gemini"
    const val READ_ONLY = "Read-only: it explains code and errors. It never changes files or runs anything."

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
    const val SELECTION_HINT = "Select code in the editor, then ask. You always see what will be sent before it leaves your phone."

    fun sheetTitle(model: String): String = "AI · $model"

    /** The user's side of the exchange, from the prompt the preview was built from. */
    fun youLine(source: AiSource, fileLabel: String, question: String): String {
        val head = (if (source == AiSource.SELECTION) EXPLAIN_SELECTION else EXPLAIN_ERROR) + " · " + fileLabel
        return if (question.isBlank()) head else head + "\n" + question.trim()
    }

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
    }
}
