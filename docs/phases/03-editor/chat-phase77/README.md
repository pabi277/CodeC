# Phase 77 — AI UI for phones: floating AI button + bottom chat sheet

> **Status: ✅ DEVICE-PASSED, merged on the owner's command (2026-10-01; owner: "all good you can merge it").** All three parts are
> implemented; both Output-conflict variants (A/B) are in the build for the owner to
> pick on the phone — see `DEVICE_ROUND.md`. Not merged. Builds on
> [Phase 76](../chat-phase76/README.md) (AI Level 1, ✅ device-passed, merged).

## Owner rows (verbatim, 2026-10-01)

> *"I have a doubt about the ui. For phone use space management is 1st
> priority. I was thinking the 3 ber ai place only for full setup ai but chat
> will be a floating botton on the screen that can be moved anywhere and one
> click to open full chat, full screen float screen etc options. You think too
> what will be best do"*

Answers to the agent's three questions:

| # | Question | Owner answer | Meaning for this phase |
|---|---|---|---|
| Q1 | Follow-up chat (multi-turn)? | *"No i just making the ui future pruff"* | **Still one question at a time** (Level 1 behaviour unchanged). The UI is shaped like a chat (message list + input) so multi-turn can arrive later without a redesign — but no earlier turns are ever re-sent in 77. |
| Q2 | Remember the button position across restarts? | *"Yes i think"* | Position (edge + height) persists — see Part 77.1 for where (no DataStore key). |
| Q3 | Output panel open when chat opens — replace or full? | *"When Build i will test whatever looking good i will select"* | **Build both variants** (final: both stay as a user setting — see PART_77_2) behind one internal constant/flag for the device round; the owner picks; the loser is deleted before merge. |

## Why (evidence on `main` after Phase 76)

- The AI panel lives in the side panel, which covers **85 % of the screen width** (`ui/editor/SidePanelPlan.kt`: `PANEL_WIDTH_FRACTION = 0.85f`) — the answer hides the very code it explains.
- The editor already has a phone-proven bottom split with drag + keyboard rules: the Output panel (`ui/editor/OutputPanelHeight.kt`: `DEFAULT_FRACTION 0.40`, `FRACTION 0.55`, `IME_FRACTION 0.38`, `resolve(requested, available, imeVisible)`; splitter in `ui/screens/EditorScreen.kt`, Phase 11). The chat sheet should reuse that model, not invent a second one.
- The AI logic is already UI-independent: `ui/ai/AiViewModel.kt` (state machine IDLE → PREVIEW → STREAMING → DONE/FAILED), `AiContextBuilder`, `GeminiClient`. Phase 77 is a **surface change**; the request/preview/key rules do not move.

## The design in one picture

```
┌──────────────────────────────┐   ┌──────────────────────────────┐   ┌──────────────────────────────┐
│ tabs                         │   │ tabs                         │   │ ✨ AI · gemini-3-flash…  ⤢ ▾ │
│ 1  def area(r):              │   │ 1  def area(r):              │   │──────────────────────────────│
│ 2      return 3.14*r*r       │   │ 2      return 3.14*r*r       │   │ You: Explain selection       │
│ 3                            │   │──────── drag handle ─────────│   │  (preview card / answer …)   │
│ 4  print(area(2))         (✨)│   │ ✨ AI · gemini-3-flash…  ⤢ ▾ │   │                              │
│ 5                            │   │ You: Explain selection       │   │ AI: `area` multiplies …      │
│                              │   │ AI: `area` multiplies …      │   │                              │
│                              │   │ [Copy]            [New]      │   │                              │
│ coding row / keys            │   │ [ Ask about the code…   ➤ ]  │   │ [ Ask about the code…   ➤ ]  │
└──────────────────────────────┘   └──────────────────────────────┘   └──────────────────────────────┘
   1. Bubble (edge-snapped)            2. HALF sheet (default)            3. FULL sheet
```

- **Tap bubble** → HALF. **Drag handle up / ⤢** → FULL. **Drag down / ▾ / Back** → minimized to the bubble (exchange kept in memory).
- **Long-press bubble** → *Hide button* · *Move to other side*.
- **✨ rail slot** → becomes **AI home**: key setup, model, Test connection, Delete key, *Show AI button* switch, *Open AI chat*.

## Parts

| Part | Doc | Scope |
|---|---|---|
| 77.1 | [PART_77_1_FLOATING_BUTTON.md](PART_77_1_FLOATING_BUTTON.md) | pure `AiBubblePolicy` (snap, bounds, keyboard, persistence) + the draggable button |
| 77.2 | [PART_77_2_CHAT_SHEET.md](PART_77_2_CHAT_SHEET.md) | pure `AiSheetPolicy` (HIDDEN/HALF/FULL, heights, Output-panel variants A/B) + the chat-shaped sheet |
| 77.3 | [PART_77_3_AI_HOME_AND_ENTRY_POINTS.md](PART_77_3_AI_HOME_AND_ENTRY_POINTS.md) | rail slot → AI home; "Explain with AI" on a failed run; optional "Ask AI" in the selection menu |

## Laws this phase must keep (from Level 0 + house rules)

- **Every request still previews** (D4) — the preview card appears *inside* the sheet; Send is still the only road to the network.
- **Read-only** (D1): Copy only. **Nothing saved** (D6): the exchange lives in `AiViewModel` memory; minimizing keeps it, New question / project switch / app close clears it. Only the *button position and visibility* persist (Q2).
- **Projects only** (D5): no bubble in SINGLE_FILE/SCRATCH (`AiGate.availability`).
- **No-nag:** the bubble appears **only after a key is set up**; people who never set up AI never see it. No tooltip, no pulse, no first-run coach mark.
- **Single-click:** one tap on the bubble opens the sheet; with a selection, the sheet opens already on *Explain selection* ready to preview (still one more tap to Send — D4).
- **Space first:** the bubble never covers the coding row, keyboard, tab strip or bottom bar; the sheet never exceeds the Output panel's height rules while the keyboard is up.
- Standing laws: no new dependency/permission; **no new DataStore key** (persistence goes in the existing `no_backup/ai/ai_settings.properties`, justified in 77.1); no `Color(0x…)`; tokens only; `CodecType.codeFamily` for code text (Phase 76 CI lesson — `TypeAdoptionTest`).

## Exit condition

1. With a key set: a bubble shows on the editor in PROJECT mode; drag → snaps left/right, stays in bounds, survives restart and rotation.
2. Tap → HALF sheet with code visible above; ⤢/drag → FULL; ▾/drag/Back → bubble, exchange intact.
3. Keyboard up → bubble lifts above it; sheet obeys `IME_FRACTION`.
4. The whole Phase 76 flow (preview → Send → stream → Stop → Copy → New) works inside the sheet.
5. ✨ rail slot shows AI home only; *Show AI button* off hides the bubble; *Open AI chat* still opens the sheet.
6. Output-panel conflict variants A and B both buildable; owner picks one in the device round; the other is removed and the choice recorded.
7. CI green; device round passed; merge only on the owner's command.

## Tests (plan)

| File | Kind | Cases (≈) |
|---|---|---|
| `AiBubblePolicyTest` | pure | 12 — snap to nearest edge, clamp into bounds, keyboard lift, rotation (fraction survives), encode/decode of the saved position, garbage → default |
| `AiSheetPolicyTest` | pure | 10 — tap/drag transitions, drag thresholds, heights with/without keyboard, Back from each state, variants A/B with Output open |
| `AiSurfaceWiringTest` | source scan | 8 — bubble only when `READY`; no bubble before setup; sheet hosts the same `AiViewModel`; rail slot has no chat; no `Color(0x`; `CodecType.codeFamily`; one `client.stream(` path unchanged; Back handler routed through `BackRouter` |
| `SidePanelWiringTest` / `AiHelperWiringTest` | moved pins | the AI panel content moves; laws unchanged |

## Device round (to write as `DEVICE_ROUND.md` when built)

Bubble: appears only after setup · drag + snap both sides · never over keys/keyboard/tabs · survives restart + rotation · long-press Hide/Move · *Show AI button* toggle. Sheet: tap → HALF · ⤢ FULL · ▾/Back → bubble keeps answer · keyboard + question field · full 76 flow inside. **Q3 pick:** open chat with Output open under variant A, then B → owner chooses.

## Deferred / rejected with reasons

- **Free-floating resizable chat window** — rejected: at phone width it is too small to read and still hides code; resizing by finger is fiddly. HALF + FULL give the benefit without the cost.
- **System overlay bubble (over other apps)** — rejected: needs `SYSTEM_ALERT_WINDOW` (new permission, standing law) and is outside the editor's scope.
- **Multi-turn follow-ups** — deferred by the owner (Q1); the message-list shape makes it additive later (a later level must re-show the whole resent history in the preview, D4).
- **Remembering the sheet size (HALF/FULL) across restarts** — not asked for; opens HALF every time (less state).
