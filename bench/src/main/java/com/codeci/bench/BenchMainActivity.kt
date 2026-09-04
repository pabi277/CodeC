package com.codeci.bench

import android.os.Bundle
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
import com.codeci.bench.harness.InputMode
import com.codeci.bench.harness.RepResult
import com.codeci.bench.harness.ResultsStore
import com.codeci.bench.harness.RunRecord
import com.codeci.bench.harness.ScriptRunner
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

/** The three candidate cores. */
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
    )
}

/** Scenario buttons (cold open is measured automatically on every screen open). */
enum class Scenario(val label: String, val factory: () -> Script, val kind: Kind) {
    BURST("Type burst ×3 (60 keys @40 ms)", { InputScripts.burst60() }, Kind.TYPING),
    FLING("Fling ×3 (~500 lines)", { InputScripts.fling() }, Kind.SCROLL),
    CARET_DRAG("Caret drag ×3 (long-press → drag)", { InputScripts.caretDrag() }, Kind.DRAG),
    COMPLETION("Completion churn ×3 (16 keys @220 ms)", { InputScripts.completionChurn() }, Kind.TYPING);

    enum class Kind { TYPING, SCROLL, DRAG }
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
        Text("CodeC Bench — Phase 25.1", fontSize = 20.sp)
        Text(
            "Device-measured editor-core spike. Release APK (R8), identical scripted input.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        HorizontalDivider()

        Text("Before you run", fontSize = 16.sp)
        Text(
            "• Battery above 30 %, phone cool; no other app in the foreground.\n" +
                "• Open a candidate, WAIT for the corpus to load, then run one scenario at a time.\n" +
                "• Do NOT touch the screen while a scenario runs (input is scripted).\n" +
                "• Each scenario runs 3 repetitions with cool-downs and lands in Results below.",
            fontSize = 12.sp
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
                "(fallback when a core ignores dispatched keys).",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        HorizontalDivider()

        for (candidate in CandidateId.entries) {
            Text(candidate.label, fontSize = 16.sp)
            Text(candidate.blurb, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (corpus in CorpusSpec.entries) {
                    Button(onClick = { onOpen(candidate, corpus) }) {
                        Text(if (corpus == CorpusSpec.BENCH_C) "bench.c" else "bench.html")
                    }
                }
            }
        }
        HorizontalDivider()

        Text("Results (${ResultsStore.runs.size} runs)", fontSize = 16.sp)
        for (run in ResultsStore.runs.takeLast(4).asReversed()) {
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
    val t0 = remember { mutableStateOf(0L) }

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
                    val traversed = if (scenario.kind == Scenario.Kind.DRAG) {
                        val endLine = target.firstVisibleLine()
                        if (startLine in 0..endLine) endLine - startLine else -1
                    } else {
                        -1
                    }
                    reps += RepResult(
                        rep = rep,
                        summary = summary,
                        typed = if (scenario.kind == Scenario.Kind.TYPING) typed else -1,
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
                    }
                }
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
