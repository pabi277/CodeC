# Proposed Phase 65.1 — Shell and navigation

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`MainActivity.kt:742`](../../app/src/main/java/com/codeci/ide/MainActivity.kt#L742), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 122157, 124049, 124105; the owner kept the four-tab bottom bar despite the reference app.

## My recommendation

Keep the four bottom destinations and the editor side panel. Polish selection, Back, reveal/hide behaviour and first-frame/resume transitions; do not rebuild navigation.

## Bounded review checklist

First launch sample; return to last file; safe mode; four bottom tabs; hide/reveal; drawer opening and closing; system Back; empty or failed startup.

## Your thoughts — ask before code

**Should the bottom bar keep hiding while typing, or should it remain visible by default?**

Suggested starting point, not your decision: Keep the current hide-while-typing behaviour until the owner says otherwise.

**Owner answer:** pending. Record it verbatim here when given; do not present the
recommendation as approval.

## Implementation and exit, after agreement

NavBarPolicy, BackRouter, LaunchReadiness, ResumePolicy; preserve sample fallback and unsaved-edit handling.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
