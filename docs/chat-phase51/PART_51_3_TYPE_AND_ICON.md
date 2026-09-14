# CodeC Phase 51.3 — A type scale and one icon set

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S/M ·
> **Owner row (verbatim):** *"it's not attractive"* / *"boost it's ui 100×"*.
> Parent: [`README.md`](README.md) ·
> [`PHASE51_53_ROADMAP.md`](../PHASE51_53_ROADMAP.md).

## First move: evidence, not code

```text
$ sed -n '9,30p' app/src/main/java/com/codeci/ide/ui/theme/Type.kt
  val Typography = Typography(
      bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
                            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp)
      /* Other default text styles to override
      titleLarge = TextStyle(… 22.sp …)   labelSmall = TextStyle(… 11.sp …)
      */
  )
```

The file is **the Android Studio template, unedited** — one style overridden and
the rest left commented out. So every "headline", "title" and "label" in the app
is whatever `androidx.compose.material3` happens to default to. That is not a
design, and its symptom is exactly what the owner feels: text that is *present*
but says nothing about importance.

Two more readings decide the rest of the part:

| Evidence | Consequence |
|---|---|
| `ui/theme/EditorThemes.kt:9-17` — `VS_CODE_DARK_PLUS`, `MONOKAI`, `DRACULA`, `GITHUB_DARK` (VS Code Dark+ is the default by the owner's Phase 29.1 decision) | the **code** surface already has a deliberate typography (it is sora's, with the theme's own font). This part must not touch it. |
| `ui/components/SpckIcons.kt`, `ui/components/FileIcon.kt`, `ui/components/StarterIconView.kt`, `ui/components/ProjectIconView.kt` (Phases 34/38) | iconography **exists** and is deliberate; what is missing is *one size per role* and consistent descriptions. |

And the accessibility half, which is also a polish half:
`grep -rn "contentDescription = null" app/src/main/java` — decorative icons
correctly declare themselves decorative, but icon-only **actions** must keep a
real description, because Google's own expressive research found that
*removing text labels from email actions decreased usability*
(dossier §3.2). Pretty icons that nobody can name are not polish.

## Design

### `CodecType` — a scale, not a list of sizes

```kotlin
object CodecType {                      // sizes in sp, line-height multiples
    object Size { const val DISPLAY = 34f; const val HEADLINE = 26f
                  const val TITLE = 20f;  const val BODY = 15f
                  const val LABEL = 13f;  const val CAPTION = 11f }
    object LineHeight { const val TIGHT = 1.20f; const val NORMAL = 1.40f
                        const val RELAXED = 1.55f }
    val codeFamily = FontFamily.Monospace            // 0 bytes — the platform's
    fun scale(): Typography = Typography(…)          // every role filled in
}
```

Rules:

1. **Six roles, each with size + line height + weight + letter spacing** —
   display (the welcome screen's "CodeC"), headline (screen titles), title
   (cards, section headers), body (everything), label (buttons, chips, tabs),
   caption (status bars, timestamps, the `~proj/…` path).
2. **Line height is a multiple, never a magic number** — the second most common
   cause of "walls of text" after spacing.
3. **Every code-bearing surface uses `FontFamily.Monospace`**: the editor status
   bar's path (Phase 46.2), the terminal, the diff view, output lines, file
   paths in dialogs. **Zero bytes, no asset, no licence question.**
4. **The editor itself is untouched** — sora owns the code font, and the four
   editor themes own its colours.

### The open question, recorded rather than decided

A real code face (JetBrains Mono, Cascadia Code…) would look better than the
platform monospace on the chrome surfaces. JetBrains Mono is **SIL OFL-1.1**,
which is **not** on `rule.md` §6's licence whitelist (MIT / Apache-2.0 / BSD /
CC0). So:

> 🚩 **Owner decision point:** vendor a code font (OFL-1.1), or keep the
> platform monospace (0 bytes)? Default ship = **platform monospace**. A yes
> adds ~200-400 KB to the APK and a licence line to `docs/`; the part doc
> records the answer before a byte is added.

### Iconography: one size per role

| Role | Size | Where |
|---|---|---|
| `INLINE` | 16 dp | inside text, chips, list rows |
| `ACTION` | 20 dp | toolbar / row actions (RUN ▶, save, ⋮) |
| `NAV` | 24 dp | bottom bar, drawer headers, empty-state hero |

- Every **action** icon keeps a `contentDescription` that describes the action
  ("Run this file"), never the glyph ("play arrow").
- Sizes come from `CodecTokens.Icon`, so 51.1's source scan covers them.
- **No** `material-icons-extended`: it is already on the classpath for the core
  set only, and extending it is a well-known APK-size trap. The existing
  `SpckIcons` / `FileIcon` hand-drawn set stays for files and languages.

## The Android edge

- `Typography` is `androidx.compose.material3.Typography`; `CodecType.scale()`
  is a pure function returning it and is therefore testable by reading the
  returned styles (no Compose runtime needed).
- `MaterialTheme(typography = CodecType.scale())` — the single wiring point is
  inside `MyApplicationTheme` (`Theme.kt:43`), one line.
- `FontFamily.Monospace` is a platform object: no asset, no `res/font`, no
  lint `NewApi` risk (the Phase 44 lesson: *a file with no Android imports is
  still linted as Android code* — keep the pure file free of `java.nio`/`java.time`).

## Exit condition

`CodecType.scale()` fills **all six** roles (a test asserts none is left at the
Material default); every code-bearing chrome surface uses a monospace family
(source scan over the named files); icon sizes in the six core surfaces are
exactly 16/20/24 (source scan); every icon-only action has a non-null
description (source scan of the six files); and the device round's L9-L10 rows
(the welcome screen's headline, and a code path in the status bar) are run.

## Tests (plan)

- `CodecTypeTest` (~6): all six roles present and ordered by size; line heights
  are the declared multiples; the code family is monospace; the scale is
  deterministic.
- `TypeAdoptionTest` (~5, source scan): `MyApplicationTheme` passes
  `CodecType.scale()`; no `fontSize = ` literal in the six core files; the
  status bar / diff / output surfaces reference `CodecType.codeFamily`.
- `IconRoleTest` (~7, source scan): only 16/20/24 dp icon sizes in the six
  files; every `IconButton(`/`Icon(` that is an action has a non-null
  `contentDescription`; decorative icons declare `null` explicitly.

≈18 cases, all host-JVM.

## Sources (record)

- `PHASE51_53_UX_RESEARCH.md` §2.1 (Type.kt evidence), §3.2 (Google's own
  finding that removing labels hurt usability), §4 (the OFL licence question).
- `ui/theme/EditorThemes.kt:9-17` + `docs/JOURNEY.md` Phase 29.1 (the editor's
  typography is the owner's decision and is out of scope).
- `rule.md` §6 (licence whitelist; clean-room law).

## Deferred / rejected with reasons

- **Vendoring a font** — owner decision, see above.
- **Variable fonts / dynamic type scaling from the system font-size setting** —
  `sp` already respects the system scale; a custom scale factor is a later,
  accessibility-specific row.
- **Restyling the editor's code font** — sora + the four themes own it.
- **A full icon audit of all 61k lines** — six surfaces now, rest on touch.
