# Phase 78 — device round 1 (AI Level 2: whole-project context)

> **Status: ⏳ NOT RUN.** These rows are owed. Nothing here is a claimed pass.
> Run them on a real phone and paste the table back with ✅ / ❌ per row.
> On ❌ a screenshot helps. Then say *"merge"* only when you want it merged —
> nothing merges without that word (`rule.md` §3).

**You need:** a project with **several** source files (a demo project is fine),
Phase 76's Gemini key already saved (or a fresh one from
https://aistudio.google.com/apikey), and — for row C — one `.env` file you do
not mind CodeC *looking at the name of* (its **contents** must never be sent;
that is what C proves).

**Build:** `CodeC-IDE-release` (or `-debug`) from **Build APK run
`36822370600`** on `arena/01a0f5d2-codec` —
https://github.com/pabi277/CodeC/actions/runs/36822370600

---

## A. The new entry point

| # | Do | Expect |
|---|---|---|
| A1 | Open a project, tap the AI bubble. | The chat sheet opens at half, as before. |
| A2 | Look at the chip row. | **Three** chips: *Explain selection*, *Explain last error*, **Ask about the project**. |
| A3 | Select some code first, then look again. | *Explain selection* is the filled button; *Ask about the project* stays an outlined chip. |
| A4 | Tap **Ask about the project** with an empty question. | It still works — the question is optional. |

## B. The preview names its files

| # | Do | Expect |
|---|---|---|
| B1 | Type *"where does this program start?"*, tap **Ask about the project**. | *"Reading the project…"* with a spinner and a **Cancel**, briefly. |
| B2 | Wait for it. | A preview headed **Files that will be sent**, then `From project <name> · N files · <chars> characters.` |
| B3 | Read the list. | One line per file: `path — <n> lines`. **At most 5 files.** |
| B4 | Scroll the preview. | The verbatim payload underneath shows each file with a `--- path (n lines) ---` header. The list above and the text below name the **same** files. |
| B5 | Tap **Send**. | It streams an answer. The answer can **name the files it used**. |
| B6 | Tap **Copy answer**, then **New question**. | Copies; the exchange clears. |

## C. Credentials are never sent — the row that matters most

| # | Do | Expect |
|---|---|---|
| C1 | Put a `.env` file in the project containing something recognisable, e.g. `MY_SECRET_TOKEN=zebra42`. Also a `.npmrc` if you have one. | — |
| C2 | Ask about the project. | `.env` and `.npmrc` are **not** in the file list. |
| C3 | Read the left-out line. | It says something like *"Also in this project: 1 left out because it looks like credentials…"*. |
| C4 | Send, then search the **answer** for `zebra42`. | **Not present.** If it is, that is a ❌ — stop and report it. |
| C5 | Scroll the preview's verbatim payload and search it for `zebra42`. | **Not present.** The payload is what leaves the phone. |

## D. Budget and honesty

| # | Do | Expect |
|---|---|---|
| D1 | Ask a question naming a word that appears in one file only. | That file is in the list, usually first. |
| D2 | Ask about a project with a **very large** file (> 3 000 characters). | Its line reads `first <n> of <m> lines` — it says it was cut. |
| D3 | Ask in a project with many files. | The left-out line mentions files that were *"less relevant"*. Nothing vanishes unexplained. |
| D4 | Ask in a project with **no** source files (e.g. only images). | A short message: *"This project has no code or text file to send…"* — no request is made. |
| D5 | Open a file, edit it **without saving**, then ask about the project. | That file's line says *"includes unsaved edits"*, and the sent header says `[unsaved edits]`. The answer reflects your **unsaved** text. |

## E. Nothing broke (Phase 76 / 77 regression)

| # | Do | Expect |
|---|---|---|
| E1 | Select code, tap **Explain selection**. | Exactly as before: preview → Send → stream → Copy. |
| E2 | Run a file with a typo, then tap **Explain with AI** on the Output header. | As before, run-output preview. |
| E3 | ☰ → ✨. | Still **setup only** — no question field, no chips, no answer. |
| E4 | *Show AI button* off. | The bubble disappears; *Open AI chat* still opens the sheet. |
| E5 | Drag the bubble, restart the app. | Position remembered, as before. |
| E6 | With the Output panel open, try **both** *When the Output panel is open, AI chat* options. | A replaces it at half, B opens full screen; the Output panel returns unchanged either way. |
| E7 | Back while the sheet is open. | The sheet minimizes to the bubble; the app does not close. |
| E8 | Single-file mode (no project). | **No** bubble, no sheet — AI is projects only. |
| E9 | Ask about the project, then switch to a **different** project. | The exchange is gone (nothing is carried across projects). |
| E10 | Close and reopen the app, then ask a fresh project question. | The file list is rebuilt from scratch — the same question on the same project gives the **same** file list. |

## F. Speed

| # | Do | Expect |
|---|---|---|
| F1 | Ask about the project in your **largest** project. | *"Reading the project…"* is brief — a second or two, not a freeze. The sheet stays scrollable and **Cancel** works. |
| F2 | Tap **Cancel** mid-read on a large project. | It stops; no request was sent. |

---

## Report

Paste the table with ✅ / ❌ per row. Row **C4/C5** is the one that must not
fail. Then say *"merge"* when you want it merged.

---

## Round 1 follow-up (2026-10-01) — the send arrow was a dead end

**Owner report, verbatim:** *"Can i directly question the ai? The device test B part if i question
anything it's saying select some code in the editor. But full project explanation work"*

**Root cause (found by reading the code, `AiChatSheet.kt` `BottomBar`).** The ➤ arrow was hardcoded:

```kotlin
IconButton(onClick = { onDismissNotice(); onExplainSelection(question) }) { … }
```

It always meant *Explain selection*, whatever was typed. With no selection,
`AiContextBuilder.fromSelection` refuses at `selected.isBlank()` (`AiContext.kt:122`) →
`NO_SELECTION` → *"Select some code in the editor first, then tap Explain selection."*
(`AiCopy.kt:142`). Phase 78 added a third intent and left the arrow serving only the first, so the
one control that looks like "send" could only produce an error. The **Ask about the project** chip
was never affected — which is why the owner's project questions worked.

**Answer to the question asked:** before this fix, no — you could not type a question and just ask
it; the three chips were the only doors. Now you can.

**Fix.** The arrow follows the question:

```kotlin
if (hasSelection) onExplainSelection(question) else onAskProject(question)
```

With code selected it is **exactly** the Phase 77 behaviour (device-passed, untouched); with nothing
selected it asks about the project, which is what a bare question is. The no-selection hint
(`AiCopy.SELECTION_HINT`) now says so too — it used to read only *"Select code in the editor, then
ask"*, which stopped being true in this phase. Pinned by a new `AiLevel2WiringTest` case so the arrow
cannot silently go back to being selection-only.

### Re-run these rows (they replace nothing above; B1–B6 still stand via the chip)

| # | Do | Expect |
|---|---|---|
| G1 | Open the sheet with **nothing selected**, type a question, tap **➤**. | *"Reading the project…"*, then the project preview. **No** "Select some code in the editor". |
| G2 | Read the grey hint above the box with nothing selected. | It offers **both** routes: select code, *or* just type a question about the whole project. |
| G3 | **Select** some code, type a question, tap **➤**. | *Explain selection* preview — the Phase 77 behaviour, unchanged. |
| G4 | With nothing selected, tap **➤** in a project with no code files. | *"This project has no code or text file to send…"* — not the selection error. |
| G5 | Select code, tap the **Explain selection** chip. | Unchanged from Phase 77. |
