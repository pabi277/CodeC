# CodeC Phase 16 — Spck-style Editor Shell (drawer, tabs, snippets keyboard, launch preview)

**Status:** Planned (design/spec only) · **Cost:** `[client-only]`
· **Depends on:** Phase 9 (Editor foundation: tabs, undo/redo, find/replace,
squiggles, status bar), Phase 11 (Output panel & RUN), Phase 14 (Web preview),
Phase 15 (Projects Hub)
· **Target files (anticipated):** `ui/screens/EditorScreen.kt`,
`ui/components/EditorTabBar.kt`, `ui/components/SymbolBar.kt`,
`ui/components/EditorStatusBar.kt`, `ui/viewmodels/EditorViewModel.kt`,
`ui/editor/*`, `ui/settings/SettingsManager.kt`

---

## 1. Context & motivation

CodeC already has a capable editor (Phase 9): tabs, undo/redo, find/replace,
formatting, bracket matching, compiler squiggles, a status bar, an in-editor file
drawer (9.1), and a simpler toolbar (9.2). Phase 11 added the Output panel + RUN,
Phase 14 added web preview. What's missing is the **Spck look and feel** — the
thing the owner pointed at in the screenshots: a clean hamburger→file-drawer,
a tidy multi-tab bar, a **snippets / extra-keys keyboard row** docked above the
IME, a legible mobile code surface, and **launch-default HTML preview** per file.

This phase re-skins and completes the editor *shell* so it reads like Spck's
editor (see [`mockups/editor-screen.png`](mockups/editor-screen.png) and
[`mockups/editor-drawer.png`](mockups/editor-drawer.png)), reusing every existing
editor engine underneath.

**Reference:** the Spck editor screen (hamburger, tabs `game.js | index.html`,
search, overflow, RUN ▶, snippet keyboard) — captured from the Play Store
listing and [Spck docs](https://spck-code-editor.readthedocs.io/en/latest/getting-started/#previewing).

---

## 2. UX / UI design (phone-first)

### 2.1 Editor top bar

```
☰   [ main.c ] index.html            🔍   ⋮   ▶ RUN
```
- **☰ hamburger** (left) — opens the navigation drawer (§2.2). Replaces the
  current open-folder sheet entry point with the Spck drawer pattern.
- **Tab bar** — horizontally scrollable file tabs (Phase 9 `EditorTabBar`),
  active tab underlined in accent purple, each tab: filename + a close `×` on the
  active tab, a dirty-dot when unsaved. Long-press a tab → "Close others / Close
  all / Copy path". Overflow when many tabs.
- **🔍 search** — opens the Phase 9 find/replace bar.
- **⋮ overflow** — Save, Save all, Format, Go to line, Find/Replace, File
  encoding, Line endings (LF/CRLF — a Spck feature), Share, Close file.
- **▶ RUN** — the Phase 11 run action (green), unchanged behavior.

### 2.2 Navigation drawer (file tree + project header)

Slides from the left (see [`mockups/editor-drawer.png`](mockups/editor-drawer.png)):
- **Project header** — project name + a **branch chip** (`⌥ main`) + a
  source-control icon with a change-count badge (→ Phase 17). Tapping the header
  offers "Switch project" (back to the Projects Hub, Phase 15).
- **Tree toolbar** — icons: New File, New Folder, Refresh, Collapse All.
- **File tree** — the Phase 8 `FileTreeRepository` tree with folder chevrons,
  indentation, file-type icons, and the **selected file highlighted** in accent.
  Files show **git status letters** on the right (`M` yellow, `A` green,
  `D` red, `?` grey) when the project is a repo (data from Phase 13/17).
- **Per-node actions** — tap `⋮` (or long-press) on a node: Open, Rename,
  Delete, New File/Folder here, **Run in terminal** (Phase 9.1), **Launch
  Default** for HTML (§2.5), Copy path.
- **Drawer footer** — quick rows: **Source Control** (badge), **Switch Branch**
  (both → Phase 17), **Settings**.

### 2.3 Code surface

- Native Compose editor (the recorded architecture decision — not WebView/Monaco;
  see `TERMINAL_PLAN.md` appendix). Keep `BasicTextField` +
  `MultiLanguageSyntaxHighlighter` (Phase 12) + Phase 9 squiggles.
- **Line-number gutter** — muted, aligned, current-line highlighted.
- **Readability controls** (addresses a common Spck review complaint that mobile
  fonts are too big): pinch-to-zoom font size (reuse the Phase 6 pinch handler
  pattern), plus a Settings font-size stepper and a **monospace font family**
  choice (Phase 4.4 parity). Word-wrap toggle.
- **Minimap-lite (stretch):** a thin scroll-position indicator / optional
  overview strip on the right. Defer if it costs measurable frame time; not a
  blocker.
- **Active-line + selection** styling from the current editor theme
  (`EditorThemes`), no new theme system.

### 2.4 Snippets / extra-keys keyboard row (the Spck signature)

A horizontally scrollable **key row docked directly above the IME** (anchored
like the Phase 6 terminal extra-keys), shown while the editor has focus:
- **Default keys:** `TAB` `{` `}` `(` `)` `;` `<` `>` `/` `=` `"` `'` and arrow
  keys `←` `→` (and optionally `↑` `↓`). Each inserts at the cursor / moves the
  caret; `TAB` inserts the configured indent (spaces:4 default).
- **Language-aware sets (stretch):** swap in C / Python / Web/HTML key sets based
  on the active file's language (Spck ships per-language keyboard modes). Start
  with one good general set; make the set data-driven so languages can be added.
- **Custom snippets (stretch):** a Settings-defined list of text snippets
  (name → body) surfaced as extra keys — Spck's "Custom Snippets". Data model can
  ship in this phase; the editing UI can be a follow-up.
- Reuses/extends the existing `SymbolBar.kt`; must not overlap the IME or the
  status bar, and must respect insets.

### 2.5 Launch / preview per file (Spck "Launch Default")

- Any **HTML file** node gets a **Launch** action (drawer `⋮` and editor `⋮`)
  that opens the Phase 14 Web Preview for that file.
- **Launch Default:** right-click/`⋮` → "Set as launch default" marks one HTML
  file (persisted in `.codec/project.json`); a small **blue file icon** marks it
  in the tree (Spck behavior). The editor's ▶/preview affordance uses the default
  when the active file isn't previewable — matching Spck.
- Markdown files get a **Preview** action (rendered view) as a stretch (Spck
  previews Markdown too).

### 2.6 Status bar

Keep the Phase 9 status bar, Spck-styled: `Ln 12, Col 4 · UTF-8 · C ·
Spaces: 4`, plus (from §2.1 overflow) tappable segments for line endings and
language. An **errors badge** (count of squiggles in the file) sits at the right,
tappable to jump to the first error (Spck added an errors badge).

---

## 3. Architecture & implementation steps

Reuse the Phase 9/11/12/14 engines; this is shell + polish.

1. **Drawer.** Wrap `EditorScreen` in a `ModalNavigationDrawer` (or dismissible
   drawer on wide screens). Move the file tree into the drawer content; the
   project header shows name + branch + SC badge. Hamburger toggles it.
2. **Tab bar polish.** Extend `EditorTabBar` with dirty-dots, active-tab close,
   long-press tab menu, overflow. State stays in `EditorViewModel` (open tabs).
3. **Snippet row.** Generalize `SymbolBar` into a data-driven key set
   (`List<EditorKey>`), IME-anchored, with the default set; expose a hook for
   language-specific sets and custom snippets.
4. **Readability.** Add font-size (pinch + Settings stepper), font-family, and
   word-wrap to `SettingsManager` and apply in the code surface.
5. **Launch default.** Persist `launchDefault: String?` in `ProjectConfig`; add
   the tree/editor "Launch" + "Set as launch default" actions; blue-icon marker;
   wire to Phase 14 `WebPreviewScreen`.
6. **Status bar + errors badge.** Add the errors count (from Phase 9
   `CompilerDiagnostics`) and tappable jump; add line-endings + language segments.
7. **Overflow menu** items (Save/Save all/Format/Go to line/encoding/line
   endings/share/close).
8. **Host unit tests** (CI-run):
   - `EditorKeySetTest` — default/language key sets produce the right insert/caret
     ops; TAB honors indent settings.
   - `LaunchDefaultTest` — set/read/clear `launchDefault` in `ProjectConfig`
     (backward compatible; omitted when null).
   - `LineEndingTest` — LF↔CRLF conversion on save (pure).
   - Extend `EditorTabBar`/status-bar view-model tests for dirty-dot + errors
     count.

---

## 4. Exit condition & device verification recipe

```text
# Editor shell
1. Open demo_flask → editor shows the ☰ hamburger, a tab for the open file,
   🔍, ⋮, and ▶ RUN in the top bar.
2. Tap ☰ → drawer opens with project header (name + "main" chip), tree toolbar
   (new file/folder, refresh, collapse), and the file tree; app.py highlighted.
3. Open index.html from the tree → a second tab appears; both tabs switchable;
   editing shows a dirty-dot; Save clears it. Long-press a tab → Close others.

# Snippet keyboard
4. Focus the code → the snippet row appears above the keyboard with TAB { } ( )
   ; < > / = and arrows. Tapping "{" inserts at the cursor; arrows move the caret;
   TAB inserts 4 spaces.

# Readability
5. Pinch-zoom changes font size smoothly; Settings font-size + word-wrap toggles
   take effect; text stays readable (no oversized-font complaint).

# Launch / preview
6. In the tree, ⋮ on index.html → "Launch" opens the Web Preview of that file.
   ⋮ → "Set as launch default" → a blue icon marks index.html; the editor's
   preview affordance now targets it by default.

# Status bar
7. Status bar reads "Ln x, Col y · UTF-8 · <lang> · Spaces: 4"; an errors badge
   shows the squiggle count and tapping it jumps to the first error.

# Regression
8. ▶ RUN still builds/runs via Phase 11; Output panel + Open Preview (Phase 14)
   still work; find/replace, undo/redo, format (Phase 9) unaffected.

PASS = steps 1–7 behave as described and step 8 shows no regressions.
```

---

## 5. Invariants & scope guard

- **Client-only**; no `[repo-build]`, no bootstrap/`$PREFIX` changes.
- **Native Compose editor stays** — no WebView/Monaco swap (recorded decision).
- Snippet row must not overlap the IME/status bar; respect insets (Phase 6).
- Reuse Phase 9 (`EditorTab`, `EditorUndoManager`, `FindReplaceEngine`,
  `CompilerDiagnostics`), Phase 12 highlighter, Phase 14 preview — no engine
  rewrites.
- `launchDefault` is optional in `project.json` and omitted when null (backward
  compatible, like Phase 14's `port`/`previewUrl`).
- **Out of scope (stretch, note don't block):** minimap, language-specific key
  sets beyond the default, custom-snippet editing UI, Markdown preview.
