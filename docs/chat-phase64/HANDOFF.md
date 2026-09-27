# Phase 64 — delivery and next-chat handoff

**Date:** 2026-09-27
**Delivery PR:** [#87](https://github.com/pabi277/CodeC/pull/87)
**Source → target:** `arena/01a0e1d9-codec` → `main`

The PR timeline and checks are the authoritative record of actual merge state,
merge commit, and final-head validation. This document records scope and approval;
it does not pre-announce a successful merge or invent a device pass.

## Owner instructions and decisions

Initial change request, verbatim:
> And 1st remove the both locker system when installing something and the guide system

Polish-plan answers:
- **Keep the current look** — refine details, not a wholesale redesign.
- **Projects and files** — the first future discussion area.
- **Keep existing progress locations** — Terminal/Output only; no new app-wide
  progress indicator.

Delivery authority, verbatim:
> Complete docs and merge

The last instruction authorises this delivery and its PR/merge after checks,
not automatic implementation of the proposed polish phases. Earlier “no PR or
merge authorised” statements describe the earlier state and are superseded for
Phase 64 only. No release/tag publication was requested.

## Delivered scope

- Both user-facing installation locks removed: userland and package installs
  no longer dim or block bottom tabs, the editor drawer, or Run via a lock policy.
- Guide slides, coach-mark tour, typing tips, anchor/state plumbing, preference
  accessors and Guide/reset entries removed from navigation, Settings and search.
- Installation safety, package serialization, tool-readiness checks, verification,
  recovery, busy-runner protection, progress and retry retained.
- Install-and-run completion is tied to the original project/file. If selection
  changed, installation succeeds but waits for an explicit Run instead of running
  another file automatically. Same-context requests retain automatic continuation.
- UI research and **ten separate one-part-per-chat proposals** prepared, each with
  code/reference evidence, an agent recommendation, owner question and test gate.
- No new dependency, permission, telemetry, destructive data migration, or
  replacement onboarding. Existing projects and preferences are not reset.

## Evidence and limitations

| Evidence | Result |
|---|---|
| Initial removal `28b47c0` | [CI 36304965446](https://github.com/pabi277/CodeC/actions/runs/36304965446) green |
| Final app code `4de912a`, including safe install continuation | [CI 36305311370](https://github.com/pabi277/CodeC/actions/runs/36305311370) green: host unit/screenshot task, debug and release APK builds, APK-set checks |
| Local pure/wiring prevalidation | 177 passed / 0 failed, 17 classes, temporary JUnit assertion/rule shim; not an Android build |
| Documentation finalisation | No changes to app/test sources after `4de912a`; final-head PR checks must pass before merge |
| Handset testing | Not performed or claimed; no compulsory replacement for the owner's declined device rounds |

Build artifacts for tested app code are available from the linked CI run:
`CodeC-IDE-debug` and `CodeC-IDE-release`. Release APK: 6,762,736 bytes;
debug APK: 25,938,412 bytes. A branch APK build is not a published app release.

## What to do next

Start with [proposed 66.1 — Projects hub](../ui-polish-chats/PHASE_66_1_PROJECTS.md),
then [67.1 — Files/search](../ui-polish-chats/PHASE_67_1_FILES_SEARCH.md) if agreed.
The part numbers are stable proposal identifiers, not an obligation to execute
65 before the owner's chosen area. Card density and whether deleted bundled
demos should remain deleted still need the owner's answer.

Copy into a new chat:

> Start the discussion for Phase 66.1. Read `docs/chat-phase64/HANDOFF.md`,
> `docs/UI_POLISH_REVIEW_20260927.md`, and
> `docs/ui-polish-chats/PHASE_66_1_PROJECTS.md`. Verify the current repository
> and PR state, then review the Projects hub. Keep the current look. Explain
> what works, recommend bounded improvements, and ask my thoughts before
> implementing. Keep installation progress in Terminal/Output. Never restore
> the guide or installation navigation locks from old docs. Do not automatically
> start another part, create a PR, or merge future work without my instruction.

## Record map

- [Phase README](README.md): implementation, preserved safety and tests.
- [64.1 — UI unlock](PART_64_1_UNLOCK_INSTALL_UI.md): evidence and context guard.
- [64.2 — guide removal](PART_64_2_REMOVE_GUIDE.md): removal boundaries and upgrade behaviour.
- [Full UI review](../UI_POLISH_REVIEW_20260927.md): research, owner choices and all ten briefs.
- [Next steps](../NEXT_STEPS.md): current priority ahead of historical entries.
