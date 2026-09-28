# Phase 70.1 + 72.1 — one chat, preview first

**Date:** 2026-09-28. **Branch:** `arena/01a0e704-codec`, based on `main` @
`fbb3056` (the merge of Phase 69.4 / PR #91). **Tip:** `d86b4a3`.
**Status:** 🚧 IMPLEMENTED, **CI ✅ GREEN — `Build APK`
[36399610563](https://github.com/pabi277/CodeC/actions/runs/36399610563) on
`d86b4a3`** (12 m 2 s: `:app:testDebugUnitTest` **2347 tests, 0 failed**, debug
and release APKs assembled, release manifest with no `android:debuggable` flag).
**No PR opened, nothing merged, nothing pushed to `main`.** No device evidence
was produced, and none is implied.

This chat began as **Phase 70.1 — Run, output and error recovery** and ended up
delivering **both** the Web Preview console (Phase 72.1) and the run Output
Panel (70.1), because the owner said so. His answers, verbatim: the surface is
**“Both, one after the other”**; the console takes **“The whole strip, Elements
included”**; the viewport is **“Yes — fix the tiny rendering in the same part”**;
the hard-to-understand error line is **“The run output panel's”**; and the order
is **“Both in this chat, preview first”**. The two sentences that started it:

> *"Try to make output exactly same for web view"* and *"And terminal output is
> good enough but is hard to understand the error line from terminal"*

Briefs (the owner's questions and answers are recorded verbatim inside them):

- [`docs/ui-polish-chats/PHASE_72_1_PREVIEW.md`](../ui-polish-chats/PHASE_72_1_PREVIEW.md)
  — the preview part, delivered first.
- [`docs/ui-polish-chats/PHASE_70_1_RUN_OUTPUT.md`](../ui-polish-chats/PHASE_70_1_RUN_OUTPUT.md)
  — the editor's run panel, delivered second.

## What shipped

| Part | Commits | What it is |
|---|---|---|
| **72.1 — the Web Preview** | `090596e`, `2a707be` | The five-tab strip the shots show (**Console · Elements · Network · Resources · Settings**), a console that takes commands (echo, `eval` of a policy-built one-liner, LOG/ERROR result, Cancel · Execute, Send on the IME), the level filter row, a Network table whose Type/Size/Time come from the page's own Resource Timing entries (Status honestly stays “—” because WebView cannot see a response), a read-only Elements tree with details/highlight/copy, Resources, a Settings page (zoom, resolution, Fit page to phone, viewport outcome, Clear cache), the **“Preview · N % Zoom”** bar with the console toggle, and a **phone-sized default viewport** added only to pages that never declared one. |
| **70.1 — the run Output Panel** | `11345c0`, `2590429`, `3ad83c7`, `d86b4a3` | A state row derived from facts (`Idle / Building / Installing / Running / Waiting / Serving / Done · exit N / Failed · exit N / Stopped`), a 48 dp action row led by a labelled **Stop**, an error/warning count banner, compiler errors as two rows (location + severity, then the message) with the *Add missing ;* fix, a collapsed strip that says *“Waiting for input — tap to answer”* while a program waits, the run keys (↵ / Ctrl+C / Tab) restored for that same case, the panel capped at 55 % — **38 % with the keyboard up** — and, on the owner's own answer, **the panel's line as a console**: a command typed there runs in the app's real shell, and a line typed while something is already running is refused rather than queued. |

## Evidence, told apart

- **Source (host JVM) tests:** pure-policy cases and wiring pins — 2347 tests in
  the CI run above, including `PreviewConsolePolicyTest` (7),
  `PreviewInspectorPolicyTest` (5), `PreviewToolsPolicyTest` (17),
  `OutputPanelStatusTest` (9), `OutputPanelWiringTest` (5),
  `OutputPanelContrastTest` (3, the panel's palette against WCAG AA).
- **Android tests (Robolectric, real Compose/WebView classes on the host):**
  `PreviewToolsLayoutTest` (3 — resizing never consumes the page, the five tabs,
  the Settings readouts) and `PreviewWebViewTest` (the view's zoom, file-access
  pins, console mapping and dispose path). These are **not** a device pass.
- **Real device:** **none.** Nothing in this chat has been installed or driven on
  a handset; the five owed handset rows for 69.1–69.4 remain owed.

## Open, and not delivered

- **N1 — the “above ber”** is still unanswered: none of the six shots identifies
  that bar, so no shape was designed for it.
- **Elements is read-only** (a DOM walk, not SPCK's DevTools protocol): no
  element editing, no computed styles.
- **Network's Status column stays “—”**, with the panel saying why.
- The panel's console refuses a command while a run is busy (by design); it does
  not queue, and it never becomes a second Terminal.

## Stop point

This part only. No PR, no merge, no `main` push without the owner's explicit
instruction, and no claim of device acceptance.
