# CodeC Website W6.8 — Chapter 18: Ask about your code

**Status:** PLANNED, not started · **Cost:** static · **Effort:** M
**Target:** website/ch-18.html · **Depends:** W4.2, W3.5/6, ch-17.
**Order:** after W6.1–5; **before W6.6 polish and W6.7 deployment**.
Scope from v2.3 handoff, recorded 2026-10-07. Sources: README AI → AI.md →
DATA_AND_PRIVACY.md, current verified facts (not AI roadmap).

## 1. Chapter design

- Crumb **Chapter 18 of 19**. Prev ch-17, next ch-19. Not course completion.
- Goals: choose the optional assistant, set up your own key safely, ask one small
  code question, read exactly what was sent and received, understand session history.
- Need: CodeC project with the learner's own harmless hello.c; network and adult/
  provider terms eligibility; provider key and potential charges. Never require
  publishing a key or sending real sensitive work. No AI key entry on website.

### Steps

1. AI optional: ✨ home; Gemini default, NVIDIA Build manual dev/test only. Explain
   no hosted CodeC service/shared key/proxy. Describe where to obtain a key per
   AI.md, but do not expand website outbound allowlist silently (O/source policy).
2. Choose provider, paste key in **app**, confirm terms/age, Save key. Explain
   Keystore/no_backup and Test connection's fixed one-word/no-code request.
3. Read “Before you ask”: small work, own bill/rate limits, review/apply yourself,
   responsibility. I understand — start accepts UI agreement, not a broad grant.
4. Ask “Explain what this hello.c program prints.” Show complete harmless C file
   inline, so no repo visit required. Preview provider/model/exact text; inspect
   then Send, or cancel. Expected content is the program's two lines and an
   explanation, **not a guaranteed verbatim model response**.
5. Follow streamed answer and timeline; expand a tool row to exact result. Explain
   bounded project follow-ups after task Send. Stop cancels; cut answer Continue
   builds a new preview. Nothing is applied merely by reading an answer.
6. Drawer: titles, current marker, pin/new-chat confirmation, ≤20 chats/project,
   ≤8 carried turns. In memory only; closing app clears history. No on-device
   model promise. Point to ch-19 for task memory versus raw chat and approvals.

### Exercises and common mistakes

- Ask about your own small loop, inspect preview before Send; expected check:
  provider/model/text visible, no secret file included, answer may need correction.
- Open a timeline row; compare to source file. Start new chat; observe confirmation.
- Optional Stop exercise (may still incur provider usage); do not promise refunds.
- Mistakes: assuming chat is a backup, expecting free/unlimited provider requests,
  confusing saved API key with saved chat, believing explanation implies an edit.
- Provide a no-key reading path: teach the exact states with labelled textual
  examples, not a fabricated live response or fake screenshot/device transcript.

## 2. Implementation steps

1. Re-verify source labels/current gate facts; record line/sha per workflow step.
2. Author complete chapter using canonical goals/steps/try-it/mistakes/navigation.
3. Validate C example against TCC subset; expected-output text matches program.
4. Review privacy/consent language and absence of real secrets/external resources.
5. Test chapter navigation ch-17 → ch-18 → ch-19, 360/1440, keyboard/offline.

## 3. Exit condition

1. Self-contained BYOK setup and first-question lesson, with honest costs/terms.
2. Preview/timeline/task-follow-up/Stop/Continue/history correctly distinguished.
3. At least two exercises with observable checks, no guaranteed AI answer or
   device acceptance invented, no secrets copied or model/local/autonomy promises.
4. Crumb 18/19, correct prev/next, Privacy footer; resource/offline/visual checks.
5. W6.9 follows; polish/deploy remains blocked until ch-19 is complete too.
