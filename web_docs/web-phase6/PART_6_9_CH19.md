# CodeC Website W6.9 — Chapter 19: Agent, tools and approvals

**Status:** PLANNED, not started · **Cost:** static · **Effort:** M
**Target:** website/ch-19.html · **Depends:** W6.8 + W4.2 verified AI facts.
**Order:** before W6.6 polish and W6.7 deployment. Final chapter, **19 of 19**.
Sources: README AI, AI.md, DATA_AND_PRIVACY.md, named proving files. v2.3 scope
supplied Oct 6; this missing spec recorded Oct 7, 2026.

## 1. Chapter design

- Goals: interpret a bounded task, understand tools/refusals, independently approve
  an edit or a run, use last-task Undo, keep controls inside the safety boundary.
- Need: ch-18; disposable project with only harmless learner-authored source.
- Prev ch-18; final next **Back to the course home** → learn.html. Completion
  belongs here, not ch-17. Keep scope humble: small reviewed work, not autonomy.

### Steps

1. Read-first model: inline eight-tool table. list_files 200; search_project
   40 hits/300 files with narrowing max/path/ext; read_file 400 lines/24 000 chars;
   read_files 8; find_files 80-char pattern; outline_file definitions;
   read_run_output bounded redacted held output; request_run **asks only**, 2/task.
2. Timeline exact results; refusals are features. Secret-like paths (.env/.npmrc/
   private keys), .git, .codec, build outputs, symlink escapes rejected before read.
   Explain using names only; never create/upload a real secret to demonstrate it.
3. **Door one, Apply:** small edit proposal drawn as a diff, select/review changes,
   Apply or Reject changes. No tool writes files. Explain dedicated applier route,
   one last-task Undo AI changes (≤5 files / ≤256 KB), conflicts after manual
   edits, no undo of network/runs/Git side effects. Disposable exercise only.
4. **Door two, Run:** request_run raises card, Run enters normal RUN pipeline,
   Skip runs nothing. Bounded real output/exit goes to configured provider. No
   terminal typing, package install, Git stage/commit or automatic execution.
5. Nine controls: read window, working set, task memory, answer detail, tool activity,
   inspection always on, backup provider, budget extension, read-only reviewer.
   **S9** all tune inside caps; no wider read/write/run/reach authority. Provider
   changes manual/fresh-preview, not a silent availability fallback.
6. Limits panel: 12 turns · 24 calls · 2 runs · 24 000-char reads · 8 000-char results
   · 8-file batch · 3 identical repeats; read window 50–400 default 400. Explain
   named stops rather than tricks to evade caps. Request cost/rate limit distinct.
7. Redaction: run output and Explain last error share secret scanning before budget;
   review preview and support exports. Raw chat/history memory-only under D6;
   bounded permitted file/task memory and undo preimages are separate no_backup
   storage, not a transcript archive. No claim the model provider retains nothing.

### Exercises and expected checks

- Ask for a small wording change in disposable hello.c; inspect diff, **Reject**:
  source remains unchanged. Then optionally re-propose, Apply, inspect, Undo:
  previous content restored unless intervening edits conflict (don't overwrite).
- Ask for a run; choose **Skip**, observe no run. If choosing Run on known safe
  code, compare actual output and exit to expected program output.
- Lower read window; observe value within 50–400 and inspection still always on.
  Discuss a refused .env request without putting a real key in any file.
- These are learner exercises, not an invented owner device pass. Existing W6
  P1+P5 transcript gate remains independent and mandatory.

Common mistakes: Apply versus Send, read tool versus exec, last-task Undo versus
version control, provider memory versus D6, widening caps via a settings toggle,
trusting an AI answer without reading/testing the code.

## 2. Implementation steps

1. Re-verify names/caps/source lines at current sha and W4.2 ledger.
2. Write complete inline tables/examples/exercises and expected observable checks.
3. Audit no secret values, cap-evasion guidance, autonomy promise or unshipped model.
4. Link ch-17→18→19→learn and all 19 course rows; check Privacy footer and AI nav.
5. Record responsive/offline/resource checks; only then release W6.6 polish.

## 3. Exit condition

1. Eight tools, individual/global caps, refusals, two approval doors, Undo,
   nine controls/S9 and redaction all source-backed and taught self-sufficiently.
2. D6 versus persistent task-memory/undo is honest; no per-read-consent fiction,
   shared key/proxy, autonomous write/run or on-device-model promise.
3. Exercises have expected checks, safe disposable context and no device claims.
4. Crumb 19/19 and final course-complete footer; 29-page set ready for polish.
5. W6.6/W6.7 still require full sweep/device gates/package-safe deployment evidence;
   do not mark website live merely because the last chapter was written.
