# Phase 64 — remove installation UI locks and the guide

**Owner instruction (2026-09-27, verbatim):**
> And 1st remove the both locker system when installing something and the guide system

**Status:** implemented on `arena/01a0e1d9-codec`; validation below. No PR or
merge authorised. This is the first concrete phase after the Phase 63 delivery.

| Part | Scope | Status |
|---|---|---|
| [64.1](PART_64_1_UNLOCK_INSTALL_UI.md) | Userland and package-install UI locks | Implemented |
| [64.2](PART_64_2_REMOVE_GUIDE.md) | Slides, coach marks, typing tips, entry points and persistence APIs | Implemented |

## Meaning of “both locks”

Remove the **user-facing chrome locks** during userland setup and during a
language/tool install. Do not remove package-operation serialization, the
single-runner busy guard, verification, atomic prefix swaps, or the checks that
refuse an operation whose required tool is not installed. Those prevent damaged
installs and invalid commands; they must not prevent navigating or editing.

## Changes

- Bottom tabs navigate normally during either install; no lock glyph/dimming or
  “Hang tight” lock snackbars. Editor drawer and Run no longer ask a chrome-lock
  policy. Run retains its normal default/open-file chooser and real progress.
- Delete the chrome-lock types/policy, not just return “allowed” from dead code.
- Remove first-launch slides, global coach-mark overlay, anchor instrumentation,
  forced panel changes, guide-only state flows and preference accessors.
- Remove typing-tip modal and its preference API too: it is another automatic guide.
- Remove Guide from hub/drawer/navigation and Help & guide / Reset tips from
  Settings and search. Five navigation cells remain; the missing sixth is plain
  layout space, not a disabled/fake destination. Settings has 64 catalog rows.
- Keep first-launch Snake sample, resume, safe mode, demo files, projects,
  installer progress/retry, and all installation safety machinery.
- Old guide preference values can remain on upgraded devices; nothing reads or
  writes them. No destructive DataStore migration or project reset.

## Follow-up: install completion must not run a newly selected file

Reviewing the newly unlocked path exposed a real dependency on the old lock:
`confirmInstall` resumed `runFile(ctx, pendingRunTarget)` using the *current*
project/file after an asynchronous install. A user can now change that context.
The ViewModel captures the original context and target before launching the
installer and uses pure `InstallResumePolicy` at completion. If the selected
project/file changed, installation ends successfully with “installed — tap Run
when ready”, not an unexpected run of another file. Same-context file and server
requests retain their existing automatic continuation. Six regression cases
cover identity and both continuation call sites. No navigation restriction added.

## Validation

- Local JVM prevalidation: **177 passed, 0 failed** (17 actual test classes,
  temporary JUnit assertion/rule shim; not an Android or Gradle test run).
- Six new `UnrestrictedUiWiringTest` cases pin removed APIs and preserved safety.
  Updated Run policy, navigation grid, Settings census, Back and setup pins.
- Retired tests for the deleted guide and chrome-lock policies are removed;
  existing readiness, setup-state, ledger, recovery and single-runner protections
  are retained. No installer implementation was changed.
- Full Android tests/build: pending session-branch CI. Local Gradle cannot
  download its distribution (TLS handshake failure); no local APK claimed.
- Device behaviour not verified in this session. The owner's earlier decision
  to decline device rounds is respected; no new compulsory handset checklist.

## Next

Review [the full UI research and proposed chat sequence](../UI_POLISH_REVIEW_20260927.md).
Only Phase 64 is implementation-approved. Later phase numbers are proposals;
ask for the owner's thoughts before building them. Never reintroduce a tour or
installation navigation lock from an older phase document.
