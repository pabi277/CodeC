# Phase 76 — device round 1 (AI Level 1, read-only Gemini helper)

Build: the `Build APK` artifact of the Phase 76 tip on `arena/01a0f34c-codec`.
You need: a Gemini API key from https://aistudio.google.com/apikey, internet,
and a CodeC project with a file that can fail (e.g. a Python file with a typo).

Report each row as ✅ / ❌ + one line (a screenshot helps on ❌).

## A. Gate and key setup

| # | Do | Expect |
|---|---|---|
| A1 | Open a file **from the Projects hub file list** (single-file mode). Open ☰ → tap the fifth rail icon (✨). | Panel says *"Open a project to use the AI helper…"*. No key field, no buttons. |
| A2 | Open the project **in the editor** (⋮ → Open in editor). ☰ → ✨. | Key setup: intro ("your own Gemini API key"), AI Studio link, masked key field, model `gemini-3.8-flash`, free-tier note, region note, terms links, the 18+ checkbox. **Save key** is disabled. |
| A3 | Paste your key but leave the checkbox unticked. | Save stays disabled. Tick it → Save enables. |
| A4 | Tap **Save key**. | Setup disappears; question field + **Explain selection** / **Explain last error** appear. |
| A5 | Force-stop CodeC, reopen the project, ☰ → ✨. | Still set up (no key field). The key survived, encrypted. |
| A6 | ⚙ → **Test connection**. | Spinner, then *"Connected. Gemini answered."* (Or a clear fixed message: bad key / model not found / rate limit / offline.) |

## B. Explain selection

| # | Do | Expect |
|---|---|---|
| B1 | Close ☰ without selecting anything, reopen ✨, tap **Explain selection**. | Red line *"Select some code in the editor first…"*. Nothing is sent. |
| B2 | Select 3–10 lines, type a question ("why is this slow?"), ☰ → ✨ → **Explain selection**. | **Check before sending** card: model + character count, free-tier note, and the exact text: instruction, "Explain this selected … code from <file>", your question, your selected lines. |
| B3 | Tap **Cancel**. | Back to the question screen; nothing was sent. |
| B4 | Again → **Send**. | The answer streams in with a **Stop** button; then **Copy answer** / **New question**, and *"AI answers can be wrong. Nothing in your project was changed."* |
| B5 | **Copy answer**, paste somewhere. | Toast "Answer copied"; the text pastes. The file in the editor is unchanged. |
| B6 | Edit the selection without saving, then Explain selection again. | The preview says *"The selection includes unsaved edits."* |

## C. Explain last error, Stop, errors

| # | Do | Expect |
|---|---|---|
| C1 | Before running anything, tap **Explain last error**. | *"There is no failed run to explain…"* |
| C2 | Run a file that fails; then ✨ → **Explain last error**. | Preview shows the error lines with paths like `src/main.py`, **not** `/data/user/0/…`. Send → explanation. |
| C3 | Ask a long question → Send → tap **Stop** within a second or two. | Streaming stops at once; partial text stays with the "cut short" note (or back to the question screen if nothing arrived). |
| C4 | Turn on airplane mode → send a preview. | *"You're offline…"*; **Try again** returns to the **preview** (not a silent resend). |
| C5 | ⚙ → change model to `gemini-does-not-exist` → Save model → Test connection. | *"This model name was not found…"*. Set it back to `gemini-3.8-flash`. |

## D. Nothing saved, delete, privacy

| # | Do | Expect |
|---|---|---|
| D1 | Get an answer, then switch to another project in the drawer, and back. | The answer is gone (fresh question screen). |
| D2 | Get an answer, force-stop CodeC, reopen. | The answer is gone. |
| D3 | ⚙ → **Delete key**. | Back to key setup; the 18+ checkbox is unticked again. |
| D4 | After using the helper, Settings → Feedback & support → tick the log → **Copy full report**, paste it somewhere. | The report does not contain your key (the helper never logs it; any `AIza…` shape is also redacted — host-tested in `FeedbackDraftTest`). |
| D5 | The side panel's other four slots (navigation, files, search, repository). | Unchanged. |
