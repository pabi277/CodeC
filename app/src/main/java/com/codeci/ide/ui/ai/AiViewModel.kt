package com.codeci.ide.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where one helper exchange stands. */
enum class AiPhase { IDLE, PREVIEW, STREAMING, DONE, FAILED }

/**
 * Everything the panel draws. **In memory only** (D6): no field here is ever
 * written to disk, and the API key is never part of it.
 */
data class AiUiState(
    val keySaved: Boolean = false,
    val model: String = AiModel.DEFAULT,
    val phase: AiPhase = AiPhase.IDLE,
    val prompt: AiPrompt? = null,
    val answer: String = "",
    val cutShort: Boolean = false,
    val error: String? = null,
    /** A one-line reason a prompt could not be built (no selection, …). */
    val notice: String? = null,
    val testing: Boolean = false,
    val testResult: String? = null,
    val setupError: String? = null,
    /** Phase 77.2 — the chat sheet. Never persisted (minimize keeps the exchange; nothing else does). */
    val sheet: AiSheetState = AiSheetState.HIDDEN,
    /** Phase 77.1 — the two values the AI surface saves (owner Q2), loaded once at start. */
    val bubble: AiBubblePosition = AiBubblePolicy.DEFAULT,
    val showBubble: Boolean = true,
    /** Phase 77 device round only (owner Q3): which Output-conflict variant is being tried. In memory. */
    val outputConflict: AiOutputConflict = AiSheetPolicy.DEFAULT_CONFLICT
)

/**
 * Phase 76 — the AI panel's state holder. One request at a time; the answer
 * lives only here and is cleared by [clear], a project switch
 * ([onProjectChanged]) or process death. The key is decrypted for one request
 * inside [send]/[testConnection] and dropped when the call returns.
 */
class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AiKeyStore(application)
    private val client = GeminiClient()
    private var job: Job? = null
    private var project: String? = null

    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { store.isReady() }
            val model = withContext(Dispatchers.IO) { store.model() }
            val bubble = withContext(Dispatchers.IO) { store.bubble() }
            val show = withContext(Dispatchers.IO) { store.showBubble() }
            _state.update { it.copy(keySaved = ready, model = model, bubble = bubble, showBubble = show) }
        }
    }

    // ---- Phase 77: the chat sheet (surface state only; requests are unchanged) ----

    /**
     * Opens the sheet from the bubble, "Open AI chat" or "Explain with AI".
     * [outputOpen] is the Output panel's own expanded flag; the policy picks
     * HALF (variant A) or FULL (variant B) when it is open. Minimizing later
     * never clears the exchange.
     */
    fun openSheet(outputOpen: Boolean) {
        _state.update { s ->
            if (s.sheet != AiSheetState.HIDDEN) s
            else s.copy(sheet = AiSheetPolicy.openWithOutput(s.outputConflict, outputOpen).first)
        }
    }

    fun sheetEvent(event: AiSheetEvent, dragFraction: Float? = null) {
        _state.update { it.copy(sheet = AiSheetPolicy.next(it.sheet, event, dragFraction)) }
    }

    fun closeSheet() = sheetEvent(AiSheetEvent.MINIMIZE)

    fun setOutputConflict(conflict: AiOutputConflict) = _state.update { it.copy(outputConflict = conflict) }

    // ---- Phase 77.1: the floating button's two saved choices ----

    fun saveBubblePosition(p: AiBubblePosition) {
        _state.update { it.copy(bubble = p) }
        viewModelScope.launch { withContext(Dispatchers.IO) { store.setBubble(p) } }
    }

    fun setShowBubble(show: Boolean) {
        _state.update { it.copy(showBubble = show) }
        viewModelScope.launch { withContext(Dispatchers.IO) { store.setShowBubble(show) } }
    }

    /** D6: a different project never sees the previous project's exchange. */
    fun onProjectChanged(name: String?) {
        if (name == project) return
        project = name
        clear()
        closeSheet()
    }

    /** Shows the exact text that would be sent (D4). Nothing leaves the device here. */
    fun preview(result: AiContextResult) {
        // A stream in flight is never replaced behind its back (Phase 77: the
        // sheet can be re-opened from several doors while it runs).
        if (_state.value.phase == AiPhase.STREAMING) return
        when (result) {
            is AiContextResult.Refused -> _state.update { it.copy(notice = AiCopy.problem(result.problem)) }
            is AiContextResult.Ready -> _state.update {
                it.copy(
                    phase = AiPhase.PREVIEW, prompt = result.prompt, notice = null,
                    answer = "", error = null, cutShort = false
                )
            }
        }
    }

    fun cancelPreview() {
        _state.update { it.copy(phase = AiPhase.IDLE, prompt = null) }
    }

    /** The only path to a helper request: the user pressed Send on a preview. */
    fun send() {
        val prompt = _state.value.prompt ?: return
        if (_state.value.phase != AiPhase.PREVIEW || job?.isActive == true) return
        val model = _state.value.model
        _state.update { it.copy(phase = AiPhase.STREAMING, answer = "", error = null, cutShort = false) }
        job = viewModelScope.launch {
            val key = withContext(Dispatchers.IO) { store.loadKey() }
            if (key == null) {
                _state.update {
                    it.copy(phase = AiPhase.FAILED, keySaved = false, error = AiCopy.KEY_UNREADABLE)
                }
                return@launch
            }
            val outcome = client.stream(key, model, GeminiRequest.body(prompt)) { text ->
                _state.update { s -> if (s.phase == AiPhase.STREAMING) s.copy(answer = text) else s }
            }
            _state.update { s ->
                if (s.phase != AiPhase.STREAMING) return@update s
                when (outcome) {
                    is AiOutcome.Answer -> s.copy(phase = AiPhase.DONE, answer = outcome.text, cutShort = outcome.cutShort)
                    is AiOutcome.Failed -> s.copy(phase = AiPhase.FAILED, error = outcome.failure.message)
                }
            }
        }
    }

    /** Stop: disconnects the request; what arrived so far stays visible. */
    fun stop() {
        job?.cancel()
        job = null
        _state.update { s ->
            if (s.phase != AiPhase.STREAMING) s
            else if (s.answer.isBlank()) s.copy(phase = AiPhase.IDLE, prompt = null)
            else s.copy(phase = AiPhase.DONE, cutShort = true)
        }
    }

    /** "Try again" goes back through the preview: every request is confirmed (D4). */
    fun retry() {
        if (_state.value.prompt == null) return
        _state.update { it.copy(phase = AiPhase.PREVIEW, error = null, answer = "", cutShort = false) }
    }

    fun clear() {
        job?.cancel()
        job = null
        _state.update {
            it.copy(
                phase = AiPhase.IDLE, prompt = null, answer = "", error = null,
                cutShort = false, notice = null, testResult = null, testing = false
            )
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** O1: only after the 18+/terms checkbox (the panel also disables the button). */
    fun saveKey(rawKey: String, model: String, confirmedAdultAndTerms: Boolean) {
        if (!AiKeySetup.canSave(rawKey, confirmedAdultAndTerms)) {
            _state.update { it.copy(setupError = AiCopy.SETUP_INCOMPLETE) }
            return
        }
        if (!AiModel.isValid(model)) {
            _state.update { it.copy(setupError = AiCopy.MODEL_INVALID) }
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { store.saveKey(rawKey, model) }
            val storedModel = withContext(Dispatchers.IO) { store.model() }
            _state.update {
                if (ok) it.copy(keySaved = true, model = storedModel, setupError = null)
                else it.copy(setupError = AiCopy.SAVE_FAILED)
            }
        }
    }

    fun saveModel(model: String) {
        if (!AiModel.isValid(model)) {
            _state.update { it.copy(setupError = AiCopy.MODEL_INVALID) }
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { store.setModel(model) }
            _state.update {
                if (ok) it.copy(model = AiModel.normalize(model), setupError = null, testResult = null)
                else it.copy(setupError = AiCopy.SAVE_FAILED)
            }
        }
    }

    fun deleteKey() {
        clear()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.deleteKey() }
            _state.update { it.copy(keySaved = false, testResult = null, sheet = AiSheetState.HIDDEN) }
        }
    }

    /** Sends [GeminiRequest.TEST_PROMPT] only — no code — after a tap (D4). */
    fun testConnection() {
        if (_state.value.testing || job?.isActive == true) return
        val model = _state.value.model
        _state.update { it.copy(testing = true, testResult = null) }
        job = viewModelScope.launch {
            val key = withContext(Dispatchers.IO) { store.loadKey() }
            val result = if (key == null) {
                AiCopy.KEY_UNREADABLE
            } else {
                when (val outcome = client.stream(key, model, GeminiRequest.testBody()) { }) {
                    is AiOutcome.Answer -> AiCopy.TEST_OK
                    // A test prompt that comes back empty still proves key + model + network.
                    is AiOutcome.Failed ->
                        if (outcome.failure.kind == AiFailureKind.EMPTY) AiCopy.TEST_OK else outcome.failure.message
                }
            }
            _state.update { it.copy(testing = false, testResult = result, keySaved = key != null && it.keySaved) }
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
