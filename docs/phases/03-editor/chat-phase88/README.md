# Phase 88 — AI Level 11: agent phone presentation

> **Status:** ✅ **IMPLEMENTED and CI-GREEN.** The owner commanded the implementation in chat
> (*"Complete level 11"*, 2026-10-03). Parts 88.1–88.5 are in the tree on `arena/01a101db-codec` @ `88f0186`;
> see [*Implementation record*](#implementation-record-2026-10-03). Build APK run [`37132310296`](https://github.com/pabi277/CodeC/actions/runs/37132310296) on `88f0186` is
> **success on the first round**: host unit and screenshot tests, including the 4 Robolectric link-dialog
> tests, plus debug and release assembly, APK checks and artifact uploads. The release APK is **7 177 184 B**,
> +22 756 B (+0.32 %) against `c3771c5`. **1 878 host tests pass, 0 fail** on the kotlinc 2.2.10 / JRE
> harness (`rule.md` §9). **No PR and no merge** without a further explicit command.
> *Brief history:* the owner first authorized Level 11 in chat on 2026-10-03 **for a brief only**
> (commit `0860ca3`, Build APK run `37128474253` green).
> **Owner decisions (2026-10-03, answered in chat):** the **full Level 11 in one phase** — Markdown
> answers, one truthful compact progress line, activity rows that show the full result on tap, request
> disclosure kept collapsed and **never removed** — in about 4–5 parts · **links:** `https` links are
> tappable **only behind a confirm dialog that shows the full URL**; `javascript:`, `data:` and plain
> `http` links stay inert · **read-window default stays 400**, which closes Level 10's *Open decision 1*.
> **Defaults the agent announced and the owner did not object to:** a **Copy** button on every fenced
> code block — copy only, never insert into the editor (an insert would be a new write path, **S6**) ·
> Markdown formatting renders **live while streaming, throttled**.
> **Baseline:** `main` @ `c3771c58007f2f7ad4af6a26437dc19ef3819a3f` — Phase 87 / Level 10 merged by
> [PR #112](https://github.com/pabi277/CodeC/pull/112); post-merge Build APK run
> [`37122702615`](https://github.com/pabi277/CodeC/actions/runs/37122702615) is green on that commit.
> The session branch `arena/01a101db-codec` @ `8062f0c` adds docs only (the carried Phase 87 merge
> record); its Build APK run [`37125187024`](https://github.com/pabi277/CodeC/actions/runs/37125187024)
> is green. Every `file:line` below was read on that tree, whose production code is identical to
> `c3771c5`. Formal device acceptance remains **POSTPONED** to Level 12.
> **Level 11 spec:** [`11_AGENT_PHONE_PRESENTATION.md`](../../../roadmaps/ai-integration/11_AGENT_PHONE_PRESENTATION.md) ·
> **shared rules:** [`00_AGENTIC_MAP_AND_SECURITY_RULES.md`](../../../roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md)

## Owner row (verbatim)

> *"add a step wise ai map update for full agentic vive with stronger security rules but more
> options to use in the app for better optimization"*

and the need the Level 11 spec quotes for this level:

> *"think I don't have much code knowledge."*

A person without much code knowledge needs **headings, lists and code that look like headings, lists
and code**, one plain sentence that says what the agent is doing, and an activity log that stays out of
the way until they ask for it.

## The rules that govern the whole phase

Level 11 is **presentation only**. It changes how text is drawn, never what is sent, read, written or run.

- **S3** — model output is untrusted data. No HTML is interpreted, no script runs, no remote image is
  fetched, and nothing on the network happens without the user's explicit second tap.
- **S8 / D4** — every request stays disclosed. Timeline disclosure may be **collapsed, never removed**;
  the pre-Send preview (`AiChatSheet.kt:365`) stays whole.
- **S1** — expanding or collapsing any row must not change what the model receives. `renderStep`
  keeps reading `modelResult` (`AiAgentLoop.kt:537`), never a preview.
- **S6** — `ui/ai/` keeps zero direct project writes and zero command execution. Code blocks **copy**;
  they never insert, apply or run.
- **Nothing in the budget moves.** `MAX_TURNS` 12 (`AiAgentLoop.kt:29`), `MAX_TOOL_CALLS` 24 (`:32`),
  `MAX_RUNS` 2 (`:35`), `MAX_BATCH_READS` 8 (`AiTools.kt:237`), `MAX_RESULT_CHARS` 8 000 (`:240`),
  `MAX_READ_CHARS` 24 000; exactly three `client.stream(` sites (`AiViewModel.kt:1117, 1540, 1924`);
  no new dependency, Android permission, endpoint, DataStore key or default-model change.

## First move: evidence, not code

### Defect 9 — answers are plain text (confirmed)

`AiParts.kt:55-58`: `Answer` is `SelectionContainer { Text(text, bodyMedium) }`. Markdown arrives and
is shown as raw `#`, `**` and backticks. It is called from six sites in `AiChatSheet.kt`:

| Site | What it shows | Phase 88 |
|---|---|---|
| `:373` | the streaming answer | Markdown, throttled (88.3) |
| `:393` | an edit proposal's prose | Markdown |
| `:407` | an invalid proposal's prose | Markdown |
| `:412` | the finished answer | Markdown |
| `:784` | a reviewer's `AiReviewVerdict.Text` | Markdown |
| `:787` | a reviewer's `AiReviewVerdict.MarkupShownAsText` — commented *"Shown exactly as it arrived"* | **stays verbatim** |

`:787` is deliberate (`AiLevel10Policies.kt:190-224`): reviewer output that contains tool or edit
markup is displayed, never parsed into a call. Formatting it would change what the user sees, so it
gets an explicit verbatim composable rather than silently becoming Markdown.

### Streaming republishes the whole answer on every event

`AiHttpStream.kt:87` calls `onText(accumulator.text)`, the **entire** text so far, on every event, and
`AiViewModel.kt:1113-1114` copies it into `state.answer`. Today that costs one `Text` relayout. A
naive Markdown `Answer` would **re-parse the whole answer on every chunk**, which is quadratic over a
long reply on the main thread. Hence the "throttled" default: 88.3 re-parses at most once per interval,
**off the main thread**, and always once more on the final text.

### `MarkdownPreview` is the discipline, not the code, and not the URL policy

`ui/utils/MarkdownPreview.kt` (345 lines, Phase 68.1) renders a `.md` file for Run ▶. It is pure and
host-tested (`MarkdownPreviewTest`, 20 cases), but its output is an **HTML string** (`toHtml`, `:29`),
and every helper is `private` and string-to-HTML (`escape :203`, `inline :210`, `safeUrl :249`, the
block recognizers `:266-313`). What carries over is the **discipline**: a hand-written subset, no
dependency, hostile input treated as text, and URLs judged by an allowlist.

Its URL allowlist does **not** carry over. `safeUrl` (`:249-262`) admits `http:`, `https:`, `mailto:`,
`file:`, `data:image/`, `#` and scheme-less relative paths, because a local README preview legitimately
needs them. Model output needs the opposite: one scheme, behind a confirm. **`safeUrl` must never be
reused as the AI link filter.** It is the same trap as reusing `ProjectSearch.isSearchable` as the AI
file filter. Phase 88 does not touch `MarkdownPreview.kt` at all.

### Compose already has the link primitive

`gradle/libs.versions.toml:15` pins Compose BOM `2024.12.01`, which resolves foundation, ui and ui-text
**1.7.6** (source 4). `LinkAnnotation`, with a `LinkInteractionListener`, is stable since Compose
Foundation 1.7.0, which also deprecated `ClickableText` (sources 2–3). With a listener attached, a tap
calls the listener instead of opening the browser (source 1). That is exactly where the confirm dialog
goes. The app's two older `ClickableText` uses (`OutputPanelView.kt:563,592`,
`GitCredentialsDialog.kt:259`) are not a pattern to copy. `ui/ai/` has exactly **one** `openUri(`
today: `AiParts.kt:67`, inside `Links()`, which opens fixed provider-key pages. There is no
`AlertDialog` in `ui/ai/` yet.

### Two progress lines, two meanings of "steps"

| Where | Builder | What "steps" counts |
|---|---|---|
| activity card, `AiChatSheet.kt:610-617` | `AiCopy.agentUsageLine` (`AiCopy.kt:213-233`): *"T of 12 steps · R of 24 reads · …"* | **model turns** |
| bottom bar while streaming, `AiChatSheet.kt:968-970` | `AiCopy.agentWorkingLine` (`AiCopy.kt:236-237`), fed `agentSteps.count { it.kind == TOOL }`: *"Working on it — N steps done…"* | **tool rows** |

After two model turns that batch eight reads, one screen says *"8 steps done"* next to *"2 of 12 steps"*.
Neither line names the stage (waiting for the model, reading files, waiting for your run decision,
stopped). **One builder, one meaning, one line** is part 88.4.

### Activity rows: the full result exists but cannot be reached

Each row draws `step.detail` with `maxLines = 12` and an ellipsis (`AiChatSheet.kt:670-678`). `detail`
is a preview by design (**S1**): `AiAgentLimits.timelineDetail` for TOOL rows (`AiViewModel.kt:1336`,
`AiAgentLoop.kt:78`) and `.take(MAX_STEP_DETAIL_CHARS)` (1 200, `AiAgentLoop.kt:66`) for ANSWER rows
(`AiViewModel.kt:1164`). The full text the model received lives in `modelResult`, which is set **once**
per step (`AiViewModel.kt:970, 985, 1005, 1165, 1179, 1261, 1337`) and never mutated afterwards. It is
read in exactly two places: `renderStep` packs `modelResult.take(MAX_RESULT_CHARS)`
(`AiAgentLoop.kt:537`), and `evictionPointer` reads its first line (`:516`).

Rows that carry a full result: **TOOL, ANSWER, DENIED, RUN_RESULT**. Rows that carry none, and stay as
they are: REQUEST (it has the request disclosure instead), TASK, RUN_DECISION, STOPPED.

One truthfulness constraint: Level 8's **restorable eviction** (`AiAgentLoop.kt:509-530`) can replace an
older result with a one-line pointer in *later* requests. So the tap view is labelled as the result **as
it first entered a request**, not "what the model sees now". The REQUEST rows remain the per-turn record
of exactly what was sent.

### Disclosure is already collapsed (Phase 87.5); the spec's citation is stale

The spec cites `AiChatSheet.kt:584` as rendering the full request on every REQUEST row. Phase 87.5
fixed that, and the code has moved. On this tree, `openRequests` (`:624`) and the collapsed row
(`:638-669`) show a one-line recipient-and-size summary, and a tap reveals the identical
`SentText(instruction + "\n\n" + user)`. Level 11 keeps this as it is and adds regression pins. It does
not redo it.

### Corrections to the Level 11 spec (from reading the tree, not the plan)

| The spec says | The tree shows | Consequence |
|---|---|---|
| `AiChatSheet.kt:584` floods the timeline | Fixed by 87.5: `:624`, `:638-669` collapsed, one tap to the full text | 88.5 pins it instead of rebuilding it |
| `MarkdownPreview` *"sanitises URLs against `javascript:`, `vbscript:` and non-image `data:`"* — implying reuse | Its allowlist is a file-preview policy (`:249-262`) that admits `http:`, `file:`, `mailto:` and relative paths | A separate, stricter `AiLinkPolicy` (88.2) |
| *"Existing Roborazzi goldens updated deliberately"* | Roborazzi is wired (`app/build.gradle.kts:8, 280-282`, verify mode on CI `:297-302`), but **zero** tests call `captureRoboImage` and **zero** reference images are tracked | The check is moot as written — see *Open decision 1* |
| *"APK size delta recorded against the Level 6 baseline"* | Levels 7–10 already grew the release APK 7 117 444 B → 7 154 428 B | Record the delta against **both** baselines; only the second isolates Level 11 |

## Design shape

Pure core first, a thin Compose edge last: the house pattern (`HOW_TO_CREATE_A_PHASE.md` step 6).

```
AiMarkdown.kt          (pure, 88.1)  text ─► List<AiMdBlock>   headings · paragraphs · lists · quotes ·
                                                               code · tables · rule; inline spans
AiLevel11Policies.kt   (pure)        AiLinkPolicy      (88.2)  target ─► Openable(url, host) | Inert(reason)
                                     AiProgressPolicy  (88.4)  state ─► stage + one line + where it shows
                                     AiResultRowPolicy (88.5)  step ─► has full result? / exact text / starts open?
AiMarkdownView.kt      (Compose, 88.3)  blocks ─► Text(AnnotatedString) · code box + Copy · link confirm dialog
AiParts.kt / AiChatSheet.kt (edge)      Answer(...) switches renderer · VerbatimText for :787 ·
                                        progress line · result rows
```

Nothing here reaches `AiViewModel`'s request building, the packer, the tool runner, the key store or the
network. A wiring pin enforces that boundary (88.5).

## Parts

| Part | Title | What it lands |
|---|---|---|
| [88.1](PART_88_1_MARKDOWN_MODEL.md) | Pure Markdown model | `AiMarkdown.parse`: block and inline model; hostile input stays text; an unclosed fence renders as code while streaming. **No UI change.** |
| [88.2](PART_88_2_LINK_POLICY.md) | Link policy | `AiLinkPolicy`: `https` only, with a host and no userinfo or hidden characters; everything else inert. **No UI change.** |
| [88.3](PART_88_3_ANSWER_SURFACE.md) | Answer surface | `AiMarkdownView`: formatted, selectable answer; code blocks with horizontal scroll and Copy; link confirm dialog; throttled off-main-thread parse; `VerbatimText` for `:787`. |
| [88.4](PART_88_4_PROGRESS_LINE.md) | One truthful progress line | `AiProgressPolicy` replaces `agentWorkingLine` and the card's usage line: one builder, "steps" means turns, the stage is named, exactly one line on screen. |
| [88.5](PART_88_5_ACTIVITY_ROWS_AND_MEASUREMENT.md) | Activity rows, disclosure pins, measurement | Rows with a full result collapse to one line and open to the exact `modelResult` text; request disclosure stays collapsed-not-removed; APK delta and inventory recorded. |

Order: **88.1 → 88.2 → 88.3** (88.3 renders what 88.1 parses and links only what 88.2 allows), then
88.4 and 88.5 in either order. 88.4 and 88.5 do not depend on 88.1–88.3.

## Open decision 1 — screenshot goldens (flagged, not silently chosen)

The spec asks for existing Roborazzi goldens to be updated deliberately. There are none to update.
Adding the **first** AI screenshot test needs a record-mode run, and CI never records
(`app/build.gradle.kts:297-302` forces verify mode when `CI=true`). This sandbox cannot run Gradle or
Robolectric.

**Taken as the agent's decision, not the owner's:** Phase 88 adds **no** screenshot goldens. The visual
check goes to the Level 12 device round, which is already the owner's chosen acceptance gate. The
behaviour that matters (a link tap opens a dialog, and nothing opens without the second tap) is covered
by a Robolectric Compose test that runs on CI (88.3). If the owner wants goldens, say *"add screenshot
goldens"*; that needs a one-off record run outside the current workflow.

## Tests (plan)

| Kind | File | Cases | Executor |
|---|---|---|---|
| Pure | `AiMarkdownTest` | ~26 — each construct; nesting cap; unclosed fence while streaming; hostile corpus stays text; images give alt text only; pathological input finishes promptly | host harness + CI |
| Pure | `AiLinkPolicyTest` | ~20 — the allow and deny tables in 88.2, case-insensitive schemes, userinfo, hidden characters, length cap | host harness + CI |
| Pure | `AiProgressPolicyTest` | ~12 — every stage, clamping against the task's caps, refused/reused only when non-zero, placement shows exactly one line | host harness + CI |
| Pure | `AiResultRowPolicyTest` | ~6 — which kinds carry a full result; the exact text equals what `renderStep` packs; option-driven initial state | host harness + CI |
| Wiring | `AiLevel11WiringTest` | ~14 — source pins listed in each part | host harness + CI |
| Robolectric Compose | `AiAnswerLinkDialogTest` | 4 — https tap → dialog with the full URL and zero opens; confirm → one open; `javascript:` and `http:` → no dialog, zero opens | **CI only** |
| Regression | `AiLevel10WiringTest`, `AiSurfaceWiringTest`, `AiLevel7WiringTest`, `AiAgentLoopTest`, `MarkdownPreviewTest` | unchanged and green | CI |

**Inventory on this checkout:** 326 Kotlin test files / 3 120 `@Test`; 46 `Ai*.kt` test files (45
classes plus `AiLevel6FixtureSupport`) / 538 AI `@Test`; 4 Robolectric Compose test files. Counts are a
plan, not a result. Nothing has been run for this phase.

## Implementation record (2026-10-03)

Built on `arena/01a101db-codec` from the brief commit `0860ca3` after the owner's *"Complete level 11"*.
Every `file:line` above is the brief's reading of the pre-implementation tree; the record below names
files, not lines, because the edits moved them.

### What landed

| Part | Files | What |
|---|---|---|
| 88.1 | `ui/ai/AiMarkdown.kt` (new, pure) | `AiMarkdown.parse` / `inlines` / `visibleText` / `inlineText`: the block and inline model as designed. No regex at all, no Android or Compose import. |
| 88.2 | `ui/ai/AiLevel11Policies.kt` (new, pure) | `AiLinkPolicy.classify` → `AiLink.Openable(url, host)` / `AiLink.Inert(AiLinkRefusal)`, `java.net.URI` only. |
| 88.3 | `ui/ai/AiMarkdownView.kt` (new), `AiParts.kt`, `AiChatSheet.kt` | `AiMarkdownAnswer`: throttled off-main-thread parse while streaming (`snapshotFlow` → `conflate` → `Dispatchers.Default` → `delay(STREAM_REPARSE_MS)`), `remember(text)` at DONE; code box with **Copy** inside `DisableSelection`; monospace tables; the link confirm dialog (the second `openUri`). `Answer(text, streaming = false)` delegates to it; `VerbatimText` is the old `Answer` body and draws `MarkupShownAsText`. |
| 88.4 | `AiLevel11Policies.kt`, `AiCopy.kt`, `AiChatSheet.kt`, `ui/ai/AiAgentState.kt` (new) | `AiProgressPolicy.stage` / `placement` / `line`; `agentWorkingLine` deleted; the card and the bar both consult `placement`, so exactly one line is drawn. |
| 88.5 | `AiLevel11Policies.kt`, `AiChatSheet.kt` | `AiResultRowPolicy`; `openResults` beside `openRequests`; a row with a full result is one line until tapped, then `SentText(AiResultRowPolicy.fullResult(step))`. |

Untouched: `AiAgentLoop.kt` (so `renderStep` is byte-identical), `MarkdownPreview.kt`, every build file,
the manifest, the three `client.stream(` sites and every budget constant.

### Deviations from the brief (each deliberate)

1. **Two existing pins amended, intent kept.** The brief planned `AiLevel10WiringTest` and
   `AiLevel7WiringTest` to stay unchanged. One assertion in each pinned the literal call of the *old* card
   counter: `usage.turnCap, usage.readCap` (Phase 87.6) and `usage.refused` (Phase 84). 88.4 moves that
   counter into the one builder, so each assertion now follows it. The sheet hands `state.agentUsage` to
   `AiProgressPolicy.line(`, and the builder passes `turnCap = usage.turnCap`, `readCap = usage.readCap`,
   `refused = usage.refused` and `reused = usage.reused`. `AiProgressPolicyTest` also asserts the
   behaviour (extended caps; refused and reused kept apart). No other assertion in either file changed.
2. **A third `AiChatSheet` touch for 88.4.** The streaming conversation's own countdown line is now drawn
   only for a single-shot ask. An agent task's countdown lives in its one progress line, as 88.4 says
   (*"the countdown does not need a line of its own"*).
3. **`agentUsageLine(…, showRuns: Boolean = true)`.** 88.4 shows *U of 2 runs* only once a run was
   requested or used. The default keeps every other caller, and every existing pin, byte-identical.
4. **`AiPhase` and `AiAgentUsage` moved, unchanged,** from `AiViewModel.kt` to the new pure
   `AiAgentState.kt`, so the progress policy and its test compile on the host harness.
5. **Parser choices the brief left open:**
   - A `__word__` whose whole content is one identifier stays text. CommonMark would bold it; the brief
     promises *"`__init__`-style identifiers stay text"*. `__two words__` is still Strong.
   - A setext underline needs three or more `=` or `-`.
   - List nesting tolerates the two-space indent models write under `1.`.
   - A bare URL is recognized only after the start of a line, whitespace, `*`, `_`, `~` or `(` (GFM), so
     `src="https://…"` inside raw HTML stays text.
   - Empty link text shows the target, so a link is never invisible.
6. **Link-check order nuance.** An empty authority (`https://`, `https:///path`, `https:x`) is `NO_HOST`
   before `java.net.URI` is consulted. `URI("https://")` throws, and the brief's own examples name those
   cases `NO_HOST`, not `MALFORMED`.

### Tests (result)

| File | `@Test` | Host harness | CI |
|---|---:|---|---|
| `AiMarkdownTest` | 27 | 27 / 27 pass | pass |
| `AiLinkPolicyTest` | 17 | 17 / 17 pass | pass |
| `AiProgressPolicyTest` | 14 | 14 / 14 pass | pass |
| `AiResultRowPolicyTest` | 7 | 7 / 7 pass | pass |
| `AiLevel11WiringTest` | 15 | 15 / 15 pass | pass |
| `AiAnswerLinkDialogTest` (Robolectric Compose) | 4 | cannot run here | pass |

**Harness run (kotlinc 2.2.10 on a JRE, `rule.md` §9):**
- 151 production and 202 test files compile with **nothing dropped**: 199 classes, **1 878 tests, 0 failures**.
- The same harness on an untouched snapshot of `0860ca3` gives 1 798 / 0. The +80 are the five new
  pure and wiring classes; every regression test listed in the plan is green.

**Harness reach widened.** `AiCopy.kt` had never compiled on the harness. Its `AiUndoSummary` import
chains through `AiEditApplier` → `GitDiscardEditors` → `GitDiscardPolicy` → `GitFileChange`, which sits in
the JGit-bound `GitManager.kt`. The harness now copies the exact `GitFileChange` and `GitFileState`
declarations into a harness-only stub, which is never committed. That brings `AiCopy`, `AiLevel10CopyTest`,
`AiLevel7CorrectnessTest` and `AiPolicyTest` onto the harness.

**Inventory delta (against this README's baseline):**

| | Baseline | Now | Delta |
|---|---:|---:|---:|
| Kotlin test files | 326 | 332 | +6 |
| `@Test` | 3 120 | 3 204 | +84 |
| `Ai*.kt` test files | 46 | 52 | +6 |
| AI `@Test` | 538 | 622 | +84 |
| Robolectric Compose test files | 4 | 5 | +1 |

### CI record

**Round 1, green:** Build APK run [`37132310296`](https://github.com/pabi277/CodeC/actions/runs/37132310296) on `88f0186` (job `111229685179`). Every step is a **success** on
the first round: host unit and screenshot tests (`:app:testDebugUnitTest`), debug assembly, measure-only
release, signed release, the APK-set check and the size report.
- The test step runs the whole unit-test task, which has no test filter, and fails the job on any failing
  test. Its success therefore covers every class, including the four Robolectric `AiAnswerLinkDialogTest`
  cases, the two amended pins and every regression class.
- The raw job log could not be downloaded from this sandbox (the log host answered `EOF` twice), so no
  executed-test count is quoted for CI.

### Measurement (88.5)

Taken from the run's *APK size* annotations:

| Build | Release APK | Debug APK |
|---|---:|---:|
| Level 6 baseline (`main` @ `7419566`, run `37047037300`) | 7 117 444 B | 26 869 612 B |
| Current `main` (`c3771c5`, run `37122702615`) | 7 154 428 B | 26 988 060 B |
| **Phase 88 implementation (`88f0186`, run `37132310296`)** | **7 177 184 B** | **27 054 816 B** |
| Delta vs `c3771c5` (Level 11 alone) | **+22 756 B (+0.32 %)** | +66 756 B |
| Delta vs Level 6 (Levels 7–11) | +59 740 B (+0.84 %) | +185 204 B |

The R8 mapping file is 71 186 679 B, and the release manifest has no `android:debuggable` flag. No
dependency was added: the growth is the new Kotlin code.

## Exit condition

Ticked items are proven by the host harness or the wiring pins named, and confirmed by Build APK
`37132310296`, which also ran the Robolectric dialog test green. The visual checks go to the Level 12 device round, as the brief planned.

- [x] Headings, lists, block quotes, inline code, fenced code and tables render; raw Markdown syntax is not shown for supported constructs. *(Model: `AiMarkdownTest`. Drawing: `AiMarkdownView`. Visual check: Level 12.)*
- [x] A hostile answer containing `<script>`, `<img onerror=…>` and `javascript:` / `data:` / `http:` links renders as inert text. *(`AiMarkdownTest` hostile corpus; `AiLinkPolicyTest`; `AiAnswerLinkDialogTest` on CI.)*
- [x] No network effect without two taps: an `https` link opens a dialog showing the full URL and host, and only **Open** calls `openUri`. `ui/ai/` has exactly **two** `openUri(` sites. *(Wiring pins; the dialog behaviour runs on CI in `AiAnswerLinkDialogTest`.)*
- [x] No remote image is ever fetched; images render as their alt text. *(`ImageAlt` keeps no target; no image loader in `ui/ai/`.)*
- [x] The answer is selectable and scrolls with the sheet; code blocks scroll horizontally; the answer is never truncated (no `maxLines` in the renderer).
- [x] Code-block **Copy** goes through the existing `copyAnswer`; there is no insert, apply or run path from an answer (**S6**).
- [x] Streaming re-parses at most once per interval, off the main thread, and the final text is parsed exactly once at DONE.
- [x] `MarkupShownAsText` stays verbatim (`VerbatimText`).
- [x] Exactly one progress line is on screen; it names the stage; "steps" means model turns everywhere; counts never exceed the task's caps.
- [x] Rows with a full result collapse to one line and open to exactly `modelResult.take(MAX_RESULT_CHARS)`, the same text `renderStep` packs.
- [x] Request disclosure is still one tap away and still shows the exact two strings sent (**S8/D4**); the pre-Send preview is whole.
- [x] Collapsing or expanding any row does not alter the packed request (**S1**).
- [x] A long beginner explanation renders in clear sections without truncation. *(By construction: headings and lists render, and there is no line or height cap. Device check: Level 12.)*
- [x] Exactly three `client.stream(` sites; no ceiling moved; no new dependency, permission, endpoint, DataStore key or default-model change.
- [x] minSdk 24 respected: `java.io` only; the URL check uses `java.net.URI`, never `android.net.Uri` (the core stays host-testable).
- [x] APK size delta recorded against both baselines (88.5): release +22 756 B against `c3771c5` and +59 740 B against Level 6 (run `37132310296`).

## Deferred / rejected with reasons

- **A Markdown library** (`jeziellago/compose-markdown`, `mikepenz/multiplatform-markdown-renderer`,
  `halilozercan/compose-richtext`): rejected by the spec. They add APK weight, and the first advertises
  HTML and remote images, a weaker posture than the tree's.
- **HTML in a WebView per bubble:** rejected by the spec (one WebView per message, plus scroll and memory
  problems on a phone).
- **`AnnotatedString.fromHtml`:** rejected. It turns model-written `<a href>` into live links, which
  bypasses `AiLinkPolicy` and the confirm dialog (**S3**).
- **`LinkAnnotation.Url` without a listener:** rejected. It opens through the platform `UriHandler` on
  the first tap, skipping the confirm dialog.
- **`ClickableText`:** rejected; deprecated since Compose Foundation 1.7.0.
- **Opening `http:` links:** rejected by the owner (2026-10-03). They stay inert, and their target stays
  visible as plain text so the user can copy it.
- **An in-app browser or Custom Tabs:** rejected. It needs a new dependency (`androidx.browser`) and an
  in-app network surface; the system browser via `LocalUriHandler` is the existing precedent
  (`AiParts.kt:67`).
- **Remote images:** rejected (**S3**); alt text only.
- **Insert, apply or run from a code block:** rejected (**S6**; the owner's default).
- **Rendering tool results as Markdown:** rejected. They are data (**S3**) and must read exactly as they
  entered the request.
- **Folding the whole activity card:** rejected. Request disclosure would become two taps away, and the
  spec's acceptance says one.
- **Moving the activity card below the answer at DONE:** rejected for now. It causes a layout jump at
  the moment the answer completes; collapsing rows achieves the un-burying without it.
- **Syntax highlighting in code blocks:** deferred. It needs a tokenizer and is not required by Level 11's
  acceptance.
- **A lazy list for very long answers:** deferred. Measure on the device first (Level 12).
- **Hiding a trailing unmatched `**` while streaming:** deferred. It is cosmetic; CommonMark leaves
  unmatched delimiters as text, and they resolve when the closing marker arrives.
- **Changing `MarkdownPreview.kt`:** out of scope. Phase 88 does not touch it.

## Sources (record)

1. Android Developers, *Enable user interactions* — `SelectionContainer`, `DisableSelection`,
   `LinkAnnotation`, and a custom listener replacing the default open —
   https://developer.android.com/develop/ui/compose/text/user-interactions (fetched 2026-10-03).
2. Compose Foundation release notes — 1.7.0: *"ClickableText has been deprecated … use BasicText with
   the new LinkAnnotation"*; 1.7.6 released 2024-12-11 —
   https://developer.android.com/jetpack/androidx/releases/compose-foundation (read via search 2026-10-03).
3. Compose UI release notes — 1.7.0-alpha07: `LinkAnnotation` takes a `LinkInteractionListener`;
   `AnnotatedString.fromHtml` builds `LinkAnnotation.Url` from `<a>` tags —
   https://developer.android.com/jetpack/androidx/releases/compose-ui (read via search 2026-10-03).
4. Google Maven, `compose-bom-2024.12.01.pom` — foundation, ui and ui-text 1.7.6; material3 1.3.1 —
   https://dl.google.com/android/maven2/androidx/compose/compose-bom/2024.12.01/compose-bom-2024.12.01.pom
   (fetched 2026-10-03).
5. CommonMark spec, *Fenced code blocks* — *"If the end of the containing block (or document) is reached
   and no closing code fence has been found, the code block contains all of the lines after the opening
   code fence"* — https://spec.commonmark.org/ (rule present since 0.12; read via search 2026-10-03).
6. GitHub Flavored Markdown spec, *Autolinks (extension)* — https://github.github.com/gfm/#autolinks-extension-
   (cited for the bare-URL rule; not fetched this session).
7. RFC 3986 §3.1 — URI schemes are case-insensitive — https://www.rfc-editor.org/rfc/rfc3986#section-3.1
   (not re-fetched this session).
8. The Level 11 spec's references (Anthropic, *Building effective agents* and *Effective context
   engineering*; SWE-agent) — cited from the spec; not re-fetched.
9. Repository: every `file:line` above, read 2026-10-03 on `arena/01a101db-codec` @ `8062f0c`
   (production code identical to `c3771c5`). APK bytes come from the *APK size* annotations of Build APK
   runs `37047037300` (Level 6 baseline) and `37122702615` (current `main`).

## Gate

The owner commanded the implementation (*"Complete level 11"*, 2026-10-03). **No PR, no merge and no push
to `main` without a further explicit command** (`rule.md` §3). **Levels 12–14 remain unauthorized.** Formal
device acceptance remains **POSTPONED** to Level 12 and is not claimed here.
