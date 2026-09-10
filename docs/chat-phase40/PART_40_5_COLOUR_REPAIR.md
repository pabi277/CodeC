# Phase 40.5 — colour repair: text you can actually read

> **Owner's report:** *"research throughly on the color of the app's inside texts
> and make it good to see. Now it is violet 💜 but not very good to read. Also
> correct other colors"* — this is the gate before merging Phase 40.
>
> **Branch:** `arena/01a08c04-codec` · **Base:** the Phase 40 repair (`29e175e`
> + `e623a16`) + the device runbook (`68321c8`, 8/8 checks passed on device).
> **Cost:** `[client-only]` — no network, no permissions, no new dependency.
> **Verification:** `AppContrastTest` (12) + `ChromeContrastTest` (7) +
> `SettingsAuditTest` (5) green in the sandbox JVM harness; CI runs the same
> suites through `:app:testDebugUnitTest`. Mutation-checked: raising any of the
> alphas below fails the build with the measured ratio in the message.

## 1. Why the violet was hard to read (root cause, not a re-tint)

`SettingsManager` stores the accent as a raw `#AARRGGBB`, default `#FF6200EE`
(the Android Studio template violet). `MyApplicationTheme` pushed that value
straight into `primary`:

```kotlin
// before — one raw hex used as *text* in both themes
val colorScheme = base.copy(primary = Color(accent))
```

`#6200EE` is a **light-theme** colour: on a near-black surface it is 2.25:1,
where WCAG 2.2 §1.4.3 asks for 4.5:1 (and `onPrimary` on it was 1.72:1, so
buttons were as unreadable as the labels). The same value on the light surface
is a perfectly fine 7.44:1 — which is why the bug only showed in dark mode.
Material 3 solves this with tonal roles: **primary is tone 40 in light, tone 80
in dark**. The app never applied that relationship to a custom accent.

**The fix** keeps the accent's hue and moves only its lightness, per theme, via
`AccentPalette.rolesFor(seed, dark, surface)` (`ui/theme/CodecPalette.kt`):
`primary` is corrected until it clears AA on the surface the theme actually
draws, `onPrimary` is *measured* (`Contrast.onColorFor`) instead of assumed,
and `primaryContainer`/`onPrimaryContainer` are derived the same way. An accent
that already passes is returned untouched.

| Accent `#FF6200EE` | dark theme | light theme |
|---|---|---|
| `primary` before | `#6200EE` — **2.25:1** ❌ | `#6200EE` — 7.44:1 ✅ |
| `primary` after | `#A15FFF` — **4.56:1** ✅ | `#6200EE` — 7.44:1 ✅ (unchanged) |
| `onPrimary` before | `#FFFFFF` — **1.72:1** ❌ | `#FFFFFF` — 7.63:1 ✅ |
| `onPrimary` after | `#101014` — 5.06:1 ✅ | `#FFFFFF` — 7.63:1 ✅ |

The six accents the picker offers (Violet `#6200EE`, Teal `#018786`, Red
`#B00020`, Blue `#1976D2`, Orange `#FF9800`, CodeC green `#3DDC84`) all derive
≥4.5:1 in **both** themes with **<0.2° hue drift** — measured with a standalone
probe before the tests existed, and now pinned by
`AppContrastTest.every accent choice is readable in both themes`. The stored
format is unchanged (`#FF%06X`), so accents saved by older builds keep working;
a stored value that is not one of the six still shows as its own hex.

## 2. The other colours that failed (found by measuring, then fixed)

Method: every `Color(0x…)` literal in `app/src/main/java` and every
`copy(alpha = …)` overlay was extracted, its real background computed
(`Contrast.composite` for translucent layers) and its ratio measured with
`Contrast.ratio` — the same pure functions the app now uses and the tests pin.

| Where | Before | After |
|---|---|---|
| Muted panel text (`#666666` / `#777777`) | 2.90:1 (`#1E1E1E`), 3.26:1 (`#121212`), 4.18:1 | `MUTED_TEXT #9AA0A6` → 6.31 / 7.09 |
| Output header legend, server badge (`#8A8A8A` on `#252526`) | 4.44:1 ❌ | `MUTED_TEXT` → 5.80:1 ✅ |
| "QR unavailable" (`#8A8A8A` on `#2A2A2A`) | 4.16:1 ❌ | `MUTED_TEXT` → 5.44:1 ✅ |
| Terminal strip "shell failed" (`#EF5350` on `#292929`) | 4.17:1 ❌ | `ERROR_TEXT #FF5555` → 4.63:1 ✅ |
| Project tiles (white content) | `#F0863C` 2.57, `#4CAF50` 2.78, `#3E7CC1` 4.32, `#8B5CF6` 4.23 ❌ | `#B45309` 5.02, `#2E7D32` 5.13, `#35699F` 5.72, `#7C4DEB` 5.10 ✅ |
| Hub rows (white icon on the fill) | `#6366F1` 4.47, `#3B82F6` 3.68 ❌ | `#4C51E0` 5.90, `#2563EB` 5.17 ✅ |
| Editor comments (monokai / dracula / github) | 3.95 / 3.03 / 3.05 ❌ | `#9C9678` 4.99, `#8DA0D0` 5.47, `#8B949E` 4.77 ✅ |
| Dracula gutter line numbers | 3.03:1 ❌ | `#8593BB` 4.67 / active `#B8C0E0` 7.90 ✅ |
| Bottom-nav idle labels (`onSurfaceVariant` @0.65) | dark 5.00, **light 3.53** ❌ | @0.80 → 6.86 / 5.23 ✅ |
| Status bar `LF` (@0.7) | dark 4.60, **light 3.78** ❌ | `onStrip(muted)` → 7.61 / 8.17 ✅ |
| Status bar selection count (raw accent on the strip) | **3.46:1** ❌ | `onStrip(primary)` → 4.51 / 6.67 ✅ |
| Status bar error/warning (`#FF5555` / `#FFB347`) | dark 4.13 / light 1.56 ❌ | `onStrip(…)` → 4.51 / 7.28 (dark), 4.51 / 4.57 (light) ✅ |
| Key-cap active tint (accent @0.6) | **light 4.34** ❌ | @0.5 → 5.40 / 5.43 ✅ |
| Key-cap corner hint (accent @0.9) / dot (accent @0.7) | 3.86 / 2.84 ❌ | `onCap(primary)` 4.54 / `onSurface` ≥5.40 ✅ |
| Keys-row badge (`onPrimary` on accent @0.7) | 3.46:1 ❌ | opaque accent → 5.06 / 7.63 ✅ |
| Suggestion glyphs (`onPrimary`@0.8 filled, raw accent idle) | 2.31 / 3.05 ❌ | `onPrimary` 5.06 / `onSurface` 10.37 ✅ |
| Accent chip borders (@0.55), conflict border (@0.6), inactive borders (@0.35/@0.25) | 2.25 / 2.55 / 2.38 ❌ (need 3:1) | opaque accent 4.56, `#BA68C8` 4.81, `outline` 5.41 / 4.44 ✅ |
| Folder icon tint (accent @0.6) | 2.45:1 ❌ (need 3:1) | opaque accent 4.56:1 ✅ |

**Nothing that already passed was restyled.** `#E6B33C` 8.87, `#66BB6A` 7.24,
`#EF5350` 4.91 (fills/icons), `#BA68C8` 4.81, `#64B5F6` 7.73, `#55FF55` 14.11,
`#FF5555` 5.96, `#FFB347` 10.52, `#66B2FF` 8.37, `#9E9E9E` 6.22/6.39,
`#6B7280` 4.83, `#6A9955` 5.00 and the violet hub row (`#A78BFA` with
`#241A4F` text, 5.78:1) are pinned by tests and left exactly as they were.

## 3. The class of bug, not just the instances

The recurring shape is **a colour designed for one background drawn on
another**: the accent on a tinted strip, an amber on a light theme, a 55% alpha
border on a dark surface. So the repair does three things beyond the values:

1. **The accent is a role, not a hex** — `AccentPalette.rolesFor` derives
   `primary`/`onPrimary`/`container`/`onContainer` per theme, so a new accent
   cannot reintroduce the original bug.
2. **Translucent chrome measures itself** — `EditorStatusBar` and
   `CodecKeycap` compute the surface they are *actually* drawn on
   (`Contrast.composite` of `surfaceVariant`@0.5 over `surface`, then
   `surface`@0.9 for a cap) and correct their meaningful colours with
   `Contrast.ensureReadable`. With dynamic colour on Android 12+ this keeps
   working, because nothing is hardcoded.
3. **One token per meaning** — `CodecPalette` holds the muted/subtle text, the
   semantic colours, the tiles, the hub rows and `ERROR_TEXT`, each with its
   measured ratio in the KDoc, and `AppContrastTest`/`ChromeContrastTest`
   re-derive every one of them.

## 4. Deliberately left alone, with the reason (WCAG 2.2)

- **Disabled controls** — the greyed "Clear diagnostics" menu item
  (`onSurface` @0.38) is an *inactive user interface component*; §1.4.3 exempts
  it, and dimming is how it reads as disabled.
- **Decorations** — the status-bar `·` separators (@0.6), the bottom-sheet drag
  handle (@0.45) and the keyboard's pressed-state tint (@0.42) carry no
  information of their own; the pressed cap's *label* measures 6.01:1 dark /
  6.45:1 light, so the state is still legible.
- **Backgrounds and fills** — row highlights (@0.18/@0.08), the suggestion
  strip wash (@0.7) and the key-cap surface (@0.9) are backgrounds: what sits
  on them is what must clear AA, and it does (pinned).

## 5. How to re-verify

```text
# in the sandbox (no Android needed — the suites are pure/text reads):
kotlinc -cp … Contrast.kt CodecPalette.kt JUnit.kt RepoFiles.kt \
    AppContrastTest.kt ChromeContrastTest.kt SettingsAuditTest.kt Runner.kt
kotlin -cp out.jar RunnerKt com.codeci.ide.AppContrastTest       # PASS=12 FAIL=0
kotlin -cp out.jar RunnerKt com.codeci.ide.ChromeContrastTest    # PASS=7  FAIL=0
kotlin -cp out.jar RunnerKt com.codeci.ide.SettingsAuditTest     # PASS=5  FAIL=0
# in CI: ./gradlew :app:testDebugUnitTest — NOT WIRED YET, see §7
```

`ChromeContrastTest` reads the alphas **out of the UI sources** — it fails if
someone raises the bottom-nav alpha back to 0.65 (`3.53:1, needs 4.5`) or a
key-cap tint to 0.62 (`4.15:1, needs 4.5`). Both were confirmed by mutating the
sources during this phase.

## 6. Device look-over (3 checks, do it with the Phase 40 runbook)

The 8 functional checks in [DEVICE_TEST_PLAN.md](DEVICE_TEST_PLAN.md) already
passed on device. These three are the *visual* half of the same round.

**C1 — the picker shows a colour, not a hex.** Settings → Appearance.
**PASS looks like:** the row reads **Accent Color** with a round violet dot and
the word **Violet**. Tapping it opens exactly six entries — **Violet, Teal,
Red, Blue, Orange, CodeC green** — each with its own dot; picking **CodeC
green** leaves a green dot and **CodeC green** on the row, and the app's
accent-coloured controls turn green. *FAIL:* the row shows a hex string.

**C2 — dark theme, the owner's complaint.** Editor screen, dark theme, with the
CodeC keyboard up (tap a code file). **PASS looks like:** the status bar reads
`Ln 1, Col 1 · UTF-8 · Kotlin · Spaces: 4 · LF` and the **LF** segment is as
legible as **UTF-8**; the bottom bar's unselected labels (Files, Editor,
Terminal, Packages, Settings) are all equally readable; the small corner hints
on the keys (`q¹`, `;:`) are visible without leaning in; a held/special key is
still a lighter violet with a readable label. *FAIL:* anything violet that
needs squinting.

**C3 — light theme.** Settings → App Theme → **Light**, then repeat C2's glance
at the bottom bar and the status bar. **PASS looks like:** the unselected
labels and the whole `Ln … · LF` line stay readable on the pale strip (this is
where 3.53:1 and 3.78:1 used to be), project tiles keep white letters/icons on
their fills, and the Output panel stays dark by design with readable hint text.

> Reminder (`rule.md` §3): CI green and pushed ≠ merged. **No PR/merge happens
> without your explicit command.**

## 7. Gap found while verifying (recorded, not smuggled into this repair)

The `Build APK` workflow assembles the app and runs the **bench** module's unit
tests (`./gradlew :bench:assembleRelease :bench:testDebugUnitTest`), but it
never runs `:app:testDebugUnitTest`. Everything under
`app/src/test/java/com/codeci/ide/` — the Phase 40 suite (36 cases in
`GitHubPhase40Test`) and the 19 contrast cases added here — is therefore
verified **in the sandbox harness** and by whoever runs the tests locally, but
**not by CI**. A green `Build APK` on this branch means "the app compiles and
the bench tests pass", nothing more.

Wiring it in is one line (`./gradlew :app:testDebugUnitTest --no-daemon`), but
the first run of ~120 app test classes that have never executed in CI can
surface pre-existing failures, which would turn the branch red for reasons that
have nothing to do with this repair. That is the owner's call, so it is
**offered, not done**: say the word and it lands as its own small commit (with
the same readable-annotations trick the bench step uses, so a failure is
readable from the sandbox instead of a 40 MB log).
