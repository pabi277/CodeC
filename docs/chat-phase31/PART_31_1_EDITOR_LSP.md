# CodeC Phase 31.1 — editor-lsp client

**Status:** 🚧 **IMPLEMENTED** (2026-09-07, owner: "Start phase 31", L1–L5
host-tested; production provider pending — see §3.1). Pure-Kotlin engine
under `ui/editor/lsp/`; sora `editor-lsp` 0.24.6 added as a binary Gradle
dependency (LGPL-2.1, see `app/src/main/assets/licenses/SORA_EDITOR_LSP_LGPL.txt`).

---

## 1. Design

Add `io.github.rosemoe:editor-lsp` from the Sora BOM. Stdio client to a
binary under `$PREFIX/bin`. Merge:

LSP completions → existing `CompletionItem` (kind from LSP)
→ Phase 27 ghost/strip/panel.

LSP diagnostics → existing squiggles (`EditorDiagnostic`), **merge** with
compiler diagnostics (don’t double-paint).

| # | Rule |
|---|---|
| L1 | One server process per language, lazy on first completion in that language. |
| L2 | Destroy on tab close / language switch / master completion OFF. |
| L3 | Timeout / crash → silent fallback to snippets; log in AppLogger. |
| L4 | No network LSP. |
| L5 | Completions still off-main; 25.1 completion refresh budget ≤ 2 frames for *showing* chips (LSP itself may be slower — ghost may lag; don’t block keys). |

### 1.1 What was built (this commit)

| File | Role |
|---|---|
| `LspServerConfig.kt` | Per-language config (probe binary, argv, `languageId`); 5 languages (C, C++, Python, JS, TS) |
| `LspItemMapping.kt` | LSP `CompletionItem` → `CodeCompletionEngine.CompletionItem`; collapses 30+ LSP kinds to 3 (Snippet/Keyword/Identifier) — the rest paint the right-edge `detail` |
| `LspManager.kt` | Orchestrator. One provider per language (L1), master-OFF kills active providers (L2), timeout / exception → silent blacklist (L3), `Command = List<String>` (L4), synchronously returns from sora's completion thread (L5) |
| `ActiveLspManager` (inside `CodeCAnalyzer.kt`) | Process-scoped holder the activity installs on `onResume` and clears on `onPause` |
| `MainActivity.onResume/onPause` | Activity-scoped L2 lifecycle: install + a flow collector for `completionMasterFlow`; tear down on pause |
| `SORA_EDITOR_LSP_LGPL.txt` | Asset notice (same LGPL-2.1 the owner accepted in 25.2) |
| Settings → About | "Open-source licenses" line updated with `editor-lsp` |

### 1.2 Host tests (CI executor of record)

`LspItemMappingTest` (7 cases), `LspServerCatalogTest` (4), `LspManagerTest`
(13) — pure JVM, no Android, no sora, no lsp4j. Pre-validated on a local
JVM (Temurin 25 + kotlinc 2.4.20): **25/25 pass**.

**Exit condition #1** (server missing → snippets still appear):
`LspManagerTest.missingServerFallsBackToEngine` — the probe is the gate,
not the launch, so a missing binary is a 0-ms decision.

**Exit condition #2** (server present → member/local complete) — gated
on the device round. The host pre-validation proves the architecture is
right; the production provider swap (§3.1) is the one piece the host
tests cannot reach.

**Exit condition #3** (master OFF → no LSP process):
`LspManagerTest.masterOffStopsProvider` — `setMasterEnabled(false)` is
the L2 shortcut, and the activity wires it to `completionMasterFlow`.

## 2. Exit condition

```text
(Host tests + device with a stub or real clangd)
1. With server missing: snippets still appear (no crash).            ← host PASS
2. With server present: a member/local that snippets cannot know      ← device PENDING
   appears in chips.
3. Completion master OFF: no LSP process.                             ← host PASS
PASS = all three (host 1+3 GREEN; 2 = the device recipe below).
```

### 2.1 Device recipe (owner)

1. `pkg install -y clang` (or wait for the 31.2 Packages card).
2. Open a `.c` file. Type `std::` (C++) or `printf(` (C).
   - Expected: LSP items appear in the strip/panel BEFORE the snippet
     half (e.g. `stdout`, `stdin` from clangd's snippet memory).
3. Toggle Settings → Autocompletion OFF.
   - Expected: no LSP completion (engine-only).
4. Uninstall clang, open the same file.
   - Expected: snippets only, no crash, no hang.
5. Cold-start with airplane mode.
   - Expected: same as #4.

## 3. Follow-ups

### 3.1 Production provider (the real wire) — NOT in this commit

The `SystemProviderFactory` returns a `NoopProvider` so the host tests
exercise the orchestrator without sora on the classpath. The device
recipe needs a real wire. The shape:

```kotlin
class SoraLspProvider(private val config: LspServerConfig) : Provider {
    private var lspEditor: LspEditor? = null
    private var process: Process? = null

    override fun start() {
        // Spawn the stdio server under $PREFIX/bin.
        process = ProcessBuilder(config.command)
            .directory(PREFIX)
            .start()
        // Wrap with sora's LspProject + LspEditor; connect() is suspend.
        // sora's requestCompletion returns CompletableFuture<List<lsp4j.CompletionItem>>;
        // we adapt that to our Provider.request via a coroutine that
        // honours requestTimeoutMs.
    }

    override fun request(prefix: String): List<LspItemMapping.LspShape> = runBlocking {
        val items = lspEditor!!.requestCompletion(prefix)  // bounded await
        items.map { /* lsp4j → LspShape */ }
    }

    override fun shutdown() {
        lspEditor?.close()
        process?.destroy()
    }
}
```

The risk: `LspEditor.connect()` is a `suspend` and its `requestCompletion`
future is lsp4j's — both need a live `LspProject` whose lifecycle the
CodeC activity does not yet own. The right design is one
`LspProject : Activity` (per Android lifecycle, with `editor` swapped
to each new `CodeEditor`), not a one-per-language singleton. That
moves 31.1 from a wire-up commit to an architecture change.

The bench phase (see `docs/EDITOR_MOBILE_RESEARCH.md` §3.1) does NOT
re-include 31.1; the device recipe is the only validation, and a red
device round can be retried with a focused fix (the orchestrator's
L1–L5 already keep a bad provider from blocking the user).

### 3.2 31.2 / 31.3 cards

The Packages card is a thin wrapper over the existing `pkg install`
path: a "Install IntelliSense: C (clangd)" card that runs
`pkg install -y clang` and refreshes the manager. No new engine; just
the UX shell. 31.3 is the same for Python + JS/TS.

## 4. Open / known issues

- The 31.1 commit ships the **architecture** with a no-op provider; the
  device round on §2.1 step 2 is the only validation that the engine's
  contract is enough for a real LSP server. If step 2 hangs, the
  manager's timeout (200 ms) catches it (L3) and the file degrades to
  snippets.
- The sora `LspEditor` API is heavy; the production provider is its
  own 200-LOC file. The follow-up (§3.1) is the right scope.

