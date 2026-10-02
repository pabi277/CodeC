# Phase 79 — device round 1 (AI Level 3: proposed edits, review, and undo)

> **Status: ✅ PASSED (2026-10-02, owner: *"All device tests passed"* → *"Ok merge it"*).** Merged to `main` @ `df2c5d4` via PR #105; post-merge CI `36934250423` green.

**You need:** a project with several source files (a demo C or Python project is
fine) and Phase 76's Gemini key already saved.

**Build:** `CodeC-IDE-release` (or `-debug`) from the Phase 79 **Build APK** CI
run on `arena/01a0f848-codec`.

---

## A. Propose edits & preview gate

| # | Do | Expect |
|---|---|---|
| A1 | Open a project, tap the AI bubble. | The chat sheet opens at half; the chip row has **four** chips: *Explain selection*, *Explain last error*, *Ask about the project*, **Propose edits**. |
| A2 | Tap **Propose edits** with the question field empty. | A short notice: *"Write what change you want in the question box first, then tap Propose edits."* — no network request is made. |
| A3 | Type *"add a short comment at the top of the main file explaining what it does"*, then tap **Propose edits**. | *"Reading the project…"* briefly, then the **exact same preview gate** as Level 2 (`Files that will be sent`, file list, verbatim payload, **Cancel** / **Send**). Nothing is sent yet. |
| A4 | Tap **Send**. | The response streams, then the **Proposed file changes** review card appears below the prose summary. |

## B. Local diff review & per-file checkboxes

| # | Do | Expect |
|---|---|---|
| B1 | Inspect the **Proposed file changes** card. | Shows `N of M files selected · +X / −Y lines`, the note *"Review each diff before applying…"*, and one card per file with a checkbox, operation badge (`Modify` / `Create` / `Delete`), path, `+X −Y`, and a unified diff in `JetBrainsMono`. |
| B2 | Ask for a change that touches **two** files (or creates a helper file + modifies the main file). Uncheck one file's checkbox. | The header updates to `1 of 2 files selected`, and the button label updates to **Apply 1 file**. |
| B3 | Uncheck **all** files. | The **Apply** button is disabled (`0 of M files selected`). Re-check at least one file to re-enable it. |
| B4 | Tap **Reject** on a proposal. | The proposal card disappears, *"Proposal discarded. No project files were changed."* is shown, and project files are untouched. |

## C. Apply, tab sync, and 1-task Undo

| # | Do | Expect |
|---|---|---|
| C1 | Have the target file open in an editor tab. Generate a proposal and tap **Apply**. | The open editor tab updates immediately to the applied code (not marked dirty), the file tree and Git badges refresh, and the **Applied AI task** card appears with **Undo AI changes** and the scope note. |
| C2 | Tap **Undo AI changes**. | The file in the editor tab and on disk returns to its exact pre-apply state; if the task created a file, that file is removed and its tab closes; if the task deleted a file, that file is restored on disk. |
| C3 | Apply a proposal, **force-close the app**, reopen the app and the same project, and open the AI sheet. | The **Applied AI task** card is still present (loaded from `noBackupFilesDir/ai/undo/`) and **Undo AI changes** still restores the pre-apply state. |

## D. Conflict checks (no silent overwrite)

| # | Do | Expect |
|---|---|---|
| D1 | Generate a proposal for an open file. **Before** tapping Apply, type a change into that file in the editor. Now tap **Apply**. | Apply **pauses** and shows the conflict card naming the changed file, with **Rebuild proposal** and **Dismiss**. Your manual edit in the editor is untouched. |
| D2 | Tap **Rebuild proposal** on that conflict card. | Re-gathers the project with your new edit included and lands back on the preview screen. |
| D3 | Apply a proposal cleanly. Now edit the applied file in the editor, then tap **Undo AI changes**. | Undo **pauses** and warns that the file was edited after AI changes were applied, offering **Keep my edits** or **Overwrite and undo**. |
| D4 | Tap **Keep my edits**, verify your edit is still there; then tap **Undo AI changes** → **Overwrite and undo**. | First choice keeps your edit; second choice restores the pre-AI file state. |

## E. Safety & regression (Phase 76 / 77 / 78)

| # | Do | Expect |
|---|---|---|
| E1 | Check `git status` (or the Git panel) after applying an AI edit. | The file appears as a normal unstaged working-tree change — AI never stages or commits automatically, and no `.journal` or backup file appears inside the project tree. |
| E2 | Ask the AI to edit `.env` or `../outside.txt`. | Refused: `.env` is never sent and any edit block targeting `.env` or `..` is rejected before a diff card can be applied. |
| E3 | Try **Explain selection**, **Explain last error**, and **Ask about the project** (or the ➤ arrow). | All work exactly as in Phase 76–78. |

---

## Report

Paste the table with ✅ / ❌ per row. Rows **C1–C3** and **D1–D4** are the core
Level 3 contract. Then say *"merge"* when you want it merged.
