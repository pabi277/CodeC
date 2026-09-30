# Part 76.3 — client, panel and the fifth rail slot

Phase: [76](README.md)

## First move: evidence, not code

- `SidePanelPlan.RAIL` = navigation · files · search · repository · **reserved**; `WIRED` excluded the reserved slot; `EditorSidePanel` drew it at 35 % alpha with `panel_reserved` as its description.
- `EditorSidePanel` already slots `files` and `repositoryContent` from the editor screen — the AI panel follows the same pattern.

## Design

**`GeminiClient`** — `suspend stream(apiKey, model, body, onText): AiOutcome` on `Dispatchers.IO`: POST, connect/read timeouts, headers from `GeminiRequest`, non-2xx ⇒ read ≤ 8 KB of the error stream (manual bounded read — `readNBytes` is API 33+) into `AiErrors.forHttp`, else `readLine` → `SseLineSplitter` → `GeminiResponse` → `AiAnswerAccumulator`. Cancellation: `Job.invokeOnCompletion { disconnect() }` + `ensureActive()` per line; `CancellationException` rethrown; `UnknownHost` ⇒ OFFLINE, `SocketTimeout` ⇒ TIMEOUT, other I/O ⇒ NETWORK. No logging; exception messages never surface.

**`AiViewModel`** (`AndroidViewModel`) — `AiUiState(keySaved, model, phase IDLE|PREVIEW|STREAMING|DONE|FAILED, prompt, answer, cutShort, error, notice, showSettings, testing, testResult, setupError)`, in memory only, no key field. `preview` → `send` (only from PREVIEW; the key is loaded inside the coroutine and dropped after the call) → `stop` (keeps partial text, marks cut short) / `retry` (back to PREVIEW — every request is confirmed) / `clear`. `onProjectChanged` clears everything. `saveKey`, `saveModel`, `deleteKey`, `testConnection` (content-free; an EMPTY reply still proves key + model + network).

**`AiPanel`** (Compose, tokens + `MaterialTheme` roles only, no `Color(0x…)`) — NEEDS_PROJECT message · key setup (masked key field, model field, AI Studio link, terms + prohibited-use links via `LocalUriHandler`, free-tier + region notes, the 18+ checkbox; Save enabled only when `AiKeySetup.canSave` and the model id is valid; the key field is cleared after Save) · ask (question, Explain selection, Explain last error, "not saved" note) · preview card (header with model + char count, unsaved note, free-tier note, the exact text in a scrollable monospace box, Send/Cancel) · streaming (spinner, Stop, live text) · done (selectable answer, cut-short note, "can be wrong / nothing changed", Copy answer, New question) · failed (fixed message, Try again, New question) · ⚙ settings (model, Test connection, Delete key).

**Rail and editor wiring**
- `RailPanel.RESERVED("reserved", "Reserved")` → `RailPanel.AI("ai", "AI helper")`, same fifth position, same `AutoFixHigh` glyph; `WIRED = RAIL`.
- `EditorSidePanel(aiContent = …)`: `RailPanel.AI -> aiContent()`; content description = slot label; `panel_reserved` string removed.
- `EditorScreen`: `aiViewModel: AiViewModel = viewModel()`; `LaunchedEffect(currentProject, openMode)` ⇒ `onProjectChanged(name only in PROJECT mode)`; prompts are built **at tap time** from `codeText.value` (selection), `activeTabPath ?: fileName` (label), `LanguageType.fromFileName(...).label`, `isDirty`; run prompts from `outputState` + `diagnostics` via `AiGate.runFailed`, with `pathLabels` = project root → relative, `filesDir` → `~app`.

## Exit condition

- The fifth slot opens the helper; in SINGLE_FILE/SCRATCH it shows only the "open a project" message.
- No request without the preview's Send; Stop disconnects.
- The answer disappears on New question, project switch, or process death.

## Tests (as built)

| File | Cases |
|---|---|
| `AiHelperWiringTest` (new) | 8 — no logging; no write/run calls; key storage literals; key not in state; send only from PREVIEW + exactly two `client.stream(` sites; answer not persisted; editor gates on `openMode` and resets per project; no `Color(0x` in the panel |
| `SidePanelPlanTest` (changed) | rail ids end with `"ai"`; all five wired; AI is last |
| `SidePanelWiringTest` (changed) | `RailPanel.AI -> aiContent()`; editor supplies `aiContent = {` + `AiPanel(` |

## Deferred / rejected with reasons

- **No Settings-screen control** (standing law): AI settings live inside the panel's ⚙, next to the only feature that uses them.
- **Inserting/applying answers** — Level 3 (review + undo), not authorized.
- **Whole-file or multi-file context** — Level 2.
- **Conversation history / follow-ups** — each exchange stands alone at Level 1 (D6: nothing saved).
- **Strings in `strings.xml`** — the panel's copy lives in `AiCopy` so the owner-required disclosures are pinned by pure tests; localisation can move them later without changing tests' intent.
