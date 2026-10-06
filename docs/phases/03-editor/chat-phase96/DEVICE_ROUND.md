# Phase 96 — owner device round

Six fixes and one read-power change, all of them things a screenshot cannot show. Check them on the phone you already have (no reinstall needed); the Phase 95 round still stands for everything else. Empty by design — nothing here is pre-ticked.

## Project switch and history (the two ordering bugs)
- [ ] **P1.** Project A: ask one question, wait for the answer. Switch to project B, ask a different question. Open the drawer on **B** → only B's row is there. **A's chat must not appear in B's list** (it did before this fix, and A's newest exchange went missing).
- [ ] **P2.** Switch back to A. Open the drawer → A's row is there **and its last exchange is intact**: open it and the answer you got before the switch is still the last thing in it.
- [ ] **P3.** In one project, with a chat on screen, tap a history row (or ☰ → a row). The tapped conversation replaces the one in front of you — **the conversation you were looking at must NOT grow a copy of the other one's last question**, and the row you tapped must not have gained a turn. Switch back and check it from the other side too.
- [ ] **P4.** Ask two questions in one chat, then open the drawer: **one** Recents row for that chat (one chat is one row; a new row appears only after *+ New chat* or a project switch). This is the corrected reading of Phase 95's D6, which asked for two rows — the app was right, the checklist was wrong.

## The drawer
- [ ] **P5.** Open the drawer and tap **+ New chat**: it asks the same one question the header `+` asks (*"Start a new chat?"* with the *"nothing here is saved to a file"* sentence), and after you confirm, the drawer **stays open** so you can see the previous chat land in the list. Rejecting the question leaves everything as it was.
- [ ] **P6.** Close the drawer by **tapping outside it** (or swiping it shut) — not by ×. Then tap ☰ and the pill: **both open it again.** (Before the fix, both went dead until something else closed the drawer.)
- [ ] **P7.** With two or more chats in the drawer, the row you are looking at has a **✓** beside its title; the others have none. Tap another row → the ✓ moves with it.

## Run output and secrets
- [ ] **P8.** Make a build fail with a line that carries a credential-shaped value — e.g. put `API_KEY=sk-abcdefghij0123` in a file the build echoes, or run `python -c "print('token: ghp_abcdefghijklmnop1234')"` as the project's run command. When the Output panel shows it, tap **Explain last error** (the chip, not the agent tool): the question preview must show `[redacted]`-style withheld values and a `[withheld: N credential-shaped value(s)]` line — **never the raw value**, and the withholding must be visible, not silent.
- [ ] **P9.** The same text really left the phone that way (D4): tap *What will be sent* and the redaction is in it, character for character, including that `[withheld: N]` line.

## Read power: a scoped search
- [ ] **P10.** Ask something that names a folder, e.g. *"find every place `malloc` appears in src/, ignore markdown"*. In the agent's step timeline the row reads `search_project "malloc" in src/ *.md` (or whatever you asked to narrow to) — **the scope is shown, not hidden** — and the answer it quotes back comes only from that folder.
- [ ] **P11.** Ask for a scope the project does not have (`in docs/`). The step shows a **refusal naming the reason** (`docs/ holds no code or text file in this project`), not an empty result — the model is told the folder is missing rather than concluding your code is absent. A plain search with no scope behaves exactly as in Phase 94.

## Round 2 — the three first-run rows he reported
- [ ] **R1.** Uninstall and reinstall (or bump the welcome version) so the welcome card shows again. Open the AI: the card must come up on the **whole screen**, and **"I understand — start" must be visible without scrolling**. The four bullets may run long on a small phone — they scroll under the button; the button never does.
- [ ] **R2.** Tap "I understand — start". The chat opens at the **same full height** — no small strip above dead space. The composer, the chips and the hint are all on screen at once.
- [ ] **R3.** Ask one question and wait for the answer. **Open the drawer before doing anything else** — the row for this chat must already be there, titled with your question, with the ✓ on it. No New chat, no project switch, no second question first.
- [ ] **R4.** Tap ⌄ (or drag the handle down) to HALF, then ask a second question: the answer still shares the screen with the code the way Phase 77 decided — the full-height rule is only about *opening* with nothing beside you, and your own gesture always wins.
- [ ] **R5.** Stop a streaming answer mid-reply (⏹/Stop). The question and the part that arrived are in the conversation **and** the drawer row exists for it.
- [ ] **R6.** Search scoping on the phone: ask *"where is `malloc` used in src/, ignore markdown"* and check the step row says `search_project "malloc" in src/ *.md`.

## Safety untouched
- [ ] **P12.** A proposed edit still arrives as a diff card with Apply / Reject, nothing applies itself, and a run request still waits for your **Run** tap.
- [ ] **P13.** The welcome card still appears on a fresh install and hides the composer until *I understand — start*; the *Grant access* row still appears under a refused write even before you accept the welcome.
- [ ] **P14.** Close the app and reopen it: no chat text survived anywhere (history is this session only — D6), and the Settings/AI store still holds nothing but key/model/bubble/options plus the welcome flag.
