# CodeC Website W3.5 — AI assistant (`ai.html`)

**Status:** PLANNED, not started · **Cost:** static · **Effort:** M
**Depends:** W1–W2, W3.1–4; **blocks:** W3 product-wing completion with W3.6.
**Scope:** v2.3 owner handoff (2026-10-06), recorded 2026-10-07.
**Sources:** README §AI assistant first, then docs/guides/AI.md and
DATA_AND_PRIVACY.md. Shipped app-v1.3.18 only, no roadmap as product promise.

## 1. Design

1. Hero: optional assistant, **your own key, your bill, your provider**. Gemini
   default, NVIDIA Build manually selected **dev/test only**; not free hosted AI.
   No CodeC server, shared key or proxy. AI navigation active; Privacy footer.
2. Setup steps: ✨ home → choose provider/key → adult/terms confirmation → Save
   key → optional Test connection (fixed one-word request, no code) → open chat →
   first-run agreement. Never ask visitor to paste a real key into this website.
3. Ask: choose project/selection/error question, inspect provider/model and exact
   two-string preview, Send, read streamed Markdown answer and expandable timeline.
   Task Send permits bounded read follow-ups without a fresh tap each time;
   Continue needs a new preview, visible single rate retry can be stopped.
4. Eight-tool table (all scoped, no direct write/exec):
   - list_files: 200 paths; search_project: 40 hits / 300 files, narrowing scope;
   - read_file: 400 lines / 24 000 chars; read_files: 8 files;
   - find_files: 80-char pattern; outline_file: definitions and lines;
   - read_run_output: bounded redacted held output;
   - request_run: **request only**, 2/task, nothing runs without Run.
5. Two approval doors: edit proposal (`<<<CODEC_EDIT>>>`) → review diff → Apply or
   Reject changes; request_run → Run or Skip. One bounded last-task Undo AI changes
   journal (≤5 files / ≤256 KB) is not a general undo of runs/network/Git.
6. Secret-like paths (.env, .npmrc, private keys), .git/.codec, output and symlink
   escapes refused before reads. Refusals are intentional, not missing features.
7. Nine controls: read window, working set, task memory, answer detail, tool
   activity, inspection (Always on), backup provider, budget extension, read-only
   reviewer. **S9: tunes inside caps, cannot widen read/write/run/reach**.
8. Cap panel: 12 model turns, 24 tool calls, 2 approved runs, 24 000-char reads,
   8 000-char results, 8 batched reads, 3 identical repeats; read window 50–400,
   default 400. Stops visible, no unlimited-use claim.
9. Honest limits: D6 raw chat/session history never persisted, drawer titles also
   in memory, 20 chats/project, 8 carried turns. Distinguish bounded task memory
   and undo preimages from raw chat. Keystore AES-256-GCM key in no_backup/ai,
   key-shaped support/output/error redaction. Link Privacy for provider retention;
   do not promise zero retention. L13 postponed, L14 unauthorized.

## 2. Implementation steps

1. Re-read source guides at current sha; record every table row/source/line.
2. Author complete plain-language page inside canonical W1 chrome, using local
   CSS, no embeds, live chat, key input or external assets.
3. Cross-check names/caps with AI.md; compare task-consent wording against privacy
   guide (not just simplified README wording). Link ai.html ↔ privacy.html.
4. Test 360/1440, keyboard/contrast/table scrolling, offline and resource sweep.
5. Update living docs; W3 remains incomplete until W3.6 is also verified.

## 3. Exit condition

1. Provider/setup/preview/timeline/approval/history/controls sections complete,
   eight exact tools and limits traced; no write/exec/autonomy/local-model claim.
2. No secret collection; request_run clearly asks only; Apply/Run separate taps.
3. D6 and task memory distinction correct; no overclaim of provider retention or
   per-read confirmation. Always-on inspection and S9 stated.
4. Privacy links in page/footer; navigation active; responsive/offline checks and
   self-dependent sweep recorded; no personal contacts or external resources.
5. Source trace and phase records committed with current web head state.
