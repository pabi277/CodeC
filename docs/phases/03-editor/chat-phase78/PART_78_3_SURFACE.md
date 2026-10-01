# Part 78.3 — the surface: a third chip and a preview that names its files

**Status: ✅ IMPLEMENTED 2026-10-01 on `arena/01a0f5d2-codec`.**
Files: [`AiChatSheet.kt`](../../../../app/src/main/java/com/codeci/ide/ui/ai/AiChatSheet.kt) (418 lines, +60) ·
[`AiCopy.kt`](../../../../app/src/main/java/com/codeci/ide/ui/ai/AiCopy.kt) (+30) ·
[`EditorScreen.kt`](../../../../app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt) (+14).
Test: [`AiLevel2WiringTest.kt`](../../../../app/src/test/java/com/codeci/ide/AiLevel2WiringTest.kt) — **15 cases** (source pins).

---

## 1. The decision this part implements

**Q5 (agent's decision, not the owner's):** the entry point is a **third chip
in the existing chat sheet**, beside *Explain selection* and *Explain last
error*. Not a new rail slot, not a row in ✨ home (which `AiSurfaceWiringTest`
pins as setup-only), and not a selection-menu item (Sora 0.24.6's
`EditorTextActionWindow` has no add-item API — Phase 77's recorded finding,
still pinned by `AiSurfaceWiringTest`).

The chip is an `OutlinedButton`, matching *Explain last error*'s weight — the
primary button stays *Explain selection* when code is selected.

---

## 2. What was added, exactly

### 2.1 The chip and the walk indicator

`BottomBar`'s `AiPhase.IDLE` row gained:

```kotlin
OutlinedButton(
    onClick = { onDismissNotice(); onAskProject(question) },
    enabled = !state.gathering
) { Text(AiCopy.ASK_PROJECT) }
```

Disabled while a walk runs, so two walks cannot race for the same sheet.
While `state.gathering` is true the conversation area shows a spinner, *"Reading
the project…"* and a **Cancel** (`TextButton` → `cancelGather()`), which cancels
the coroutine. Nothing has been sent at that point (D4) — it is a read of the
user's own files.

### 2.2 The preview names its files

For `AiSource.PROJECT` the `PREVIEW` branch renders, in order:

1. **Files that will be sent** (`AiCopy.PROJECT_PREVIEW_TITLE`);
2. `From project <name> · N files · <chars> characters.`;
3. one line per file — `path — first 120 of 310 lines, includes unsaved edits`;
4. the left-out line — `Also in this project: 3 left out because they look like
   credentials, 118 not code or text, 2 less relevant.`;
5. the free-tier note (unchanged from Level 1);
6. `SentText(systemInstruction + "\n\n" + userText)` — the verbatim payload.

Rows 3 and 4 are the Level 2 half of D4: with five files the user cannot read
12 000 characters in a phone sheet, so the honest unit of consent is **the path
list plus the counts**, and the verbatim text is still there underneath. Rows 3
and 4 come from the same `AiProjectSummary` the request body was built from, so
they cannot drift.

For `SELECTION` / `RUN_OUTPUT` the preview is **unchanged** — the old
`PREVIEW_TITLE` + `previewHeader` path is the `else` branch.

### 2.3 The user's side of the exchange

`AiCopy.youLine` became an exhaustive `when` over the three sources, so the
compiler refuses a fourth source without a label here.

### 2.4 The editor wiring

```kotlin
val aiAskProject: (String) -> Unit = { question ->
    val buffer = viewModel.codeText.value
    aiViewModel.askProject(
        question = question,
        openPath = viewModel.activeTabPath.value ?: viewModel.fileName.value,
        openText = buffer.text,
        openDirty = viewModel.isDirty.value
    )
}
```

`activeTabPath` is the tab's project-relative path (`EditorViewModel.kt:1612`
stores `safe`, the same value `EditorTab.relativePath` holds), so it matches
the reader's relative paths. One call site, pinned.

---

## 3. What did **not** change

- **No new `BackHandler`.** Gathering adds no back row; `BackRouter` row 0
  (`CollapseAiSheet`, `BackRouter.kt:137`) still owns Back while the sheet is
  up, and `AiSurfaceWiringTest` still asserts the sheet contains no
  `BackHandler`.
- **No new colour, font or motion.** Theme roles only; code text still uses
  `CodecType.codeFamily`; no `Color(0x…)`, no `FontFamily.Monospace`, no
  animation spec. (`AiHelperWiringTest`, `AiSurfaceWiringTest`,
  `TypeAdoptionTest`, `MotionWiringTest` all still apply.)
- **No new `.dp` literal** in `EditorScreen.kt` (`TokenAdoptionTest`).
- **✨ home untouched**, so the rail slot is still setup-only.
- **Both Output-conflict variants untouched** — the Phase 77 setting still
  decides HALF vs FULL when the Output panel is open.
- **One question at a time** (Q1): the sheet still shows one `YouBubble` and
  one answer; *New question* still clears.

---

## 4. Tests

`AiLevel2WiringTest` — 15 cases of source pins, all run through the real
`RepoFiles.codeOnly` (comments and string literals blanked, so a pin cannot be
satisfied by a sentence promising the thing):

1. no file under `ui/ai` mentions `isSearchable` or `ProjectSearch.`, and the
   hazard is named where the filter lives;
2. `SECRET` is the first branch of `exclusionFor`;
3. no secret opt-in exists;
4. the reader asks the policy and the view model asks the reader;
5. `askProject` lands on `preview(result)`, never on `client.stream(`, and
   there are still exactly two network entry points;
6. the editor hands over the live buffer, one call site only;
7. the sheet offers the third source, disables it while gathering, and renders
   the file list;
8. the new files write and run nothing;
9. the new files persist nothing, and `AiKeyStore` gained no Level 2 key;
10. the new files log nothing;
11. the walker uses only minSdk-24 APIs, with the canonical-path symlink test;
12. a project prompt carries its file list and the body is built from the same
    two strings, with `"store":false` pinned as written;
13. the third source is refused, not silently narrowed;
14. the budget is the existing 12 000 ceiling, and that value is untouched;
15. no `SYSTEM_ALERT_WINDOW`, no Settings control, no AI dependency.

Pre-validated in-sandbox before CI: **139/139** pin assertions executed through
the real `RepoFiles.codeOnly` on the local harness. Two pins in the first draft
were wrong and were caught before CI, not by it: the manifest **already**
contains `READ_EXTERNAL_STORAGE` (pre-existing, not this phase's business — the
pin was reframed to the overlay permission Level 2 might actually have
tempted), and `"store":false` is written escaped in source
(`append("\"store\":false")`), so a plain substring pin could never have matched.

## 5. Deferred / rejected with reasons

- **A ✨ home row for project questions** — rejected: `AiSurfaceWiringTest`
  pins the rail slot as setup-only, and widening it would reopen a Phase 77
  decision the owner device-passed.
- **"Ask AI" in the selection menu** — still unbuilt (Sora 0.24.6 has no
  add-item API; no fork, no view injection).
- **A per-file exclude checkbox in the preview** — deferred. The effective list
  is *inspectable* now (spec: *"let users inspect included paths and exclude
  files/folders"*); letting the user edit it needs a second preview round and a
  place to keep the choice, which collides with Q6 (nothing persisted).
- **Persisting the last question per project** — rejected (D6).
