# CodeC Phase 33.2 — Packages hub for humans

**Status:** 🚧 IMPLEMENTED (CI ✅ `34346424311`, tip `5db9e33`; device round pending) · **Cost:** `[client-only]` · **Effort:** S
· **Target:** `ModuleCatalog` / `ModulesScreen`

---

## 1. Design

Two sections:

1. **Languages & IntelliSense** (python, nodejs, clang, clangd/pylsp/tsserver
   cards from Phase 31 if present)
2. **Unix tools** (nano, ripgrep, tmux, …) collapsed by default

Same `pkg install` plumbing. No new store format.

## 2. Exit condition

```text
(Device)
1. Packages opens on Languages; Unix is a collapsed header.
2. INSTALL python still streams in Terminal (Phase 10 regression).
PASS = both.
```

## 3. Implementation (2026-09-09, `arena/01a085e0-codec`)

- **`ui/modules/ModuleCatalog.kt` (pure)** — a new `PackageSection` enum
  (`LANGUAGES_INTELLISENSE` / `UNIX_TOOLS`, with a render order that puts the
  language section first) and `PackageCatalog.sectionOf(item)`: languages +
  compilers/build tools → the always-open section; editors, cli/search and
  archives/utils → the collapsed "Unix tools" section. Every `intellisense-*`
  card already carries `PackageCategory.LANGUAGES`, so they land in the
  language section automatically — no per-card change.
- **`ui/screens/ModulesScreen.kt`** — the flat list + category filter chips are
  replaced by the two sections. The hub now opens on **Languages &
  IntelliSense** (always expanded); **Unix tools** is a collapsed header that
  expands on tap. A typed search flattens both sections so a name is found
  regardless of its section. Quick Actions + the custom-command runner move
  below the sections. Install/Run/Uninstall/Copy wiring is unchanged
  (extracted into one `PackageCardRow` shared by the sectioned and the
  search-flat lists), so INSTALL still streams `pkg install -y …` in the
  Terminal (Phase 10 regression intact).

## 4. Tests

- `PackageSectionTest` (5 cases): languages + compilers land in
  "Languages & IntelliSense"; editors/cli/utils land in "Unix tools"; every
  `intellisense-*` card is in the language section; every package maps to
  exactly one section; render order opens on the language section.
