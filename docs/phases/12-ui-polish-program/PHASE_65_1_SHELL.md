# Proposed Phase 65.1 — Shell and navigation

**Status: owner-approved and implemented 2026-09-29 in the same chat as 74.1.** No navigation redesign; existing hide-while-typing behavior retained by delegated agent choice. See [`docs/phases/12-ui-polish-program/chat-phase65/README.md`](chat-phase65/README.md). CI round 1 `36607240454` caught a test declaration syntax error (missing `()`); corrected, CI round 2 ✅ GREEN `36607641208` on `52420c1`. No PR/merge.

## Copy into a new chat

> Review `docs/journal` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`MainActivity.kt:742`](../../../app/src/main/java/com/codeci/ide/MainActivity.kt#L742), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 122157, 124049, 124105; the owner kept the four-tab bottom bar despite the reference app.

## My recommendation

Keep the four bottom destinations and the editor side panel. Polish selection, Back, reveal/hide behaviour and first-frame/resume transitions; do not rebuild navigation.

## Bounded review checklist

First launch sample; return to last file; safe mode; four bottom tabs; hide/reveal; drawer opening and closing; system Back; empty or failed startup.

## Your thoughts — ask before code

**Should the bottom bar keep hiding while typing, or should it remain visible by default?**

Suggested starting point, not your decision: Keep the current hide-while-typing behaviour until the owner says otherwise.

**Owner instruction:** “I don't understand the 65 phase so everything in your hand” (agent delegated the design choice); all recommendations approved. Keep the current hide-while-typing behavior.

## Implementation and exit, after agreement

NavBarPolicy, BackRouter, LaunchReadiness, ResumePolicy; preserve sample fallback and unsaved-edit handling.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.

## Already done outside this part

2026-09-27, owner device report during 66.1: Back on the Projects hub returns
to the editor the user came from instead of showing the exit prompt — the two
editor→Projects doors no longer pop the editor (`MainActivity.kt`; pinned by
`BackHandlerWiringTest`). Record:
[`docs/phases/12-ui-polish-program/chat-phase66/README.md` § Follow-up](chat-phase66/README.md).
