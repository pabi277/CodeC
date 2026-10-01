# Part 77.2 — the chat sheet (HALF / FULL / minimized)

Phase: [77](README.md) · 📋 PLANNED (docs only)

## First move: evidence, not code

- Read the Output panel split in `ui/screens/EditorScreen.kt` (Phase 11 splitter, the `OutputPanelView(` call sites) and `ui/editor/OutputPanelHeight.kt` (`resolve(requested, available, imeVisible)`). The chat sheet uses the **same height maths**; do not fork it — extract or call it.
- Read `ui/navigation/BackRouter.kt` + `BackHandlerWiringTest`: every `BackHandler(` must be root-or-router (Phase 49 law). The sheet's Back is a new router row, not an ad-hoc handler.
- Read `ui/ai/AiPanel.kt`: its sections (setup / ask / preview / streaming / done / failed / settings) are split in 77.2 (chat) vs 77.3 (AI home).

### Evidence found (2026-10-01, before coding)

- `OutputPanelHeight.kt`: `IME_FRACTION 0.38` (l.34), `DEFAULT_FRACTION 0.40` (l.37), `defaultFor` (l.41), `resolve(requested, available, imeVisible)` (l.43). `AiSheetPolicy.halfHeight` *calls* `resolve` — no fork.
- `BackRouter.kt`: `BackHandlerWiringTest` pins exactly one `BackHandler(` in `EditorScreen` plus `BackRouter.decide(` and each `BackAction.* ->` branch. New state `aiSheetOpen` (l.51), action `CollapseAiSheet` (l.78), row 0 guarded `aiSheetOpen && !editorDrawerOpen && !keyboardVisible` (l.137) — existing rows were not reordered; with the IME up Back is the platform's, as for the Output panel.
- `AiPanel.kt` (deleted, copy not kept in repo) sections moved: ask/preview/streaming/done/failed → `AiChatSheet.kt` + shared `AiParts.kt`; setup/settings → `AiHome.kt` (77.3). `AiHelperWiringTest`'s Send pin moved to `AiChatSheet.kt`.
- Prompts were built at tap time in `EditorScreen` from `codeText` selection, `activeTabPath`, `outputState`, `diagnostics`; that code is hoisted to lambdas (`aiExplainSelection`, `aiExplainError`) that feed both the sheet chips and the Output header action.

### Built

- `ui/ai/AiSheetPolicy.kt` (pure): `AiSheetState` HIDDEN/HALF/FULL, `AiSheetEvent` TAP_BUBBLE/EXPAND/COLLAPSE/BACK/DRAG_END (>0.75 FULL, <0.20 HIDDEN, else HALF), `AiOutputConflict` REPLACE_OUTPUT (A) / OPEN_FULL (B), `openWithOutput`, `halfHeight`, `outputVisible`, `LIVE_DRAG_MAX 0.80`. `AiSheetPolicyTest` — 12 cases.
- `AiViewModel`: `sheet`, `outputConflict` (in memory, default A = `AiSheetPolicy.DEFAULT_CONFLICT`), `openSheet` / `sheetEvent` / `openWithOutput`; `preview()` refuses while STREAMING. Only `AiViewModel`, `AiContextBuilder` and `GeminiClient` logic otherwise unchanged; still exactly two `client.stream(` calls.
- `EditorScreen`: HALF sheet is a Column slot in the Output panel's place (A: Output hidden while the sheet is up — its state is untouched; B: the FULL overlay with `imePadding()`). The coding row, status bar, collapsed Output strip and Codec keyboard step aside while the sheet is open (`!aiSheetOpen`). Sheet closes on project change or when availability ≠ READY.
- Variant choice for the device round: ✨ home shows two temporary rows (A / B). Loser is deleted before merge, then `DEFAULT_CONFLICT`, the rows, `AiCopy.VARIANT_*` and `AiSheetPolicy.openWithOutput`'s other branch go with it.

## Design — pure `ui/ai/AiSheetPolicy.kt`

```kotlin
enum class AiSheetState { HIDDEN, HALF, FULL }
enum class AiSheetEvent { TAP_BUBBLE, EXPAND, COLLAPSE, BACK, DRAG_END }

/** Q3 — both built for the device round; the owner keeps one, the other is deleted. */
enum class AiOutputConflict { REPLACE_OUTPUT /* A */, OPEN_FULL /* B */ }

object AiSheetPolicy {
    /** Build-time pick for the device round (flip, build, compare). */
    val OUTPUT_CONFLICT: AiOutputConflict = AiOutputConflict.REPLACE_OUTPUT

    fun next(state: AiSheetState, event: AiSheetEvent, dragFraction: Float? = null): AiSheetState
    // TAP_BUBBLE: HIDDEN→HALF · EXPAND: HALF→FULL · COLLAPSE: FULL→HALF, HALF→HIDDEN
    // BACK: FULL→HALF→HIDDEN · DRAG_END: > 0.75 → FULL, < 0.20 → HIDDEN, else HALF

    /** Opening while the Output panel is open. */
    fun openWithOutput(conflict: AiOutputConflict): Pair<AiSheetState, Boolean /* collapse output */>
    // A: HALF + collapse output (chat takes the bottom slot; output returns on minimize)
    // B: FULL, output untouched underneath

    /** Height in px for HALF (reuses OutputPanelHeight rules incl. keyboard). */
    fun halfHeightPx(available: Float, imeVisible: Boolean): Float
}
```

Minimizing (HIDDEN) **never** clears `AiViewModel` state; only New question / project switch / process death do (D6). Opening the sheet while a request streams shows the live stream.

## The Android edge — `AiChatSheet`

Chat-shaped so multi-turn can be added later without redesign (owner Q1: *"future pruff"*), but **one exchange at a time** in 77:

```
┌ ✨ AI · <model>                          ⤢  ▾ ┐   header: title, model, expand, minimize
│ ─ drag handle ─                               │
│ [You]  Explain selection · main.py · 12 lines │   the question bubble (from AiPrompt)
│ [Check before sending] card  → Send / Cancel  │   = Phase 76 preview (D4), unchanged text
│ [AI]   streaming text …            [Stop]     │
│        Copy · New question · "can be wrong"   │
│ [ Ask about the code…                   ➤ ]   │   input row; ➤ builds a prompt → preview
│ [Explain selection] [Explain last error]      │   chips above the input when idle
└───────────────────────────────────────────────┘
```

- Code/answer text: `CodecType.codeFamily` for code blocks; body text via `MaterialTheme.typography`.
- Keyboard: the input row sits above the IME; HALF obeys `IME_FRACTION`.
- FULL still keeps the status bar; it is a sheet, not a new route (no navigation-state changes).
- With a selection when opened by the bubble: the idle state pre-selects *Explain selection* (one tap to preview, one tap to Send).
- Back: `BackRouter` row "AI sheet open" above the drawer/output rows → `AiSheetPolicy.next(BACK)`.

## Exit condition

Tap → HALF with code visible; drag/⤢ → FULL; ▾/drag/Back → bubble with the exchange intact; the full Phase 76 flow works inside; Output conflict A and B both demonstrable on device.

## Tests (plan)

`AiSheetPolicyTest` ≈10: every `next` transition; drag thresholds at 0.19/0.20/0.75/0.76; `openWithOutput` for A and B; `halfHeightPx` with and without the keyboard equals the Output panel's rule. Wiring: `BackRouter` has the sheet row; `AiChatSheet` reads the same `AiViewModel` (no second instance).

## Deferred / rejected with reasons

- **Free-floating windowed chat** — rejected (see README).
- **Multi-turn history** — owner deferred (Q1).
- **Persisting HALF/FULL** — not asked; always opens HALF.
- **Variant loser** — NOT deleted: **Owner decision (2026-10-01, at the merge gate): both variants STAY — the user chooses.** Answer to "which one did you pick?": *"Stay both user can select whatever they want"*. So nothing is deleted. The A/B choice is now a permanent ✨ home setting, "When the Output panel is open, AI chat" (A "Replaces the Output panel (bottom half)" default, B "Opens full screen"), saved as `sheet_with_output` (`REPLACE` | `FULL`) in the existing `no_backup/ai/ai_settings.properties` — still no DataStore key, no Settings-screen control; `deleteKey()` keeps it. This is a deliberate, owner-directed widening of "nothing saved except button position and visibility" to one more non-secret layout value.
