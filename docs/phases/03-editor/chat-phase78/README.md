# Phase 78 — AI Level 2: whole-project context (read-only)

> **Status: 🚧 IMPLEMENTED 2026-10-01 on `arena/01a0f5d2-codec`. CI ✅ GREEN
> `36822370600` on `8abd49e` (first round, zero error annotations, release APK
> 7,055,936 B = +7,980 B / +0.11 % over the merged Phase 77 tip). Device round
> ⏳ NOT RUN — [`DEVICE_ROUND.md`](DEVICE_ROUND.md). NOT merged (`rule.md` §3).**
>
> The owner authorized **Level 2** of the AI roadmap as the next phase in this
> chat (2026-10-01) by selecting *"Authorize AI Level 2 (whole-project
> context)"* — the explicit word `00_LEVEL0_DECISION_RECORD.md:125` and
> `AI_INTEGRATION_ROADMAP.md:5` require — and then started it with
> ***"Complete the lavel 2"***.
>
> **The six scope questions in §5 were NOT answered by the owner.** He said
> "complete it", so each was taken at its **most conservative** option and is
> recorded in §5 and in the part docs as **the agent's decision, not the
> owner's** (the `PHASE_73_1_GIT.md` precedent). Any one of them can be changed
> without re-litigating the others.
>
> Parts: [78.1 the file policy](PART_78_1_FILE_POLICY.md) ·
> [78.2 the walk and the third source](PART_78_2_WALK_AND_CONTEXT.md) ·
> [78.3 the surface](PART_78_3_SURFACE.md).
>
> Baseline for every claim below: **`main` @ `5abe768`** (PR #103, Phase 77),
> read **2026-10-01** in the session sandbox. Every repository claim is a
> `file:line` read from that tree; every external claim names its source and
> whether it was fetched this session (none were — no network beyond PyPI/npm).
> Build on [Phase 76](../chat-phase76/README.md) (Level 1) and
> [Phase 77](../chat-phase77/README.md) (the phone AI UI).

---

## 1. The owner row

The owner's product direction for the whole AI roadmap (`AI_INTEGRATION_ROADMAP.md:9`):
*"Build toward an agent that works across the user's selected whole CodeC
project."* Level 2 is the first step of that (`AI_INTEGRATION_ROADMAP.md:29`,
level table row 2): *"Answer questions across the selected project using
relevant files, with visible context"* — depends on *"Level 1;
root/exclusion/dirty-buffer contract"*, complexity/risk *"Medium; data
disclosure/context limits"*.

The Level 2 spec is [`02_WHOLE_PROJECT_CONTEXT.md`](../../../roadmaps/ai-integration/02_WHOLE_PROJECT_CONTEXT.md).
This brief turns it into CodeC parts; it does not restate it.

**Not in this phase, by the spec's own last line** (`02_WHOLE_PROJECT_CONTEXT.md:44`):
*"Deliberately deferred — Applying edits, shell access, unattended indexing,
embeddings/vector storage, and parallel agents."* Those are Levels 3, 4, 6, 7.

---

## 2. Evidence on `main` @ `5abe768` (2026-10-01)

### 2.1 What Level 1 sends today — and why it cannot answer a project question

| Read | Fact |
|---|---|
| `ui/ai/AiContext.kt:13` | `enum class AiSource { SELECTION, RUN_OUTPUT }` — **exactly two** sources exist. There is no third. |
| `ui/ai/AiContext.kt:5-11` | The file's own contract: *"Level 1 has exactly two sources (D1/D5): the selected code of the active project tab, or the latest run output of a failed run. Nothing else — no other files, no project tree, no history."* |
| `ui/ai/AiPolicy.kt:23` | `MAX_CONTEXT_CHARS = 12_000` — the hard ceiling on one request's payload. |
| `ui/ai/AiPolicy.kt:26,29,32,40` | `MAX_QUESTION_CHARS = 1_000`; `MAX_OUTPUT_LINES = 60`; `MAX_REPLY_CHARS = 24_000`; `MAX_OUTPUT_TOKENS = 8_192`. |
| `ui/ai/AiContext.kt:73-75` | `fromSelection` **refuses** a selection over `MAX_CONTEXT_CHARS` — it never silently cuts (*"half a function explained as if it were whole is worse than 'select less'"*). |
| `ui/ai/AiContext.kt:112-124` | `fromRunOutput` **does** truncate, and sets `AiPrompt.truncated = true` so the preview can say so. Two different policies, deliberately. |
| `ui/ai/AiContext.kt:35` | `AiPrompt.sentChars = systemInstruction.length + userText.length` — one number that describes exactly what leaves the phone. |
| `ui/ai/AiContext.kt:38-49` | `AiContextProblem` has four values (`NO_SELECTION`, `SELECTION_TOO_LONG`, `NO_FAILED_RUN`, `QUESTION_TOO_LONG`), each mapping to one UI line. |

### 2.2 The invariant that makes Level 2 cheap — and safe

| Read | Fact |
|---|---|
| `ui/ai/GeminiRequest.kt:45-49` | `body(prompt)` is built from `prompt.systemInstruction` and `prompt.userText` — **the same two strings the preview renders**. |
| `ui/ai/AiViewModel.kt:120,140,167` | `preview(result)` / `send()` / `stop()` — Send is the only road to the network (D4), unchanged since 76. |
| `ui/ai/GeminiRequest.kt:61` | `append("\"store\":false")` on every body. |

**Consequence.** Level 2 does **not** need to touch the request path, the key
path, the streaming path or the preview mechanism. If whole-project context is
assembled *inside* `AiPrompt.userText`, then "what the user saw in the
preview" and "what was sent" stay one value for free. That is the design
anchor of this phase: **widen the prompt, not the pipeline.**

### 2.3 The crawl root, and a ready-made containment check

| Read | Fact |
|---|---|
| `ui/projects/ProjectManager.kt:17-19` | `projectsRoot() = File(appContext.filesDir, "CodeC/projects")`, `mkdirs()` on demand. |
| `ui/projects/ProjectManager.kt:33-38` | `project(name)` sanitizes the name, then requires `root.canonicalFile.parentFile?.path == projectsRoot().canonicalFile.path` — **a canonical root-containment check already exists and is already used**. Level 2 should reuse this shape rather than invent one. |
| `ui/projects/ProjectTransfer.kt:114-115` | The `FileManager` also lists `getExternalFilesDir(null)/CodeC/projects` and a legacy location — so "the project root" is **not** a single literal path. Level 2 must take the root from the open project's own `ProjectInfo.root`, never construct one. |
| `MainActivity.kt:498-502` | `projectRoot` falls back to `filesDir` when the projects dir is missing. |

### 2.4 The exclusion problem — the one real hazard in this phase

The Level 2 spec warns: *"Do not reuse `ProjectSearch.isSearchable` as the AI
filter unchanged: it deliberately includes `.env`, `.env.*` and `.npmrc`."*
**Verified true on this tree:**

| Read | Fact |
|---|---|
| `ui/editor/ProjectSearch.kt:159-166` | `isSearchable(name)` = `ProjectFilesPolicy.visible(name, false)` && (`usefulConfig(name)` \|\| extension ∈ `TEXT_EXTENSIONS`). |
| `ui/editor/ProjectFilesPolicy.kt:9-12` | `configNames` contains **`.env`** and **`.npmrc`** (plus `.gitignore`, `.gitattributes`, `.editorconfig`, `.prettierrc`, `.eslintrc`, `dockerfile`, `makefile`, `license`, `gemfile`). |
| `ui/editor/ProjectFilesPolicy.kt:13` | `usefulConfig(name)` also returns true for **anything starting `.env.`** — so `.env.local`, `.env.production` all pass. |
| `ui/editor/ProjectFilesPolicy.kt:16` | `visible(name, false)` lets a dotfile through when `usefulConfig(name)` is true. |
| `ui/editor/ProjectSearch.kt:169-175` | `TEXT_EXTENSIONS` itself contains **`"env"`**. |

So `.env`, `.env.production` and `.npmrc` are reachable by the existing search
filter on three independent routes. **Reusing `isSearchable` for AI context
would send credentials to a cloud provider.** A separate, narrower AI file
filter is required — this is the phase's central correctness requirement, not
a nice-to-have.

### 2.5 What already helps, and can be reused

| Read | Fact |
|---|---|
| `ui/editor/ProjectFilesPolicy.kt:5-8` | `excludedDirectories` = `.git`, `.hg`, `.svn`, `.gradle`, `.idea`, `.cache`, `node_modules`, `build`, `bin`, `dist`, `__pycache__`, `.venv`, `venv`, `.next` — a sane build-output set already exists. |
| `ui/editor/ProjectSearch.kt:93-110` | The existing walker already: sorts children, applies the directory-visibility rule, **skips symlinks** (`isSymlink(child)` at :102 — matches the spec's "do not follow symlinks outside the root"), enforces a hit `limit`, and calls `checkActive()` for cancellation. This is the shape an AI file enumerator should copy. |
| `ui/editor/ProjectSearch.kt:178-181` | `looksBinary(text)` — NUL in the first 8 KB. Reusable for "is this a text file". |
| `ui/ai/AiKeyStore.kt:181-186` | The AI properties file holds exactly six keys (`model`, `terms_version`, `terms_accepted_at`, `bubble_pos`, `bubble_show`, `sheet_with_output`). **No index storage exists**, and D6 forbids adding a persistent index without an explicit owner policy. |

### 2.6 What does **not** exist and would be new code

| Read | Fact |
|---|---|
| *(grep)* `object SymbolIndex` / `fun symbols(` | **No symbol, outline or structure engine** anywhere in `app/src/main/java`. The spec's step 1 ("identify likely relevant files using … symbol/search results") has only *filename*, *language* and *plain-text search* to work with. |
| `ui/projects/RepoHygiene.kt:166-186, 217-220` | There is **no general `.gitignore` glob matcher**. `userWantsTracked(gitignoreLines, pattern)` does line-membership/negation matching against CodeC's *own fixed* pattern table, and `missingLines` de-duplicates against the user's `.gitignore` text. Nothing maps an arbitrary project-relative path to ignored/not-ignored. The spec's *"Respect `.gitignore`"* therefore means **writing a matcher**, not calling one. |
| `ui/ai/AiContext.kt` (whole file) | There is **no dirty-buffer contract** in the AI path: `fromSelection` takes text plus an `unsaved: Boolean` flag from the caller, but nothing today reads other files' buffers. The spec's *"the editor's live buffer may differ from disk"* is unimplemented. |

---

## 3. How the binding laws constrain this phase

From [`00_LEVEL0_DECISION_RECORD.md`](../../../roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md) §1–§2 and `rule.md` §6 / `HOW_TO_CREATE_A_PHASE.md` §4:

- **D1 read-only** — unchanged. Level 2 adds *reading more files*, never
  writing, running or applying. No Apply button. Copy stays the only output
  action (`AiCopy.kt:52`).
- **D4 preview every request** — **this law gets harder, not easier.** A
  selection preview shows the code; a whole-project preview cannot show 12 000
  characters of five files in a phone sheet. The preview must therefore show
  the **path list, per-file line counts and total characters**, and be honest
  that it is a list, not the text. *What the user approves must still equal
  what is sent* — so the preview must enumerate **exactly** the files and
  ranges in the body, and any change to that set must invalidate the preview.
- **D5 projects only** — already enforced by `AiGate`; unchanged. The crawl
  root is the open project's `ProjectInfo.root`, never a constructed path, and
  never another project under `projectsRoot()`.
- **D6 nothing saved** — **the binding constraint on design.** No index, no
  cache, no embeddings, no vector store, no background crawl. Context is
  rebuilt per request from disk. Nothing new is written to
  `ai_settings.properties` without an explicit owner decision (and the current
  six keys are the whole allowance today).
- **O1/O2/O3** — unchanged. Gemini, BYOK, `HttpURLConnection`, `store:false`,
  model editable (`AiModel.DEFAULT = "gemini-3-flash-preview"`,
  `AiPolicy.kt:127`).
- **House laws** — no new dependency, permission, DataStore key or
  Settings-screen control; no `Color(0x…)`; `CodecType.codeFamily` for code
  text (`TypeAdoptionTest`); minSdk 24, so no `InputStream.readNBytes`
  (`GeminiClient.kt:92` records the workaround); every `BackHandler(` is
  root-or-router (`BackHandlerWiringTest`); no motion helper may be named
  `snap(` (`MotionWiringTest`, `TROUBLESHOOTING.md:2143`).
- **`rule.md` §6 last bullet** — do not re-debug Phase 76/77 behaviour that is
  device-passed without a new symptom. Level 2 must be additive to it.

---

## 4. Design direction (proposed, not agreed)

Widen `AiPrompt`, leave the pipeline alone:

1. **A pure AI file policy** — a new pure object (working name
   `AiProjectContext`) that, given a project-relative path list, decides
   include/exclude. It is **narrower than** `ProjectSearch.isSearchable` and
   refuses the §2.4 set by default: `.env`, `.env.*`, `.npmrc`, private keys,
   credential/token stores. Excluded-by-default must be **inspectable** — the
   user sees what was left out and why (spec: *"let users inspect the
   effective list"*).
2. **A bounded, cancellable enumerator** — copies the `ProjectSearch.walk`
   shape (§2.5): sorted children, `excludedDirectories`, no symlinks, hard
   file-count and byte budgets, `checkActive()` cancellation, no UI freeze
   (spec acceptance: *"A large project does not freeze the UI or create an
   unbounded request"*).
3. **Deterministic relevance, no embeddings** — filename + language + the
   existing plain-text search + current editor/run context. The spec is
   explicit that embeddings/vector storage are not prerequisites
   (`02_WHOLE_PROJECT_CONTEXT.md:16`).
4. **An honest budget** — a project map (paths + sizes) plus targeted file
   ranges, and when the project is too large the request is **refused or
   visibly reduced with `truncated = true`**, never silently narrowed (spec:
   *"report when the project is too large rather than silently dropping
   important information"*). This mirrors the existing two-policy split:
   refuse for a selection, truncate-and-label for run output.
5. **A dirty-buffer contract** — per the spec, use the editor buffer for a
   file that is open and dirty, or require a save; **label the source in the
   preview**. Never send stale disk text while implying it is the visible
   version.
6. **The preview shows the paths** — a new `AiSource` (e.g. `PROJECT_FILES`)
   whose preview renders the file list, per-file line counts, total
   `sentChars`, and the excluded list. Send remains the only road out.

---

## 5. Scope questions — decided conservatively BY THE AGENT (2026-10-01)

The owner was asked these six and answered *"Complete the lavel 2"* instead.
Each was therefore taken at its most conservative option. **These are the
agent's decisions, not the owner's** — recorded so a later chat can tell the
difference, and so any one of them can be reversed on the owner's word without
reopening the others.

| # | Question | **Decision taken** | Why this is the conservative one |
|---|---|---|---|
| Q1 | Multi-turn? | **No** — one question at a time; Level 1's exchange behaviour unchanged | Phase 77's Q1 answer was already *"No i just making the ui future pruff"*; the Level 2 spec never asks for turns; a turn would re-open D4 (the whole resent history must be re-previewed). |
| Q2 | The context budget | **`MAX_CONTEXT_CHARS` stays 12 000** (`AiPolicy.kt:23`); answer with relevance + whole-line ranges | Does not touch a value the Phase 76 device round validated. Pinned by `AiLevel2WiringTest`. |
| Q3 | Secret-like files | **Never offered, no opt-in**, but counted and reported in the preview | A false positive costs one file of context; a false negative sends a credential to Google. |
| Q4 | `.gitignore` | **Out of scope** — the fixed `ProjectFilesPolicy.excludedDirectories` set only | §2.6: no glob matcher exists anywhere in the app; writing one is a separate sub-project with its own bug surface. Recorded deferred. |
| Q5 | Entry point | **A third chip in the existing chat sheet**, beside *Explain selection* / *Explain last error* | No new surface. ✨ home stays setup-only (`AiSurfaceWiringTest`), and Sora's selection menu has no add-item API. |
| Q6 | Persistence | **Nothing new saved**; context rebuilt from disk every request | D6. `ai_settings.properties` still has exactly its six keys; `AiLevel2WiringTest` pins that no seventh arrived. |

---

## 6. Parts as built

| Part | Doc | What shipped | Tests |
|---|---|---|---|
| 78.1 | [PART_78_1_FILE_POLICY.md](PART_78_1_FILE_POLICY.md) | `ui/ai/AiProjectFiles.kt` (398 lines, pure) — the AI's own file filter, deterministic relevance, the shortlist, and a packer that enforces the budget on the rendered body | `AiProjectFilesTest` **28** |
| 78.2 | [PART_78_2_WALK_AND_CONTEXT.md](PART_78_2_WALK_AND_CONTEXT.md) | `ui/ai/AiProjectReader.kt` (230 lines, `java.io` only) + `AiSource.PROJECT` + `AiContextBuilder.fromProject` + `AiViewModel.askProject` | `AiProjectReaderTest` **14** |
| 78.3 | [PART_78_3_SURFACE.md](PART_78_3_SURFACE.md) | The third chip, the walk indicator, and a preview that names every file it sends | `AiLevel2WiringTest` **15** |

**57 new host cases**, all in `app/src/test/java/com/codeci/ide/`.

> **Correction to the commit message of `8abd49e`:** it states
> "AiProjectFilesTest 26, AiProjectReaderTest 15, AiLevel2WiringTest 14".
> The real counts, `grep -c '@Test'`, are **28 / 14 / 15**. The commit is
> pushed and is not rewritten (`rule.md` §2: never force-push); the correct
> numbers are recorded here and in the part docs instead.

### Pre-validation, and what it was worth

The sandbox cannot compile Compose, but a JRE + kotlinc **are** installable
(pip `jdk4py` 25.0.2 + npm `kotlin-compiler` 2.4.20). Before CI:

- **102/102** policy + prompt assertions executed against the **real compiled**
  `AiProjectFiles` / `AiContextBuilder` / `AiPromptText`;
- **30/30** walker assertions against a **real temporary tree** containing
  `.env`, `.env.production`, `.npmrc`, `node_modules`, `.git`, a NUL file, a
  file symlink and a directory symlink escaping the root, plus a 4 005-file
  entry-cap case;
- **139/139** source-scan pins executed through the **real
  `RepoFiles.codeOnly`**.

Three real defects were caught locally, before CI:

1. a genuine compile error — `return@buildString null` in a `String`-returning
   `buildString` block (`AiContext.kt`);
2. a **wrong test assertion** — "a slice must not end with `padding padding`"
   is false for a *correct* whole-line slice, which legitimately ends with the
   line's last word;
3. **two wrong wiring pins** — `READ_EXTERNAL_STORAGE` is already in the
   manifest (pre-existing, so its absence could never be asserted), and
   `"store":false` is written escaped in source (`append("\"store\":false")`),
   so a plain substring pin could never have matched.

Pre-validation never replaces CI; CI is the only compile check for the Compose
files (`AiChatSheet.kt`, `EditorScreen.kt`, `AiHome.kt`), which were **not**
compiled locally.

---

## 7. Exit condition (from the Level 2 spec's acceptance checks)

1. A multi-file question uses relevant files and the answer **can name the
   paths used**.
2. A large project does not freeze the UI or create an unbounded request.
3. Ignored, excluded, secret-like and out-of-root files are **not silently
   included**.
4. Cloud context transfer happens only after user initiation and is visible.
5. Read-only is unbroken: Copy is still the only output action.
6. Nothing new is persisted (or the one owner-approved exception is recorded).
7. `Build APK` green; device round passed; merge only on the owner's command.

---

## 8. Deferred / rejected with reasons

- **Embeddings, vector storage, background indexing** — rejected: the Level 2
  spec defers them, and D6 forbids a persistent index without an explicit
  owner policy.
- **Applying edits, shell access, parallel agents** — Levels 3/4/7, not here.
- **Reusing `ProjectSearch.isSearchable` as the AI filter** — **rejected with
  evidence** (§2.4): it admits `.env`, `.env.*` and `.npmrc` on three routes.
- **Forking Sora or injecting views** — still rejected (Phase 77's recorded
  finding: `EditorTextActionWindow` has no add-item API at tag 0.24.6).
- **Multi-provider** — Level 5.
