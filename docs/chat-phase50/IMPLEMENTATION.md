# Phase 50.1 — execution record

**Started:** 2026-09-13  
**Branch:** `arena/01a099b4-codec`  
**Scope:** documentation and cross-device evidence only; no product code or new dependency.

## Work completed in this start

- Promoted `DEVICE_MATRIX.md` from a historical plan to the active owner runbook.
- Bound the runbook to this session branch and explicitly separated the CI build
  gate from the handset gate.
- Added the no-invention rule: this environment has no attached handset or
  emulator, so no device row is marked passed without the owner's copied report.
- Corrected the Projects `+`-sheet expectation to match Phase 46's shipped
  surface: **New project / Clone / Import ZIP** (the retired Open folder and
  Import file rows are not expected).
- Kept the result format and the requirement to paste each result into its
  owning Phase 44–49 part file. A matrix with unfilled rows is deliberately not
  called complete.

## Evidence baseline

The repository tip is the Phase 48/49 device-passed build:

- commit: `62cfe7b`
- session branch: `arena/01a099b4-codec`
- Phase 48/49 owner report: all device checks passed
- Phase 50 cross-device rows: **not run** (no device attached to this agent)

The owner should run the matrix on at least:

1. a small gesture-navigation phone (the mandatory caret/anchor case),
2. a large 3-button-navigation phone (the mandatory back/exit case),
3. an Android 13+ phone (setup foreground-service/notification case), and
4. a tablet or foldable if available.

A single handset, a JVM test, or a screenshot cannot substitute for the
keyboard-inset and navigation-mode rows.

## CI gate

Before the device round, run the repository's normal green build on this
branch and record its `Build APK` run ID at the top of
`DEVICE_MATRIX.md`. The build is a prerequisite, not a device result. Do not
claim Phase 50 complete until the owner supplies the filled result block and
any failures have either been fixed in the owning phase or explicitly deferred
with a date and reason.

## Exit checklist

- [x] Active runbook is bound to the session branch.
- [x] No new app code, dependency, permission, DataStore key, or telemetry.
- [x] No device result invented.
- [ ] Green CI run ID recorded in `DEVICE_MATRIX.md`.
- [ ] D1 and D2 device classes tested.
- [ ] D3 Android 13+ behavior tested.
- [ ] D4 tablet/foldable tested if available.
- [ ] Every A–F row has a result and evidence.
- [ ] Results pasted into the owning Phase 44–49 `## Test log` sections.
- [ ] `PHONE_UX_ANALYSIS.md` pending checks reconciled.

## Status

**Phase 50 is started, not complete.** The remaining work is the owner/device
round and its evidence record.
