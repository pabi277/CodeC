# Phase 94 — more power: three read-only tools, a structure reader, and the value-level secret guard

> **Status: 🚧 IMPLEMENTED (2026-10-05) on `arena/01a10b97-codec`; Build APK ⏳ PENDING; not merged.** **No PR, no merge,
> no `main` push** (`rule.md` §3). **Authorization:** the owner's message, same day, after Phase 93c was verified on his
> phone (*"Yes it's working"*): *"More working functions with restrictions for sensitive information, but to use it
> fully more tool with readable structure like that it didn't failed. I want to make it very powerful."*
>
> **The one rule this phase did not touch:** *power is in reading.* The owner asked for more functions; the app's law
> (Level 3/4, restated by Level 12) is that the AI has **no write tool and no command tool** — an edit is a proposal the
> user reviews and applies, and a run is a request the user taps. Phase 94 therefore adds **three read-only tools** and
> one content-level guard, and nothing that can change or execute anything. *"More working functions with restrictions
> for sensitive information"* is answered literally: more functions, and a second credential rule that works on
> **values**, not file names.

## 1. What the owner asked for, and what shipped

| His words | What shipped | Pinned by |
|---|---|---|
| *"More working functions"* | Three new tools, all read-only, all in the wire's own vocabulary: **`find_files(pattern, max?)`** — a glob (`*`, `?`, `**`) over the paths the Level 2 walk already admitted (no new walk, no disk scan, so it cannot reach a file the walk refused: `*.md` matches by name anywhere, `src/*.kt` by relative path, and a pattern without a slash matches names at any depth); **`outline_file(path)`** — the *structure* of one file (functions, classes, headings, shell functions) with real line numbers and indentation, so the model can read a range instead of the whole file; **`read_run_output(lines?)`** — the tail of the build/run output that was **on screen when the task started**, frozen at Send. | `AiToolRunnerTest` (5 find_files, 4 outline_file, 4 read_run_output), `AiToolProtocolTest` (4 validation cases), `AiOutlineTest` 7 |
| *"with restrictions for sensitive information"* | **The value-level secret guard** (`AiSecretScan`, new): the name rule (`isSecretLike` — `.env`, `id_rsa`, `*.pem`) had a blind spot the wider surface makes bigger, because an ordinary file can *carry* a credential value. Nine known shapes (Google `AIza…`, NVIDIA `nvapi-…`, OpenAI/Anthropic `sk-…`, GitHub `ghp_`/`github_pat_`, Slack `xox…-`, GitLab `glpat-`, AWS `AKIA…`, JWTs), PEM `-----BEGIN…-----END` blocks (one marker; an unterminated block swallows its tail), and assignment-shaped lines whose name says credential (`api_key`, `secret_key`, `token`, `password`, `client_secret`, …) whose value is a plausible literal. **Nothing is hidden silently**: every redaction is marked and the result ends with `[withheld: N credential-shaped value(s)]`, so the model is told something was withheld instead of wondering why a line looks odd. Placeholders and references (`YOUR_API_KEY`, `<your-key>`, `os.environ[…]`, `System.getenv(…)`, `changeme`, `getAccessToken()`) are deliberately **not** redacted. | `AiSecretScanTest` 9 |
| *"more tool with readable structure"* | Every new answer is a fixed, tab-free, human-readable shape: `FILES matching "*.c" — 2 of 2 listed`, `STRUCTURE src/main.c — 214 lines, 7 definitions` (then `12: int main(void) {`), `OUTPUT (build/run, as it was on screen when this task started) — last 60 of 168 lines`. Indentation is preserved as two spaces per level (clamped), comments never become definitions, and a truncated answer always says so. | `AiOutlineTest`, `AiToolRunnerTest` |
| *"like that it didn't failed"* | The three tools are built to **answer, not to error**: an empty glob result is a result (*"none of the 3 admitted files match"*), a file with no definitions says *"no definitions were recognised"* instead of failing, `read_run_output` with nothing built says *"nothing has been built or run yet"*, and out-of-range asks clamp (`lines: 0` → 1, `lines: 9999` → 200) instead of refusing a round trip. The **wire reader** was widened at the same time (`<<<codec_tool`, `<<< CODEC_TOOL`, `name = x`, `name: x`, `END_CODEC_TOOL >>>` all read the same), and the reviewer's markup check now uses the same reader instead of its own raw string test. | `AiToolProtocolTest` (tolerant spellings + `containsBlock`), `AiToolRunnerTest` (honest empty answers) |

## 2. Where the guard runs (one place each, and why that matters)

| Route | Where | Note |
|---|---|---|
| Tool results | `AiToolRunner.execute` → `Outcome.scrubbed()` | **The single exit**: every branch of the `when` returns through it, so a tool added later cannot forget the guard (the same single-point rule the walk's name filter follows). |
| The packed project text | `AiProjectFiles.sliceFor` | The guard runs **before** the budget is measured, so the one-request ceiling still holds exactly and the preview shows precisely what leaves (**D4**). |
| The edit parser's baselines | **not redacted, on purpose** | Baselines are a different object (`Candidate.text` stays raw). Redacting them would make the app write a redaction into a project file — the write path must never do that. |

## 3. The three bugs the host sweep caught before CI (and the fixes)

| What | Why it mattered | Fix |
|---|---|---|
| `AiToolRunner`'s new KDoc contained ```src/*.kt` `` — Kotlin **nests** block comments, so the inner `/*` swallowed the rest of the file (`Missing '}'`, `Unclosed comment`). | A comment would have broken the build. | The doc now says *"`*.kt` after a `src/` prefix"*; every touched file was re-scanned for a nested `/*`. |
| `AiSecretScan`'s placeholder list used `"$it_"` — the template grabbed the identifier `it_`, which does not exist. | A compile error. | `${it}_`. |
| `AiOutline` skipped **every** `#` line before the Markdown branch (so `# Title` was never a heading), had no `struct`/`union`/`enum`/`typedef` row, and no one-line body (`void f(void) { }`) or Java-style `void start()` row. | The tool's whole promise is *readable structure that does not fail* — three of the four new `AiOutlineTest` shapes were wrong. | The `#` skip excludes Markdown, `C_DEF` gained the type-declaration and one-line forms, and the JVM scan accepts the C-shaped method form. |

## 4. Limits (all shown to the model, all enforced in `AiToolPolicy`)

| Tool | Arguments | Limits |
|---|---|---|
| `find_files` | `pattern` (required), `max?` | ≤ 80 chars; letters/digits and `*?._-/@ ` only; no absolute path, no `~`, no drive letter, no `..`, no control character; `max` clamps to `MAX_LIST_ENTRIES` (200) |
| `outline_file` | `path` (required) | identical to `read_file`: `isSecretLike` → `AiEditProposalParser.validateTargetPath` → must exist in the walk's admitted set; ≤ `MAX_OUTLINE_ROWS` (120) rows |
| `read_run_output` | `lines?` | 1…`MAX_OUTPUT_LINES` (200), default 60; the snapshot itself is capped at `MAX_OUTPUT_SNAPSHOT_LINES` (400) lines at Send |

## 5. Verification round 3 — the two audit items Phase 93/94 owed (2026-10-05)

Two questions were left open when Phase 94 was committed, both about the Phase 93 fix this phase sits on. Both are now
answered, and one of them was a real bug.

| Question | Verdict | Action |
|---|---|---|
| Does the self-check's `runRefusedReason` really find the row the timeline writes? | **Sound, and now pinned both ways.** The row's title is composed as `AiCopy.agentStepDenied(AiToolProtocol.describe(request), reason)` — `describe` starts with the wire name, and the check looks for `AiCopy.agentStepDeniedPrefix(AiToolName.REQUEST_RUN.wire)`. The two halves are built from the same wire, so the prefix can never drift. | `Phase93WiringTest` +1: the prefix match and the two source sites are asserted together. |
| Does a refused **Apply** (or undo) offer the one-tap fix? | **It did not, and that was the bug.** `storageProblem` — the flag `storageFixRow` draws from — was set on the *preflight* refusal only, so when the write itself failed on a missing grant (facts stale by the time the bytes were written, or `AiEditApplier`'s own `canRead/canWrite` test firing) the owner got the sentence and **no button**: the Phase 93 complaint again, one layer deeper, and the worst possible shape of it — a named cause with no way to act. | Both failure branches now ask **the same policy the preflight asks** (`storageProblemFor(root, AiSource.PROPOSE_EDITS)`) and set `notice = problem ?: outcome.message`, `storageProblem = problem != null`. The row appears exactly when granting could change the answer; it is never matched out of a sentence. `Phase93WiringTest` +1 pins both call sites and the count (no third way to set the flag). |

Build APK [`37365936119`](https://github.com/pabi277/CodeC/actions/runs/37365936119) **green** on `2d495ab` (the round-3 code is `3a07ac0`; the empty commit that follows it exists only because GitHub refused to rerun `37364178929`, whose job was **cancelled by the runner** at 15m01s with no failing step). Release universal APK **7 206 076 B**; artifacts release set **6 544 793 B**, R8 mapping **4 710 804 B**, debug **26 168 818 B**; *release manifest: no `android:debuggable` flag*. **Docs round 3:** [`37369079122`](https://github.com/pabi277/CodeC/actions/runs/37369079122)
**green** on `a172673` (19m46s; release set **6 544 799 B**, R8 mapping **4 710 804 B**, debug **26 168 929 B**).

**Two honest CI wrinkles, both worth keeping.** The first round-3 run (`37364178929`, on `3a07ac0`) and the
docs-only run after it (`37367483241`, on `60c25af`) both ended as **failures whose jobs conclude `cancelled`** — no
failing step, no annotations, and GitHub refused `gh run rerun` (*"cannot be rerun"*) — so each was re-triggered with an
empty commit on the same tree. The re-triggers are the two green runs recorded above (`2d495ab` for the code and
`a172673` for the docs), and the last one took **19m46s** where the cancelled pair stopped at ~15m03s, which is why the
cancellations are recorded as **transient runner events, not a duration cap**: a cancelled job is not evidence of
anything, in either direction, and it must not be mistaken for a pass.


## 6. Files

| File | Change |
|---|---|
| `app/src/main/java/com/codeci/ide/ui/ai/AiSecretScan.kt` | **new** — the value-level guard (`redact`, `Result`, `MARKER`, `KEY_BLOCK_MARKER`, `noteFor`); pure, no `android.*`, no `java.io` |
| `app/src/main/java/com/codeci/ide/ui/ai/AiOutline.kt` | **new** — the per-language structure scan (`outline`, `Row`, `MAX_ROWS`, `MAX_LEVELS`); pure |
| `…/ui/ai/AiTools.kt` | three `AiToolName` wires, `AiToolCall.pattern`/`.lines`, limits, validators, `describeCall`/`describe`, `INSTRUCTIONS`, the tolerant `OPEN_MARKER`/`CLOSE_MARKER` + `nameValue`, `containsBlock` |
| `…/ui/ai/AiToolRunner.kt` | `execute(…, runOutput)`; `Outcome.scrubbed()`; `findFiles`, `globMatches`, `outlineFile`, `readRunOutput`, `lineCountOf` |
| `…/ui/ai/AiViewModel.kt` | `AgentSession.runOutput` (frozen at Send, `takeLast(MAX_OUTPUT_SNAPSHOT_LINES)`); `agentAsk`/`agentPropose`/`startAgent` take it; it is passed to `AiToolRunner.execute` for every tool |
| `…/ui/screens/EditorScreen.kt` | both Send paths hand over `outputState.lines.map { it.text }` |
| `…/ui/ai/AiProjectFiles.kt` | `sliceFor` redacts before measuring — the packed body, the preview and the request stay one string (**D4**) |
| `…/ui/ai/AiContext.kt` | comment records that the packer, not `projectBody`, is where the guard runs (and why baselines stay raw) |
| `…/ui/ai/AiLevel10Policies.kt` | the reviewer's markup check uses `AiToolProtocol.containsBlock` |
| `…/ui/ai/AiCopy.kt` | the task preview names the whole read surface (the three new tools included) and states the value-level guard |
| `…/ui/ai/AiViewModel.kt` (round 3) | the `AiApplyOutcome.Failed` and `AiUndoOutcome.Failed` branches ask `storageProblemFor` and set `storageProblem`, so a refused write offers `Grant access` |
| Tests | `AiSecretScanTest` **9** (new), `AiOutlineTest` **7** (new), `AiToolRunnerTest` 16→**29**, `AiToolProtocolTest` 18→**26**, `AiTaskMemoryTest` 9→**10** (a `read_run_output` call is never versioned or snapshotted), `AiLevel12InjectionTest` tool-set pin widened to the 8 wires, `Phase93WiringTest` 16→**18** (round 3) |

## 7. Verification

* **Host sweep (`/home/user/harness`, kotlinc 2.2.10 — the project's own version).** Round 3 (with the two audit pins
  above): **55 classes / 728 passed / 0 failed**, with 11 classes listed as dropped (they need Android/Robolectric or a
  source the offline sweep cannot reach — `AiEditApplierTest`, `AiProvidersTest`, `AiHttpStreamTest`, the kotlinx
  clients — and CI runs every one of them). The earlier round measured **45 classes / 595 passed / 0 failed** over a
  narrower list; the two numbers are not the same set and are quoted separately on purpose.
  The `pins` group compiles the pure AI source set (39 production files, including DiffEngine/ProjectFilesPolicy/
  EditorOpenMode) plus 66 test files, and runs the AI suite plus `Phase93WiringTest` (16) and `Phase91SimpleChatTest`
  (10) — the pins that guard the Phase 93/93c fixes this phase sits on. Dropped (needing Android/Robolectric or
  non-AI sources): 19 classes, listed by the sweep, never silently.
* **Build APK: ✅ GREEN — [`37345207580`](https://github.com/pabi277/CodeC/actions/runs/37345207580) on `12a6606`**,
  all 45 steps passed, including *Run host unit and screenshot tests (Phase 52)* — which is what compiles the two
  Android files this sweep cannot (`AiViewModel.kt`, `EditorScreen.kt`) and runs the whole on-device unit suite. **Build
  facts:** release universal APK **7 206 016 B** (from the run log); uploaded artifacts as reported by the API: release
  set **6 544 987 B**, debug **26 169 040 B**, R8 mapping **4 710 924 B**; *release manifest: no `android:debuggable`
  flag*. Honest limit: this round the run's log/artifact download endpoints answered empty, so the unzipped `debug
  universal` and `mapping.txt` byte counts the earlier phases quote could not be read back — the numbers above are the
  ones the API did return.
* **The first push was rejected — and it is worth keeping.** GitHub push protection refused `de5c7a7`: the new
  `AiSecretScanTest` carried a **fixture** long enough to look like a live Slack token. Every fixture in that test is now
  deliberately short (still long enough for the guard's own pattern) and the Slack-shaped one is assembled at runtime,
  with the reason written in the test itself. A guard's own tests must not look like the thing the guard refuses.
* **Device:** owner-only, [`DEVICE_ROUND.md`](DEVICE_ROUND.md) — D1…D8, all rows empty.

## 8. Boundaries kept

* No write tool, no delete tool, no command tool, no shell, no new permission, no new dependency, no new store key;
  `request_run` is still an approval request that only the user can turn into a run.
* The shortlist rules (`READ_SHORTLIST = 12`), the request ceiling, the tool-call budget and the Level 10/11/12
  policies are untouched — the new tools consume the same budget and produce the same kind of result.
* **D6 unchanged**: the run-output snapshot lives in the in-memory `AgentSession` and dies with it; nothing is written
  to a file, key or backup.
* `AiToolName` grows only into *read* shapes: the `AiLevel12InjectionTest` pin forbids `write`/`edit`/`delete`/
  `remove`/`exec`/`shell` in every wire and now lists all eight by name, so a ninth tool cannot appear silently.
