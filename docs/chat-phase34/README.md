# CodeC Phase 34 — Official file icons

> **Status:** 📋 PLANNED (researched + specced, not implemented) ·
> **Cost:** `[client-only]` · **Effort:** S · **Owner row:** *"The file icons
> i want the official icons"*

## What exists today (evidence)

- `FileManagerScreen.iconForFile(name)` maps `.c/.h/.cpp` → `Icons.Default.Code`
  and **everything else** → `Icons.Default.InsertDriveFile` (two glyphs for
  the whole tree).
- `EditorProjectDrawer.FileTypeIcon(name)` knows four marks: `.py` →
  `SpckIcons.PythonLogo`, HTML → `HtmlShield`, `.md/.txt` → `BookLine`,
  else `FileLine`.
- `SpckIcons.kt` is the clean-room vector set (Python logo, HTML shield,
  globe, git-branch, folder/file lines, …) built as `ImageVector` from
  `List<PathNode>` — no raster, no SVG path strings.
- `ProjectsHub.HubIconToken` + the five coloured project marks (C/Py/Web/
  server/generic) are separate and already good; they are **not** this part.

So today the tree is effectively two Material glyphs. The owner wants the
**official** (recognizable, VS Code-style) per-file icons.

## Research (licensing is the deciding factor — record of sources)

- **VS Code's built-in *Seti* file-icon theme** is the official file icon set
  that ships in VS Code: **MIT-licensed** (Microsoft). The open-vsx listing
  for `vscode/vscode-theme-seti` states the icons derive from "Original VS
  Code sources Copyright (c) 2015–present Microsoft Corporation … released
  under the MIT License" — i.e. vendorable **with attribution**, exactly the
  precedent the repo already set with the MIT `friendly-snippets` packs
  (Phase 30, notice + About line).
- **`vscode-icons`** (the popular third-party extension) is **NOT safe to
  vendor**: its README says source = MIT **but the icons = Creative Commons
  ShareAlike (CC BY-SA)** and branded icons under their own licences. SA is a
  copyleft clause the repo must not take on. Avoid.
- **Material Icon Theme** (PKief) is MIT — an acceptable fallback, but Seti is
  the more "official" default and is monochrome (fits the existing
  `SpckIcons` single-tint convention).
- The repo's `ImageVector` path format means Seti SVGs must be **converted to
  `PathNode` lists** (a small converter script checked into `scripts/`, with
  the upstream SVGs vendored under `licenses/` + attribution), or hand-ported
  for the highest-value glyphs. Either way the result is an
  Android-free, host-testable **icon map**, not a pile of drawables.

## Design

1. **`ui/components/FileIcons.kt` (pure)** — a `FileIcon` enum + an
   extension→icon resolver:
   - `resolve(name: String): FileIcon` — case-insensitive extension map;
   - filename specials first (`Dockerfile`, `.gitignore`, `package.json`,
     `Makefile`, `CMakeLists.txt`, dotfiles);
   - coverage of every `LanguageRegistry`/`LanguageType` entry (c, h, cpp, hpp,
     cc, py, js, mjs, cjs, ts, tsx, jsx, html, htm, css, scss, json, md, sh,
     yaml/yml, toml, xml, java, kt, swift, go, rs, rb, php, lua, sql, bat) plus
     assets (png/jpg/gif/svg/webp, zip/tar/gz, mp3/wav, mp4/webm, ttf/woff) and
     folders (open/closed).
2. **Glyphs** — monochrome `ImageVector`s in the existing `SpckIcons` style
   (white base, re-tintable via `Icon(tint=…)`); the two-tone **brand marks**
   (Python, HTML, JS, TS) stay as drawn colour marks. Tint = theme
   `onSurfaceVariant`, matching today.
3. **Apply everywhere** — `iconForFile` (Files tab), `EditorProjectDrawer.
   FileTypeIcon` (drawer), the editor tab bar, and the single-file glyphs;
   one resolver, four call sites, no per-screen forks.

## Exit condition

```text
(Device)
1. Open a mixed project (C + Python + JS + HTML + JSON + README + images):
   every file shows its own recognizable icon, not a generic sheet/code glyph.
2. The icon set renders in BOTH the Files tab and the editor drawer + tab bar,
   monochrome and correctly tinted in both light and dark themes.
3. Settings → About (or the OSS notice) lists the icon set + MIT attribution.
PASS = all three. Cross-device: icons must not blur on a small/dense screen.
```

## Tests

- `FileIconTest` (host): the resolver is total over `LanguageRegistry`
  extensions + the special filenames; extension matching is case-insensitive;
  unknown extensions fall back to the generic file icon; no two icons collide
  in name.

## Risks / notes

- Size: ~40 glyphs × ~200 bytes of path data is negligible; keep it
  `ImageVector`, **not** a web-font or raster (no bitmap scaling issues on
  the device matrix).
- Do not touch `HubIconToken` (project cards) unless the owner asks — that is
  a separate visual system and is already good.
