# CodeC Website W3.6 — Privacy (`privacy.html`)

> **Current batch status — 2026-10-07, D33: IMPLEMENTED FOR REVIEW.**
> Owner authorized W3–W6 content together, then one commit/watch; separate start
> commands/intermediate commits below are superseded. W4.2 verification ran FIRST.
> [Batch record](../chat-web6/BATCH_SUMMARY.md) · [checks](../chat-web6/CHECKS.md) ·
> [device gates](../chat-web6/DEVICE_TESTS.md) · [current full ZIP](../chat-web6/review-zips/CodeC-website-W6-review.zip).
> D31 download law retained. No device pass, PR, merge or public deployment implied.

**Status:** IMPLEMENTED FOR REVIEW · **Cost:** static · **Effort:** M
**Depends:** W3.5; **blocks:** W3 completion (9/9 product pages).
**Scope:** v2.3 handoff (2026-10-06), recorded 2026-10-07.
**Sources:** README first, then docs/guides/DATA_AND_PRIVACY.md (claim ceiling),
AI.md (user workflow). Every claim names the implementation file proving it.

## 1. Design

1. Start with the two actual claims: **no telemetry** (no analytics, crash upload,
   advertising/tracking SDK) and **nothing leaves unless you start it**. No
   mandatory CodeC account is not a claim that Git/provider accounts don't exist.
2. Keep the honest correction **“with ONE approval it can read your files”** and
   the guide's sentence about shared-folder access. Explain optional all-files
   access, revocation and picker access. This permission is not a promise that
   the deleted Projects + → Open Folder action still exists.
3. Plain-language table: action, what data, recipient, proving file. Include
   packages/bootstrap, Git, manual updates, LAN sharing, user-shared feedback/logs,
   and AI. No generic “everything stays local” slogan.
4. AI: named provider and exact preview → Send; admitted read follow-ups during
   that task may send without a new tap. No hidden continuation/provider switch;
   visible single rate retry, Stop, fresh-preview Continue. Test connection sends
   fixed text, not code. Review each Apply and each Run, Skip runs nothing.
5. Recipient/terms: Gemini default; NVIDIA Build manual dev/test only. No CodeC
   server/shared key/proxy. Provider data use/retention can apply; store:false
   is NOT zero retention. Warn not to send secrets/sensitive/personal data.
6. Storage distinctions: encrypted provider key in no_backup/ai; settings/model
   metadata; bounded last-task undo; bounded permitted task-memory cache; raw
   questions/replies/timeline and drawer titles stay in memory only (D6). No
   statement that ALL AI data is ephemeral. Support redaction, including the
   Explain last error route, reduces risk but is not a license to share secrets.
7. Permissions: distill EVERY row of the guide's current manifest-backed table,
   with proving file references and actual user action. Re-verify at write time.
   Backup is include-list-only; projects, not userland/tokens/provider keys.
8. Site itself: local files only, no analytics/third-party loads. Optional outbound
   links leave the site when clicked. Questions/bugs → GitHub Issues; no number/email.

Approved AI summary: **“nothing leaves unless you send it; when you do it goes
to the provider you configured — there is no CodeC server.”** Follow it with
bounded task-follow-up disclosure, so it cannot imply per-read taps.
**Banned visitor claim:** “your code never leaves the device”.

## 2. Implementation steps

1. Re-read README, complete privacy guide and AI guide at current sha. Build a
   claim → source file/line → proving implementation filename table in web records.
2. Write page in W1 chrome; Privacy is a footer link, not an extra header item.
3. Add contextual Privacy links from Install/About/AI and FAQ privacy answer.
4. Check every AI storage/recipient/consent statement against the guide; record
   ambiguities rather than changing the app guide to suit site copy.
5. Run phrase/contact/resource sweeps, keyboard/contrast/360/1440/offline checks.

## 3. Exit condition

1. Both claims + honest correction retained, no stronger assertion than source.
2. All permissions/outbound surfaces/storage classes explained and proved by file.
3. Every page footer has Privacy, plus four contextual links; no developer contacts.
4. No banned phrase as a claim, no zero-retention guarantee or automatic fallback
   assertion; D6 raw-chat law coexists with task-memory/undo facts.
5. W3 product pages 9/9, source ledger and checks committed; report then merge gate.
