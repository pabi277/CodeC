# Phase 64.1 — navigate while installation runs

**Status:** implemented · **Cost:** client-only · **Effort:** S

## First move: evidence, not code

Before-edit base: `ef0e030`, 2026-09-27.
- `MainActivity.kt:1042` created `SetupLockPolicy.lock`; `:1181` gated tabs.
- `ui/screens/EditorScreen.kt:526` created the package-install lock;
  `:559` refused drawer taps; `:1262` blocked gestures; `:1552` refused Run.
- `ui/terminal/SetupState.kt:694` owned both lock reasons/policies.
- `ui/terminal/ShellEnvironment.kt:300` is a **different** lock: package
  transaction serialization. It remains unchanged.

Paths are relative to `app/src/main/java/com/codeci/ide/`.

## Design

Delete `ChromeOption`, `ChromeLockReason`, `ChromeLock`, `SetupLockPolicy` and
cross-screen install-running publication. Navigation is a direct user action,
not a permission granted by the installer. Simplify `RunButtonState` to actual
job progress and file availability. Keep `SetupGatePolicy`, `SetupTracker`,
`SetupAnnouncer`, the transaction lock, and ViewModel busy guards.

## The Android edge

FlatBottomBar always calls `onNavigate(screen)`. Drawer uses its normal toggle;
its existing gutter/scroll gesture restriction stays. Run calls the normal
chooser/open-file path. No new dependency, permission, setting or telemetry.

## Exit condition

Neither installation can dim/lock tabs or intercept drawer/Run with a lock
message. Progress/retry still exist. Unsafe package transactions are still
refused and no second runner job is started.

## Tests / sources

`UnrestrictedUiWiringTest`, `RunButtonStyleTest`, retained setup policy/ledger/
progress/wiring tests. Source evidence above; local verdict in phase README.
Android progress-indicator reference, fetched 2026-09-27:
https://developer.android.com/develop/ui/compose/components/progress

## Deferred / rejected

Reject deleting internal mutexes/transaction locks. Reject replacing the lock
with another modal. No arbitrary parallel-install feature in a UI-removal task.

## Implementation follow-up — 2026-09-27

Unlocking file/project changes also requires safe continuation after install.
`InstallResumePolicy` compares the captured project/file with the current one;
`EditorViewModel.confirmInstall` captures the target/server intent before async
work and guards BOTH continuation paths. A changed context gets successful
completion with an explicit “tap Run when ready” message, not an automatic run
of a different file. `InstallResumePolicyTest`: five policy cases + one wiring
case; included in the 177/0 local result. This is an operation-identity guard,
not a new navigation lock.
