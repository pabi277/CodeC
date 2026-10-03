# Phase 86 — AI Level 9: task memory and planning

> **Status:** ✅ **COMPLETE & MERGED.** Merged by the owner's explicit command in
> [PR #111](https://github.com/pabi277/CodeC/pull/111) on 2026-10-03 to `main` @
> `6838ea6cf72937766f4d92eb5e9729b71f86b9ee`. Post-merge Build APK run
> [`37109573383`](https://github.com/pabi277/CodeC/actions/runs/37109573383) is **GREEN** on that
> commit (job `build`: success — host unit and screenshot tests, debug and release assembly, APK
> checks, artifact uploads). No APK byte count is recorded for the merge run: it was not
> re-measured, and the artifact/log endpoints were not reachable from the sandbox that wrote this
> row. Latest local host pre-validation on the branch had passed **72/72 selected methods across
> eight test classes**. Build APK CI run [`37106545726`](https://github.com/pabi277/CodeC/actions/runs/37106545726)
> was **GREEN** on fix commit `ddb75d3`; round 1 (`37106180481`) had exposed three existing Level 8
> regression assertions, and the refusal-copy and pointer-budget causes were fixed. Formal device
> acceptance remains **POSTPONED** to Level 12 — it has not been performed or passed.
> **Owner authorization:** Level 9 started with the bounded D6 amendment recorded below (2026-10-03). Level 10's memory on/off UI is **not** part of this phase.
> **Baseline:** `main` @ `32e4a5f87a9f73874a4de17967187445c49a2461` (Phase 84+85 merged via PR #110; final-head Build APK run `37084800939` green). Level 9 spec: [`09_TASK_MEMORY_AND_PLANNING.md`](../../../roadmaps/ai-integration/09_TASK_MEMORY_AND_PLANNING.md); shared rules: [`00_AGENTIC_MAP_AND_SECURITY_RULES.md`](../../../roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md).

## Owner D6 amendment — bounded task memory only

The owner authorized only a bounded, device-local task-memory exception under
`noBackupFilesDir/ai/task/<project>/`, excluded from Android Auto Backup and
outside the project tree. The exception is limited to admitted non-secret file
cache entries and structured findings, decisions, and plan. Raw chat transcript,
user prompts, model answers, maps, tool results, and timeline remain ephemeral.
Secrets are filtered before each persistent write. Project deletion and any
provider-key deletion clear the task-memory directory. Every other D6 rule stays
in force. The persistent-off store path clears retained copies and keeps the
active task's in-memory behavior; the user-facing switch belongs to Level 10.

## Implementation

- `AiTaskMemory.kt` holds bounded snapshots and structured notes. Cache identity
  includes canonical admitted path, effective line range, and SHA-256 content
  version. Dirty-buffer or disk changes, removed/unadmitted paths, secret-like
  content, and symlink/path failures prevent stale cache reuse; dirty buffers
  are never persisted.
- The version-checked file cache serves later reads, while the loop-local result
  cache still serves exact duplicate tool results at zero executions. Read
  runner behavior is kept consistent for disk, cached, and dirty content,
  including out-of-range refusals.
- Findings, decisions, important refusals, open questions, and plan are extracted
  from a bounded structured model block. Raw prompt-like entries and secret-like
  content are filtered before the store boundary. The plan recitation replaces
  the generic request tail and is the exact suffix of the packed request.
- `AiPrompt.userText` and every later disclosed `REQUEST` use the same packed
  text. Send-time version reconciliation refreshes the preview and requires a
  second Send if the disclosed memory changed. Before later requests, file-backed
  memory is reconciled against a stable dirty-buffer snapshot; after three
  unstable checks, file-backed notes/cache are omitted rather than sending stale
  state, and can be rebuilt from a later stable read.
- `AiTaskMemoryStore.kt` writes one bounded binary file via a synced temporary
  file and atomic replacement at
  `noBackupFilesDir/ai/task/<project>/task-memory.bin`; it contains no transcript,
  prompt, answer, or timeline fields. The store is capped at 256 KiB, with at
  most five file snapshots (32 KiB each / 160 KiB total), six findings and six
  decisions (512 chars each), and eight plan steps (256 chars each). Oversized
  files are not cached; only files up to 512 KiB are considered for a snapshot.
- `ProjectManager.deleteProject` and `AiKeyStore.deleteKey` clear the memory.
  `persistentEnabled = false` reads no saved state into the task and clears any
  retained project copy on load/save; task-local state remains in `AgentSession`.
- Exactly three `client.stream(` sites remain. No new model default, dependency,
  Android permission, DataStore key, endpoint, or Level 10 control was added.
  `ui/ai/` remains free of direct project writes and command execution; tool IO
  stays read-only and single-agent (S7).

## Tests and validation status

New JVM test-source additions: `AiTaskMemoryTest` **9**,
`AiTaskMemoryStoreTest` **5**, `AiLevel9WiringTest` **6**, and one regression in
`AiToolRunnerTest` (**21 additions** total). Coverage includes version/effective-range
keys, dirty/disk invalidation (including removal of invalidated stored bytes),
preview/send disclosure, secret filtering, storage caps, deletion cleanup,
persistent-off behavior, the plan-tail budget regression, and a 24-read replay.

**Local pre-validation passed:** Temurin 25.0.2 + kotlinc 2.4.20 compiled the
Android-free production core/store and these real test sources, then ran 72 test
methods across `AiTaskMemoryTest`, `AiTaskMemoryStoreTest`, `AiToolRunnerTest`,
`AiLevel8BatchTest`, `AiLevel8ContextTest`, `AiHelperWiringTest`,
`AiLevel4WiringTest`, and `AiLevel9WiringTest` via a small JUnit-compatible
host shim: **72 passed, 0 failed**. This is not the full Gradle
suite and does not compile Android/Compose wiring (including `AiViewModel`); the
Build APK run `37106545726` is green on fix head `ddb75d3`; the workflow remains the executor of record. One over-restrictive source
pin was corrected after it failed: the store must receive the project root for
version reconciliation, while its durable writer must never target that root.

**Build APK CI round 1:** run [`37106180481`](https://github.com/pabi277/CodeC/actions/runs/37106180481)
on code head `3716dd0` failed after 3,044 tests, with three Level 8 regressions.
`AiLevel8BatchTest` caught changed refusal wording for secret/unadmitted paths; the
runner now preserves the established specific reasons while retaining the new
path/admission checks. `AiLevel8ContextTest` caught a missing “more” marker when
the pointer budget filled; the packer now reserves room for that marker. These
fixes pass in the expanded 72/72 host harness.

**Build APK CI round 2:** run [`37106545726`](https://github.com/pabi277/CodeC/actions/runs/37106545726)
passed on code fix head `ddb75d3`. The full Build APK job passed host unit/screenshot
tests, debug and release assembly, and the release APK set checks. Artifacts: release
APK **7,138,224 B**, debug APK **26,931,320 B**. This is CI/build validation, not
formal device acceptance or a provider/device run.

Measured repository inventory: **320 Kotlin test files / 3,044 `@Test`
annotations / 39 AI test classes / 462 AI tests**. Counts describe source only,
not passing results.

- [x] Implementation, source pins, plan-tail regression and local pre-validation.
- [x] Build APK CI run [`37106545726`](https://github.com/pabi277/CodeC/actions/runs/37106545726) passed on code fix head `ddb75d3`; artifacts recorded above.
- [x] PR/merge — merged by the owner's explicit command in [PR #111](https://github.com/pabi277/CodeC/pull/111) to `main` @ `6838ea6cf72937766f4d92eb5e9729b71f86b9ee` (2026-10-03). Post-merge Build APK run [`37109573383`](https://github.com/pabi277/CodeC/actions/runs/37109573383) is green on that commit. That authorization was one-time and is complete.
- [ ] Device acceptance — not part of this phase; formal round remains Level 12.
