package com.codeci.ide.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.ui.projects.AiApplyOutcome
import com.codeci.ide.ui.projects.AiEditApplier
import com.codeci.ide.ui.projects.AiUndoOutcome
import com.codeci.ide.ui.projects.AiUndoSummary
import com.codeci.ide.ui.projects.ProjectManager
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
    val outputConflict: AiOutputConflict = AiSheetPolicy.DEFAULT_CONFLICT,
    /** Phase 78 — the project is being read on IO. Nothing has been sent (D4). */
    val gathering: Boolean = false,
    /** Phase 79 (Level 3) — parsed edit proposal from a PROPOSE_EDITS reply. */
    val proposalResult: AiProposalResult? = null,
    /** Phase 79 — target paths that drifted since the proposal baseline. */
    val applyConflictPaths: List<String> = emptyList(),
    /** Phase 79 — summary of the 1-task preimage journal for the open project. */
    val undoSummary: AiUndoSummary? = null,
    /** Phase 79 — task paths edited by the user after the AI change was applied. */
    val undoConflictPaths: List<String> = emptyList(),
    /** Phase 79 — an approved apply or undo is running on IO. */
    val applying: Boolean = false
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

    /** Phase 78 — the project walk. Separate from [job]: it never touches the network. */
    private var gatherJob: Job? = null
    private var project: String? = null

    /** Phase 79 — baseline snapshot captured when a PROPOSE_EDITS prompt is built. */
    private var pendingBaselines: Map<String, AiFileBaseline> = emptyMap()
    private var pendingExistingPaths: Set<String> = emptySet()

    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { store.isReady() }
            val model = withContext(Dispatchers.IO) { store.model() }
            val bubble = withContext(Dispatchers.IO) { store.bubble() }
            val show = withContext(Dispatchers.IO) { store.showBubble() }
            val conflict = withContext(Dispatchers.IO) { store.outputConflict() }
            _state.update {
                it.copy(keySaved = ready, model = model, bubble = bubble, showBubble = show, outputConflict = conflict)
            }
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

    fun setOutputConflict(conflict: AiOutputConflict) {
        _state.update { it.copy(outputConflict = conflict) }
        viewModelScope.launch { withContext(Dispatchers.IO) { store.setOutputConflict(conflict) } }
    }

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
        pendingBaselines = emptyMap()
        pendingExistingPaths = emptySet()
        clear()
        closeSheet()
        _state.update { it.copy(undoSummary = null, undoConflictPaths = emptyList()) }
        if (name != null) {
            viewModelScope.launch {
                val summary = withContext(Dispatchers.IO) {
                    runCatching {
                        AiEditApplier.readUndoSummary(getApplication<Application>().noBackupFilesDir, name)
                    }.getOrNull()
                }
                _state.update { s ->
                    if (project == name) s.copy(undoSummary = summary) else s
                }
            }
        }
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
                    answer = "", error = null, cutShort = false,
                    proposalResult = null, applyConflictPaths = emptyList(),
                    undoConflictPaths = emptyList()
                )
            }
        }
    }

    fun cancelPreview() {
        _state.update { it.copy(phase = AiPhase.IDLE, prompt = null) }
    }

    /**
     * Phase 78 (Level 2) — build the PREVIEW for a whole-project question.
     *
     * Reads the project on [Dispatchers.IO] (a walk plus at most
     * [AiProjectFiles.READ_SHORTLIST] file reads, both bounded by
     * [AiProjectFiles]), then hands the packed plan to
     * [AiContextBuilder.fromProject]. **Nothing is sent here** — the result
     * goes through the same [preview] gate as the other two sources, and only
     * Send on that preview reaches the network (D4).
     *
     * The open tab's live buffer is passed in and used when it is dirty, so a
     * file the user is editing is never described from stale disk bytes
     * (`02_WHOLE_PROJECT_CONTEXT.md`, "Editor consistency").
     */
    fun askProject(question: String, openPath: String?, openText: String?, openDirty: Boolean) {
        val s = _state.value
        if (s.phase == AiPhase.STREAMING || s.gathering) return
        val q = question.trim()
        if (q.length > AiLimits.MAX_QUESTION_CHARS) {
            _state.update { it.copy(notice = AiCopy.problem(AiContextProblem.QUESTION_TOO_LONG)) }
            return
        }
        val projectName = project
        if (projectName == null) {
            _state.update { it.copy(notice = AiCopy.NEEDS_PROJECT) }
            return
        }
        _state.update { it.copy(gathering = true, notice = null) }
        gatherJob = viewModelScope.launch {
            val root = withContext(Dispatchers.IO) {
                runCatching { ProjectManager(getApplication()).project(projectName)?.root }.getOrNull()
            }
            if (root == null) {
                _state.update { it.copy(gathering = false, notice = AiCopy.NEEDS_PROJECT) }
                return@launch
            }
            val scan = withContext(Dispatchers.IO) {
                runCatching {
                    AiProjectReader.scan(root, q, openPath, openText, openDirty)
                }.getOrNull()
            }
            if (scan == null) {
                _state.update { it.copy(gathering = false, notice = AiCopy.NO_PROJECT_FILES) }
                return@launch
            }
            val plan = AiProjectFiles.plan(scan.candidates, q, openPath)
            val result = AiContextBuilder.fromProject(
                plan = plan,
                question = q,
                projectName = projectName,
                scannedFiles = scan.filesSeen,
                skippedSecret = scan.skippedSecret,
                skippedNotText = scan.skippedNotText,
                hitEntryCap = scan.hitEntryCap
            )
            _state.update { it.copy(gathering = false) }
            preview(result)
        }
    }

    /** Stops waiting on the walk. No request was in flight, so nothing to cancel. */
    fun cancelGather() {
        gatherJob?.cancel()
        gatherJob = null
        _state.update { it.copy(gathering = false) }
    }

    /**
     * Phase 79 (Level 3) — build the PREVIEW for a multi-file edit proposal.
     *
     * Walks the open project on [Dispatchers.IO], captures the baseline state
     * of the candidate files at proposal time, and hands the packed plan to
     * [AiContextBuilder.fromProposeEdits]. **Nothing is sent here** — the
     * result goes through the same [preview] gate as every other source (D4).
     */
    fun proposeEdits(question: String, openPath: String?, openText: String?, openDirty: Boolean) {
        val s = _state.value
        if (s.phase == AiPhase.STREAMING || s.gathering || s.applying) return
        val q = question.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(notice = AiCopy.problem(AiContextProblem.EMPTY_EDIT_QUESTION)) }
            return
        }
        if (q.length > AiLimits.MAX_QUESTION_CHARS) {
            _state.update { it.copy(notice = AiCopy.problem(AiContextProblem.QUESTION_TOO_LONG)) }
            return
        }
        val projectName = project
        if (projectName == null) {
            _state.update { it.copy(notice = AiCopy.NEEDS_PROJECT) }
            return
        }
        _state.update { it.copy(gathering = true, notice = null) }
        gatherJob = viewModelScope.launch {
            val root = withContext(Dispatchers.IO) {
                runCatching { ProjectManager(getApplication()).project(projectName)?.root }.getOrNull()
            }
            if (root == null) {
                _state.update { it.copy(gathering = false, notice = AiCopy.NEEDS_PROJECT) }
                return@launch
            }
            val scan = withContext(Dispatchers.IO) {
                runCatching {
                    AiProjectReader.scan(root, q, openPath, openText, openDirty)
                }.getOrNull()
            }
            if (scan == null) {
                _state.update { it.copy(gathering = false, notice = AiCopy.NO_PROJECT_FILES) }
                return@launch
            }
            val plan = AiProjectFiles.plan(scan.candidates, q, openPath)
            val includedByPath = plan.included.associateBy { it.relativePath }
            val baselines = LinkedHashMap<String, AiFileBaseline>()
            for (c in scan.candidates) {
                val inc = includedByPath[c.relativePath]
                baselines[c.relativePath] = AiFileBaseline(
                    path = c.relativePath,
                    exists = true,
                    content = AiEditProposalParser.normalizeLf(c.text),
                    cut = inc?.cut ?: true,
                    fromBuffer = c.fromBuffer
                )
            }
            pendingBaselines = baselines
            pendingExistingPaths = scan.allTextPaths.toSet()
            val result = AiContextBuilder.fromProposeEdits(
                plan = plan,
                question = q,
                projectName = projectName,
                scannedFiles = scan.filesSeen,
                skippedSecret = scan.skippedSecret,
                skippedNotText = scan.skippedNotText,
                hitEntryCap = scan.hitEntryCap
            )
            _state.update { it.copy(gathering = false) }
            preview(result)
        }
    }

    /** Phase 79 — flips the per-file review checkbox for [path]. */
    fun toggleProposalFile(path: String) {
        _state.update { s ->
            val current = s.proposalResult as? AiProposalResult.Proposal ?: return@update s
            s.copy(
                proposalResult = AiProposalResult.Proposal(current.proposal.toggleFile(path)),
                applyConflictPaths = emptyList()
            )
        }
    }

    /** Phase 79 — rejects the current proposal without touching any project file. */
    fun rejectProposal() {
        _state.update {
            it.copy(
                proposalResult = null,
                applyConflictPaths = emptyList(),
                notice = AiCopy.PROPOSAL_REJECTED
            )
        }
    }

    /**
     * Phase 79 — applies the user-approved files from the current proposal
     * through [AiEditApplier.apply] on [Dispatchers.IO], then invokes
     * [onApplied] on the main thread so `EditorViewModel` refreshes open tabs,
     * the file tree, and Git badges.
     */
    fun applyProposal(
        dirtyBuffers: Map<String, String>,
        onApplied: (updatedPaths: List<String>, deletedPaths: List<String>) -> Unit
    ) {
        val current = _state.value
        if (current.applying) return
        val proposal = (current.proposalResult as? AiProposalResult.Proposal)?.proposal ?: return
        val projectName = project ?: return
        _state.update { it.copy(applying = true, notice = null, applyConflictPaths = emptyList()) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val root = withContext(Dispatchers.IO) {
                runCatching { ProjectManager(app).project(projectName)?.root }.getOrNull()
            }
            if (root == null) {
                _state.update { it.copy(applying = false, notice = AiCopy.NEEDS_PROJECT) }
                return@launch
            }
            val outcome = withContext(Dispatchers.IO) {
                AiEditApplier.apply(
                    projectRoot = root,
                    noBackupRoot = app.noBackupFilesDir,
                    projectName = projectName,
                    proposal = proposal,
                    dirtyBuffers = dirtyBuffers
                )
            }
            when (outcome) {
                is AiApplyOutcome.Applied -> {
                    onApplied(outcome.appliedPaths, outcome.deletedPaths)
                    val totalChanged = outcome.appliedPaths.size + outcome.deletedPaths.size
                    _state.update {
                        it.copy(
                            applying = false,
                            proposalResult = null,
                            applyConflictPaths = emptyList(),
                            undoSummary = outcome.undoSummary,
                            undoConflictPaths = emptyList(),
                            notice = AiCopy.appliedNotice(totalChanged)
                        )
                    }
                }
                is AiApplyOutcome.BaselineConflict -> {
                    _state.update {
                        it.copy(applying = false, applyConflictPaths = outcome.stalePaths)
                    }
                }
                is AiApplyOutcome.Failed -> {
                    _state.update {
                        it.copy(applying = false, notice = outcome.message)
                    }
                }
            }
        }
    }

    /**
     * Phase 79 — undoes the last applied AI task for the open project through
     * [AiEditApplier.undo]. When [force] is false and the user edited an
     * affected file after the AI change, pauses with `undoConflictPaths`
     * instead of overwriting user work.
     */
    fun undoLastTask(
        dirtyPaths: Set<String>,
        force: Boolean,
        onUndone: (restoredPaths: List<String>, deletedCreatedPaths: List<String>) -> Unit
    ) {
        val current = _state.value
        if (current.applying) return
        val projectName = project ?: return
        _state.update { it.copy(applying = true, notice = null, undoConflictPaths = emptyList()) }
        viewModelScope.launch {
            val app = getApplication<Application>()
            val root = withContext(Dispatchers.IO) {
                runCatching { ProjectManager(app).project(projectName)?.root }.getOrNull()
            }
            if (root == null) {
                _state.update { it.copy(applying = false, notice = AiCopy.NEEDS_PROJECT) }
                return@launch
            }
            val outcome = withContext(Dispatchers.IO) {
                AiEditApplier.undo(
                    projectRoot = root,
                    noBackupRoot = app.noBackupFilesDir,
                    projectName = projectName,
                    dirtyPaths = dirtyPaths,
                    force = force
                )
            }
            when (outcome) {
                is AiUndoOutcome.Restored -> {
                    onUndone(outcome.restoredPaths, outcome.deletedCreatedPaths)
                    val count = outcome.restoredPaths.size + outcome.deletedCreatedPaths.size
                    _state.update {
                        it.copy(
                            applying = false,
                            undoSummary = null,
                            undoConflictPaths = emptyList(),
                            notice = AiCopy.undoneNotice(count)
                        )
                    }
                }
                is AiUndoOutcome.UserEditedConflict -> {
                    _state.update {
                        it.copy(applying = false, undoConflictPaths = outcome.modifiedPaths)
                    }
                }
                AiUndoOutcome.NothingToUndo -> {
                    _state.update {
                        it.copy(applying = false, undoSummary = null, undoConflictPaths = emptyList())
                    }
                }
                is AiUndoOutcome.Failed -> {
                    _state.update {
                        it.copy(applying = false, notice = outcome.message)
                    }
                }
            }
        }
    }

    fun dismissUndoConflict() {
        _state.update { it.copy(undoConflictPaths = emptyList()) }
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
                    is AiOutcome.Answer -> {
                        val parsed = if (prompt.source == AiSource.PROPOSE_EDITS) {
                            AiEditProposalParser.parse(outcome.text, pendingBaselines, pendingExistingPaths)
                        } else {
                            null
                        }
                        s.copy(
                            phase = AiPhase.DONE,
                            answer = outcome.text,
                            cutShort = outcome.cutShort,
                            proposalResult = parsed,
                            applyConflictPaths = emptyList()
                        )
                    }
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
            else {
                val parsed = if (s.prompt?.source == AiSource.PROPOSE_EDITS) {
                    AiEditProposalParser.parse(s.answer, pendingBaselines, pendingExistingPaths)
                } else {
                    null
                }
                s.copy(phase = AiPhase.DONE, cutShort = true, proposalResult = parsed)
            }
        }
    }

    /** "Try again" goes back through the preview: every request is confirmed (D4). */
    fun retry() {
        if (_state.value.prompt == null) return
        _state.update {
            it.copy(
                phase = AiPhase.PREVIEW, error = null, answer = "", cutShort = false,
                proposalResult = null, applyConflictPaths = emptyList()
            )
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        gatherJob?.cancel()
        gatherJob = null
        _state.update {
            it.copy(
                phase = AiPhase.IDLE, prompt = null, answer = "", error = null,
                cutShort = false, notice = null, testResult = null, testing = false,
                gathering = false, proposalResult = null, applyConflictPaths = emptyList(),
                undoConflictPaths = emptyList()
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
            _state.update {
                it.copy(
                    keySaved = false,
                    testResult = null,
                    sheet = AiSheetState.HIDDEN,
                    undoSummary = null,
                    undoConflictPaths = emptyList()
                )
            }
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
        gatherJob?.cancel()
        super.onCleared()
    }
}
