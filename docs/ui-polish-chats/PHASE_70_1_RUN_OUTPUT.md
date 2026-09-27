# Proposed Phase 70.1 — Run, output and error recovery

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/components/OutputPanelView.kt:99`](../../app/src/main/java/com/codeci/ide/ui/components/OutputPanelView.kt#L99), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** No reference screenshot shows output. Agree the desired behaviour before designing a new panel.

## My recommendation

Keep one runner owner. Make running, installing, waiting for input, failed and completed states distinguishable without locking unrelated navigation.

## Bounded review checklist

Offline C; language missing; explicit install confirmation; default/open-file chooser; interactive stdin; output collapse; stop; test runner; compile-error jump; server lifecycle; no-file state.

## Your thoughts — ask before code

**After a run, should output open automatically every time, or primarily on errors?**

Suggested starting point, not your decision: Keep current auto-opening behaviour initially; separate idle success from failure visually before changing routing.

**Owner answer:** pending. Record it verbatim here when given; do not present the
recommendation as approval.

## Implementation and exit, after agreement

OutputRunState, LanguageRunPlanner, InteractiveRunSession, RunButtonStyle and CompilerRemediation; no second job on an occupied runner.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
