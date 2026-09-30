# CodeC Phase 35.1 — "Always keyboard stay open"

**Status:** 🚧 IMPLEMENTED · **Cost:** `[client-only]` · **Effort:** S

## Symptom (owner)

*"some option like always keyboard stay open"* — the editor's keys row (CodeC
Keys) does not reliably stay up; the owner wants an option to keep it open
while editing.

## What exists today (evidence)

- `EditorScreen.keysRowVisible` (default true) is toggled only by the
  toolbar's expand/collapse button (`editor_hide_keys_row` /
  `editor_show_keys_row`).
- The keys row renders in two dock positions: `if (keysRowVisible && !imeVisible)`
  (no IME — above the bottom) and `if (keysRowVisible && imeVisible)` (riding
  the IME). So its visibility currently **follows the system IME**: when the
  IME hides (back, done, tap-outside), the keys row either moves or drops out
  of the "always visible" intent.
- `soraEditor.setSoftKeyboardEnabled(!codecKeysUp)` switches sora's soft IME
  off while CodeC Keys is up — so "stay open" means the CodeC Keys grid, not
  the system IME.

## Design

1. **Settings toggle** — `editor.keep_keys_open` (DataStore boolean, default
   **on** for new installs; the existing `codec_keys_enabled` master stays).
   Wired as a new `SettingsManager` key + a Settings → Editor row
   ("Keep the code keyboard open while editing").
2. **Semantics** — while the toggle is on and the editor tab is focused, the
   keys row stays mounted; the system IME stays suppressed
   (`setSoftKeyboardEnabled(false)` already does this). The expand/collapse
   toolbar button keeps working as an **explicit** override (a user gesture
   still wins over the default), but nothing else dismisses the grid.
3. **Interactive-run exception stays** (Phase 23.2 law): a program waiting on
   stdin still hands the IME back (`codecKeysUp = codecKeysOn && !waitingForInput`).

## Exit condition

```text
(Device)
1. Settings → Editor → "Keep the code keyboard open" ON; open a file, type,
   switch to Output, back — the keys row is still there without re-tapping.
2. The toolbar collapse still hides it; re-opening the editor restores the
   toggle's default.
3. RUN an interactive program → the system IME appears for stdin; after it
   ends, the keys row returns.
PASS = all three.
```

## Tests

- `SettingsManager` key round-trip (follows the DataStore-key pattern); the
  Phase 35 CI build `34367008019` is GREEN.
- `KeysStayPolicyTest` (pure): given (toggle, focused, waitingForInput,
  explicitCollapse) → expected "keys visible" — pins the one law all three
  exit conditions rely on (pure function, mirroring `CompletionPolicy`).
