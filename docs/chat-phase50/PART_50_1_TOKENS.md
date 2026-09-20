# CodeC Phase 50.1 — One scale, not 801 numbers

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"it's not attractive"* / *"boost it's ui 100×"*.
> Parent: [`README.md`](README.md) (the evidence table) ·
> [`PHASE50_52_ROADMAP.md`](../PHASE50_52_ROADMAP.md).

## First move: evidence, not code

The claim to convict or clear is *"the app has no design system"*. It is a grep,
not an opinion (all on `main` @ `62cfe7b`, 2026-09-13):

```text
$ grep -rho "[0-9]\+\.dp" app/src/main/java --include=*.kt | wc -l
801
$ grep -rho "[0-9]\+\.dp" app/src/main/java --include=*.kt | sort | uniq -c | sort -rn | head -8
  127 8.dp    88 16.dp    82 6.dp    78 4.dp    67 10.dp    59 12.dp    47 2.dp    26 20.dp
$ grep -rho "RoundedCornerShape([0-9]*\.dp)" app/src/main/java --include=*.kt | sort | uniq -c | sort -rn
  17 8.dp   12 12.dp   12 10.dp   10 6.dp    8 5.dp    5 2.dp    4 14.dp    3 16.dp    1 9.dp    1 4.dp
```

The verdict is in the tail of the second list: **10 distinct corner radii**,
including a `9.dp` and a `4.dp` that appear exactly once — a value used once is
a value chosen by hand, per composable, at the moment it was written. That is
what "unfinished" looks like to a user who cannot name it.

Three readings also tell us what the scale *should* be:

- The six most-used gaps (8, 16, 6, 4, 10, 12) are all on or adjacent to a
  **4-based** scale, so adoption is a rename rather than a redesign.
- `ui/theme/CodecPalette.kt` and `ui/theme/Contrast.kt` prove the house pattern
  already: **a pure object holding the values, tests that measure them**.
  Tokens follow the same shape.
- `EditorScreen.kt` (2,423 lines) and `FileManagerScreen.kt` (1,830) hold the
  majority of the literals, and they are also the two most device-tested files
  in the repo — so the conversion is done **chrome-only**, never inside the sora
  host, the terminal emulator or the sheets.

## Design

### The scale (pure — no Android import except `Dp`, which is `androidx.compose.ui.unit`)

```kotlin
// app/src/main/java/com/codeci/ide/ui/theme/CodecTokens.kt
object CodecTokens {
    object Space {
        const val NONE = 0f; const val XXS = 2f; const val XS = 4f;  const val S = 8f
        const val M = 12f;   const val L = 16f;  const val XL = 24f; const val XXL = 32f
        const val HUGE = 48f
    }
    object Radius { const val XS = 4f; const val S = 8f; const val M = 12f
                    const val L = 16f; const val XL = 28f }
    object Elevation { const val FLAT = 0f; const val RAISED = 1f
                       const val CARD = 3f; const val SHEET = 6f }
    object Icon { const val INLINE = 16f; const val ACTION = 20f; const val NAV = 24f }
    const val MIN_TOUCH = 48f
    fun space(v: Float): Dp = v.dp
    fun radius(v: Float): Dp = v.dp
}
```

Rules that make it survive the next fifty phases:

1. **Nothing new may be added to the scale without a test case naming it.**
   `CodecTokensTest` asserts the ladder is complete and monotonic.
2. **A raw `N.dp` is allowed only when `N` is on the scale.** The source-scan
   test greps the six core files and fails otherwise — the same mechanism as
   `SetupGateWiringTest` / `CaretCallSiteTest`, which is why no reviewer has to
   remember the rule.
3. **`MIN_TOUCH = 48`** is a floor, not a suggestion: every icon button in the
   six surfaces gets a 48 dp touch target, which is both the M3 guidance and
   WCAG 2.2 §2.5.8, and it is the cheapest possible *feel* improvement.

### The six surfaces (the blast radius, deliberately bounded)

| # | File | What converts |
|---|---|---|
| 1 | `ui/screens/WelcomeScreen.kt` | padding ladder, tile radius (today `RoundedCornerShape(16.dp)`, `defaultElevation = 0.dp` at `:100-115`), the 48/12/36 spacers |
| 2 | `ui/screens/EditorScreen.kt` | tab bar, status bar, find bar, the RUN ▶ row — **chrome only, above `SoraEditorHost`** |
| 3 | `ui/screens/FileManagerScreen.kt` | hub cards (`ProjectHubCard` at `:1351`), the empty state (`:1223`), row paddings |
| 4 | `ui/screens/ModulesScreen.kt` | package cards, section headers |
| 5 | `ui/screens/TerminalScreen.kt` | chrome around the emulator view — never the emulator itself |
| 6 | `ui/screens/SettingsScreen.kt` | row paddings, card radii (`:405`, `:474` already agree on `surfaceVariant.copy(alpha = 0.5f)`) |

Everything else keeps today's numbers. Nothing about behaviour changes, so no
device-tested flow (44–49) is touched.

## The Android edge

- `Dp` is `androidx.compose.ui.unit.Dp`; the object therefore compiles on the
  host JVM for tests, exactly like `CodecPalette`.
- `RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.L))` is the only
  shape constructor used in the six surfaces afterwards.
- **Do not** animate a size or a padding in this part — that is 50.4's job, and
  an animated padding on the editor while the IME resizes would re-break
  Phase 48.

## Exit condition

`CodecTokens` exists and is imported by all six core files; the six files
contain **no radius and no gap outside the scale** (source scan); every icon
button in them has a ≥48 dp target (source scan on `Modifier.size` /
`minimumInteractiveComponentSize`); the six screens are visually unchanged in
**layout** (same order, same content) — this part is a *renaming*, and if a
device round reports a moved element, the conversion is wrong.

## Tests (plan)

- `CodecTokensTest` (~10): ladder completeness/monotonicity for Space and
  Radius; `space()`/`radius()` round-trip; `MIN_TOUCH >= 48`.
- `TokenAdoptionTest` (~8, source scan): each of the six files imports
  `CodecTokens`; no `RoundedCornerShape(` value outside `Radius`; no
  `padding(`/`size(`/`height(` literal outside `Space` in those files.
- `TouchTargetTest` (~6, source scan): every `IconButton(` in the six files sits
  in a ≥48 dp box (`Modifier.size(48.dp)` or `minimumInteractiveComponentSize`).

≈24 cases, pure + source scan. No Robolectric, no Compose runtime.

## Sources (record)

- The measured greps at the top of this file (2026-09-13, `main` @ `62cfe7b`).
- Dossier §3.1-§3.3 (50 ms / aesthetic-usability / what users call polished) —
  the *reason* consistency is worth a phase.
- House precedent for "pure object + source scan": `CodecPalette.kt`,
  `Contrast.kt`, `CaretCallSiteTest`, `SetupGateWiringTest`.

## Deferred / rejected with reasons

- **A lint rule / detekt / custom Lint check** for raw `dp`: correct in
  principle, but it is a build-graph change to a `targetSdk 28` compatibility
  build; the source-scan test gets the same guarantee for zero risk. Revisit if
  the repo ever adopts a lint module.
- **Converting the remaining 55 files**: out of scope (see blast radius).
- **A `CodecTheme` wrapper object**: tempting, but three of the six surfaces are
  mid-refactor in Phases 46/47; adding a wrapper now would conflict. Plain
  `CodecTokens` first, wrapper later if it earns itself.
