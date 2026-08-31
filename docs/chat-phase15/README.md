# CodeC Phases 15–17 — Spck-style Editor & Project Experience

**Status (2026-08-31):** Phases 15 + 16 IMPLEMENTED, mockup-exact re-skinned,
3 device rounds done, **MERGED to `main`** (session branch
`arena/01a057e0-codec`); **Phase 17 now fully IMPLEMENTED on
`arena/01a05878-codec`** — Switch Branch (stash/auto-restore + New branch),
merge-conflict grouping, Mark Resolved and commit blocking join the SC sheet
+ per-file stage toggle; **device gate pending** · **Cost:**
`[client-only]` · **Depends on:** Phase 8 (Projects & folder tree), Phase 9
(Editor foundation), Phase 11 (Output panel), Phase 13 (Git integration),
Phase 14 (Web preview)

> These three phases redesign CodeC's project and editor UX so it looks and
> flows like **[Spck Editor / Git Client](https://play.google.com/store/apps/details?id=io.spck)**
> — a well-loved mobile-first code editor. The goal is a **UI/UX parity + gap
> fill**, not a rewrite: most of the engine work (project model, git clone/commit,
> editor tabs, run pipeline, web preview) already shipped in Phases 8–14. These
> phases put a Spck-grade skin and flow on top and fill the missing pieces
> (unified project list, one create/import entry point, in-tree git status,
> branch switching, snippet keyboard, launch-default preview).

## Why this exists

You asked for the CodeC editor's project options to be a near-exact clone of
Spck Editor's — "import git project", a proper project list, and the full
editor look and feel. Research on Spck's live app and docs
([spck.io](https://spck.io), [docs.spck.io](https://docs.spck.io),
[readthedocs](https://spck-code-editor.readthedocs.io/en/latest/getting-started/))
produced the flows captured here. Each phase is scoped to be independently
implementable, CI-green, and device-verifiable on the owner's aarch64 hardware.

## The three phases

| Phase | Title | What it delivers | Doc |
|---|---|---|---|
| **15** | Projects Hub & Unified Import (Spck-style project list) | Redesigned Projects screen: card list, filter chips, search, one `+` sheet with **New Project / Clone Git Repo / Import ZIP / Open Folder**, per-project overflow git actions | [PART_15_PROJECTS_HUB.md](PART_15_PROJECTS_HUB.md) |
| **16** | Spck-style Editor Shell (drawer, tabs, snippets keyboard, launch preview) | Editor screen re-skin: nav drawer file tree with git status, refined tab bar, snippet/extra-keys row, minimap-lite, launch-default HTML preview, per-file actions | [PART_16_EDITOR_SHELL.md](PART_16_EDITOR_SHELL.md) |
| **17** | In-editor Source Control & Branching (Spck git parity) | Source Control bottom sheet, in-tree M/A/D/? status letters, tap-to-diff, **Switch Branch**, Pull/Push, merge-conflict marking — matching Spck's git UX | [PART_17_SOURCE_CONTROL.md](PART_17_SOURCE_CONTROL.md) |

> **Phase 18 (was Phase 15) — CodeCApi Device Capabilities** moved to the end
> of the roadmap: [`../chat-phase18/`](../chat-phase18/). It is unaffected by
> this work and stays the final polish phase.

## Design mockups

Phone-screen mockups (dark theme, Material 3, Spck-inspired) live in
[`mockups/`](mockups/) and are referenced from each part:

| Mockup | Screen |
|---|---|
| `mockups/projects-list.png` | Phase 15 — Projects Hub list |
| `mockups/create-import-sheet.png` | Phase 15 — `+` create/import bottom sheet |
| `mockups/clone-git-dialog.png` | Phase 15 — Clone Git Repository dialog |
| `mockups/editor-screen.png` | Phase 16 — Editor shell (tabs + snippet keyboard) |
| `mockups/editor-drawer.png` | Phase 16 — Editor navigation drawer / file tree |
| `mockups/source-control.png` | Phase 17 — Source Control bottom sheet |

## ⚖️ Ground rule — clone the experience, not the code

**Do NOT copy code from Spck Editor or any other app.** Spck is closed-source
and its code is not ours to take. What we replicate is the **experience**: the
same screens, the same file/project layout, the same flows and features (project
list, unified `+` import, clone-git, editor drawer, snippet keyboard, source
control) — **re-implemented from scratch in CodeC's own Kotlin/Compose code**
reusing CodeC's existing engines. Same *files and features*, original code.
Where a UI detail is unclear, match Spck's visible behavior (from the mockups and
the research links below), not its internals.

## 🔎 Research step — do more research when needed (per part)

These docs are the starting point, not the final word. **Before/while
implementing each part, do additional research if anything is unclear** and record
what you find in that part's doc (a short "Research notes" block) so the next
session benefits:

- Re-open the Spck references and compare against the current UI:
  [spck.io](https://spck.io) · [docs.spck.io](https://docs.spck.io) ·
  [getting-started](https://spck-code-editor.readthedocs.io/en/latest/getting-started/) ·
  [git-features](https://spck-code-editor.readthedocs.io/en/latest/git-features/) ·
  [Play listing](https://play.google.com/store/apps/details?id=io.spck).
- Look up **Material 3 / Jetpack Compose** patterns for the specific component
  (ModalBottomSheet, ModalNavigationDrawer, filter chips, nested LazyColumn tree)
  before hand-rolling one.
- Re-read the CodeC engine you're building on (Phase 8 `ProjectManager`/
  `FileTreeRepository`, Phase 13 `GitManager`, Phase 14 `ProjectScaffold`/
  `WebPreviewScreen`) so the UI calls the existing API instead of duplicating it.
- If a flow can't be matched exactly on mobile (e.g. Spck desktop-only bits),
  research the closest mobile-appropriate equivalent and note the decision (a
  `D#` design-decision line, like the other phases use).
- **TODO for the implementer:** capture any open question as a checkbox in the
  relevant part and resolve it with a linked source before marking the part done.

## Standing rules (unchanged)

- **No PR/merge and no push to `main` without the owner's explicit command.**
  Committing and pushing the session `arena/*` branch is fine.
- Client-only: **no `[repo-build]`**, no bootstrap changes, no `$PREFIX/bin`
  writes. Reuse the existing engines (Phase 8/9/11/13/14).
- Verify state (`git status`, `gh pr list`, `gh run list`) before acting; a
  part is done only when its Exit condition is device-verified.
