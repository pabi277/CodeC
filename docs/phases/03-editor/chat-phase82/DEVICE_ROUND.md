# Phase 82 / 82B — device round 1

> **Status: ⏸ POSTPONED by owner (2026-10-02), until after agent optimization. NOT PASSED.** Informal screenshot problem reports exist; this formal acceptance matrix has not been completed.
> Local core **462/462** + privacy/backup **13/13**; real Build APK **36995145462 green** on **7b2ecac**. Owner explicitly authorized the merge despite postponing this round; PR #107 merged at 4cb4151, main CI 37006780729 green. No acceptance inferred.

## Build for this round

- **Tested code:** `7b2ecac` on `arena/01a0fbc2-codec`.
- **Build APK:** [**36995145462 — ✅ GREEN**](https://github.com/pabi277/CodeC/actions/runs/36995145462) (unit/screenshot tests, debug/lint, signed release + APK guards).
- **Recommended install:** [**CodeC-IDE-release ZIP**](https://github.com/pabi277/CodeC/actions/runs/36995145462/artifacts/11221750847), unzip and install `CodeC-IDE-1.3.17-universal.apk` (**7,117,440 B**). Keep the build type/signing consistent with the current app; do not uninstall/wipe project data just to change type.
- **Debug alternative:** [CodeC-IDE-debug ZIP](https://github.com/pabi277/CodeC/actions/runs/36995145462/artifacts/11220829913) → `CodeC-IDE-1.3.17-universal-debug.apk` (**26,869,612 B**) if currently on debug.
- **Device result:** **POSTPONED until after optimization**, not passed. Existing informal screenshots report repetition/raw tool output/budget-counter issues. CI and merge permission are not device acceptance.
- Round 1 `36994479128` had no APK (JUnit fixture signature); use the green build above, not a stale APK.

### Owner scheduling decision / informal problem evidence

> “The device test is postponed, 1st i will make it optimized than device test”
> “Last merge it”

See [agent-core research](../../../research/AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md) for the three supplied screenshot names, observed Nemotron tool repetition/raw prompts/“49 of 24 reads”, source findings and proposed optimization. The displayed model is **nvidia/nemotron-3-super-120b-a12b**, not GLM-5.3. No row below is marked passed by this scheduling/merge decision; **all ⏳ entries mean deferred formal checks**. This is not a successful live-key/Keystore/model-quality acceptance claim.

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

After the agreed optimization, refresh this checklist to the new tested build and reply with ✅ / ❌ / NOT EXERCISED per row (screenshots without secrets). Device/Android/theme are optional facts. The owner's **“Last merge it”** authorizes the current Phase 82 delivery only; it does not pass these rows or authorize a later change's merge.

## Merge / post-merge CI — PR #107

The owner explicitly commanded **“Last merge it”** while postponing formal device acceptance until after optimization. Both final-head checks passed on **268ed24e2123cd7a55aa6f535bbff594d5004407**: [push **37005368058**](https://github.com/pabi277/CodeC/actions/runs/37005368058) and [PR **37005372003**](https://github.com/pabi277/CodeC/actions/runs/37005372003). The guarded merge used that exact head; old PRs #42/#83 were not touched.

- **[PR #107](https://github.com/pabi277/CodeC/pull/107): MERGED** at **`4cb4151f1a59f9b719cc9bd64224ea9040c1a022`** on **2026-10-02**.
- **Main [Build APK 37006780729](https://github.com/pabi277/CodeC/actions/runs/37006780729): GREEN** on that merge SHA; real unit/screenshot tests, debug/lint, signed release and APK guards passed.
- **Release:** `CodeC-IDE-1.3.17-universal.apk` = **7,117,440 B**; **debug:** `CodeC-IDE-1.3.17-universal-debug.apk` = **26,869,596 B**. APK bytes are annotations, not compressed artifact ZIP sizes. Release manifest has no android:debuggable flag; no failure annotation, no release/tag published.
- Delta over Phase 81/main baseline **7,104,312 B**: **+13,128 B** (~12.8 KiB / 0.18%). Different build artifacts have their own measured sizes; earlier branch facts remain historical.
- **Formal device acceptance: POSTPONED until after optimization, NOT PASSED.** Informal Nemotron screenshot issues remain open; successful Keystore/live GLM access/model quality not inferred.
- **Research only:** full-file/batch/context/loop/native-tool/Markdown/multi-API proposals are not implemented; no default-model change, automatic provider fallback or new tool permission.
- Post-merge verification-ledger-only record is committed/pushed on **`arena/01a0fbc2-codec`**, not to main. Next: agree/start optimization, then refresh/run the postponed owner device matrix. Standing §3 still requires the next change's explicit merge command.
