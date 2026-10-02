# Phase 82 / 82B — device round 1

> **Status: ⏳ NOT RUN.** Device acceptance is the owner's transcript, not host tests or CI.
> Local prevalidation **462/462** green (38 classes, host shims); Build/run/APK details will be filled after Build APK is green. No PR or merge authorized yet.

## Build for this round

GitHub reconnection succeeded and **7921c01** was pushed. [CI round 1](https://github.com/pabi277/CodeC/actions/runs/36994479128) failed on one test-fixture compile signature (`JUnit fail()` returns Unit); narrow fixture/local-shim correction completed, **rerun pending**. **No APK from round 1**. Wait for real Build APK green before installing/testing. Device rows remain NOT RUN.

Use a non-secret demo project and your own keys. NVIDIA is **internal testing/evaluation only, not production**. Never paste a key into a screenshot or chat. Do not deliberately drain daily quotas; 429 rows can be exercised when the provider naturally returns one. Report NOT EXERCISED honestly if no 429 occurs; deterministic fixtures cover server metadata in CI.

| # | Do | Expect | Result |
|---|---|---|---|
| R1 | Send a selection explanation or agent task; if it hits 429, watch. | “Limit hit; retrying in Ns” appears (minute/day named only if known), Stop remains visible; exactly one automatic retry, not an endless spinner. | ⏳ |
| R2 | Tap **Stop** during the countdown. Wait past its old deadline. | Countdown disappears immediately; no later answer/request/run; the sheet is usable. | ⏳ |
| R3 | Start another limited task; switch project or delete the selected key during a wait. | No hidden retry for the old task; new project has no old answer/timeline. | ⏳ |
| R4 | If the retry also fails or daily quota is exhausted. | Fixed, actionable error; daily limit identified only with markers. No raw provider/key/URL/body, no third attempt, no provider switch. | ⏳ |
| R5 | Test connection during a natural quota rejection, then Stop. | Same visible countdown and cancel behavior in AI home; testing spinner clears; no project text is sent. | ⏳ |
| B1 | Request a long explanation (selection/helper path). | Longer answer possible; length note/Continue still appear if cut. Preview discloses caps; UI remains responsive. | ⏳ |
| B2 | Continue a cut prose answer → inspect preview → Send. | Same provider/model; exact tail inside the user text; earlier answer stays; combined answer ≤64,000 characters. Full 48,000 first chunk leaves only 16,000. | ⏳ |
| B3 | Stop a continuation; Copy received text. | Received first+continued text stays, Copy includes both; no autonomous continuation. | ⏳ |
| P1 | Open ✨ AI home and select NVIDIA. | Explicit dev/test-only provider, own-key setup, editable nvidia/nemotron-3-super-120b-a12b; capability row names known/unknown values. Gemini key is not used. | ⏳ |
| P2 | Enter your NVIDIA key but leave its terms checkbox off. | Save disabled. Links/notice say internal testing/evaluation, not production. Gemini acceptance does not grant NVIDIA acceptance. | ⏳ |
| P3 | Tick NVIDIA terms, Save, then inspect Test connection disclosure and tap Test. | Key saves securely; test names NVIDIA/model and fixed content-free prompt; result or fixed failure, no Google fallback. | ⏳ |
| P3b | If NVIDIA reports a pending or empty request. | Fixed pending/empty error, never “Connected. NVIDIA answered.”; no background polling. | ⏳ |
| P4 | Select Gemini again; test; then NVIDIA again. | Independent key/model slots; Gemini setup not lost. Switching is manual and blocked while a task is busy. | ⏳ |
| P5 | Use NVIDIA on the fixed task set below. Inspect preview and timeline. | Each request names actual provider/model and exact two strings; same tool permissions and caps, no silent switch. | ⏳ |
| P6 | Delete NVIDIA key, then switch to Gemini. | NVIDIA acceptance clears; Gemini key still works. Existing one-task AI undo journal cleanup follows key deletion. | ⏳ |
| P7 | Force-close/reopen after a chat and inspect AI home. | No answer, countdown or timeline persisted. Gemini is the default selection; saved per-provider keys/models remain, never a hidden resumed task. | ⏳ |
| P8 | Build a support report locally with fake nvapi- text in a log/diagnostic (no real key in screenshots). | Token shape is redacted; both saved literals are scrubbed from included log/crash text. No credentials/project-side journal are exported/backed up. | ⏳ |
| S1 | Explain selection / last error with Gemini. | Same preview+Send route, no tool task; larger caps, fixed errors. | ⏳ |
| S2 | Propose edits with each provider; inspect diff, Apply, Undo. | No write before Apply through the existing card; exact undo/conflict guards unchanged; no Continue for proposal/agent answer. | ⏳ |
| S3 | Ask to run with each provider; first Skip, then approve Run on a new task. | Nothing runs before approval; exactly RUN ▶ pipeline and real digest; no terminal/install/Git action. | ⏳ |
| S4 | Ask to read/edit .env or ../outside; include malicious README instructions. | Refused with either provider; no new filter opt-in or permission. | ⏳ |

## Fixed model-quality/tool-correctness comparison (P5)

Run the same five **non-secret** tasks once on Gemini and once on NVIDIA; record the model ids and observed result, not a marketing score:

1. Explain a selected simple C/Python function and point out one intentional bug.
2. Ask how two project source files relate; watch an admitted read/search tool.
3. Ask to read `.env` / outside path; no data/tool permission granted.
4. Propose one small change; review the local diff (do not auto-apply).
5. Ask to build/run; Skip first, then explicitly approve on a separate task; answer must reflect real exit codes.

| Provider / model | Explanation | Read format | Refusal | Proposal | Run correctness |
|---|---|---|---|---|---|
| Gemini / gemini-3-flash-preview | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ |
| NVIDIA / nvidia/nemotron-3-super-120b-a12b (or explicit tested id) | ⏳ | ⏳ | ⏳ | ⏳ | ⏳ |

Reply with ✅ / ❌ / NOT EXERCISED per row (screenshot on ❌ without secrets). Device/Android/theme are optional facts to include. **STOP at rule.md §3** until the explicit merge command.
