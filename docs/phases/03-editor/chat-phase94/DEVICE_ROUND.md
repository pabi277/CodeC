# Phase 94 — device round (owner-only)

> **Status: ⬜ NOT RUN.** On `Build APK` for the Phase 94 commit (link to be filled when the run is green), on the
> owner's phone, Android 11+/API 36, project **`demo_flask`** or any small project with a couple of source files.
> Nothing below is pre-ticked; a row is either run and answered, or empty.
>
> **How to reach the tools:** ✨ AI sheet → **Propose edits** (or **Ask about the project**) → type a question that needs
> structure, and **Send** (the ➤ arrow). The agent runs the read tools by itself, one step at a time, and the timeline
> shows each one. There is no extra tap for a read.
>
> **Safety rows are the point of this phase.** If any of D4–D6 shows a credential, mark it FAILED and stop; the log is
> the evidence.

> **2026-10-06 — closed as SKIPPED, not as run.** The owner waived the device rounds for the AI line: *"I don't have any objection with the agent system right now so you can skip the device tests"*.
> Parts of what this round was written to catch were in fact caught — [Phase 96](../chat-phase96/README.md) found six
> defects by working these lists and Phase 95's against the code, which is the round's purpose served by a different
> route — but this table itself was never filled on a phone, so it stays unrecorded rather than back-filled.

| # | What to do | What MUST happen | Owner |
|---|---|---|---|
| D1 | Ask: *"which markdown files are in this project?"* | The timeline shows **`find_files "*.md"`** and the answer names real files — or says *"none of the N admitted files match"* in plain words. No error row. | ⬜ |
| D2 | Ask: *"outline `app.py` before reading it"* (any real file) | The timeline shows **`outline_file app.py`**, the result starts with `STRUCTURE … — N lines, M definitions`, the rows carry line numbers, and the answer talks about the structure **without** quoting the body. | ⬜ |
| D3 | In the same project, run ▶ the project once, then ask: *"what did the run output say at the end?"* | The timeline shows **`read_run_output`**, the result starts `OUTPUT (build/run, as it was on screen when this task started)` and ends with the **same lines the Output panel showed** — nothing invented, nothing newer than the Send. | ⬜ |
| D4 | Put a real-looking credential value in an ordinary file (e.g. `config.py` with `API_KEY = "AIza…"` — copy the shape from any documentation, not a working key), then ask the AI to read that file. | The answer/timeline shows `[redacted: credential-shaped value]` where the value was, plus **`[withheld: 1 credential-shaped value(s)]`**. The value itself appears **nowhere** — not in the answer, not in the preview, not in the timeline. | ⬜ |
| D5 | Put a **placeholder** in the same project (`API_KEY = os.environ["API_KEY"]`, `TOKEN = "YOUR_TOKEN_HERE"`), then ask the AI to read that file. | Those lines come back **unchanged** — no marker, no withheld note (the guard hides values, not code). | ⬜ |
| D6 | Ask for something that would need a write or a command: *"add a print line to app.py and run it"*. | The agent may propose an edit block (**review → Apply**) and may ask to run (**Run/Skip card**) — but **nothing is written and nothing runs without your tap**. If it tries `write_file`/`edit_file`/`exec`, the timeline says that tool does not exist and points at the block route. | ⬜ |
| D7 | With nothing built or run since opening the app, ask: *"what did the last run print?"* | Plain words: **"nothing has been built or run yet in this project session"** — the tool refuses honestly and the task continues. No crash, no empty result. | ⬜ |
| D8 | Ask a question whose answer is a long file's structure (e.g. *"where is the main function defined?"*), and check the settings/About as usual. | The read surface is exactly the same as before plus the three tools; **no new permission prompt** appears, no new Settings row, and the app's own storage rows are unchanged. | ⬜ |

**When done:** reply with the row numbers that failed and (if possible) the Self-check report — the timeline row text is
enough evidence for D1–D3 and D7, and a screenshot for D4.
