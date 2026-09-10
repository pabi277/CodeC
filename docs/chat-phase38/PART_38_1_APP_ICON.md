# CodeC Phase 38.1 — An original CodeC launcher icon (and the notification silhouette)

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` + **build-time tooling** ·
> **Effort:** M · **Owner row (verbatim):** *"I have to set a app icon"*

## Symptom

CodeC currently ships the **Android Studio new-project icon**: a `#3DDC84`
green field and the robot-head paths in `ic_launcher_foreground.xml`, with the
template's webp bitmaps in every `mipmap-*`. It is also the notification small
icon for both long-lived notifications. So on a tester's home screen the app
looks like *a template*, and in the status bar it looks like a bug — the two
first impressions an app cannot afford when it is being shared by word of mouth.

## Design

### 1. The mark, and where it lives

One original drawing, one source of truth: **`docs/icon/codec-mark.svg`**
(108 × 108 viewBox, flat colours, no gradients, no text smaller than the 66 dp
safe zone allows). Shape guidance, not taste policing:

- **Idea**: a caret + braces reading as `>_` inside a rounded square — i.e. the
  app in one glyph (editor + terminal), legible at 48 px. Two colours max
  (surface + accent), so the monochrome version is *the same drawing*, not a
  compromise.
- Key art **entirely inside the central 66 dp diameter**; the 18 dp per-side
  margin is empty or flat background (parallax/pulse zone).
- **No** Android robot, no GitHub Octocat, no VS Code/Spck/Termux glyph, no
  Material symbol used as a logo (`rule.md` §6 + trademark law).
- Contrast: the mark must clear 3:1 against both its own background and a
  typical light/dark wallpaper, because launchers may composite our layers onto
  their own tint for themed icons.

### 2. The layers (all four, in this order)

| File | Change |
|---|---|
| `res/drawable/ic_launcher_foreground.xml` | rewritten as a plain `<vector>` of the mark's paths, **no `aapt:attr` gradient** |
| `res/drawable/ic_launcher_background.xml` | flat brand colour field (the grid/paths go away); optionally a `<color>` in `values/colors.xml` (`ic_launcher_background`) like AOSP's own template |
| `res/drawable/ic_launcher_monochrome.xml` | **new** — single-colour silhouette (white on transparent, per the themed-icon convention) |
| `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` | `<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>` — the current one points at the *full-colour* foreground |

### 3. Legacy bitmaps + store art, generated not hand-made

API 24-25 need real raster icons (no adaptive layer support), and the release
page needs 512×512. Pipeline, all **build-time**, so CI needs nothing new and
`assembleDebug` is unaffected:

```
docs/icon/codec-mark.svg
   └─ scripts/render_icon.mjs   (node + `sharp` (BSD-3) or `@resvg/resvg-js` (MPL-2.0))
        ├─ app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png     48/72/96/144/192
        ├─ app/src/main/res/mipmap-*/ic_launcher_round.png                               same sizes, circular mask baked
        ├─ app/src/main/res/drawable/ic_stat_codec.png  (or a vector silhouette)         24 dp status-bar art
        └─ docs/icon/codec-512.png      (GitHub Release / repo identity; not in the APK)
```

Rules: **delete the template `.webp` files in the same commit** (a stale
`ic_launcher.webp` + a new `ic_launcher.png` in one density folder is a
duplicate-resource build failure); record the generator + exact command in the
script header so a re-run reproduces byte-comparable output; keep the SVG as the
only hand-edited file. A test-ish check for CI without new tooling:
`scripts/check_icon_assets.sh` asserting every `mipmap-*` contains both
`ic_launcher.*` and `ic_launcher_round.*` and that no `.webp` remains
(missing-density `IconMissingDensityFolder` lint covers part of this, but a
shell check also catches the half-deleted case).

### 4. Notification + in-app uses

- `RunForegroundService` and `TerminalForegroundService`:
  `setSmallIcon(R.drawable.ic_stat_codec)` — a silhouette, not
  `ic_launcher_foreground`. `CodecApiBridge`'s `android.R.drawable.ic_dialog_info`
  becomes the same in-app drawable (a system drawable in a third-party
  notification renders unpredictably across OEMs).
- `ic_launcher_foreground` (the adaptive layer) stops being used as a general
  purpose logo; the in-app **About** header and the README badge point at a
  dedicated `@drawable/app_mark` (the same paths, unscaled to a 24-48 dp
  context), so the launcher art and the UI art can each be tuned without
  breaking the other. `SettingsScreen`'s About section already exists — it gets
  the mark + the version line, nothing more.
- The `terminal`/`run` notification *channel* icons (created alongside) use the
  same drawable for consistency.

### 5. Nothing else changes

`android:icon`/`android:roundIcon` names stay (so no manifest churn and no
installer confusion), `versionCode` is untouched, and the app's colours/typography
are out of scope (Phase 34 owns the file-icon surface, 29 owns the theme).

## Exit condition

```text
See chat-phase38/README.md §Exit condition items 1-6 (this part owns 1-6; 38.2
owns 7). Extra specifics for this part:
·  the 66 dp rule verified by looking at a ROUND mask, not only the default;
·  themed (monochrome) icons ON → the mark is legible as one colour;
·  both notifications show a silhouette, no white box;
·  `node scripts/render_icon.mjs` run twice produces identical files (git diff
   empty), i.e. the committed assets are provably generated;
·  `scripts/check_icon_assets.sh` passes;
·  APK size delta reported (icon assets are ~KB; the point is that it is
   measured, not assumed — Phase 37's +355 660 B discipline).
```

## Tests (plan)

There is no unit test for art, and pretending otherwise would be theatre. What
*is* testable, and therefore gets a test:

- `IconAssetSetTest` (host, reads the repo's `app/src/main/res` through a
  path-relative `File` — the same technique `EditorKeySetTest` and
  `DemoProjectSeedTest` already use to read real repo files/asset trees from a
  JVM test, minus Robolectric): every density folder has both `ic_launcher` and
  `ic_launcher_round`, no `.webp` leftovers, `mipmap-anydpi-v26/*.xml` declares
  `background` + `foreground` + **`monochrome`**, and each adaptive XML's
  monochrome drawable name ≠ the foreground's (the exact mistake in the
  template).
- `IconGeometryTest` (host, parses the vector XML with the stdlib): the
  foreground `vector` has `viewportWidth == viewportHeight == 108`, and the
  bounding box of every `pathData`'s numbers lies inside the safe ring
  (21..87 on both axes) — a numeric approximation (min/max of the coordinate
  pairs) is enough to catch "someone filled the whole canvas", and it is
  honest about what it checks. It also **fails on the art being replaced**
  today, which is the proof the test is worth having: the template
  foreground's first path runs to `L107,108.928` — the bugdroid is drawn edge
  to edge of the 108 dp canvas, so on a round mask its corners are already
  clipped on every device CodeC runs on.
- `SafeZoneMathTest` for the helper that computes the box, so the parser can't
  drift into a false green.
- CI compile + lint: `MonochromeLauncherIcon` and the `Icon*` checks must be
  silent; the run is the executor of record for the resource wiring.

## Sources (record)

- Adaptive-icon geometry: canvas 108 dp, masked viewport 72 dp, and the
  "consider a centered 66 dp diameter circle as a safe zone" rule that follows
  from masks reaching only 33 dp from the centre [stackoverflow.com/a/49672632
  on q/49661571, quoting Romain Guy's "Designing Adaptive Icons"]; the outer
  18 dp per side is reserved for parallax/pulse [anything.com Android icon
  guide].
- Density table for legacy launcher icons 48/72/96/144/192 px and the separate
  **512 × 512** store icon [iconikai.com chart; adaptive-icons.com Play
  compliance summary].
- Monochrome/themed icons: Android 13 tints the app-supplied monochrome layer;
  **Android 16 QPR 2 auto-themes icons for apps that ship none**, and the
  auto-generated result "can look different from your intended design" — which
  is the argument for supplying ours [anything.com guide, §"The monochrome
  layer is now effectively mandatory"].
- Tooling licences: `sharp` is BSD-3 (npm); `@resvg/resvg-js` is **MPL-2.0**
  with PNG as the supported output and other lossless formats (webp/avif) only
  on its roadmap [npmx.dev/@resvg/resvg-js v2.6.2] — hence PNG for the legacy
  rasters unless `sharp`'s WebP encoder is used deliberately.
- CodeC code, 2026-09-10: the four launcher files and their template content
  (`#3DDC84`, robot paths, gradient), `mipmap-anydpi-v26/*.xml` (monochrome →
  foreground), `setSmallIcon(R.drawable.ic_launcher_foreground)` in
  `RunForegroundService.kt:115` and `TerminalForegroundService.kt:45`,
  `CodecApiBridge.kt:816`'s system `ic_dialog_info`, and Phase 34's file-icon
  surface (`ProjectIconView.kt`, `FileIcon.kt`) which this part must not touch.

## Deferred / rejected with reasons

- **Letting the launcher auto-theme us (ship no monochrome layer)** — free, but
  Android 16 QPR 2's auto-generated mark is not our design.
- **A gradient or drop-shadow in a layer** — launchers that rasterise layers
  themselves render it wrong; AOSP's own guidance says keep masks/shadows out of
  the layers.
- **Generating the rasters in CI** (npm install in the workflow) — CI is the
  executor of record for *builds*, not for art generation; a committed asset set
  with a documented generator is reviewable and offline-buildable.
- **An `ic_launcher.xml` vector-only legacy set** — API 24/25 need bitmaps in
  `mipmap-*` (and a `drawable` vector as a launcher icon has its own history of
  launcher bugs); bitmaps are the boring correct answer.
- **Reworking the app name/label here** — the label stays `CodeC`
  (`@string/app_name`), already covered by Phase 33's identity work.
