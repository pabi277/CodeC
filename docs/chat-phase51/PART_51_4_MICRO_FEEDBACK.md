# CodeC Phase 51.4 — Haptics, press states, confirmations

> **Status:** 🚧 IMPLEMENTED (2026-09-21, `arena/01a0c4cb-codec`) — device round
> F14-F16 NOT run · **Cost:** `[client-only]` · **Effort:** S/M ·
> **Owner row (verbatim):** *"it's not attractive to user to use multiple time"*.
> Parent: [`README.md`](README.md) ·
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md).

## First move: evidence, not code

```text
$ grep -rn "HapticFeedback\|vibrate" app/src/main/java --include=*.kt | wc -l
  28
```

…and every one of those 28 lines is in one of three places:

| Where | Lines | What it is |
|---|---|---|
| `ui/keyboard/CodecKeyboard.kt` | 172, 255, 267 | the **CodeC keyboard's own** key haptics (`HapticFeedbackType.LongPress`) |
| `ui/components/TerminalEmulatorView.kt` | 126, 133, 136 | the terminal **bell** (a `VibrationEffect` on BEL) |
| `ui/terminal/CodecApiBridge.kt` + `ShellEnvironment.kt` | 875-884, 1321-1372 | the `codec-vibrate` **CodeCApi script** (users' own programs) |

So: **the app's own chrome never gives a haptic.** Not when a program runs, not
when it fails, not when a file saves, not when an install finishes. A real user
describing the UI they love names exactly this — *"has a ton of little details
and haptics that I love too"* (dossier §3.3) — and CodeC is silent.

The correction is small and must stay small: haptics everywhere is noise, and a
vibrating IDE is worse than a silent one.

## Design

### Eight moments, and nothing else

```kotlin
enum class HapticMoment {
    RUN_STARTED, PROGRAM_FINISHED, PROGRAM_FAILED,
    FILE_SAVED, INSTALL_FINISHED, TAB_CLOSED, PROJECT_OPENED, DRAG_STARTED,
}
data class HapticInput(val moment: HapticMoment?, val enabledInSettings: Boolean,
                       val hasVibrator: Boolean)
object HapticPolicy {
    /** null = do nothing. One row per moment; anything unlisted is silent. */
    fun performFor(i: HapticInput): HapticFeedbackType? = when {
        i.moment == null                  -> null
        !i.enabledInSettings              -> null
        !i.hasVibrator                    -> null
        i.moment in setOf(PROGRAM_FAILED, TAB_CLOSED) -> HapticFeedbackType.LongPress
        else                              -> HapticFeedbackType.TextHandleMove
    }
}
```

- **Two strengths only.** `TextHandleMove` (a light tick) for "it happened",
  `LongPress` (a firmer one) for "something stopped / something closed". A
  taxonomy of eight different buzzes is unlearnable.
- **Failures get the firm one** — the haptic is the fastest channel for "your
  program did not work" when the user's eyes are on the keyboard.
- **`hasVibrator` is honoured** (a device with no vibrator must not log or
  crash; the terminal bell already guards this at `TerminalEmulatorView.kt:126`).
- **One Settings switch** (Settings → Appearance → *"Haptics"*, on by default)
  governs these eight and **nothing else**: the CodeC keyboard's own setting
  (`codec_keys_*`, `KeysStayPolicy`) and the terminal bell stay exactly as
  Phases 47.2 and 36 left them.

### Press states

Every tappable surface in the six core files gets a visible pressed state.
Material3 gives a ripple for free; what is missing is **containment** — the
surfaces that today are bare `Row`s with a `clickable` and no shape. The rule is
one line: *a tappable thing has a shape and a background role.* token radius
(50.1) + `surfaceContainerHigh`/`surfaceContainerHighest` roles (50.2).

### Confirmations, never dialogs

| Moment | Confirmation |
|---|---|
| File saved | one word (`Saved`) as a snackbar; never a toast-callback, never a dialog |
| Install finished | one line + the row's own state change (51.3) |
| Program finished / failed | the output panel's own state (Phase 19/36) + the haptic |
| Copy / export / share | the platform's own affordance (already correct today) |

**No new `AlertDialog` in this phase, ever** — the no-nag law (Phases 41/42/45)
and the owner's own guide law ("no skip, no interruption") forbid it.

## The Android edge

- `LocalHapticFeedback.current.performHapticFeedback(type)` — Compose, already
  used by `CodecKeyboard`; the wiring is one call site per moment, all funnelled
  through the policy (a source-scan test pins that no file calls
  `performHapticFeedback` directly).
- `HapticFeedbackType.TextHandleMove` / `LongPress` are plain constants; the
  policy is testable on the host JVM.
- The vibrator check uses `VibratorManager` on 31+ and `Vibrator` below (as
  `TerminalEmulatorView.kt` already does) — inside a thin adapter, never inside
  the policy.
- **Never** on a recomposition: haptics fire from event handlers (a click, a
  run-state transition), and the "celebrate only on a genuine transition" rule
  from 51.3 is what keeps a re-render from buzzing twice.

## Exit condition

`HapticPolicy.performFor` is pinned for all eight moments, for the switch-off
case and for the no-vibrator case; a source scan proves every
`performHapticFeedback` call goes through the policy and that neither
`CodecKeyboard` nor the terminal bell route changed; the Settings switch exists
and defaults on; and `DEVICE_ROUND.md` F14-F16 (haptic felt on run/save/install,
switch off = silence, keyboard haptics unchanged) has been run by the owner.

## Tests (plan)

- `HapticPolicyTest` (~12): one case per moment for the light tick; the two firm
  ones; switch off → null for all eight; no vibrator → null; an unlisted moment
  (e.g. `null`) → null.
- `HapticWiringTest` (~8, source scan): no direct `performHapticFeedback` outside
  the single adapter; the adapter is the only importer of
  `LocalHapticFeedback`; `CodecKeyboard.kt`'s haptic lines are unchanged;
  `TerminalEmulatorView.kt`'s bell is unchanged.
- `PressStateTest` (~7, source scan): every `clickable(`/`combinedClickable(` in
  the six core files sits on a surface with a shape and a container role.
- `NoNewDialogTest` (~4, source scan): this phase adds **zero** `AlertDialog(`
  call sites (`EditorScreen`'s existing ones are pre-existing and pinned).

≈31 cases, host-JVM.

## Sources (record)

- `PHASE50_52_UX_RESEARCH.md` §2.3 (the 28-hit breakdown), §3.3 (users naming
  haptics), §3.2.
- `ui/keyboard/CodecKeyboard.kt`, `ui/components/TerminalEmulatorView.kt`,
  `ui/terminal/CodecApiBridge.kt` reads (2026-09-13, `main` @ `62cfe7b`).
- Phases 36 (bell), 41/42 (no-nag), 45 (no interruption), 47.2 (keyboard
  default off, `KeysStayPolicy` untouched).

## Deferred / rejected with reasons

- **Rich haptics (`VibrationEffect.createWaveform`, `Composition` primitives)** —
  API 30+ only in parts, unavailable on much of the installed base, and a
  "premium waveform" is exactly the decoration the research says does not carry
  the win.
- **Haptics on typing in the editor** — the keyboard already does that, and a
  second source would double-buzz.
- **Sound effects** — no audio assets; out of scope by repo convention.
- **A per-moment user setting** — one switch, not eight.

## Implementation (2026-09-21)

**The policy stayed pure and the platform stayed in one file.** The spec's
`performFor` returned a platform `HapticFeedbackType`, which would have pulled
Android into the decision and cost the host test; the landed split is
`HapticPolicy.performFor(HapticInput): HapticStrength?` in
`ui/components/Haptics.kt` (new — **pure Kotlin**, no Compose, no Android) and
`CodecHaptics` in `ui/components/CodecHaptics.kt` (new), the single adapter that
turns `LIGHT` → `HapticFeedbackType.TextHandleMove` and `FIRM` →
`HapticFeedbackType.LongPress` inside `runCatching`. Three guards are checked
*before* the mapping (no moment / switch off / no vibrator), so "off means off"
cannot be bypassed by a moment that happens to be firm. `rememberCodecHaptics()`
reads the DataStore switch itself (one reader, one behaviour, and the switch
cannot be half-wired) and `HapticsSupport.hasVibrator` guards the API split
(`VibratorManager` on API 31+, `Vibrator.hasVibrator()` below — no
`VibrationEffect`, which is API 26+, is built anywhere).

**The eight moments are wired, each at the place the user's own act happens:**
`RUN_STARTED` / `PROGRAM_FINISHED` / `PROGRAM_FAILED` from ONE
`LaunchedEffect(busy, exitCode)` through the pure `RunHapticRule` (a screen that
opens onto an already-finished run stays quiet — the first observation owes
nothing, Phase 48's discipline); `FILE_SAVED` on the write; `TAB_CLOSED` on both
close paths (never from the dirty-tab dialog — a dialog is a question, not an
act); `INSTALL_FINISHED` on the genuine install transition (51.3's policy);
`PROJECT_OPENED` on the hub's OPEN; `DRAG_STARTED` on the file tree's long press.

**One switch, default on, and the keyboard's own setting is untouched.** DataStore
key `haptics` (`booleanPreferencesKey("haptics")`, read as `it[HAPTICS] ?: true`)
with `hapticsFlow`/`setHaptics`, rendered in Settings → Appearance above the theme
preview, with `SETTINGS_AUDIT.md` row 65 in the same commit. `codec_keys_haptics`
(Phase 28.2/47.2) stays the keyboard's own — `HapticWiringTest` pins that both
keys still exist and that each is read by its own surface.

**A tappable thing has an edge.** `PressableSurface` (new) is the containment
rule: token radius, a container role, the `MIN_TOUCH` floor, and the press state
itself — 0.98 on `CodecMotion.effectsSpring`, `indication = null`, because the
scale *is* the feedback and the size never changes, so nothing re-measures under
a typing finger. It renders its content verbatim (no new widget), and the
`contained` flag keeps a plain label plain: the two call sites are the Packages
section header (`ModulesScreen`, a bare `Row` with a `clickable`) and the hub's
New-Project sheet rows (`FileManagerScreen`, `clip` + `clickable` with no
container). Cards keep Material's own ripple — they already have containment.

**Confirmations are still snackbars and states.** The save path's one-word
`Saved`, the install finish's one line, and the output panel's own state for a
finished run. This phase adds **no dialog**.

**Tests:** `HapticPolicyTest` 14 (the eight by name, exactly three firm, every
guard, the run rule's transitions and its silences), `HapticWiringTest` 12 (the
platform call exists in exactly two files and one of them is new; `LocalHapticFeedback`
is borrowed by the adapter and the keyboard only; the adapter asks the policy and
never invents a moment; both DataStore keys; all eight moments wired; the sora
host, the emulator view and the whole `ui/guide` package stay haptic-free; the
policy file imports no Android/Compose; the press component's call sites).

**Not run:** device rows F14-F16 (the eight moments felt, the switch off, a
press state seen). A haptic's *feel* is a handset judgement — the policy is a
test, the feel is the owner's.
