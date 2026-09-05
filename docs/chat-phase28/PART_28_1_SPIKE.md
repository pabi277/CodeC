# CodeC Phase 28.1 — IME-free Input Path Spike (feel gate)

**Status:** 🚧 **SPIKE BUILT 2026-09-05** (owner: "Start phase 28") on
`arena/01a070ae-codec` — lives entirely in `:bench` (never shipped), ready
for the owner's device round (§4). · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** nothing (gate for the whole phase)
· **Target files:** spike-only (never shipped), feeding a go/no-go note in
   `docs/EDITOR_MOBILE_RESEARCH.md` §9

---

## 1. Design

Prove the ONE thing that decides the phase: CodeC-drawn keys feeding the
editor **without opening the system IME**, at typing speed, with zero jank —
on both candidate cores.

| Spike | Path | Mechanism (documented Android pattern, clean-room) |
|---|---|---|
| S1 — Compose core | Editor stays `BasicTextField`-backed document in VM | IME suppressed on editor focus; key grid composable calls the existing `EditorKeySet.apply`/VM edit ops — **the strip already proves this path works**; spike only validates full-letter typing + focus/insets behavior |
| S2 — Sora core | `CodeEditor` in `AndroidView` | `rawInputType`/`textIsSelectable`-style IME suppression + programmatic insert/commit — the same route Sora's own `SymbolInputView` uses to insert text without IME characters |

Measured budgets (owner device, release build):
- key down → glyph committed + rendered ≤ 1 frame p95 (matches 25.1 budget law);
- 30-key burst with hold-repeat on: no dropped/swapped events;
- `adjustResize` layout settles with keyboard open/close with **no IME flicker**
  (this is where IME-free wins — measure it, don't assert it);
- caret-follow never lags behind held key repeat.

Also answer in the spike (evidence, not reading):
1. With IME fully suppressed, do HW Bluetooth keyboards still reach the editor
   (they must — 24.3 law)? Any focus trick needed?
2. Does suppressing IME break the **interactive run strip** (RunKeySet sends
   lines into the PTY — those edits flow through VM/terminal path, likely
   unaffected — verify)?
3. Any accessibility regression when the editor never owns an IME connection?
   (TalkBack exploration of editor content must still work.)

## 2. Implementation steps

1. Spike harness: editor screen variant with IME suppressed + a 3-row key grid
   (letters only, DEL, space, ⏎, TAB) routed through the key model.
2. Instrument key-latency logging (down→commit timestamps; reuse 25.1 harness).
3. Owner device runs ×3, both cores; fill the budget table; answer the three
   questions in writing.
4. Go/no-go recorded in §9 addendum. No-go = phase stops, strip path (L0)
   remains the product answer. Go = 28.2 starts.

## 3. Exit condition

```text
PASS = budgets met on BOTH cores + three questions answered + owner's
"feels instant" on a 5-minute typing session. Fail any → record no-go and stop.
```

## 4. Implementation record (2026-09-05, `arena/01a070ae-codec`)

All in `:bench` (throwaway; `:app` shipped ZERO of it — same law as 25.1).
`main` tip at branch start: `92af7fb` (Phase 27 merged, PR #51).

- **Pure model first (host-tested).** `bench/…/bench/keys/`:
  `CodecKeyGrid` (3 letter rows + TAB/DEL/⏎/space; every cap applies through
  the VERBATIM-mirrored `EditorKeySet.apply` — DEL is the only spike-local op,
  an explicit `backspace` flag, since the production model has no backspace
  key while the IME owns deletion); `KeysMetrics` (`LatencyStats` percentiles
  + over-1-frame count on the 25.1 percentile law, `KeyLatencyLedger` ring,
  `TapAuditor` strict-subsequence audit for dropped/dup/swap, `ImeFlicker`
  reducer); `KeysSpikeScripts` (`type_burst64`, `hold_repeat30` = 30 presses
  / 40 commits at 26.1's 150 ms + 40 ms law, `run_row_check`);
  `SpikeSession` (the ONE press path shared by script and thumb — echo,
  ledger, stdin-route). Mirrored pure files: `EditorKeySet`, `SmartTyping`,
  `CompletionPolicy`, `KeyStripStorage` (verbatim, bench convention).
  Host tests: `CodecKeyGridTest`, `KeysMetricsTest`, `KeysSpikeScriptsTest`,
  `SpikeSessionTest` (+ existing 25.1 suites).
- **Harness.** `harness/KeyScriptRunner.kt` lowers a `GridScript` onto the
  live session on the main dispatcher and returns (audit, latency, Δlength,
  ime-probe); `harness/ImeInset.kt` samples the platform ime inset (API 30+;
  honest "n/a" below). FrameMetrics capture + cold open + the markdown
  ResultsStore export are REUSED from 25.1 — `RepResult`/`toMarkdown` gained
  optional latency/audit/ime columns and an owner-notes section.
- **The two spike screens** (`keys/SpikeScreens.kt`): **K1** = C-now
  `NowState` (VM-shape document + undo + debounced highlight/decoration)
  behind a plain `BasicTextField`; **K2** = production-shape sora
  `CodeEditor`, every cap a programmatic `Content` insert/delete (the
  `SymbolInputView` route). IME suppression (measured, not assumed):
  window `SOFT_INPUT_STATE_ALWAYS_HIDDEN` while the K-screen is open + a
  60 ms `hideSoftInputFromWindow` poll + a hide in the Initial pointer pass
  on every down anywhere; the 120 ms `ImeInset` ticker proves whether the
  soft IME ever ate layout space (control: the "IME: allowed" toggle must
  make the probe go >0 — the detector proves itself).
- **Scenarios** (`SpikeScenario`): grid type burst ×3; hold-repeat burst ×3;
  **HW key path ×3** (20 synthesized KeyEvents — mechanical Q1 answer);
  **run-row route** (Q2: 12 grid commits → stdin buffer only, document
  length must be untouched — "editor without an IME connection still feeds
  every non-editor input path"); **human 5-min session** (live p95 line,
  stop-and-record).
- **CI.** `build-apk.yml` regained the bench wrapper (it was removed with
  Phase 25's merge): `./gradlew :bench:assembleRelease
  :bench:testDebugUnitTest` with `set -o pipefail | tee`, + the
  **`CodeC-Bench`** artifact, + a failure step re-emitting the real error
  lines as check-run annotations (the raw log stream is not sandbox-
  readable; this makes every bench round self-describing — the 25.1 shim
  trick generalized).
- **CI history on `arena/01a070ae-codec`:** `33956591999` ❌ (app steps
  green; bench step exit-1, raw log not readable — motivated the annotation
  step; two host-test expectation bugs found by self-review: the fold-math
  DEL count and the swapped-law drop count — `5bf70bd`).
  → `33956854196` (result recorded by the next line when resolved).

## 5. Owner device-round recipe (the gate)

1. github.com → **Actions** → latest green **Build APK** (this branch) →
   Artifacts → download **`CodeC-Bench`** → install (app "CodeC Bench",
   installs alongside the IDE; update of the 25.1 bench — same debug key).
2. Phone cool, battery > 30 %. In the bench Home, write your three answers +
   the feel verdict into the **notes box FIRST** (it rides the export).
3. Open **K1-codecgrid · bench.c**, wait for "ready". Run in order:
   `Grid type burst`, `Hold-repeat burst`, `HW key path check`,
   `Run-row routing check`. Then flip "IME: allowed", summon Gboard once —
   the live `ime=` readout must go > 0 — flip back (detector self-check).
   Then run `Human session` and type on the GRID for 5 minutes: free C-ish
   text, held DELs, the works. "stop & record" ends it early if you want.
4. Repeat on **K2-codecgrid**. Between the two, also test with a real
   Bluetooth keyboard while the grid is up (Q1 — the synthesized-keys check
   is the mechanism proof; the BT pass is the fact).
5. Optional Q3: enable TalkBack, explore the K1/K2 screens (editor text +
   caps must still read).
6. Home → **Copy all** → paste the markdown sheet into the chat, plus a
   one-word verdict: *feels instant? yes/no*.

Budget table the sheet must fill (25.1 law, per core):

```text
                     K1 (compose core)   K2 (sora core)
keystroke p95            ≤ 16.7 ms           ≤ 16.7 ms
DOWN→commit p95          (report; budget    (report;  ≤ 16.7 ms
                           is subsumed)       subsumed)
burst audit              tap=64/64 drop=0 dup=0 swap=no   (both)
hold-repeat audit        tap=40/40, exact order           (both)
IME flicker              max=0px over all samples        (both)
frame stats during burst p95 ≤ 16.7 ms, bad=0            (both)
```

Go = every row green on BOTH cores + owner "feels instant" → 28.2 starts (on
the owner's word). No-go = record it in §9 of `EDITOR_MOBILE_RESEARCH.md`;
the strip path (L0) stays the product answer.
