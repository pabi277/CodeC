# Proposed Phase 73.1 — Source control and GitHub

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/GitControlView.kt:96`](../../app/src/main/java/com/codeci/ide/ui/screens/GitControlView.kt#L96), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 124055 shows an empty repository state only. It is not evidence for the layout of populated Git controls.

## My recommendation

Improve staged/unstaged grouping, button hierarchy and actionable errors. Preserve the recently implemented discard safety exactly.

## Bounded review checklist

Uninitialised repo; status/refresh; diff; stage/unstage; commit; push/pull; publish; branch/stash; conflicts; auth/identity failures; discard confirmation and editor reconciliation.

## Your thoughts — ask before code

**Do you want Git focused on beginner actions, or a denser power-user layout with more controls visible?**

Suggested starting point, not your decision: Lead with status, diff and commit; put dangerous/advanced actions behind clearly named confirmations, not hidden semantics.

**Owner answer:** pending. Record it verbatim here when given; do not present the
recommendation as approval.

## Implementation and exit, after agreement

GitControlViewModel, GitManager, GitDiscardPolicy, GitDiscardEditors and BranchSwitchSheet; discard remains unstaged-only and exact-path checked.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
