# CodeC Phase 87.4 — Task memory toggle and *Clear now*

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S
> **Owner row (verbatim):** *"more options to use in the app for better optimization."*
> **Parent brief:** [Phase 87 README](README.md)

## First move: evidence, not code

Read 2026-10-03 against `main` @ `6838ea6`. Level 9 built the mechanics and deliberately left
the UI to Level 10:

| Site | Behaviour today |
|---|---|
| `AiTaskMemoryStore.kt:18` | `private val persistentEnabled: Boolean = true` — a constructor parameter no caller varies |
| `AiTaskMemoryStore.kt:27-28` | when off, `load` returns empty **and** clears any retained project copy |
| `AiTaskMemoryStore.kt:76-78` | when off, `save` clears instead of writing |
| `AiTaskMemoryStore.kt:118` | `clearProject(noBackupRoot, projectName)` |
| `AiTaskMemoryStore.kt:127` | `clearAll(noBackupRoot)` |
| `AiViewModel.kt:672` | the one construction site: `AiTaskMemoryStore(getApplication<Application>().noBackupFilesDir, projectName)` |
| `ProjectManager.kt:72` | `AiTaskMemoryStore.clearProject(it, info.name)` on project deletion |
| `AiKeyStore.kt:138` | `AiTaskMemoryStore.clearAll(it)` on key deletion |
| `AiTaskMemoryStore.kt:154` | the path is `File(root, "ai/task")` under the no-backup root |
| `AiTaskMemoryStore.kt:148` | `AiProjectReader.isSymlink(noBackupRoot)` refuses a symlinked root |

So the toggle is a value threaded into `AiViewModel.kt:672`, and *Clear now* is a call to the
already-tested `clearProject`. **No new storage semantics, no new path, no new cap.**

## Design

```kotlin
// AiViewModel — the only construction site gains the stored option:
val memoryStore = AiTaskMemoryStore(
    noBackupRoot = getApplication<Application>().noBackupFilesDir,
    projectName = projectName,
    persistentEnabled = state.options.taskMemory,
)
```

*Clear now* calls `AiTaskMemoryStore.clearProject(noBackupRoot, projectName)` on
`Dispatchers.IO`, then drops the live in-memory `AgentSession` task memory so the UI reflects the
deletion immediately rather than on next launch. It reports success/failure rather than
pretending: `clearProject` already returns `Boolean`.

**Ordering matters for the off switch.** Level 9's contract at `:27-28` and `:76-78` is that
switching off *clears* the retained copy — off is not "pause", it is "forget". The row's copy must
say so plainly, because a user who expects a pause will lose their findings. Recommended label:
*"Task memory — remember files, findings and plan between requests. Turning this off deletes what
is stored for this project."*

### What must not change

- The secret filter still runs before every write (**S5**); the toggle does not bypass it.
- The caps are unchanged (**S4**).
- Deletion on project deletion (`ProjectManager.kt:72`) and key deletion (`AiKeyStore.kt:138`)
  still fires regardless of the toggle's value.
- Task-local in-memory memory still works with persistence off — that is Level 9's designed
  behaviour, and turning the toggle off must not make the current task worse.

## The Android edge

`AiHome` gains one switch and one *Clear now* row. The clear action needs a confirmation only if
the project actually has stored memory; otherwise it is a no-op that says so. No new permission,
no new path, no new dependency.

## Exit condition

- Turning task memory off deletes the project's `ai/task/<project>/task-memory.bin` and the
  in-memory ledger, immediately and visibly.
- With it off, the running task still behaves sensibly in-session; nothing is written.
- Turning it back on starts from empty and rebuilds from later reads.
- *Clear now* reports the real result and is reflected in the UI without a restart.
- Project deletion and key deletion still clear memory with the toggle in either position.

## Tests (plan)

- `AiTaskMemoryStoreTest` — **3 new**: off-mode load clears a retained copy; off-mode save does
  not create the file; `clearProject` returns the real result for a present and an absent store.
- `AiLevel10WiringTest` — **2 new**: source pin that `AiViewModel`'s single `AiTaskMemoryStore(`
  construction passes `persistentEnabled` from options; source pin that `ui/ai/` still contains
  no project write and no command execution (**S6**).
- `AiLevel9WiringTest` — must stay green **unchanged**: the deletion hooks at
  `ProjectManager.kt:72` and `AiKeyStore.kt:138` are already pinned there.

## Sources (record)

1. Repository: `AiTaskMemoryStore.kt:12,15,18,27-31,76-80,113-114,118,127-128,147-154`,
   `AiViewModel.kt:672`, `ProjectManager.kt:72`, `AiKeyStore.kt:138` — read 2026-10-03 against
   `main` @ `6838ea6`.
2. [`09_TASK_MEMORY_AND_PLANNING.md`](../../../roadmaps/ai-integration/09_TASK_MEMORY_AND_PLANNING.md)
   — the persistent-off path and the statement that Level 10 owns the switch.

## Deferred / rejected with reasons

- **A "pause without deleting" third state** — rejected; it would retain derived state the user
  believes they switched off, which is worse than either clear choice.
- **Clearing all projects from this row** — rejected; *Clear now* is project-scoped, matching the
  memory's own directory layout. `clearAll` stays reserved for key deletion.
- **Moving the memory path** — rejected; `noBackupFilesDir/ai/task/<project>/` is the
  owner-authorized D6 amendment and is already backup-excluded.
