# Proposed Phase 68.1 — Editor chrome, tabs and file actions

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/EditorScreen.kt:217`](../../app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt#L217), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 122157 and 124105 show the actual top/tab rows. Do not use the earlier drawn mockups as higher authority.

## My recommendation

Keep the thin top bar, separate bounded tab row, and menu outside the fold. Focus on overflow, discoverability, active/dirty state and long file names.

## Bounded review checklist

No file; one/many tabs; dirty marker; sorting/close-unmodified; hide and reveal; find/replace; rename; format; diagnostics; save failure; line endings; single-file save-to-project.

## Your thoughts — ask before code

**Which actions do you want visible without opening the menu: Save, Undo/Redo, Format, or just the existing Search and Run?**

Suggested starting point, not your decision: Keep Search and Run on top; avoid adding a second permanent toolbar until you choose priority actions.

**Owner answer:** pending. Record it verbatim here when given; do not present the
recommendation as approval.

## Implementation and exit, after agreement

EditorShellUi, TabClosePolicy, TabSortPolicy, TabLayoutPolicy and EditorChrome; protect the owner-confirmed 48dp tab-height fix.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
