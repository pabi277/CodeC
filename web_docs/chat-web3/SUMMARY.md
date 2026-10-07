# chat-web3 — v2.3 reconciliation (W0.4), performed 2026-10-07

## Owner request and scope

The owner supplied a v2.3 website handoff dated 2026-10-06, then authorized:
“You can watch the pr 42 for reference but you have build it with updated docs
and more powerful website”. This session first verified, then reconciled plans,
then implemented W1 only. W1 has its own [record](../chat-web4/W1_SUMMARY.md).
No historical claim is made that a docs sync had already landed October 6.

## Observed state, before edits

- Clean `arena/8b8f8edc-codec`, base d4231f0d69633e6961976e7c7d32c13b9041d8d9;
  remote main same sha, shallow checkout (git log -10 returns one commit).
- Website docs actually v2.2, decisions D1–D22, timeline W0.3, **35** phase docs
  (6 READMEs + 29 PARTs). No website/. Missing all four v2.3 AI/privacy/chapter specs.
- chat-web2 already contains September 12 W0.3 sync, so never replace with W1.
- PR #42 open on arena/01a062f7-codec, head 6f6a3cf, old Home/Install/Start/CSS/
  favicon; two build checks success. PR #83 app bookkeeping also open. No PR from
  this session; no imported branch/code/authority and no existing PR changed.
- PR #116 merged at d4231f0, main run 37505147370 success, publish run 37505175304
  success, release app-v1.3.18 one APK 7 217 532 B, versionCode 22, SHA256
  549a8c54cf9ff3fff4f6d985997bbb887f6b3b3098f4ab6ba68fefe7028a60d8.
- PR #115 mergeCommit actually 0edf4dd; 1c6f910 was an earlier baseline. App
  NEXT_STEPS records Phase 96 complete, Level13 postponed/14 unauthorized, phase97 free.

## Reconciliation delivered

- Master WEBSITE_PLAN v2.3, 9 product pages + course home + 19 chapters = 29.
- D23–28 supplied-scope decisions recorded **Oct 7**, not forged Oct 6 history;
  D29 authorization/reference boundary, D30 verified-state/deployment findings.
- **39 docs (6 phase READMEs + 33 PART docs)**; per-phase parts 2/2/6/8/6/9.
  Added W3.5 AI, W3.6 Privacy, W6.8 ch18, W6.9 ch19, each design/steps/numbered exits.
- Every existing phase README/PART has a prominent binding v2.3 amendment before
  its preserved, explicitly historical prior design. Conflicting old requirements
  and counts are superseded, not silently left authoritative. Historical session
  records and earlier decisions remain intact.
- Current README/NEXT_STEPS/WEB_JOURNEY/web_prompt updated in same W1 delivery.
- O4/O6/O8 owner scope closed; O6 technical verification at W4.2 still mandatory.
- W4.2 first, expanded Verified Facts Table includes all AI/privacy/current first-run
  and Phase46 facts; actual package config and script inventory deferred to gate.
- W6.8/9 before polish/deploy, final footer moves ch17→ch19. Device transcript
  gates for W5 ch08 and W6 P1/P5 remain, no acceptance invented.

## Stale-fact sweep — current requirements versus retained history

| Old text / assumption | Current binding correction / proof |
|---|---|
| 37-doc arithmetic nit only | Those strings weren't in checkout; actually 35 + 4 = **39**, not 37. Three current summaries now use 39 (6+33). |
| 7 product / 17 chapters / 25 pages | 9 / 19 / 29 per supplied v2.3 scope |
| 6.6 MB or -74% current APK | 7 217 532 B release; -74% historical v1.3.17 only |
| Engine selection / CHECK BRIDGE / Termux settings card | README 72–84, 261–275: Auto/fallback/output guidance, no selector/card |
| First-hour tiles / mandatory tour / automatic userland | README 109–148: opt-in setup, intro/privacy acknowledgement/Arcade |
| Open Folder + proposed ProjectLink/mirror | README 167–177: Open Folder deleted Phase46, Import ZIP, file versus whole-project entry |
| Public developer number/email | D27: site Issues only; old historical decision rows not republished |
| AI omitted / no code leaves / all AI storage ephemeral | README 277–319; AI.md; DATA_AND_PRIVACY 56–168: providers, task consent, D6 versus bounded memory/undo |
| ch17 course-complete | ch17→18→19→learn; W6.8/9 before polish |
| Branch-source /website deployment | Unsupported arbitrary source folder; Pages artifact design in W6, preserve signed /dev and /keys from existing site; PR42 body finding |
| PR42 = completed work on main | Unmerged old reference only; no code imported, fresh W1 |

The old v2.2 bodies are intentionally retained as **historical design detail**;
a raw grep still finds their old numbers/contact discussions. The current top
amendments, master plan, handoff and site must be read separately. This is not a
claim that historical text was erased or that every app doc is internally fresh.
READMEs themselves have older contradictory troubleshooting paragraphs; use the
newer automatic-engine/current-first-run sections and record conflicts instead
of modifying app history.

## Gates still open

W2–W6 implementation, O1/O2/O3/O5/O7, W4.2 technical verification, W5/W6 owner
device transcripts, W6 full website link/offline/Pages verification. No PR/merge
or deployment authorized by this session's build instruction.
