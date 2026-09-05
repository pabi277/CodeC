package com.codeci.bench

import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.bench.candidate.Compose2Candidate
import com.codeci.bench.candidate.NowCandidate
import com.codeci.bench.candidate.SoraCandidate
import com.codeci.bench.core.FrameStats
import com.codeci.bench.core.FrameSummary
import com.codeci.bench.core.InputScripts
import com.codeci.bench.core.Script
import com.codeci.bench.harness.FrameCapture
import com.codeci.bench.harness.HarnessState
import com.codeci.bench.harness.ImeInset
import com.codeci.bench.harness.InputMode
import com.codeci.bench.harness.KeyScriptRunner
import com.codeci.bench.harness.RepResult
import com.codeci.bench.harness.ResultsStore
import com.codeci.bench.harness.RunRecord
import com.codeci.bench.harness.ScriptRunner
import com.codeci.bench.keys.ComposeCoreSpike
import com.codeci.bench.keys.ImeFlicker
import com.codeci.bench.keys.KeysSpikeScripts
import com.codeci.bench.keys.SoraCoreSpike
import com.codeci.bench.keys.SpikeSession
import com.codeci.ide.ui.utils.LanguageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The bench corpus checked into this module (never into the app). */
enum class CorpusSpec(val label: String, val asset: String, val language: LanguageType) {
    BENCH_C("bench.c (~5 000 lines / 175 kB)", "bench/bench.c", LanguageType.C),
    BENCH_HTML("bench.html (517 lines / 31 kB)", "bench/bench.html", LanguageType.HTML_CSS)
}

/** The candidate cores: 25.1's three + the two 28.1 CodeC Keys spikes. */
enum class CandidateId(val label: String, val blurb: String, val notes: String) {
    NOW(
        "C-now",
        "Today's BasicTextField + SyntaxVisualTransformation (Phase 22 shape)",
        "mirror of the EditorScreen widget stack: ±3 000-char windowed spans, " +
            "80 ms debounced off-thread highlight, 20 ms decorations, 120 ms completion scan"
    ),
    SORA(
        "C-sora",
        "sora-editor 0.24.6 CodeEditor in AndroidView + JavaLanguage lexer",
        "LGPL-2.1 BINARY dependency (no source vendored); C code drawn with " +
            "Java token rules — incremental spans + identifier completion"
    ),
    COMPOSE2(
        "C-compose2",
        "Visible-window Compose sketch (DocumentBuffer + per-line spans)",
        "only visible lines laid out; one line re-tokenized per edit; spike " +
            "editing scope: caret-line field, no cross-line IME composing"
    ),
    K_COMPOSE(
        "K1-codecgrid",
        "28.1 spike S1: Compose core (C-now NowState) + IME-free CodeC key grid",
        "grid caps → CodecKeyGrid.commit → EditorKeySet.apply → NowState.updateCode; " +
            "system IME suppressed (window ALWAYS_HIDDEN + hide poll + hide on down); " +
            "DOWN→commit latency ledger + tap echo + IME-inset probe active"
    ),
    K_SORA(
        "K2-codecgrid",
        "28.1 spike S2: sora CodeEditor + IME-free CodeC key grid",
        "grid caps → programmatic Content insert/delete (SymbolInputView route); " +
            "same suppression trio as K1; same latency/echo/IME probes"
    );

    /** The 28.1 candidates — grid scenarios instead of the 25.1 scenario set. */
    val isKeysSpike: Boolean
        get() = this == K_COMPOSE || this == K_SORA
}

/** Scenario families the runner reports differently. */
enum class ScenarioKind { TYPING, SCROLL, DRAG }

/** Scenario buttons for the 25.1 cores (cold open is measured on every screen open). */
enum class Scenario(val label: String, val factory: () -> Script, val kind: ScenarioKind) {
    BURST("Type burst ×3 (60 keys @40 ms)", { InputScripts.burst60() }, ScenarioKind.TYPING),
    FLING("Fling ×3 (~500 lines)", { InputScripts.fling() }, ScenarioKind.SCROLL),
    CARET_DRAG("Caret drag ×3 (long-press → drag)", { InputScripts.caretDrag() }, ScenarioKind.DRAG),
    COMPLETION("Completion churn ×3 (16 keys @220 ms)", { InputScripts.completionChurn() }, ScenarioKind.TYPING)
}

/** The 28.1 CodeC Keys spike scenarios (spec §2 — the budget table rows). */
enum class SpikeScenario(val label: String, val reps: Int) {
    TYPE_BURST("Grid type burst ×3 (64 commits @40 ms)", 3),
    HOLD_REPEAT("Hold-repeat burst ×3 (30 presses / 40 commits)", 3),
    HW_PATH("HW key path check ×3 (20 synthesized keys, no IME)", 3),
    RUN_ROUTE("Run-row routing check (grid → stdin row)", 1),
    HUMAN("Human session: tap 5 min (live p95) — stop early to record", 1)
}

private sealed interface Screen {
    data object Home : Screen
    data class Candidate(val candidate: CandidateId, val corpus: CorpusSpec) : Screen
}

class BenchMainActivity : ComponentActivity() {

    private val harness = HarnessState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        harness.rootView = window.decorView
        harness.window = window
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                BenchApp(harness)
            }
        }
    }
}

@Composable
private fun BenchApp(harness: HarnessState) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            harness,
            onOpen = { candidate, corpus -> screen = Screen.Candidate(candidate, corpus) }
        )
        is Screen.Candidate -> CandidateScreen(
            candidate = s.candidate,
            corpus = s.corpus,
            harness = harness,
            onBack = { screen = Screen.Home }
        )
    }
}

@Composable
private fun HomeScreen(harness: HarnessState, onOpen: (CandidateId, CorpusSpec) -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("CodeC Bench — 25.1 editor cores + 28.1 CodeC Keys spike", fontSize = 20.sp)
        Text(
            "Device-measured, release APK (R8), identical scripted input.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        HorizontalDivider()

        Text("Before you run", fontSize = 16.sp)
        Text(
            "• Battery above 30 %, phone cool; no other app in the foreground.\n" +
                "• 28.1 gate: open K1-codecgrid and K2-codecgrid, WAIT for bench.c, " +
                "run every scenario, then the 5-min human session, on BOTH cores.\n" +
                "• Do NOT touch the screen while a scripted scenario runs (input is scripted).\n" +
                "• Control check: flip the screen's \"IME\" toggle to allowed, summon the " +
                "keyboard once — the live ime probe must go >0, then flip back (proves the " +
                "flicker detector is real, per the measure-don't-assert law).\n" +
                "• Type the three spike answers into the notes box below BEFORE exporting.",
            fontSize = 12.sp
        )
        OutlinedTextField(
            value = ResultsStore.ownerNotes,
            onValueChange = { ResultsStore.ownerNotes = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            placeholder = {
                Text(
                    "Q1: BT keyboard typed into the editor while the IME was suppressed? " +
                        "Q2: stdin route kept working while the editor never owned an IME? " +
                        "Q3: TalkBack read editor content with IME off? " +
                        "Final: does grid typing FEEL instant (yes/no)?",
                    fontSize = 11.sp
                )
            }
        )
        HorizontalDivider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                harness.inputMode =
                    if (harness.inputMode == InputMode.KEY_EVENTS) InputMode.DIRECT else InputMode.KEY_EVENTS
            }) {
                Text("Input mode: ${harness.inputMode.label}")
            }
        }
        Text(
            "keys = synthesized hardware-key events into the focused editor " +
                "(production path). direct = each editor's own content API " +
                "(fallback when a core ignores dispatched keys). Grid scenarios " +
                "always press the real cap handler; the toggle only affects 25.1 cores.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        HorizontalDivider()

        for (candidate in CandidateId.entries) {
            Text(candidate.label, fontSize = 16.sp)
            Text(candidate.blurb, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val corpora = if (candidate.isKeysSpike) listOf(CorpusSpec.BENCH_C) else CorpusSpec.entries.toList()
                for (corpus in corpora) {
                    Button(onClick = { onOpen(candidate, corpus) }) {
                        Text(if (corpus == CorpusSpec.BENCH_C) "bench.c" else "bench.html")
                    }
                }
            }
        }
        HorizontalDivider()

        Text("Results (${ResultsStore.runs.size} runs)", fontSize = 16.sp)
        for (run in ResultsStore.runs.takeLast(6).asReversed()) {
            Text(
                "${run.candidate} · ${run.corpus} · ${run.scenario}: ${run.medianLine()}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (ResultsStore.copyToClipboard(context)) {
                    Toast.makeText(context, "Results copied as markdown", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Copy all") }
            OutlinedButton(onClick = { ResultsStore.share(context) }) { Text("Share") }
            OutlinedButton(onClick = { ResultsStore.clear() }) { Text("Clear") }
        }
        val apk = harness.apkSizeMb()
        Text(
            "Bench APK size: " + (apk?.let { "%.1f MB".format(it) } ?: "?") +
                " · file: " + ResultsStore.persistedPath(context).absolutePath,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CandidateScreen(
    candidate: CandidateId,
    corpus: CorpusSpec,
    harness: HarnessState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showSurface by remember { mutableStateOf(false) }
    var coldOpenMs by remember { mutableStateOf<Double?>(null) }
    var status by remember { mutableStateOf("loading corpus…") }
    var running by remember { mutableStateOf(false) }
    var humanStop by remember { mutableStateOf(false) }
    val t0 = remember { mutableStateOf(0L) }
    // The 28.1 spike's shared press/echo/latency session (null for 25.1 cores).
    val spikeSession = remember { if (candidate.isKeysSpike) SpikeSession() else null }

    // Cold-open measurement: t0 before the corpus is read; stop after the
    // surface has drawn two frames WITH content. Same protocol per candidate.
    LaunchedEffect(candidate, corpus) {
        t0.value = System.nanoTime()
        try {
            text = withContext(Dispatchers.IO) {
                context.assets.open(corpus.asset).bufferedReader().use { it.readText() }
            }
        } catch (t: Throwable) {
            loadError = "corpus load failed: $t"
            return@LaunchedEffect
        }
        showSurface = true
        withFrameNanos { }
        withFrameNanos { }
        coldOpenMs = (System.nanoTime() - t0.value) / 1_000_000.0
        ResultsStore.add(
            RunRecord(
                candidate = candidate.label,
                corpus = corpus.label,
                scenario = "cold_open",
                mode = harness.inputMode,
                notes = "read + compose + 2 frames",
                reps = listOf(RepResult(rep = 1, summary = FrameSummary.EMPTY, coldOpenMs = coldOpenMs))
            )
        )
        ResultsStore.persist(context)
        status = "ready — run a scenario"
    }

    // Continuous IME-inset sampling while the spike surface is up — every
    // 120 ms, independent of the suppression poll, so the flicker probe also
    // covers the "IME allowed" control state.
    LaunchedEffect(spikeSession, showSurface) {
        val session = spikeSession ?: return@LaunchedEffect
        while (true) {
            session.addImeSample(ImeInset.bottomPx(harness.rootView))
            delay(120)
        }
    }

    fun runScenario(scenario: Scenario) {
        val target = harness.attachedTarget ?: run {
            status = "no editor attached yet"
            return
        }
        running = true
        scope.launch {
            val mode = harness.inputMode
            val reps = mutableListOf<RepResult>()
            val window = harness.window
            try {
                for (rep in 1..3) {
                    status = "rep $rep/3: ${scenario.label} (mode ${mode.label})…"
                    target.scrollToTop()
                    delay(700) // settle + cool-down before the rep
                    val startLine = target.firstVisibleLine()
                    val capture = window?.let { FrameCapture(it) }
                    capture?.start()
                    val typed = ScriptRunner.run(scenario.factory(), target, mode)
                    val samples = capture?.stop() ?: LongArray(0)
                    val summary = FrameStats.summarize(samples)
                    val traversed = if (scenario.kind == ScenarioKind.DRAG) {
                        val endLine = target.firstVisibleLine()
                        if (startLine in 0..endLine) endLine - startLine else -1
                    } else {
                        -1
                    }
                    reps += RepResult(
                        rep = rep,
                        summary = summary,
                        typed = if (scenario.kind == ScenarioKind.TYPING) typed else -1,
                        linesTraversed = traversed
                    )
                    delay(2500) // battery/thermal cool-down between reps
                }
                ResultsStore.add(
                    RunRecord(
                        candidate = candidate.label,
                        corpus = corpus.label,
                        scenario = scenario.name.lowercase(),
                        mode = mode,
                        notes = candidate.notes,
                        reps = reps
                    )
                )
                ResultsStore.persist(context)
                status = "done — ${scenario.name.lowercase()} recorded"
            } catch (t: Throwable) {
                status = "scenario failed: ${t.message}"
            } finally {
                running = false
            }
        }
    }

    fun runSpikeScenario(scenario: SpikeScenario) {
        val session = spikeSession ?: return
        val target = harness.attachedTarget ?: run {
            status = "no spike editor attached yet"
            return
        }
        running = true
        scope.launch {
            val reps = mutableListOf<RepResult>()
            val window = harness.window
            try {
                for (rep in 1..scenario.reps) {
                    status = "rep $rep/${scenario.reps}: ${scenario.label}…"
                    humanStop = false
                    target.scrollToTop()
                    delay(700)
                    session.resetRun()
                    val capture = window?.let { FrameCapture(it) }
                    capture?.start()
                    var typedNote = -1
                    var latencyLine: String? = null
                    var auditLine: String? = null
                    try {
                        when (scenario) {
                            SpikeScenario.TYPE_BURST -> {
                                val outcome = KeyScriptRunner.run(KeysSpikeScripts.typeBurst64(), session, target)
                                latencyLine = outcome.latency.line()
                                auditLine = outcome.audit.line()
                                typedNote = outcome.lengthDelta
                            }
                            SpikeScenario.HOLD_REPEAT -> {
                                val outcome = KeyScriptRunner.run(KeysSpikeScripts.holdRepeat30(), session, target)
                                latencyLine = outcome.latency.line()
                                auditLine = outcome.audit.line()
                                typedNote = outcome.lengthDelta
                            }
                            SpikeScenario.HW_PATH -> {
                                // Synthesized hardware-key Events (Q1 probe): with the
                                // soft IME suppressed this is exactly the BT-keyboard
                                // path — the window hands KeyEvents to the focused view.
                                val landed = ScriptRunner.run(InputScripts.hwCheck20(), target, InputMode.KEY_EVENTS)
                                typedNote = landed
                                auditLine = "hwkeys=$landed/20"
                            }
                            SpikeScenario.RUN_ROUTE -> {
                                session.routeToRunRow = true
                                val outcome = KeyScriptRunner.run(KeysSpikeScripts.runRowCheck(), session, target)
                                val rowText = synchronized(session.runRowText) { session.runRowText.toString() }
                                val rowOk = rowText == "run stdin "
                                val docUntouched = outcome.lengthDelta == 0
                                typedNote = outcome.audit.landed
                                latencyLine = outcome.latency.line()
                                auditLine = "runrow=${if (rowOk) "OK" else "FAIL($rowText)"} " +
                                    "doc-untouched=${if (docUntouched) "OK" else "FAIL(Δ${outcome.lengthDelta})"} " +
                                    "echo=${outcome.audit.line()}"
                            }
                            SpikeScenario.HUMAN -> {
                                status = "human session — tap freely (IME suppressed). 'stop & record' ends it."
                                val started = SystemClock.uptimeMillis()
                                while (!humanStop && SystemClock.uptimeMillis() - started < 300_000L) {
                                    delay(500)
                                }
                                typedNote = session.commitCount
                                latencyLine = session.ledger.snapshot().line()
                                auditLine = "owner taps (no script); stopped=${if (humanStop) "manual" else "timeout"}"
                            }
                        }
                    } finally {
                        session.routeToRunRow = false
                    }
                    val samples = capture?.stop() ?: LongArray(0)
                    val summary = FrameStats.summarize(samples)
                    val ime = ImeFlicker.analyze(session.snapshotIme())
                    reps += RepResult(
                        rep = rep,
                        summary = summary,
                        typed = typedNote,
                        linesTraversed = -1,
                        latencyLine = latencyLine,
                        auditLine = auditLine,
                        imeLine = if (ImeInset.supported()) ime.line() else "ime: probe n/a (pre-API 30)"
                    )
                    if (scenario.reps > 1) delay(2500)
                }
                ResultsStore.add(
                    RunRecord(
                        candidate = candidate.label,
                        corpus = corpus.label,
                        scenario = scenario.name.lowercase(),
                        mode = InputMode.DIRECT, // grid commits go straight to the document
                        notes = candidate.notes,
                        reps = reps
                    )
                )
                ResultsStore.persist(context)
                status = "done — ${scenario.name.lowercase()} recorded"
            } catch (t: Throwable) {
                status = "scenario failed: ${t.message}"
            } finally {
                running = false
                humanStop = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            TextButton(onClick = onBack) { Text("← Home") }
            Column {
                Text("${candidate.label} · ${corpus.label}", fontSize = 14.sp)
                Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }

        loadError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            return
        }

        val loaded = text
        if (loaded != null && showSurface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (candidate) {
                        CandidateId.NOW -> NowCandidate(loaded, corpus.language, harness)
                        CandidateId.SORA -> SoraCandidate(loaded, harness)
                        CandidateId.COMPOSE2 -> Compose2Candidate(loaded, corpus.language, harness)
                        CandidateId.K_COMPOSE ->
                            spikeSession?.let { ComposeCoreSpike(loaded, harness, it) }
                        CandidateId.K_SORA ->
                            spikeSession?.let { SoraCoreSpike(loaded, harness, it) }
                    }
                }
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (candidate.isKeysSpike) {
                        for (scenario in SpikeScenario.entries) {
                            Button(
                                onClick = { runSpikeScenario(scenario) },
                                enabled = !running,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(scenario.label, fontSize = 12.sp) }
                        }
                        if (running) {
                            OutlinedButton(
                                onClick = { humanStop = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("stop & record", fontSize = 12.sp) }
                        }
                    } else {
                        for (scenario in Scenario.entries) {
                            Button(
                                onClick = { runScenario(scenario) },
                                enabled = !running,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(scenario.label, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }
}
