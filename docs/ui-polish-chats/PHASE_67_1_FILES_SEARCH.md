# Proposed Phase 67.1 — Files, drawer and project search

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/components/EditorProjectDrawer.kt:73`](../../app/src/main/java/com/codeci/ide/ui/components/EditorProjectDrawer.kt#L73), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 122203 Files and 124052 Search are real references. They do not justify importing a new editor engine.

## My recommendation

Make selection and scope obvious while preserving the existing tree and search engines. Do not collapse the drawer unexpectedly during a project switch.

## Bounded review checklist

Expand/collapse; project switch; scratch and single-file mode; rename/delete confirmations; path overflow; hidden files; project search options/results/jump; no-results errors.

## Your thoughts — ask before code

**Should file rows be more compact, or easier to tap with more spacing?**

Suggested starting point, not your decision: Use compact visuals with generous non-overlapping touch targets, rather than shrinking all controls.

**Owner answer:** pending. Record it verbatim here when given; do not present the
recommendation as approval.

## Implementation and exit, after agreement

DrawerPolicy, SidePanelPlan, ProjectSearch, EditorFileTree; keep selected-file and active-project meaning distinct.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
