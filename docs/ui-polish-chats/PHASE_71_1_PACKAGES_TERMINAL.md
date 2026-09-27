# Proposed Phase 71.1 — Packages, installation and Terminal

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/ModulesScreen.kt:99`](../../app/src/main/java/com/codeci/ide/ui/screens/ModulesScreen.kt#L99), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** No Terminal or Packages reference shot. Distinguish a UX proposal from a tested performance improvement.

## My recommendation

The lock removal is done in Phase 64. Next polish status visibility, the Packages-to-Terminal handoff, recovery copy and session identity—not another global progress overlay.

## Bounded review checklist

Available/installing/installed/update/failure; unavailable userland; verification errors; retry; command handoff; session switch; selection/copy/paste; extra keys; restart; screen-off foreground notification.

## Your thoughts — ask before code

**While installing, do you want a small status indicator visible elsewhere, or progress only in Terminal/Output?**

Suggested starting point, not your decision: Keep progress at the operation’s existing surface unless you want a compact, non-blocking indicator. Never bring back navigation locks.

**Owner answer:** pending. Record it verbatim here when given; do not present the
recommendation as approval.

## Implementation and exit, after agreement

InstallMoment, SetupState, TerminalViewModel, TerminalSessionManager and TerminalHandoff; keep signatures, atomic recovery and transaction serialization.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
