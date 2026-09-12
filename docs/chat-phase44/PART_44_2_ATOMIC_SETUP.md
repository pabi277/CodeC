# CodeC Phase 44.2 — The install cannot half-finish

> **Status:** 🚧 **IMPLEMENTED** (2026-09-12, `arena/01a0955a-codec`) · CI pending · device round required · **Cost:** `[client-only]` · **Effort:** M ·
> **Owner row (verbatim):** *"they close app before it complete than letter when
> they try to install any other pkg got errors"*
>
> 44.1 makes the user *know*. This part makes the app *survive being wrong*.
> Both are needed: banners reduce kills, only restartability makes a kill
> harmless.

## Root cause, precisely (verified 2026-09-12)

`UserlandInstaller.swapPrefix` (`UserlandInstaller.kt:377-395`):

```kotlin
val old = File(prefix.parentFile, prefix.name + ".old-" + System.currentTimeMillis())
if (!prefix.renameTo(old)) throw IllegalStateException("cannot move old userland aside")
if (!staged.renameTo(prefix)) { old.renameTo(prefix); throw … }   // rollback path
old.deleteRecursively()
```

There are three states the filesystem can be in when the process dies here:

| Kill point | Filesystem | Next launch, today |
|---|---|---|
| before `prefix.renameTo(old)` | `usr` intact + orphan `.userland-staging-<ts>` | works; orphan leaks disk forever |
| **between the two renames** | **no `usr`** + `usr.old-<ts>` | `hasRunnableUserland` false → fresh install attempted → offline ⇒ `SkippedOffline` (`:141`) → **no `pkg` anywhere** |
| after `staged.renameTo(prefix)`, before `deleteRecursively` | `usr` valid + `usr.old-<ts>` | works; orphan leaks disk forever |

The middle row is the owner's bug. Two further facts make it worse and both are
grep-verified: `".old-"` appears **only** at line 384 and `STAGING_DIR`
(`.userland-staging`, `:538`) **only** at line 295 — so **nothing in the app ever
repairs or cleans either directory**, and a leaked staging tree can be hundreds
of MB on a phone with 2 GB free.

Note what is *already* correct and must not be touched: `.partial` downloads
resume with HTTP Range and are SHA-256-gated before the rename
(`downloadFile`, `:428-480`; `installRelease`, `:262-283`), extraction happens
into a staging dir, and a failed staged install leaves the live prefix alone
(`:296-301`). The single defect is the non-atomic swap plus the absence of a
boot-time repair.

## Design

### 1. A durable ledger (`SetupLedger`)

The same shape `ui/crash/StartupLedger.kt` already uses — two values in a
`SharedPreferences`, written with **`commit()` not `apply()`** because the
marker must be durable *before* the risky step (that comment is already in
`MainActivity.kt:212-215`, and the reason transfers exactly).

```kotlin
// ui/terminal/SetupLedger.kt  (pure + a 6-line Store interface, StartupLedger-shaped)
enum class SetupPhase { IDLE, DOWNLOADING, EXTRACTING, SWAPPING, DONE }
data class Record(val phase: SetupPhase, val release: String, val startedAt: Long)
class SetupLedger(private val store: Store) {
    fun note(phase: SetupPhase, release: String)      // commit()
    fun read(): Record
    fun clear()
    /** Boot-time verdict: what does a leftover record MEAN? */
    fun resumePlan(record: Record, prefixExists: Boolean, oldDirs: List<String>): SetupResume
}
sealed interface SetupResume {
    object Nothing : SetupResume                                   // DONE or IDLE
    object RetryDownload : SetupResume                             // DOWNLOADING
    object Reextract : SetupResume                                 // EXTRACTING
    data class RestoreOld(val dir: String) : SetupResume           // SWAPPING + no prefix
    data class SweepOnly(val orphans: List<String>) : SetupResume  // prefix fine, junk left
}
```

`resumePlan` is the whole intelligence and it is pure, so the kill matrix above
becomes a table CI can pin instead of a field bug report.

### 2. A swap with no "no prefix" window — plus a boot repair

Two mechanisms, because a rename window cannot be made truly atomic across two
directories on every filesystem:

- **(a) Rename the staged tree to `usr.new` first, then copy-into-place is *not*
  used** (copying 200 MB doubles the peak disk need — rejected). Instead the
  order changes so the *dangerous* window is bounded and self-describing:
  `ledger.note(SWAPPING)` → `prefix.renameTo(old)` → `staged.renameTo(prefix)`
  → `ledger.note(DONE)` → delete `old`. The window still exists (it is
  unavoidable with two renames) but it is now **recorded**, which is what makes
  (b) possible.
- **(b) `restoreAfterInterruptedSwap()` on boot**, called from
  `MainActivity.onCreate` next to the existing `TempGc` sweep
  (`MainActivity.kt:196-208`, same daemon-thread shape):
  ```text
  record = ledger.read()
  if (record.phase == SWAPPING && !prefix.isDirectory) {
      // the exact owner's-bug state: put the previous userland back
      rename(newest usr.old-*) -> usr ; ledger.clear()
      log "setup interrupted during swap — previous userland restored"
  }
  sweep every usr.old-* and .userland-staging-* older than the current run
  ```
  The restored prefix is the *previous* release, which may be older — that is
  correct and honest: a working older userland beats a missing one, and the next
  `installIfNeeded(checkForUpgrade = false)` pass upgrades it when online.
- **The sweep is bounded** exactly like `TempGc`: only names matching
  `usr.old-*` / `.userland-staging-*`, only inside `filesDir`, never following
  symlinks, never touching `usr`, failures counted and never thrown.

### 3. Capability, not optimism (`pkg` refuses with a reason)

A one-function truth test used by 44.1's gate:

```kotlin
object SetupGatePolicy {
    fun userlandUsable(prefix: File, ledger: SetupLedger): Boolean  // pkg exists AND is exec'able AND ledger != SWAPPING
}
```

`userlandUsable` checks the **actual** `bin/pkg` file (exists, `canExecute`,
non-zero length) rather than the release marker, because the marker is written
*after* the swap (`writeMarkers`, `:369-372`) and a marker-only test would pass
on a half tree. The result feeds the same `SetupVerdict` messages 44.1 defines,
so there is one vocabulary for "not ready" in the whole app.

### 4. What the user sees after a kill

| Killed during | Next launch |
|---|---|
| download | setup bar returns with *"Resuming the download (38 %)"* (Range resume already works) |
| extract | *"Finishing setup — unpacking the Linux tools"*; the previous `usr` is untouched, so `pkg` still works if it was installed |
| swap | *"Setup was interrupted; CodeC restored your tools."* + the normal path; `pkg --version` works |
| any, offline | *"CodeC needs the network once to finish setting up its Linux tools. C works offline right now."* — never a silent `SkippedOffline` |

That last row is the fix for the *second* half of the owner's sentence: today
the offline message is `"userland: offline — using built-in cc (TCC)"`, which
reads like success.

## Exit condition

```text
1. Kill mid-download → relaunch: resume or restart, prefix never half-written,
   surface tells the truth, `pkg` state honest.
2. Kill mid-extract → relaunch: no orphan `.userland-staging-*` after the boot
   sweep; a previously working `pkg` still works.
3. Kill mid-swap → relaunch: `usr` exists; `pkg --version` prints a version in
   the terminal; the ledger is clear.
4. After 1-3: `pkg install python` succeeds and `python3 -V` runs — the owner's
   exact failure, now impossible.
5. Repeat 3 three times in a row: no `usr.old-*` accumulation (the sweep works),
   and disk usage does not grow.
6. Offline + interrupted: the message names the network, not "using built-in cc".
7. A device that never installs (x86_64 emulator, no bootstrap): no ledger
   churn, no repeated retries, C still runs.
PASS = all seven; 3-4 are the core promise.
```

## Tests (plan)

- `SetupLedgerTest` (host): every transition; a `SWAPPING` record read on boot
  yields `RestoreOld` when the prefix is missing and `SweepOnly` when it is not;
  `DONE` never triggers a repair; the store is called with `commit`-semantics
  (asserted on the fake).
- `SwapRecoveryTest` (host, real temp dirs — this is the money test):
  build a fake prefix + staged + `usr.old-*`, simulate each kill point by
  performing only the first *n* operations, run `restoreAfterInterruptedSwap`,
  assert the resulting tree is runnable; assert a **valid** `usr` is never
  renamed, moved or deleted; assert the sweep removes both orphan shapes and
  nothing else (including a directory named `usr.old-but-mine` inside a project —
  the sweep must stay inside `filesDir`, never inside `projects/`).
- `UserlandUsableTest` (host): marker present + `bin/pkg` missing ⇒ false;
  `bin/pkg` present but 0 bytes ⇒ false; `bin/pkg` present + marker missing ⇒
  **true** (a working prefix beats a missing marker — the honest direction).
- Existing `UserlandInstallerTest` keeps passing **untouched** (its loopback
  keep-alive flake is `TROUBLESHOOTING.md` §31; this part must not "fix" it).

## Implementation (2026-09-12, `arena/01a0955a-codec`)

### New files

| File | What it holds |
|---|---|
| `ui/terminal/SetupLedger.kt` (155 lines) | `SetupPhase` (`IDLE/DOWNLOADING/EXTRACTING/SWAPPING/DONE`), `SetupRecord` (+ `wasInProgress`), `SetupLedger` over an injected `Store` (`readPhase/readRelease/readStartedAt/note/clear`): `note:86` stamps `startedAt` only on the **first** in-progress write of an attempt and re-stamps it when a `DONE` record is followed by a new `DOWNLOADING`; `read:97` maps a corrupt or throwing store to `IDLE`; `clear:109`; `resumePlan:120`; `resumeMessage:144` |
| `ui/terminal/SetupLedgerPrefs.kt` (48 lines) | the Android `Store`: `SharedPreferences("setup_ledger")`, **`commit()` on every write, never `apply()`** |
| `ui/terminal/SetupRecovery.kt` (292 lines) | `SetupRecovery.recover:92` (one entry point: read the ledger → `restoreOldPrefix:161` when the kill window was hit → `sweep:187` bounded by `scan:220` → mark the ledger `DONE`), `SetupRecoveryGate:266` + `awaitFinished:282` (5 s) |

### The kill matrix as implemented (`resumePlan`)

| Ledger | `usr` | `usr.old-*` | Action |
|---|---|---|---|
| `SWAPPING` | absent | ≥1 | **`RestoreOld`** (newest stamp) → then sweep |
| `SWAPPING` | present | ≥1 | `SweepOnly` — the swap completed |
| `SWAPPING` | absent | 0 | `FreshInstall` + a one-time notice |
| `DOWNLOADING`/`EXTRACTING` | absent | ≥1 | **`RestoreOld`** (an older interrupted swap still owned the prefix) |
| `DOWNLOADING`/`EXTRACTING` | absent | 0 | `FreshInstall` + notice |
| `DOWNLOADING`/`EXTRACTING` | present | any | `SweepOnly` (the install can continue from cache) |
| `DONE`/`IDLE`/corrupt | any | ≥1 | `SweepOnly` |
| any | any | 0 | `None` |

The sweep deletes **only** `usr.old-<digits>` and `.userland-staging-<digits>`
that are real directories directly inside `filesDir`, never the live `usr`, and
never anything newer than the repair's own `startedAt` (a concurrent install's
staging directory is untouchable). Three interrupted swaps leave one prefix and
zero orphans — pinned by `SwapRecoveryTest`.

### Changed files

| Site | Change |
|---|---|
| `MainActivity.kt:258-284` | the repair runs on a daemon thread **next to `TempGc`**, before the Compose tree, and always calls `SetupRecoveryGate.finished()` (even on a throwing store) |
| `ui/terminal/UserlandInstaller.kt:308-328` | `SetupRecoveryGate.awaitFinished()` before touching the prefix; ledger `EXTRACTING` note before the extract and `SWAPPING` immediately before `swapPrefix` |
| `ui/terminal/UserlandInstaller.kt:378-390` | the post-install marker is now written **after** the swap — the old order made `installUserlandInternal` believe the tools existed while `usr` was mid-rename. `swapPrefix` itself keeps its byte-identical staging/old names via `SetupRecovery.stagingName`/`oldPrefixName` |
| `ui/viewmodels/TerminalViewModel.kt` | ledger notes on the stage transitions (`DOWNLOADING`/`EXTRACTING`/`DONE` on a working prefix, `IDLE` on `SkippedOffline`), and `userlandUsable()` = `SetupGatePolicy.userlandUsable(prefix, ledgerPhase)` — the real `bin/pkg`, not the marker |
| `ui/viewmodels/EditorViewModel.kt:2224` | the offline refusal names the network and offers **RUN C** and **VIEW SETUP**; the sentence comes from `SetupGatePolicy.refusal`, not from the installer's `"using built-in cc"` wording |

### Deviations from this part's spec (recorded, not silent)

1. **One public repair entry point.** The spec's
   `UserlandInstaller.restoreAfterInterruptedSwap()` + `sweepOrphanSetupDirs()`
   are implemented as `SetupRecovery.recover(...)`, because the repair must run
   from `MainActivity` (which has no `UserlandInstaller` instance) and must not
   depend on the JNI session classes. `UserlandInstaller` keeps its existing
   public API — `UserlandInstallerTest` is **untouched** (its staging/old names
   are byte-identical, produced by `SetupRecovery.stagingName`/`oldPrefixName`).
2. **`SetupRecoveryGate` was added** (not in the spec): the installer waits up to
   5 s for the boot repair to finish, so a repair renaming `usr.old-*` → `usr`
   can never interleave with a fresh install's swap. Both directions are safe by
   construction (different directories, stamps guard the sweep), but the wait
   removes the ambiguity entirely.
3. **The repair notice is one-time and non-nagging** (`SetupNoticeBridge` →
   the setup bar's notice row), and only appears when the repair actually
   restored or cleaned something.
4. **The ledger rides on BOTH installer constructors.** The primary constructor
   takes `ledger: SetupLedger? = null`; the secondary `(Context)` constructor —
   the one `TerminalViewModel` actually uses — must declare and forward it too.
   CI round 1 (`34692621773`) proved what happens otherwise: the constructor
   call does not resolve, `userland`'s type becomes unknown, and **every**
   member access on it reports `Unresolved reference` (seven errors from one
   missing parameter). `SetupGateWiringTest` now pins the forwarding, so the
   next refactor of that constructor fails on the host, not on CI.
5. **"Never a symlink" is enforced with API-1 mechanisms.** The first
   implementation used `java.nio.file.Files.isSymbolicLink` and CI's lint
   rejected it (`NewApi`: `java.nio.file` is API 26, `minSdk` is 24 — a *pure*
   file is still linted). `isSymlink` now compares the canonical name with the
   name (a link's canonical file is its target) and treats an unresolvable path
   as a link, and `deleteOrphan` calls a plain `File.delete()` **before**
   `deleteRecursively()`: `delete()` unlinks a symlink without following it,
   while `deleteRecursively()` does follow one. Together they also cover the case
   the name check cannot see — a link whose target has the *same* orphan-shaped
   name — and both are pinned by `SwapRecoveryTest` cases that create **real
   symlinks**. Runbook: TROUBLESHOOTING §34.

## Sources (record)

- CodeC 2026-09-12: `UserlandInstaller.kt:116-125` (`installIfNeeded`),
  `:262-283` (download + SHA gate), `:295-301` (staging + rollback),
  `:369-372` (`writeMarkers`), `:377-395` (`swapPrefix`), `:428-480`
  (`downloadFile` Range resume), `:538-552` (constants);
  `MainActivity.kt:196-215` (the boot-sweep + `commit()` patterns to copy);
  `ui/crash/StartupLedger.kt` (the ledger shape); `ui/services/TempGc.kt` (the
  bounded-sweep shape).
- `docs/PHASE44_50_UX_RESEARCH.md` §2.3.
- Android's process-death contract (any process may be killed at any time) —
  the reason restartability, not politeness, is the deliverable.

## Deferred / rejected with reasons

- **`fsync` + a real journal** — over-engineering for one directory swap on one
  filesystem; the ledger + boot repair covers the observable failure.
- **Copying instead of renaming** — doubles peak disk (200 MB+) on the devices
  least able to afford it; rejected.
- **Deleting `usr` before the swap** — strictly worse: it widens the no-prefix
  window to the whole extraction.
- **Automatic re-download on every boot when interrupted** — a phone on metered
  data would eat the user's plan; the resume path plus one honest message is the
  product answer.
- **Making `pkg` a shim that self-heals the prefix** — violates the standing law
  *never overwrite `cc` or the real ELF `bash` with a shim* (`rule.md` §6) in
  spirit; the repair belongs at boot, in one place, with a log line.
