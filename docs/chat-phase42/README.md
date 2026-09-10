# CodeC Phase 42 — Identity: a real app icon, and Settings that stop explaining Termux

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
> `[client-only]` · **Effort:** S/M · **Owner rows:** *"I have to set a app
> icon"* · *"From the settings remove unessesary Termux bridge"*

```text
  42.1  An original CodeC launcher icon (adaptive + monochrome + legacy bitmaps)
  42.2  Settings trim: drop the Termux card, audit for rows with no effect
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [42.1](PART_42_1_APP_ICON.md) | App icon, notification icon, release/store art | M | 📋 PLANNED |
| [42.2](PART_42_2_SETTINGS_TRIM.md) | Termux bridge out of Settings + row audit | S | 📋 PLANNED |

## What exists today (evidence, read 2026-09-10)

- **The launcher icon is the Android Studio template art.**
  `res/drawable/ic_launcher_background.xml` is a `#3DDC84` full-bleed field with
  the template's grid paths; `ic_launcher_foreground.xml` is the template's
  robot-head paths plus a `aapt:attr` linear gradient. `mipmap-anydpi-v26/`
  wires `background`+`foreground`, and its `monochrome` layer points at
  **the same full-colour foreground** (it "works", but it is not a monochrome
  design); `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher{,_round}.webp` are the
  template's bitmaps. Manifest: `android:icon="@mipmap/ic_launcher"`,
  `android:roundIcon="@mipmap/ic_launcher_round"` — so the shape of the wiring
  is right and only the art and the monochrome layer are wrong.
- **The notification small icon is that same foreground vector**: both
  `RunForegroundService.kt:115` and `TerminalForegroundService.kt:45` call
  `.setSmallIcon(R.drawable.ic_launcher_foreground)` — a full-colour adaptive
  foreground in a slot that wants a single-colour silhouette is the classic
  white-blob status bar. (`CodecApiBridge.kt:816` uses
  `android.R.drawable.ic_dialog_info`, a system drawable that also should not be
  used by a third-party notification.)
- **The Termux card is a whole Settings section.** `SettingsScreen.kt:320-366`:
  a "TERMUX BRIDGE CARD" with `SettingsSectionHeader("Termux Engine")`, a
  status row built by `buildTermuxStatusText(...)`, **OPEN TERMUX** and
  **CHECK** buttons (`TermuxCompiler.runCommand` probe), and a four-step
  instruction paragraph (`allow-external-apps=true`, `termux-reload-settings`,
  *Additional permissions*, `pkg update && pkg install clang`) — plus
  `TermuxUiState`/`loadTermuxState`/`formatProbe` helpers at
  `:1195-1230` and two mentions inside the compiler-explanation copy at
  `:1187-1190`. Meanwhile `CompilerService` already treats Termux as a
  *fallback engine only*: `BACKEND_AUTO` is "the only value the app passes
  since Phase 21 removed the Settings picker", and the Termux paths are
  guarded by `if (!TermuxCompiler.isTermuxInstalled(context))` returning the
  bundled result.
- The manifest declares `com.termux.permission.RUN_COMMAND` (install-time,
  user-granted in system settings) and a `<queries><package
  android:name="com.termux"/></queries>` entry for the visibility check.

## Research that shaped the design

Dossier: [`../PHASE38_43_OSS_RESEARCH.md`](../PHASE38_43_OSS_RESEARCH.md) §5-§6.
Key constraints, all from AOSP-derived guidance: **108 dp canvas → 72 dp masked
viewport → 66 dp safe circle** for key art, with the outer 18 dp per side
reserved for parallax/pulse; **a real `monochrome` layer** because Android 13
tints themed icons and **Android 16 QPR 2 auto-generates one when the app
ships none** (i.e. skipping it means somebody else designs your icon for you);
density bitmaps still needed for API 24-25 (48/72/96/144/192 px) and a
**separate 512×512** store/release icon. Tooling: `sharp` (BSD-3) or
`@resvg/resvg-js` (**MPL-2.0**, PNG is the maintained output, webp is still on
its roadmap) as a **build-time generator committed as files** — no runtime
dependency, no CI toolchain requirement, and the master SVG stays in the repo
as the single source of truth.

## What "identity" means for this phase (scope guard)

One mark, used consistently: launcher (adaptive + legacy), notification small
icon, the in-app About/header mark, and the 512×512 that goes on a GitHub
Release. **Not** in scope: a splash screen animation, an animated/adaptive
"pulsing" icon, a monochrome-only theme, re-theming the app colours
(`SpckIcons`/`#3DDC84`-era palette stays), or touching Phase 34's **file**
icons (the vendored Seti set is a separate, already-device-passed surface).
And by `rule.md` §6: the mark must be **original** — no Android robot, no
GitHub Octocat, no VS Code glyph, no Material glyph used *as* the logo.

## Exit condition

```text
1. On the owner's launcher (and one other device if available): CodeC's icon is
   the new mark in circle, squircle and rounded-square shapes, with no clipped
   detail (the 66 dp rule holds — check by looking at a circle mask).
2. Themed/monochrome icons ON (Android 12+ launcher setting): the mark reads
   correctly in single-colour — it is legible, not a filled blob.
3. Status bar: the run and terminal notifications show a clean silhouette
   (compare against an app that does it right, e.g. the system's own).
4. Settings → About and README show the same mark; the 512×512 asset exists in
   the repo and is what the GitHub Release uses.
5. API 24/25 device (if the owner has one): a real bitmap icon, not the
   adaptive XML fallback, not a default Android icon.
6. `gradle :app:assembleDebug` and `:app:lintDebug` green with no new lint
   finding in the icon family (e.g. `MonochromeLauncherIcon`, the `Icon*`
   density/shape checks), and
   a `scripts/render_icon.mjs` re-run reproduces the committed bitmaps
   byte-comparably (so the assets are provably generated, not hand-tweaked).
7. Settings no longer shows the Termux Engine card, and a compile that needs the
   Termux fallback still works — with the *error path* naming Termux when it is
   genuinely the problem (42.2's exit checks).
PASS = all seven; 1-3 and 5 are the owner's eyes.
```

## Risks to watch (multi-device round)

- **Vector gradients**: the current foreground uses `aapt:attr`
  `linearGradient`, which needs API 21+ for VectorDrawables — fine — but
  **launchers that rasterise the layer themselves have rendered vector
  gradients wrong before**; the new mark should be flat-colour per layer
  (that is also what makes the monochrome version honest).
- **Adaptive-icon mask differences** (OEM launchers: Samsung OneUI, Xiaomi
  HyperOS, Motorola) — check on at least two, because the safe-zone bug only
  shows on the roundest mask.
- **WebP vs PNG** in `mipmap-*`: existing assets are `.webp`; switching to
  `.png` in the same folders is legal (Android resolves the resource by name)
  but the *old* files must be deleted in the same commit, or the build fails
  with duplicate resources.
- **Removing a Settings section users may rely on**: 42.2 must keep the
  capability and the *documentation* path (the same four steps belong in
  `docs/`, referenced from the compiler error), not silently delete knowledge.

## Deferred, recorded on purpose

- **An animated / adaptive-parallax icon** — no.
- **Replacing `SpckIcons` file icons with brand glyphs** — Phase 34 already
  settled that surface (MIT Seti, monochrome), and Simple Icons-style brand
  marks are trademarks.
- **Deleting `TermuxCompiler` entirely** — rejected with reasons in 42.2 (it is
  the working fallback on devices where the downloaded clang cannot be exec'd).
- **An icon-pack style Settings option ("choose your launcher colour")** —
  cute, meaningless for a beta, and the launcher already themes it.
