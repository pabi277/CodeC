# Part 77.3 — the ✨ rail slot becomes AI home, plus zero-space entry points

Phase: [77](README.md) · 📋 PLANNED (docs only)

## First move: evidence, not code

- `ui/components/EditorSidePanel.kt`: `RailPanel.AI -> aiContent()`; `ui/screens/EditorScreen.kt`: `aiContent = { AiPanel(...) }` (Phase 76).
- `ui/ai/AiPanel.kt`: split its composables — setup + settings stay here (AI home); ask/preview/stream/done/failed move to `AiChatSheet` (77.2).
- The Output panel header in `ui/components/OutputPanelView.kt` (`fun OutputPanelView(` at ~line 129) — where a failed run could offer "Explain with AI".
- Selection menu: CodeC does **not** customise Sora's text-action window today (no `EditorTextActionWindow` reference in `app/src/main/java`). Verify against the pinned sora version before promising a menu item.

### Evidence found (2026-10-01, before coding)

- `EditorSidePanel.kt`: `RailPanel.AI -> aiContent()` unchanged; `EditorScreen.kt:1620` now renders `AiHome(` there instead of `AiPanel(`. `SidePanelWiringTest` pin moved to `AiHome(`.
- `OutputPanelView.kt`: the header action row is `OutputActionRow` (param `onExplainWithAi: (() -> Unit)?`, button at l.420 only when non-null). `EditorScreen.kt:1135` builds the lambda only when `aiReady && aiRunFailed` (`AiGate.runFailed`) and passes it to the *expanded* panel only.
- **Ask AI in the selection menu — SKIPPED, with proof.** Sora 0.24.6 `EditorTextActionWindow extends EditorPopupWindow`; its five buttons are `private final` `ImageButton`s found by `R.id.panel_btn_*` from `R.layout.text_compose_panel` in the constructor; `updateBtnState()` only toggles those five. No registration API (read through `gh api` at tag 0.24.6). The only routes are a fork (rejected in the brief) or runtime view injection into `getView()` (fragile across Sora updates, untestable here). Substitute: select code, tap the bubble — with a selection while IDLE the sheet opens with *Explain selection* already previewed (Send still separate).

### Built

- `ui/ai/AiHome.kt`: NEEDS_PROJECT message, O1 key-setup gate, model + Save model, Test connection, Delete key, *Show AI button* switch, *Open AI chat* (closes the drawer with `DrawerCloseReason.FILE_OPENED`, opens the sheet at HALF). No ask/preview/stream composables remain here (pinned by `AiSurfaceWiringTest`).
- "Explain with AI" on the Output header: failed run + READY only; opens the sheet with the run-output prompt already in PREVIEW.
- `AiSurfaceWiringTest` — 12 source-pin cases.

## Design

**AI home (the ✨ slot)** — no chat here any more (owner: *"the 3 ber ai place only for full setup ai"*):
- NEEDS_PROJECT message (unchanged) · key setup gate (unchanged O1 text/checkbox/links) · when set up: model + Save, Test connection, Delete key, **Show AI button** switch (77.1 setting), **Open AI chat** button (opens the sheet HALF and closes the drawer — the fallback when the bubble is hidden).

**Zero-space entry points** (take no pixels until needed):
1. **"Explain with AI"** on the Output panel header, shown **only** when `AiGate.runFailed(...)` is true and AI is READY → opens the sheet with the run-output prompt already in PREVIEW (still Send to send — D4).
2. **"Ask AI" in the selection menu** — *optional, verify-first*: only if Sora's text-action window can take an extra item without forking the component. If not, record why and skip; the bubble's selection dot covers the need.

## Exit condition

The ✨ slot never shows a question field or answer; everything chat is in the sheet; the Output header offers AI only after a failed run and only when READY.

## Tests (plan)

Source pins (in `AiSurfaceWiringTest`): `AiPanel` has no `EXPLAIN_SELECTION` button; AI home has the Show-button switch and Open-chat button; the Output header's AI action is guarded by `AiGate.runFailed` and READY; `AiCopy` gains the new labels (pinned wording).

## Deferred / rejected with reasons

- **A Settings-screen AI section** — rejected (standing law: no new Settings control); AI home is the one place.
- **Selection-menu item by forking Sora's component** — rejected if it needs a fork; maintenance cost > benefit.
