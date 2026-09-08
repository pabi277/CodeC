# CodeC Phase 31.5 / 31.6 — hand-rolled LSP stdio wire + editor attach

**Status:** 🚧 **WIRED** (2026-09-08, continuing `arena/01a07baa-codec`
on `arena/01a08023-codec` after the previous chat dropped). Owner:
the remaining work was *\"wire the editor\"*.

---

## 1. Why not sora `editor-lsp`

The 31.1 plan was `io.github.rosemoe:editor-lsp` from the sora 0.24.6
BOM. The gate-flip (`ce4c41b`) reproduced the original
`:app:processDebugMainManifest` red (run `34182333111`). The AAR
declares minSdk 26; CodeC is minSdk 24. Path (B) from JOURNEY §42
wins: a ~300 LOC stdio JSON-RPC client, no AGP bump, no AAR.

## 2. What was missing

31.1–31.4 shipped:

- the orchestrator (`LspManager`, L1–L5)
- Packages hub install cards
- a `NoopProvider` / then a first-cut `LspStdioClient`

The **chip strip and ghost** (Phase 27's primary surface) still called
`CodeCompletionEngine.completions()` only. LSP items could appear in
sora's ⌄-more panel (`CodeCLanguage.requireAutoComplete`) but never
in the thumb chips. That is the editor wire.

## 3. What this commit does

| Piece | Change |
|---|---|
| `LspWire` / `LspUris` | Byte `Content-Length` framing (not chars). Absolute `file://` URIs. |
| `LspStdioClient` | Reader thread + timed poll. `TimeoutException` on a hung server so L3 blacklists. |
| `StdioLspProviderFactory` | `$PREFIX/bin/<cmd>` + PATH / `LD_LIBRARY_PATH` / `LD_PRELOAD` (termux-exec) so shebang scripts actually exec. |
| `EditorViewModel.refreshCompletionItems` | Debounced off-main leg merges LSP into `completionItemsBase`. Instant leg still paints snippets (L5). |
| `ActiveLspManager.documentPath` | Absolute path both surfaces send. |
| `CodeCLanguage.requireAutoComplete` | Same `LspRequestContext.at(...)` as the VM. |
| Parser | Bare `CompletionItem[]` + `error.message` extraction. |

JSON files are no longer skipped by the VM (31.4's json-language-server
is the only completion source for `.json`; the engine still returns
empty for JSON — that is unchanged).

## 4. Exit condition (this part)

```text
1. Host: LspWireTest clientRoundTripsInitializeAndCompletionAgainstFakeProcess PASS
2. Host: existing LspManagerTest 1+3 still PASS (missing server / master OFF)
3. Device (owner): install clangd card → type in a .c file → a member
   snippets cannot know appears in the CHIP STRIP, not only ⌄ more.
```

(3) is the device gate. Do not claim it without a transcript.

## 5. CI hang (2026-09-08)

`LspStdioClientFramingTest.writerFramesContentLengthHeader` wrote a few
bytes into a `PipedOutputStream` then called `readBytes()` on the
connected `PipedInputStream`. `readBytes()` waits for **EOF**. The
writer was the same thread and never closed, so the test blocked
forever. That is runs `34185097875` / `34188032592` / `34189756351`
(owner cancelled at 3+ hours) and `34206169145`. Fix: frame through
`LspWire` into a `ByteArray`. Gradle `Test` tasks now time out at 5
minutes so a future hang fails the run instead of pinning the runner.

## 6. Research notes

- LSP spec `Content-Length` is **bytes** (microsoft.github.io/language-server-protocol).
- Android `ProcessBuilder` of a PREFIX shebang needs termux-exec
  `LD_PRELOAD` (CodeC `ShellEnvironment.termuxExecPreload`, Phase 3).
- Phase 27: chips/ghost are canonical; sora panel is ⌄-more browse.
