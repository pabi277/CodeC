# Proposed Phase 66.1 — Projects hub and creation

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/FileManagerScreen.kt:155`](../../app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt#L155), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 124049 shows recent projects, not the whole hub. Hub changes need the owner’s preference; no invented screenshot parity.

## My recommendation

Keep All / Recent / Create and name-derived marks. Improve hierarchy, naming, empty states and progress only where the owner identifies friction.

## Bounded review checklist

Search/filter combinations; long and duplicate-looking names; recent ordering; create/template sheets; clone/import/export; failure and permission states; resume card.

## Your thoughts — ask before code

**For projects, do you prefer the current cards or a denser list? Should deleted bundled demos stay deleted?**

Suggested starting point, not your decision: Keep cards for now; discuss persistent demo re-seeding before changing it.

**Owner answer:** pending. Record it verbatim here when given; do not present the
recommendation as approval.

## Implementation and exit, after agreement

ProjectsHub, ProjectMark, ProjectTransfer, WelcomeStarters and DemoProjects; preserve file-safe imports and real recency.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
