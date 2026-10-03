package com.codeci.ide.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeci.ide.ui.projects.AiApplyOutcome
import com.codeci.ide.ui.projects.AiEditApplier
import com.codeci.ide.ui.projects.AiUndoOutcome
import com.codeci.ide.ui.projects.AiUndoSummary
import com.codeci.ide.ui.projects.AiTaskMemoryStore
import com.codeci.ide.ui.projects.ProjectManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

/** Where one helper exchange stands. */
enum class AiPhase { IDLE, PREVIEW, STREAMING, DONE, FAILED }

/**
 * Everything the panel draws. Chat, prompts, answers and timeline stay in
 * memory (D6); Level 9's separate bounded derived task memory is not UI state.
 * The API key is never part of this object.
 */
data class AiUiState(
    val keySaved: Boolean = false,
    val model: String = AiModel.DEFAULT,
    /** Phase 82B: manual selection, never persisted or silently changed on failure. */
    val provider: AiProviderId = AiProviderId.GEMINI,
    val configuring: Boolean = true,
    /** Phase 82: plain additive field; the countdown type is declared outside this state. */
    val retryCountdown: AiRetryCountdown? = null,
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
    /**
     * Phase 87 (Level 10) — the nine bounded agent controls. Loaded once at
     * start beside the other saved AI preferences; **S9**: none of them can
     * raise a permission, and each is clamped by [AiOptionsPolicy].
     */
    val options: AiOptions = AiOptionsPolicy.DEFAULT,
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
    val applying: Boolean = false,
    /**
     * Phase 81 — how many times the current answer has been continued after it
     * was cut short. In memory only (D6); reset by every fresh preview, by
     * [clear], and by a new question.
     */
    val continuations: Int = 0,
    /**
     * Phase 80 (Level 4) — the visible activity timeline of the current agent
     * task: one row per model step, tool read, refusal, run request and run
     * result. **In memory only** (D6); it disappears with [clear], a project
     * switch, or process death, and it is never written anywhere.
     */
    val agentSteps: List<AiAgentStep> = emptyList(),
    /**
     * Phase 80 — non-null while the AI is waiting for the user's Run/Skip
     * decision. The loop is paused: no request is in flight and nothing runs.
     */
    val agentRun: AiAgentRunRequest? = null,
    /** Phase 80 — true between the user's Run tap and the run's result. */
    val agentRunRunning: Boolean = false,
    /** Phase 80 — the caps in use for the running task, for the sheet's counter. */
    val agentUsage: AiAgentUsage? = null,
    /**
     * Phase 87 (Level 10, 87.6) — non-null while a budget-extension offer stands
     * on the sheet. The task has stopped and its answer is already prose; the
     * offer only lets the user buy a little more **read-only** budget. In memory
     * only (D6). `runs` is not part of this state and cannot be raised (**S9**).
     */
    val budgetOffer: AiBudgetOfferState? = null,
    /**
     * Phase 87 (Level 10, 87.7) — non-null while a **manual** backup-provider
     * offer stands, after a failure the provider itself caused. Accepting
     * switches the recipient and rebuilds the preview; it sends nothing (D4).
     * In memory only.
     */
    val backupOffer: AiProviderId? = null,
    /**
     * Phase 87 (Level 10, 87.8) — the read-only second opinion, once it arrives.
     * It is **displayed, never parsed into a call**: [AiReviewerPolicy.parse]
     * decides whether the text merely looks like markup, and either way the same
     * characters are shown. Kept apart from [answer] so asking for a review
     * never overwrites the answer being reviewed. In memory only (D6).
     */
    val review: AiReviewVerdict? = null,
    /**
     * Phase 87 (Level 10, 87.8) — the answer the review was asked about, kept so
     * that asking for a second opinion does not delete the first one. The review
     * preview clears [answer] like every other preview does. In memory only (D6).
     */
    val reviewedAnswer: String? = null
)

/** Phase 80 — the AI's pending run request, as the approval card renders it. */
data class AiAgentRunRequest(val target: String?)

/** Phase 80 — a snapshot of the task's usage counters (in memory, display only). */
data class AiAgentUsage(
    val turns: Int,
    val toolCalls: Int,
    val runs: Int,
    /** Phase 84 (fix 3): refused blocks, counted separately from executions. */
    val refused: Int = 0,
    /** Phase 84 (fix 3): duplicate reads served from a working set (0 until Level 9). */
    val reused: Int = 0,
    /**
     * Phase 87 (Level 10, 87.6): the caps this task actually ran under. They
     * equal the constants unless the user accepted one budget extension, and
     * the counter must render against them, not against the constants.
     */
    val turnCap: Int = AiAgentLimits.MAX_TURNS,
    val readCap: Int = AiAgentLimits.MAX_TOOL_CALLS
)

/**
 * Phase 76 — the AI panel's state holder. One request at a time; the answer
 * lives only here and is cleared by [clear], a project switch
 * ([onProjectChanged]) or process death. The key is decrypted for one request
 * inside [send]/[testConnection] and dropped when the call returns.
 */
class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AiKeyStore(application)
    private val client = AiProviderClient()
    private var job: Job? = null

    /** Phase 78 — the project walk. Separate from [job]: it never touches the network. */
    private var gatherJob: Job? = null
    private var project: String? = null

    /** Phase 79 — baseline snapshot captured when a PROPOSE_EDITS prompt is built. */
    private var pendingBaselines: Map<String, AiFileBaseline> = emptyMap()
    private var pendingExistingPaths: Set<String> = emptySet()

    /**
     * Phase 80/86 — the running agent task, or null. Chat, requests, answers,
     * timeline and exact-result cache are in memory only. The separate bounded
     * [AiTaskMemory] contains only admitted file snapshots and structured notes;
     * it is revalidated before use and never contains raw prompts/transcripts.
     */
    private class AgentSession(
        val question: String,
        val source: AiSource,
        val root: File,
        val mapText: String,
        val memoryStore: AiTaskMemoryStore,
        var memory: AiTaskMemory,
        /** The Level 2 walk's admitted code/text paths — the tool surface's whole world. */
        val paths: List<String>,
        var dirtyBuffers: Map<String, String>,
        val systemInstruction: String,
        val provider: AiProviderId,
        val model: String,
        var budget: AiAgentBudget,
        var pendingRun: AiToolCall? = null,
        /** Tool calls that arrived in the same answer as a run request. */
        var queuedAfterRun: List<AiToolCall> = emptyList(),
        var queuedDeniedAfterRun: List<AiToolVerdict.Denied> = emptyList(),
        /**
         * Phase 84 (fix 4): the reserved final-synthesis turn runs at most once
         * per task, so a budget stop can never loop back into another synthesis.
         */
        var synthesisDone: Boolean = false,
        /**
         * Phase 85 (Level 8, S11): the loop-local working set. Exact-duplicate reads
         * are served from it at zero execution cost, and a run of identical
         * no-progress calls stops the loop. Born with the task, gone when it ends.
         */
        var workingSet: AiAgentWorkingSet = AiAgentWorkingSet(),
        var memoryPersistenceFailed: Boolean = false,
        /**
         * Phase 87 (Level 10): the controls **frozen at Send**. A settings change
         * mid-task must not alter the window, depth or detail of an in-flight
         * request, because the disclosed preview would then describe a different
         * request than the one performed (D4).
         */
        val options: AiOptions = AiOptionsPolicy.DEFAULT,
        /** Level 10 (87.6): how many read-only budget extensions this task has used. */
        var extensionsUsed: Int = 0,
        /** Level 10 (87.6): the caps in force, raised only by an accepted extension. */
        var caps: AiAgentCaps = AiAgentCaps(),
        /**
         * Level 10 (87.7): each provider's standing, read once at Send so the
         * backup-provider offer can be decided without a disk read on the stop
         * path. Accepting re-verifies on IO before switching — this snapshot
         * only decides whether to *offer*, never whether to send.
         */
        val providerReadiness: Map<AiProviderId, AiProviderReadiness> = emptyMap()
    ) {
        val projectView: AiToolProjectView
            get() = AiToolProjectView(existingPaths = paths.toSet(), runsRemaining = budget.runsRemaining())
    }

    private var agent: AgentSession? = null

    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { store.isReady() }
            val model = withContext(Dispatchers.IO) { store.model() }
            val bubble = withContext(Dispatchers.IO) { store.bubble() }
            val show = withContext(Dispatchers.IO) { store.showBubble() }
            val conflict = withContext(Dispatchers.IO) { store.outputConflict() }
            val options = withContext(Dispatchers.IO) { store.options() }
            _state.update {
                it.copy(keySaved = ready, model = model, bubble = bubble, showBubble = show, outputConflict = conflict, options = options, configuring = false)
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

    // ---- Phase 87 (Level 10): the nine bounded agent controls ---------------
    // One setter per control, each clamped by AiOptionsPolicy before it is
    // stored, so a tampered properties file cannot produce an out-of-range
    // value either (**S9**). *Request inspection* deliberately has no setter:
    // it is always on (D4).

    fun setReadWindow(lines: Int) = updateOption(
        { it.copy(readWindowLines = AiOptionsPolicy.clampReadWindow(lines)) },
        { store.setReadWindow(lines) }
    )

    fun setWorkingSetDepth(depth: Int) = updateOption(
        { it.copy(workingSetDepth = AiOptionsPolicy.clampWorkingSetDepth(depth)) },
        { store.setWorkingSetDepth(depth) }
    )

    /**
     * Off means **forget**, not pause: the store clears the retained copy
     * (Level 9's own behaviour at `AiTaskMemoryStore`), and the live task drops
     * its in-memory ledger so the UI reflects it immediately.
     */
    fun setTaskMemory(on: Boolean) = updateOption(
        { it.copy(taskMemory = on) },
        { store.setTaskMemory(on) }
    ) {
        if (!on) dropLiveTaskMemory()
    }

    fun setAnswerDetail(detail: AiAnswerDetail) = updateOption(
        { it.copy(answerDetail = detail) },
        { store.setAnswerDetail(detail) }
    )

    fun setActivity(display: AiActivityDisplay) = updateOption(
        { it.copy(activity = display) },
        { store.setActivity(display) }
    )

    fun setBackupMode(mode: AiBackupMode) = updateOption(
        { it.copy(backup = mode) },
        { store.setBackupMode(mode) }
    )

    fun setBudgetOffer(offer: AiBudgetOffer) = updateOption(
        { it.copy(budgetOffer = offer) },
        { store.setBudgetOffer(offer) }
    )

    fun setReviewer(reviewer: AiReviewer) = updateOption(
        { it.copy(reviewer = reviewer) },
        { store.setReviewer(reviewer) }
    )

    private fun updateOption(
        change: (AiOptions) -> AiOptions,
        persist: () -> Boolean,
        after: () -> Unit = {}
    ) {
        _state.update { it.copy(options = change(it.options)) }
        after()
        viewModelScope.launch { withContext(Dispatchers.IO) { persist() } }
    }

    /**
     * Applies a whole option set from the UI. Every field is clamped again here
     * (**S9**), so neither the UI nor a tampered properties file can push a value
     * outside its range, and a task-memory flip keeps its "off means forget"
     * behaviour.
     */
    fun setOptions(next: AiOptions) {
        val safe = next.copy(
            readWindowLines = AiOptionsPolicy.clampReadWindow(next.readWindowLines),
            workingSetDepth = AiOptionsPolicy.clampWorkingSetDepth(next.workingSetDepth)
        )
        val turnedMemoryOff = _state.value.options.taskMemory && !safe.taskMemory
        _state.update { it.copy(options = safe) }
        if (turnedMemoryOff) dropLiveTaskMemory()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.setReadWindow(safe.readWindowLines)
                store.setWorkingSetDepth(safe.workingSetDepth)
                store.setTaskMemory(safe.taskMemory)
                store.setAnswerDetail(safe.answerDetail)
                store.setActivity(safe.activity)
                store.setBackupMode(safe.backup)
                store.setBudgetOffer(safe.budgetOffer)
                store.setReviewer(safe.reviewer)
            }
        }
    }

    /**
     * *Clear now* — project-scoped, matching the memory's own directory layout.
     * Reports the store's real result rather than pretending: [AiTaskMemoryStore.clearProject]
     * returns whether anything was there to delete.
     */
    fun clearTaskMemoryNow(onResult: (Boolean) -> Unit = {}) {
        val name = project
        if (name == null) {
            onResult(false)
            return
        }
        // The visible effect must not wait for the disk: drop the live ledger now.
        dropLiveTaskMemory()
        viewModelScope.launch {
            val cleared = withContext(Dispatchers.IO) {
                runCatching {
                    AiTaskMemoryStore.clearProject(
                        getApplication<Application>().noBackupFilesDir,
                        name
                    )
                }.getOrDefault(false)
            }
            onResult(cleared)
        }
    }

    /**
     * Drops the running task's derived memory; nothing on disk is touched here.
     *
     * [AgentSession] is a plain class, not a data class, so this assigns the
     * mutable field rather than copying the session — copying would also have
     * discarded every other `var` the loop is mid-way through using.
     */
    private fun dropLiveTaskMemory() {
        agent?.memory = AiTaskMemory.EMPTY
    }

    /**
     * Phase 87 — a one-line result the AI panel shows (e.g. whether *Clear now*
     * actually found something to delete). Reports the real outcome rather than
     * always claiming success.
     */
    fun notice(text: String) {
        _state.update { it.copy(notice = text) }
    }

    /** D6: a different project never sees the previous project's exchange. */
    fun onProjectChanged(name: String?) {
        if (name == project) return
        project = name
        pendingBaselines = emptyMap()
        pendingExistingPaths = emptySet()
        agent = null
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
        if (_state.value.phase == AiPhase.STREAMING || _state.value.testing || _state.value.configuring) return
        when (result) {
            is AiContextResult.Refused -> _state.update { it.copy(notice = AiCopy.problem(result.problem)) }
            is AiContextResult.Ready -> _state.update {
                it.copy(
                    phase = AiPhase.PREVIEW,
                    prompt = result.prompt.copy(provider = it.provider, model = it.model), notice = null,
                    retryCountdown = null,
                    answer = "", error = null, cutShort = false, continuations = 0,
                    proposalResult = null, applyConflictPaths = emptyList(),
                    undoConflictPaths = emptyList(),
                    // A new preview is a new task: the previous timeline belongs
                    // to a task that is over, and it is never persisted (D6).
                    agentSteps = emptyList(), agentRun = null, agentRunRunning = false, agentUsage = null
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

    // ---- Phase 80 (Level 4): the agent task -------------------------------
    //
    // What happens here, in the owner's words (2026-10-02): one preview, then
    // read-only steps with a visible timeline and Stop; every run and every
    // file apply still needs its own tap. The decisions live in the pure
    // objects — AiRepoMap, AiToolPolicy, AiAgentPolicy, AiRunDigest — and this
    // section only executes them, one at a time, on IO, with the caps checked
    // before every request (D4 as amended; `04_AGENT_TOOLS_AND_RUN_LOOP.md`).

    /**
     * Phase 80 — start an **agent question** (the *Ask about the project* chip).
     * Reads the project, builds the map, and lands on the same preview gate as
     * every other source; nothing leaves the phone until Send.
     */
    fun agentAsk(
        question: String,
        openPath: String?,
        openText: String?,
        openDirty: Boolean,
        dirtyBuffers: Map<String, String> = emptyMap()
    ) = startAgent(AiSource.PROJECT, question, openPath, openText, openDirty, dirtyBuffers)

    /**
     * Phase 80 — start an **agent edit task** (the *Propose edits* chip). Same
     * skills as [agentAsk], and when the model stops calling tools its
     * `<<<CODEC_EDIT …>>>` blocks go through the Level 3 parser and diff
     * review — so an agent edit is reviewed exactly like a Level 3 one.
     */
    fun agentPropose(
        question: String,
        openPath: String?,
        openText: String?,
        openDirty: Boolean,
        dirtyBuffers: Map<String, String> = emptyMap()
    ) = startAgent(AiSource.PROPOSE_EDITS, question, openPath, openText, openDirty, dirtyBuffers)

    private fun startAgent(
        source: AiSource,
        question: String,
        openPath: String?,
        openText: String?,
        openDirty: Boolean,
        dirtyBuffers: Map<String, String>
    ) {
        val s = _state.value
        if (s.phase == AiPhase.STREAMING || s.gathering || s.applying) return
        val q = question.trim()
        if (source == AiSource.PROPOSE_EDITS && q.isEmpty()) {
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
                runCatching { AiProjectReader.scan(root, q, openPath, openText, openDirty) }.getOrNull()
            }
            if (scan == null || scan.allTextPaths.isEmpty()) {
                _state.update { it.copy(gathering = false, notice = AiCopy.NO_PROJECT_FILES) }
                return@launch
            }
            // The candidates carry text (so the map can name definitions); every
            // other admitted path is listed by name alone — the map is bounded,
            // and reading a 3 000-file project to print line counts is not a
            // promise this app makes (AiRepoMap).
            val infos = scan.candidates.map {
                AiRepoMap.FileInfo(it.relativePath, it.lines, it.text)
            }
            val listed = infos.map { it.path }.toSet()
            val rest = scan.allTextPaths.filter { path -> listed.none { AiProjectFiles.samePath(it, path) } }
            val map = AiRepoMap.build(infos + rest.map { AiRepoMap.FileInfo(it, 0, null) })
            // Every dirty open tab is admitted under its project-relative path;
            // the active buffer is refreshed from the explicit snapshot as well.
            val liveBuffers = LinkedHashMap<String, String>()
            for ((rawPath, text) in dirtyBuffers) {
                val safePath = AiTaskMemoryPolicy.safeRelativePath(rawPath) ?: continue
                val admittedPath = scan.allTextPaths.firstOrNull { AiProjectFiles.samePath(it, safePath) } ?: continue
                liveBuffers[admittedPath] = text
            }
            val safeOpenPath = openPath?.let(AiTaskMemoryPolicy::safeRelativePath)
            val admittedOpenPath = safeOpenPath?.let { safe ->
                scan.allTextPaths.firstOrNull { AiProjectFiles.samePath(it, safe) }
            }
            if (admittedOpenPath != null && openDirty && openText != null) {
                liveBuffers[admittedOpenPath] = openText
            }
            // Level 10: the *task memory* control, frozen for this task. Off keeps
            // working memory task-local and clears any retained copy (Level 9's
            // own behaviour); the secret filter and the caps still apply either way.
            val memoryStore = AiTaskMemoryStore(
                getApplication<Application>().noBackupFilesDir,
                projectName,
                persistentEnabled = s.options.taskMemory
            )
            val taskMemory = withContext(Dispatchers.IO) {
                runCatching { memoryStore.load(root, scan.allTextPaths, liveBuffers) }
                    .getOrDefault(AiTaskMemory.EMPTY)
            }
            val prompt = AiContextBuilder.fromAgent(
                source = source,
                map = map,
                question = q,
                projectName = projectName,
                scannedFiles = scan.filesSeen,
                skippedSecret = scan.skippedSecret,
                skippedNotText = scan.skippedNotText,
                hitEntryCap = scan.hitEntryCap,
                taskMemory = taskMemory,
                answerDetail = s.options.answerDetail
            )
            // Level 3's baseline snapshot still protects an agent edit: the
            // proposal is parsed against the project as it was when the preview
            // was built, and [AiEditApplier] re-checks before writing.
            pendingBaselines = scan.candidates.associate { c ->
                c.relativePath to AiFileBaseline(
                    path = c.relativePath,
                    exists = true,
                    content = AiEditProposalParser.normalizeLf(c.text),
                    cut = c.readCut,
                    fromBuffer = c.fromBuffer
                )
            }
            pendingExistingPaths = scan.allTextPaths.toSet()
            if (prompt is AiContextResult.Ready) {
                agent = AgentSession(
                    question = q,
                    source = source,
                    root = root,
                    mapText = map.text,
                    memoryStore = memoryStore,
                    memory = taskMemory,
                    paths = scan.allTextPaths,
                    dirtyBuffers = liveBuffers,
                    systemInstruction = prompt.prompt.systemInstruction,
                    provider = s.provider, model = s.model,
                    budget = AiAgentBudget(),
                    options = s.options,
                    providerReadiness = withContext(Dispatchers.IO) { store.providerReadiness() }
                )
            } else {
                agent = null
            }
            _state.update { it.copy(gathering = false) }
            preview(prompt)
        }
    }

    /** Level 9 — keep cache validation aligned with every currently dirty project tab. */
    fun updateAgentDirtyBuffers(dirtyBuffers: Map<String, String>) {
        val session = agent ?: return
        val current = LinkedHashMap<String, String>()
        for ((rawPath, text) in dirtyBuffers) {
            val safePath = AiTaskMemoryPolicy.safeRelativePath(rawPath) ?: continue
            val admittedPath = session.paths.firstOrNull { AiProjectFiles.samePath(it, safePath) } ?: continue
            current[admittedPath] = text
        }
        session.dirtyBuffers = current
    }

    /** Phase 80 — the user approved the run: the sheet calls the editor's RUN. */
    fun approveAgentRun() {
        val session = agent ?: return
        val request = _state.value.agentRun ?: return
        session.pendingRun = null
        session.budget = session.budget.withRun()
        _state.update { s ->
            s.copy(
                agentRun = null,
                agentRunRunning = true,
                agentSteps = s.agentSteps + AiAgentStep(
                    kind = AiAgentStepKind.RUN_DECISION,
                    title = AiCopy.agentRunApproved(request.target)
                ),
                agentUsage = s.agentUsage?.copy(runs = session.budget.runsUsed)
            )
        }
    }

    /** Phase 80 — the user declined the run; the model is told and carries on. */
    fun skipAgentRun() {
        val session = agent ?: return
        if (_state.value.agentRun == null) return
        session.pendingRun = null
        val step = AiAgentStep(
            kind = AiAgentStepKind.RUN_RESULT,
            title = AiCopy.AGENT_RUN_SKIPPED,
            detail = AiCopy.AGENT_RUN_SKIPPED_MODEL,
            modelResult = AiCopy.AGENT_RUN_SKIPPED_MODEL,
            ok = false
        )
        appendAgentStep(step)
        resumeAgentOrStop(session)
    }

    /** Phase 80 — the editor could not start the approved run (install prompt, no profile). */
    fun onAgentRunNotStarted(reason: String) {
        val session = agent ?: return
        if (!_state.value.agentRunRunning) return
        val step = AiAgentStep(
            kind = AiAgentStepKind.RUN_RESULT,
            title = AiCopy.AGENT_RUN_NOT_STARTED_TITLE,
            detail = reason,
            modelResult = reason,
            ok = false
        )
        _state.update { it.copy(agentRunRunning = false) }
        appendAgentStep(step)
        resumeAgentOrStop(session)
    }

    /**
     * Phase 80 — the approved run finished. Only the digest goes back to the
     * model, and the digest is exactly what the timeline shows (D4).
     */
    fun onAgentRunFinished(result: AiRunDigest.RunResult) {
        val session = agent ?: return
        if (!_state.value.agentRunRunning) return
        val digest = AiRunDigest.build(result)
        _state.update { it.copy(agentRunRunning = false) }
        appendAgentStep(
            AiAgentStep(
                AiAgentStepKind.RUN_RESULT, AiCopy.AGENT_RUN_FINISHED, digest,
                modelResult = digest, ok = !result.timedOut
            )
        )
        resumeAgentOrStop(session)
    }

    private fun appendAgentStep(step: AiAgentStep) {
        _state.update { it.copy(agentSteps = it.agentSteps + step) }
    }

    /** Continues the loop when the budget allows, otherwise stops with the reason. */
    private fun resumeAgentOrStop(session: AgentSession) {
        // Phase 84 (fix 2): gate on the turn/wall-clock AND the tool budget, so the
        // malformed-parse, denied-only and post-run resume paths can no longer walk
        // past an exhausted tool cap the way `AiAgentPolicy.decide` alone could not.
        val reason = session.budget.blockResume(System.currentTimeMillis(), session.caps)
        if (reason != null) {
            stopAgent(reason)
            return
        }
        val queued = session.queuedAfterRun
        val deniedAfterRun = session.queuedDeniedAfterRun
        session.queuedAfterRun = emptyList()
        session.queuedDeniedAfterRun = emptyList()
        if (queued.isNotEmpty() || deniedAfterRun.isNotEmpty()) {
            executeToolBatch(session, queued, deniedAfterRun)
            return
        }
        agentTurn(session)
    }

    /**
     * One model turn of an agent task. This is the third and last `client.stream`
     * call site in this file, and it is reachable only from the agent loop,
     * which starts only after the task preview's **Send** (D4 as amended).
     *
     * Phase 84 (fix 4): when [finalSynthesis] is set this is the reserved last
     * turn — tools **masked, not removed** (the system instruction still defines
     * them) — that turns a budget stop into a readable prose answer. It reuses
     * this same call site, so the app still has exactly three stream call sites.
     */
    private fun agentTurn(
        session: AgentSession,
        finalSynthesis: Boolean = false,
        stopReason: AiAgentStopReason? = null,
        refreshMemory: Boolean = true
    ) {
        job = viewModelScope.launch {
            if (refreshMemory) {
                val before = session.memory
                var reconciled: AiTaskMemory? = null
                var attemptsRemaining = 3
                while (reconciled == null && attemptsRemaining > 0) {
                    attemptsRemaining--
                    val dirtySnapshot = session.dirtyBuffers.toMap()
                    val candidate = withContext(Dispatchers.IO) {
                        session.memoryStore.reconcile(before, session.root, session.paths, dirtySnapshot)
                    }
                    if (session.dirtyBuffers == dirtySnapshot) reconciled = candidate
                }
                // If editing never settles during three disk checks, fail closed:
                // omit file-backed memory for this request instead of sending stale
                // cached notes. The next request can restore it from a stable snapshot.
                session.memory = reconciled ?: withContext(Dispatchers.IO) {
                    session.memoryStore.reconcile(before, session.root, emptyList(), emptyMap())
                }
                if (session.memory != before) persistTaskMemory(session)
            }
            if (!isActive || agent !== session) return@launch
            val steps = _state.value.agentSteps
            val packed = if (finalSynthesis) {
                AiAgentPrompt.finalSynthesis(
                    question = session.question,
                    mapText = session.mapText,
                    steps = steps,
                    memory = session.memory
                )
            } else {
                AiAgentPrompt.pack(
                    question = session.question,
                    mapText = session.mapText,
                    steps = steps,
                    memory = session.memory,
                    // Level 10: the working-set depth, frozen at Send.
                    keepLastResults = session.options.workingSetDepth
                )
            }
            // Captured once. Every retry of this turn has the identical recipient, strings and budget.
            val body = AiProviderRequests.body(session.provider, session.model, session.systemInstruction, packed.text)
            if (!finalSynthesis) session.budget = session.budget.withTurn()
            _state.update { it.copy(phase = AiPhase.STREAMING, answer = "", error = null, cutShort = false, agentUsage = usage(session)) }
            appendAgentStep(AiAgentStep(
                kind = AiAgentStepKind.REQUEST,
                title = AiCopy.requestRecipient(session.provider, session.model),
                sentSystemInstruction = session.systemInstruction,
                sentUserText = packed.text,
                // Level 10 (S8): the collapsed row names this same frozen recipient.
                sentProvider = session.provider,
                sentModel = session.model
            ))
            val key = withContext(Dispatchers.IO) { store.loadKey(session.provider) }
            if (key == null) {
                _state.update { it.copy(keySaved = false) }
                if (finalSynthesis) finalizeStop(session, stopReason ?: AiAgentStopReason.PROVIDER_FAILURE, AiCopy.KEY_UNREADABLE)
                else stopAgent(AiAgentStopReason.PROVIDER_FAILURE, AiCopy.KEY_UNREADABLE)
                return@launch
            }
            val outcome = try {
                streamWithRetry(session = session, onText = { text ->
                    _state.update { s -> if (s.phase == AiPhase.STREAMING) s.copy(answer = text) else s }
                }) { publish ->
                    // The agent loop's sole request road; always reached after Send.
                    client.stream(session.provider, key, session.model, body, onText = publish)
                }
            } catch (_: AgentDeadlineReached) {
                if (finalSynthesis) finalizeStop(session, stopReason ?: AiAgentStopReason.WALL_CLOCK, null)
                else stopAgent(AiAgentStopReason.WALL_CLOCK)
                return@launch
            } catch (_: TimeoutCancellationException) {
                if (finalSynthesis) finalizeStop(session, stopReason ?: AiAgentStopReason.WALL_CLOCK, null)
                else stopAgent(AiAgentStopReason.WALL_CLOCK)
                return@launch
            }
            when (outcome) {
                is AiOutcome.Answer ->
                    if (finalSynthesis) {
                        val visible = extractAndPersistTaskMemory(session, outcome.text)
                        // Tools are masked: whatever came back is the user's answer.
                        // Any stray block is stripped so the answer is always prose (S12).
                        val prose = AiToolProtocol.proseOnly(visible)
                            .ifBlank { AiAgentLimits.stopSentence(stopReason ?: AiAgentStopReason.PROVIDER_FAILURE) }
                        finalizeStop(session, stopReason ?: AiAgentStopReason.PROVIDER_FAILURE, null, prose)
                    } else {
                        onAgentAnswer(session, outcome.text, outcome.cutShort)
                    }
                is AiOutcome.Failed ->
                    if (finalSynthesis) finalizeStop(session, stopReason ?: AiAgentStopReason.PROVIDER_FAILURE, outcome.failure.message)
                    else stopAgent(AiAgentStopReason.PROVIDER_FAILURE, outcome.failure.message)
            }
        }
    }

    private fun usage(session: AgentSession): AiAgentUsage =
        AiAgentUsage(
            turns = session.budget.turnsUsed,
            toolCalls = session.budget.toolCallsUsed,
            runs = session.budget.runsUsed,
            refused = session.budget.toolCallsRefused,
            reused = session.budget.toolCallsReused,
            turnCap = session.caps.turns,
            readCap = session.caps.toolCalls
        )

    private suspend fun onAgentAnswer(session: AgentSession, text: String, cutShort: Boolean) {
        val visibleText = extractAndPersistTaskMemory(session, text)
        _state.update { it.copy(agentUsage = usage(session)) }
        val step = AiAgentStep(
            kind = AiAgentStepKind.ANSWER,
            title = AiCopy.agentStepAnswer(session.budget.turnsUsed),
            detail = visibleText.take(AiAgentLimits.MAX_STEP_DETAIL_CHARS),
            modelResult = visibleText
        )
        appendAgentStep(step)
        when (val parsed = AiToolProtocol.parse(visibleText)) {
            is AiToolParse.Malformed -> {
                // Phase 84 (fix 6): a block CodeC cannot read is refused, but the
                // blocks that DID parse are kept and run; the memory protocol block
                // was removed before this tool parser ever sees the answer.
                session.budget = session.budget.withRefused(1)
                appendAgentStep(
                    AiAgentStep(
                        kind = AiAgentStepKind.DENIED,
                        title = AiCopy.AGENT_STEP_MALFORMED,
                        detail = parsed.reason,
                        modelResult = parsed.reason,
                        ok = false
                    )
                )
                if (parsed.calls.isEmpty()) {
                    _state.update { it.copy(agentUsage = usage(session)) }
                    resumeAgentOrStop(session)
                } else {
                    handleParsedCalls(session, AiToolParse.Calls(parsed.prose, parsed.calls), cutShort)
                }
            }
            is AiToolParse.Calls -> handleParsedCalls(session, parsed, cutShort)
        }
    }

    private suspend fun extractAndPersistTaskMemory(session: AgentSession, text: String): String {
        val extracted = AiTaskMemoryProtocol.extract(text)
        val update = extracted.update ?: return extracted.visibleText
        val next = session.memory.applyUpdate(update, session.paths, session.question)
        if (next != session.memory) {
            session.memory = next
            persistTaskMemory(session)
        }
        return extracted.visibleText
    }

    private suspend fun persistTaskMemory(session: AgentSession): Boolean {
        val saved = withContext(Dispatchers.IO) {
            session.memoryStore.save(session.memory, session.paths)
        }
        if (!saved) {
            session.memoryPersistenceFailed = true
            _state.update { state ->
                if (state.notice == null || state.notice == AiCopy.TASK_MEMORY_SAVE_FAILED) {
                    state.copy(notice = AiCopy.TASK_MEMORY_SAVE_FAILED)
                } else state
            }
        }
        return saved
    }

    /** Routes one parsed answer's calls through the policy and executes the decision. */
    private fun handleParsedCalls(session: AgentSession, parsed: AiToolParse.Calls, cutShort: Boolean) {
        when (val decision = AiAgentPolicy.decide(
            parsed = parsed,
            budget = session.budget,
            nowMs = System.currentTimeMillis(),
            projectView = session.projectView,
            // Level 10: the read window, frozen at Send and clamped by the policy.
            readWindow = session.options.readWindowLines,
            // Level 10 (87.6): the task's caps. Without this the tool gate
            // inside `decide` would keep using the plain constants and an
            // accepted extension would buy nothing at all.
            caps = session.caps
        )) {
            is AiAgentDecision.Finish -> finishAgent(session, decision.answer, cutShort)
            is AiAgentDecision.Stop -> stopAgent(decision.reason)
            is AiAgentDecision.AskRunApproval -> {
                // The loop pauses for the user's tap; anything else the
                // model asked for in the same answer waits with it.
                session.pendingRun = decision.call
                session.queuedAfterRun = decision.alsoQueued
                session.queuedDeniedAfterRun = decision.denied
                _state.update { it.copy(agentRun = AiAgentRunRequest(target = decision.call.path)) }
            }
            is AiAgentDecision.ExecuteTools -> executeToolBatch(session, decision.calls, decision.denied)
        }
    }

    /** Runs a batch of validated calls on IO, oldest first, then takes another turn. */
    private fun executeToolBatch(
        session: AgentSession,
        calls: List<AiToolCall>,
        denied: List<AiToolVerdict.Denied>
    ) {
        _state.update { s ->
            var steps = s.agentSteps
            for (d in denied) {
                steps = steps + AiAgentStep(
                    kind = AiAgentStepKind.DENIED,
                    title = AiCopy.agentStepDenied(AiToolProtocol.describe(d.request), d.reason),
                    detail = d.reason,
                    modelResult = d.reason,
                    ok = false
                )
            }
            s.copy(agentSteps = steps)
        }
        if (calls.isEmpty()) {
            // Phase 84 (fix 3): refusals are counted separately and never inflate
            // the execution cap that gates MAX_TOOL_CALLS.
            session.budget = session.budget.withRefused(denied.size)
            _state.update { it.copy(agentUsage = usage(session)) }
            resumeAgentOrStop(session)
            return
        }
        job = viewModelScope.launch {
            val stop = { !isActive }
            for (call in calls) {
                val memoryBefore = session.memory
                var dirtySnapshot = session.dirtyBuffers.toMap()
                var readPlan = withContext(Dispatchers.IO) {
                    memoryBefore.prepareRead(
                        call, session.root, session.paths, dirtySnapshot, System.currentTimeMillis(),
                        // Level 10: the SAME window the validator and runner use,
                        // so the cache identity describes what was delivered.
                        session.options.readWindowLines
                    )
                }
                // A keystroke/tab switch during snapshotting invalidates that
                // candidate before it can serve a cached result. Re-plan once
                // from the newest live buffer map; the runner uses this same map.
                if (session.dirtyBuffers != dirtySnapshot) {
                    dirtySnapshot = session.dirtyBuffers.toMap()
                    readPlan = withContext(Dispatchers.IO) {
                        memoryBefore.prepareRead(
                            call, session.root, session.paths, dirtySnapshot, System.currentTimeMillis(),
                            session.options.readWindowLines
                        )
                    }
                }
                if (session.dirtyBuffers != dirtySnapshot) {
                    // Continuous editing: use no cache for this call. The tool
                    // still receives one stable buffer snapshot; next read rekeys it.
                    readPlan = readPlan.copy(cachedFiles = emptyMap(), resultCacheKey = null)
                }
                session.memory = readPlan.memory
                session.workingSet = session.workingSet.note(readPlan.progressSignature)
                val cached = readPlan.resultCacheKey?.let { session.workingSet.cached(it) }
                val outcome: AiToolRunner.Outcome
                if (cached != null) {
                    outcome = AiToolRunner.Outcome(true, cached)
                    session.budget = session.budget.withReused(1)
                } else {
                    outcome = withContext(Dispatchers.IO) {
                        AiToolRunner.execute(
                            call = call,
                            root = session.root,
                            paths = session.paths,
                            dirtyBuffers = dirtySnapshot,
                            shouldStop = stop,
                            cachedFiles = readPlan.cachedFiles,
                            // Level 10: the follow-up hint names this same window.
                            readWindow = session.options.readWindowLines
                        )
                    }
                    if (readPlan.resultCacheKey != null && outcome.ok) {
                        session.workingSet = session.workingSet.record(readPlan.resultCacheKey, outcome.text)
                    }
                    session.budget = session.budget.withToolCalls(1, session.caps)
                }
                if (session.memory != memoryBefore) persistTaskMemory(session)
                val step = AiAgentStep(
                    kind = AiAgentStepKind.TOOL,
                    title = AiToolProtocol.describeCall(call) + if (cached != null) " (reused)" else "",
                    // The model gets the full result (cut marker intact); the
                    // timeline gets a short preview that keeps the marker.
                    detail = AiAgentLimits.timelineDetail(outcome.text, AiToolRunner.CUT_NOTE),
                    modelResult = outcome.text,
                    ok = outcome.ok
                )
                _state.update { it.copy(agentSteps = it.agentSteps + step, agentUsage = usage(session)) }
                if (!isActive) return@launch
            }
            if (denied.isNotEmpty()) {
                session.budget = session.budget.withRefused(denied.size)
                _state.update { it.copy(agentUsage = usage(session)) }
            }
            // S11: the model repeated one call MAX_IDENTICAL_REPEATS times with no
            // progress in between — stop instead of spinning (a final synthesis turn
            // still gives the user a prose answer).
            if (session.workingSet.stalled()) {
                stopAgent(AiAgentStopReason.NO_PROGRESS)
                return@launch
            }
            resumeAgentOrStop(session)
        }
    }

    private fun finishAgent(session: AgentSession, answer: String, cutShort: Boolean) {
        val parsed = if (session.source == AiSource.PROPOSE_EDITS) {
            AiEditProposalParser.parse(answer, pendingBaselines, pendingExistingPaths)
        } else {
            null
        }
        _state.update {
            it.copy(
                phase = AiPhase.DONE,
                answer = answer,
                cutShort = cutShort,
                proposalResult = parsed,
                applyConflictPaths = emptyList(),
                agentRun = null,
                agentRunRunning = false
            )
        }
    }

    private fun stopAgent(reason: AiAgentStopReason, message: String? = null) {
        val session = agent
        // Phase 84 (fix 4 / S12): a budget stop with a reachable provider gets ONE
        // reserved final-synthesis turn — tools masked, not removed — so the user
        // receives a prose answer instead of a raw `<<<CODEC_TOOL` block. USER_STOP
        // makes no delayed call ("stop means stop"); a failure message or the wall
        // clock finalizes locally without another round-trip.
        val synthesisEligible = session != null && message == null && !session.synthesisDone &&
            reason != AiAgentStopReason.USER_STOP &&
            reason != AiAgentStopReason.WALL_CLOCK &&
            reason != AiAgentStopReason.PROVIDER_FAILURE
        if (synthesisEligible) {
            session.synthesisDone = true
            agentTurn(session, finalSynthesis = true, stopReason = reason)
            return
        }
        finalizeStop(session, reason, message)
    }

    /**
     * Terminal stop: guarantees [AiUiState.answer] is prose on every stop reason
     * (S12). [answerOverride] is the synthesis turn's prose; otherwise any partial
     * answer is stripped of tool blocks, falling back to the stop sentence.
     */
    private fun finalizeStop(
        session: AgentSession?,
        reason: AiAgentStopReason,
        message: String?,
        answerOverride: String? = null
    ) {
        job = null
        val prose = (answerOverride ?: AiToolProtocol.proseOnly(_state.value.answer))
            .ifBlank { AiAgentLimits.stopSentence(reason) }
        // Level 10 (87.6): a turn/tool-budget stop with the option left on gets a
        // standing read-only extension offer — after the answer is prose, never
        // instead of one (S12). The policy decides; this only asks it.
        val offer = if (
            session != null &&
            AiBudgetExtensionPolicy.offerAt(session.options.budgetOffer, reason, session.extensionsUsed)
        ) {
            AiBudgetOfferState()
        } else {
            null
        }
        // Level 10 (87.7): a manual backup-provider offer, only on a failure the
        // provider caused, only to a provider the user has configured AND
        // consented to for itself. Off by default (S9: it is an option).
        val backup: AiProviderId? = session?.let { s ->
            val readiness = s.providerReadiness
            val configured = readiness.filterValues { it.configured }.keys
            val terms = readiness.filterValues { it.termsAccepted }.keys
            (
                AiBackupProviderPolicy.offer(
                    s.options.backup, s.provider, reason, configured, terms
                ) as? AiBackupOffer.Offer
                )?.provider
        }
        _state.update { s ->
            s.copy(
                phase = if (message != null) AiPhase.FAILED else AiPhase.DONE,
                error = message,
                answer = prose,
                retryCountdown = null,
                agentRun = null,
                agentRunRunning = false,
                notice = null,
                budgetOffer = offer,
                backupOffer = backup,
                agentUsage = session?.let { usage(it) } ?: s.agentUsage,
                agentSteps = s.agentSteps + AiAgentStep(
                    kind = AiAgentStepKind.STOPPED,
                    title = AiCopy.agentStopped(reason),
                    ok = false
                )
            )
        }
    }

    /** The only path to a helper request: the user pressed Send on a preview. */
    fun send() {
        val prompt = _state.value.prompt ?: return
        if (_state.value.phase != AiPhase.PREVIEW || job?.isActive == true || _state.value.configuring || _state.value.testing) return
        if (prompt.agent) {
            val session = agent
            if (session == null) {
                _state.update { it.copy(notice = AiCopy.NO_PROJECT_FILES) }
                return
            }
            // D4: revalidate task memory after the user has seen the preview. If
            // any cached file/derived note changed, refresh the exact preview and
            // require another Send rather than silently changing its contents.
            job = viewModelScope.launch {
                var dirtySnapshot = session.dirtyBuffers.toMap()
                var currentMemory = withContext(Dispatchers.IO) {
                    session.memoryStore.reconcile(session.memory, session.root, session.paths, dirtySnapshot)
                }
                if (session.dirtyBuffers != dirtySnapshot) {
                    dirtySnapshot = session.dirtyBuffers.toMap()
                    currentMemory = withContext(Dispatchers.IO) {
                        session.memoryStore.reconcile(currentMemory, session.root, session.paths, dirtySnapshot)
                    }
                }
                if (agent !== session || _state.value.phase != AiPhase.PREVIEW) {
                    job = null
                    return@launch
                }
                session.memory = currentMemory
                if (currentMemory != prompt.agentMemory || session.dirtyBuffers != dirtySnapshot) {
                    _state.update { state ->
                        if (state.phase == AiPhase.PREVIEW && state.prompt?.agent == true) {
                            state.copy(
                                prompt = state.prompt.copy(agentMemory = currentMemory),
                                notice = AiCopy.TASK_MEMORY_CHANGED
                            )
                        } else state
                    }
                    job = null
                    return@launch
                }
                session.budget = AiAgentBudget(startedAtMs = System.currentTimeMillis())
                _state.update {
                    it.copy(
                        phase = AiPhase.STREAMING, answer = "", error = null, cutShort = false,
                        proposalResult = null, applyConflictPaths = emptyList(), undoConflictPaths = emptyList(),
                        agentSteps = listOf(AiAgentStep(AiAgentStepKind.TASK, session.question)),
                        agentRun = null, agentRunRunning = false, agentUsage = usage(session), notice = null
                    )
                }
                agentTurn(session, refreshMemory = false)
            }
            return
        }
        val model = prompt.model
        val provider = prompt.provider
        val body = AiProviderRequests.body(prompt)
        // Phase 81: a continuation keeps what is already on screen and streams
        // the rest underneath it, inside one shared total budget. A fresh
        // request starts empty, exactly as before.
        val base = if (prompt.continuation != null) _state.value.answer else ""
        _state.update {
            it.copy(
                phase = AiPhase.STREAMING, answer = base, error = null, cutShort = false,
                continuations = prompt.continuation?.index ?: 0
            )
        }
        job = viewModelScope.launch {
            val key = withContext(Dispatchers.IO) { store.loadKey(provider) }
            if (key == null) {
                _state.update {
                    it.copy(phase = AiPhase.FAILED, keySaved = false, error = AiCopy.KEY_UNREADABLE)
                }
                return@launch
            }
            // Level 10 (87.8): a review streams into its own field so asking for
            // a second opinion never overwrites the answer being reviewed.
            val reviewing = prompt.source == AiSource.REVIEW
            val outcome = streamWithRetry(onText = { text ->
                _state.update { s ->
                    if (s.phase != AiPhase.STREAMING) s
                    else if (reviewing) s.copy(review = AiReviewVerdict.Text(base + text))
                    else s.copy(answer = base + text)
                }
            }) { publish ->
                client.stream(provider, key, model, body, AiContinuation.requestBudget(base.length), onText = publish)
            }
            _state.update { s ->
                if (s.phase != AiPhase.STREAMING) return@update s
                when (outcome) {
                    is AiOutcome.Answer -> {
                        if (reviewing) {
                            // Level 10 (87.8): **displayed, never parsed into a
                            // call**. The reviewer has no tools, so any markup it
                            // emits stays text on screen (S3) and never reaches
                            // AiEditApplier or a tool runner.
                            s.copy(
                                phase = AiPhase.DONE,
                                review = AiReviewerPolicy.parse(outcome.text),
                                cutShort = outcome.cutShort
                            )
                        } else {
                            val parsed = if (prompt.source == AiSource.PROPOSE_EDITS) {
                                AiEditProposalParser.parse(outcome.text, pendingBaselines, pendingExistingPaths)
                            } else {
                                null
                            }
                            s.copy(
                                phase = AiPhase.DONE,
                                answer = base + outcome.text,
                                cutShort = outcome.cutShort,
                                proposalResult = parsed,
                                applyConflictPaths = emptyList()
                            )
                        }
                    }
                    is AiOutcome.Failed -> s.copy(phase = AiPhase.FAILED, error = outcome.failure.message)
                }
            }
        }
    }

    /** Stop: disconnects the request; what arrived so far stays visible. */
    fun stop() {
        val stoppingAgent = agent != null && _state.value.prompt?.agent == true && _state.value.phase == AiPhase.STREAMING
        job?.cancel()
        job = null
        _state.update { it.copy(
            retryCountdown = null, testing = false,
            testResult = if (it.testing) AiCopy.STOPPED else it.testResult
        ) }
        if (stoppingAgent) {
            stopAgent(AiAgentStopReason.USER_STOP)
            return
        }
        _state.update { s ->
            if (s.phase != AiPhase.STREAMING) s
            // Level 10 (87.8): stopping a review keeps whatever review text
            // arrived, as text. It is never parsed into a call.
            else if (s.prompt?.source == AiSource.REVIEW) {
                val arrived = (s.review as? AiReviewVerdict.Text)?.text.orEmpty()
                s.copy(
                    phase = if (arrived.isBlank()) AiPhase.IDLE else AiPhase.DONE,
                    prompt = if (arrived.isBlank()) null else s.prompt,
                    review = if (arrived.isBlank()) null else AiReviewerPolicy.parse(arrived),
                    cutShort = arrived.isNotBlank()
                )
            } else if (s.answer.isBlank() && s.agentSteps.none { it.kind == AiAgentStepKind.TOOL }) {
                s.copy(phase = AiPhase.IDLE, prompt = null)
            } else {
                val parsed = if (s.prompt?.source == AiSource.PROPOSE_EDITS) {
                    AiEditProposalParser.parse(s.answer, pendingBaselines, pendingExistingPaths)
                } else {
                    null
                }
                s.copy(
                    phase = AiPhase.DONE, cutShort = true, proposalResult = parsed,
                    agentRun = null, agentRunRunning = false,
                    agentSteps = s.agentSteps + AiAgentStep(
                        kind = AiAgentStepKind.STOPPED,
                        title = AiCopy.agentStopped(AiAgentStopReason.USER_STOP),
                        ok = false
                    )
                )
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

    /**
     * Phase 81 — the user tapped **Continue** on an answer CodeC cut short.
     *
     * This builds a new PREVIEW of the same request with the tail of the answer
     * and the resume sentence appended ([AiContinuation]); it sends nothing.
     * The user reads the exact follow-up text on the preview and taps Send, so
     * D4 ("preview every request") is untouched. Not offered for an agent task
     * (its loop has ended) or for an edit proposal (its review card owns the
     * reply) — those get a plain sentence instead of a button.
     */
    /**
     * Level 10 (87.6) — the user accepted the standing budget-extension offer.
     *
     * What it buys: up to [AiBudgetExtensionPolicy.EXTRA_TURNS] more model turns
     * and [AiBudgetExtensionPolicy.EXTRA_TOOL_CALLS] more tool calls, once per
     * task. What it cannot buy: a run ([AiAgentCaps.runs] is echoed back
     * unchanged), a waived approval, or a second extension (**S9**).
     *
     * [AgentSession.synthesisDone] is reset on purpose. Phase 84's rule is that
     * a budget stop may not loop back into synthesis *on its own*; here the user
     * has explicitly bought more work, the extension is capped at one per task,
     * and the offer is cleared before resuming, so the cycle is bounded.
     */
    fun acceptBudgetExtension() {
        val session = agent
        val standing = _state.value.budgetOffer
        if (session == null || standing == null || job?.isActive == true) return
        if (session.extensionsUsed >= AiBudgetExtensionPolicy.MAX_EXTENSIONS_PER_TASK) {
            _state.update { it.copy(budgetOffer = null, notice = AiCopy.BUDGET_OFFER_SPENT) }
            return
        }
        session.extensionsUsed += 1
        session.caps = AiBudgetExtensionPolicy.extend(session.caps)
        session.synthesisDone = false
        _state.update { s ->
            s.copy(
                budgetOffer = null,
                phase = AiPhase.STREAMING,
                notice = AiCopy.BUDGET_EXTENDED,
                error = null,
                agentUsage = usage(session),
                agentSteps = s.agentSteps + AiAgentStep(
                    kind = AiAgentStepKind.STOPPED,
                    title = AiCopy.BUDGET_EXTENDED,
                    ok = true
                )
            )
        }
        agentTurn(session)
    }

    /** Level 10 (87.6) — the user kept the answer as it is. Nothing resumes. */
    fun declineBudgetExtension() {
        if (_state.value.budgetOffer == null) return
        _state.update { it.copy(budgetOffer = null) }
    }

    /**
     * Level 10 (87.7) — the user accepted the manual backup-provider offer.
     *
     * This **sends nothing**. It re-checks on IO that the offered provider still
     * has a usable key under its own current terms, switches the recipient, and
     * rebuilds the ordinary D4 preview of the same question — so the user reads
     * the new recipient on the preview and taps Send themselves (S8, D4). A
     * provider whose key or consent disappeared since the offer gets a plain
     * notice and no switch.
     */
    fun acceptBackupProvider() {
        val next = _state.value.backupOffer ?: return
        if (_state.value.prompt == null) {
            _state.update { it.copy(backupOffer = null) }
            return
        }
        _state.update { it.copy(backupOffer = null, configuring = true) }
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { store.isReady(next) }
            val model = withContext(Dispatchers.IO) { store.model(next) }
            if (!ready) {
                _state.update {
                    it.copy(configuring = false, notice = AiCopy.BACKUP_OFFER_UNAVAILABLE)
                }
                return@launch
            }
            _state.update { s ->
                s.copy(
                    provider = next,
                    model = model,
                    keySaved = true,
                    configuring = false,
                    // A fresh preview of the same question, naming the new
                    // recipient. Send is still the only road (S8).
                    phase = AiPhase.PREVIEW,
                    error = null,
                    answer = "",
                    cutShort = false,
                    notice = AiCopy.backupSwitched(next)
                )
            }
        }
    }

    /** Level 10 (87.7) — the user kept the current provider. Nothing changes. */
    fun declineBackupProvider() {
        if (_state.value.backupOffer == null) return
        _state.update { it.copy(backupOffer = null) }
    }

    /**
     * Level 10 (87.8) — the user asked for a read-only second opinion.
     *
     * This sends nothing. It builds an ordinary [AiSource.REVIEW] prompt whose
     * system instruction is [AiReviewerPolicy.instruction] — no tool protocol,
     * no task-memory protocol, therefore no format in which the reviewer could
     * ask for a read, an edit or a run — and routes it through the same preview
     * and the same single-shot stream site as any other request, so the app
     * still has exactly three `client.stream` call sites (**S8**). The user
     * reads the full request, including the answer being reviewed, and taps
     * Send (D4).
     *
     * [AiReviewerPolicy.mayTrigger] is the only gate: the option on, a user tap,
     * and an idle surface.
     */
    fun requestReview() {
        val s = _state.value
        val prompt = s.prompt
        if (prompt == null || s.answer.isBlank() || job?.isActive == true) return
        if (!AiReviewerPolicy.mayTrigger(s.options.reviewer, userTapped = true, idle = s.phase == AiPhase.DONE)) {
            _state.update { it.copy(notice = AiCopy.REVIEWER_UNAVAILABLE) }
            return
        }
        val reviewed = s.answer
        val review = prompt.copy(
            source = AiSource.REVIEW,
            // Never an agent task: the reviewer gets no tools and no further turns.
            agent = false,
            question = AiCopy.reviewerQuestion(reviewed),
            continuation = null,
            agentMemory = AiTaskMemory.EMPTY
        )
        _state.update { it.copy(reviewedAnswer = reviewed, review = null) }
        preview(AiContextResult.Ready(review))
    }

    fun continueAnswer() {
        val s = _state.value
        val prompt = s.prompt ?: return
        if (s.phase != AiPhase.DONE || !s.cutShort || job?.isActive == true) return
        if (prompt.agent) {
            _state.update { it.copy(notice = AiCopy.CONTINUE_AGENT_NOTE) }
            return
        }
        if (prompt.source == AiSource.PROPOSE_EDITS) {
            _state.update { it.copy(notice = AiCopy.CONTINUE_PROPOSAL_NOTE) }
            return
        }
        if (!AiContinuation.canContinue(s.continuations, s.answer.length)) {
            _state.update {
                it.copy(notice = AiContinuation.limitNote(s.continuations, s.answer.length))
            }
            return
        }
        val next = prompt.copy(
            continuation = AiContinuationRequest(
                index = s.continuations + 1,
                tail = AiContinuation.tailOf(s.answer)
            )
        )
        _state.update {
            it.copy(
                phase = AiPhase.PREVIEW, prompt = next, notice = null, error = null,
                cutShort = false, proposalResult = null, applyConflictPaths = emptyList(),
                undoConflictPaths = emptyList()
            )
        }
    }

    fun clear() {
        job?.cancel()
        job = null
        gatherJob?.cancel()
        gatherJob = null
        agent = null
        _state.update {
            it.copy(
                phase = AiPhase.IDLE, prompt = null, answer = "", error = null,
                cutShort = false, continuations = 0, notice = null, testResult = null, testing = false, retryCountdown = null,
                gathering = false, proposalResult = null, applyConflictPaths = emptyList(),
                undoConflictPaths = emptyList(),
                agentSteps = emptyList(), agentRun = null, agentRunRunning = false, agentUsage = null,
                // Level 10: offers and a review belong to the task being cleared.
                budgetOffer = null, backupOffer = null, review = null, reviewedAnswer = null
            )
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private fun settingsBusy(): Boolean = _state.value.let {
        it.configuring || it.testing || it.gathering || it.applying || it.phase == AiPhase.STREAMING || job?.isActive == true
    }

    /** Manual recipient selection. No request, no fallback; the old task/preview is discarded. */
    fun selectProvider(provider: AiProviderId) {
        if (provider == _state.value.provider || settingsBusy()) return
        clear()
        _state.update { it.copy(
            provider = provider, model = AiProviders.defaultModel(provider), keySaved = false,
            configuring = true, setupError = null, notice = AiCopy.PROVIDER_CHANGED
        ) }
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { store.isReady(provider) }
            val model = withContext(Dispatchers.IO) { store.model(provider) }
            _state.update { it.copy(keySaved = ready, model = model, configuring = false) }
        }
    }

    /** Each provider needs its own explicit adult/terms confirmation; the store checks too. */
    fun saveKey(rawKey: String, model: String, confirmedAdultAndTerms: Boolean) {
        if (settingsBusy()) return
        val provider = _state.value.provider
        if (!AiKeySetup.canSave(rawKey, confirmedAdultAndTerms)) {
            _state.update { it.copy(setupError = AiCopy.SETUP_INCOMPLETE) }
            return
        }
        if (!AiProviders.isValidModel(provider, model)) {
            _state.update { it.copy(setupError = AiCopy.modelInvalid(provider)) }
            return
        }
        _state.update { it.copy(configuring = true, setupError = null) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { store.saveKey(rawKey, model, provider, confirmedAdultAndTerms) }
            val storedModel = withContext(Dispatchers.IO) { store.model(provider) }
            _state.update {
                if (ok) it.copy(keySaved = true, model = storedModel, setupError = null, configuring = false)
                else it.copy(setupError = AiCopy.SAVE_FAILED, configuring = false)
            }
        }
    }

    fun saveModel(model: String) {
        if (settingsBusy()) return
        val provider = _state.value.provider
        if (!AiProviders.isValidModel(provider, model)) {
            _state.update { it.copy(setupError = AiCopy.modelInvalid(provider)) }
            return
        }
        clear()
        _state.update { it.copy(configuring = true) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { store.setModel(model, provider) }
            _state.update {
                if (ok) it.copy(model = model.trim(), setupError = null, testResult = null, configuring = false, notice = AiCopy.MODEL_CHANGED)
                else it.copy(setupError = AiCopy.SAVE_FAILED, configuring = false)
            }
        }
    }

    fun deleteKey() {
        if (_state.value.configuring || _state.value.applying) return
        val provider = _state.value.provider
        clear() // also cancels any countdown/socket before deleting this slot
        _state.update { it.copy(configuring = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.deleteKey(provider) }
            _state.update {
                it.copy(
                    keySaved = false, configuring = false,
                    testResult = null, sheet = AiSheetState.HIDDEN,
                    undoSummary = null, undoConflictPaths = emptyList()
                )
            }
        }
    }

    /** Fixed content-free prompt after its disclosed tap, never project text. */
    fun testConnection() {
        if (settingsBusy() || !_state.value.keySaved) return
        val provider = _state.value.provider
        val model = _state.value.model
        val body = when (provider) {
            AiProviderId.GEMINI -> GeminiRequest.testBody()
            AiProviderId.NVIDIA -> NvidiaRequest.testBody(model)
        }
        _state.update { it.copy(testing = true, testResult = null) }
        job = viewModelScope.launch {
            val key = withContext(Dispatchers.IO) { store.loadKey(provider) }
            val result = if (key == null) {
                AiCopy.KEY_UNREADABLE
            } else {
                when (val outcome = streamWithRetry(onText = {}) { publish ->
                    client.stream(provider, key, model, body, onText = publish)
                }) {
                    is AiOutcome.Answer -> AiCopy.testOk(provider)
                    is AiOutcome.Failed -> if (provider == AiProviderId.GEMINI && outcome.failure.kind == AiFailureKind.EMPTY) AiCopy.testOk(provider) else outcome.failure.message
                }
            }
            _state.update { it.copy(testing = false, testResult = result, keySaved = key != null && it.keySaved) }
        }
    }

    private class AgentDeadlineReached : Exception()

    /**
     * Phase 82 — ONE shared retry helper around the existing three call sites.
     * The attempt closure captures the exact already-approved request and key.
     * No network call here, no recursion, no extra turn/tool/run/Continue budget.
     */
    private suspend fun streamWithRetry(
        session: AgentSession? = null,
        onText: (String) -> Unit,
        attempt: suspend ((String) -> Unit) -> AiOutcome
    ): AiOutcome {
        val caller = coroutineContext[Job]
        var partial = false
        val retry: suspend () -> AiOutcome = {
            AiRetry.once(
                attempt = {
                    coroutineContext.ensureActive()
                    attempt { text ->
                        caller?.ensureActive()
                        if (text.isNotEmpty()) partial = true
                        if (job === caller) onText(text)
                    }
                },
                wait = { delay(it) },
                onCountdown = { countdown ->
                    // A cancelled old request must not clear a new request's countdown.
                    _state.update { if (job === caller) it.copy(retryCountdown = countdown) else it }
                },
                hasPartialText = { partial },
                allowed = { seconds ->
                    caller?.ensureActive()
                    if (session != null) {
                        val remaining = AiAgentLimits.MAX_WALL_CLOCK_MS - (System.currentTimeMillis() - session.budget.startedAtMs)
                        if (remaining <= seconds * 1_000) throw AgentDeadlineReached()
                    }
                    true
                }
            )
        }
        return if (session == null) retry() else {
            val remaining = AiAgentLimits.MAX_WALL_CLOCK_MS - (System.currentTimeMillis() - session.budget.startedAtMs)
            if (remaining <= 0) throw AgentDeadlineReached()
            withTimeout(remaining) { retry() }
        }
    }

    override fun onCleared() {
        job?.cancel()
        gatherJob?.cancel()
        super.onCleared()
    }
}
