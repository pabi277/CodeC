# Phase 81 — device round 1 (Continue after cut off)

> **Status: ✅ PASSED (2026-10-02 — owner, in chat: *"Mark the docs device pass and merge it"*).** The pass is recorded on the owner's instruction; the rows below are the checklist this round covers. Merged to `main` @ `e089880` via **PR #106** (post-merge CI `36972776771` ✅ green, release APK `7,104,312 B`).

**You need:** a project open, Phase 76's Gemini key saved, and something long
enough to hit a length limit — the easiest is to select a whole file (or ask a
broad question like *"explain this whole file in detail, line by line"*).

**Build:** `CodeC-IDE-1.3.17-universal.apk` (`7,104,312 B`) from the Phase 81
**Build APK** CI run **`36946839410` ✅ GREEN** on `arena/01a0f9a5-codec` @
`9f9ffac` (artifact `CodeC-IDE-release`; debug `26,831,328 B`).

---

## A. The cut-off answer and the Continue door

| # | Do | Expect |
|---|---|---|
| A1 | Ask something long enough to be cut (or let a big selection be explained). | The answer stops, and under it: *"The answer was cut short because it reached the length limit."* plus the hint that **Continue** asks for the rest and shows the exact request first. The pinned bottom bar now has **Copy · Continue · New question**. |
| A2 | Tap **Continue**. | The ordinary preview appears (nothing sent): the file/question card, then a line *"Continuing this answer (part 2)…"*, then the verbatim text — which now ends with the block *"[The answer above stopped before it was finished … Continue it from exactly where it stopped …]"* and the last lines of the answer so far. |
| A3 | Tap **Cancel** on that preview. | The preview closes; the cut-off answer and its **Continue** button are still there, exactly as before. Nothing was sent. |
| A4 | Tap **Continue** again, then **Send**. | The rest of the answer streams **under what is already on screen** (the bubble grows; the earlier part is not repeated and disappears nowhere). The bottom bar shows **Stop** while it streams. |
| A5 | While that continuation streams, tap **Stop**. | The stream stops; everything received so far stays visible and is still marked cut short, so **Continue** works again. |
| A6 | Tap **Copy** after a continuation. | The clipboard holds the **whole** answer (first part + continuation), not just the last piece. |

## B. The limits are stated, never silent

| # | Do | Expect |
|---|---|---|
| B1 | Keep tapping **Continue → Send** until the app stops offering it. | At the limit, the button disappears **and** a sentence says why (either *"…has been continued 8 times — that is the limit"* or *"…has reached the app's overall length limit (64000 characters)"*). **New question** still works. |
| B2 | Ask a normal short question. | No **Continue** button appears anywhere — it exists only on a cut-off answer. |

## C. Nothing else changed

| # | Do | Expect |
|---|---|---|
| C1 | Use **Propose edits** and let the reply be cut (large request). | No Continue button; instead a one-line note that this reply was an edit proposal — the review card is where it is handled. All Phase 79 review/Apply/Undo behaviour is unchanged. |
| C2 | With Phase 80's build: ask via **Ask about the project** and let the agent's final answer be cut. | No Continue button; the note says the agent task has ended and to ask a new question. Run approval, timeline and Stop behave exactly as in the Phase 80 round. |
| C3 | **Explain last error** after a failing run, and **Explain selection** with a short selection. | Identical to before; the preview still shows the same two strings, Send still the only road to the network. |
| C4 | Force-close the app mid-answer and reopen the AI sheet. | No leftover answer, no leftover Continue state, no error — the sheet starts clean (nothing about this is persisted). |

---

## Report

Paste the table with ✅ / ❌ per row. Rows **A2–A5** (preview, cancel, land,
stop) and **B1** (the limit is explained) are the core of this round; the rest
are regressions worth a quick pass. On ❌ a screenshot helps. Nothing merges
until you say so (`rule.md` §3).
