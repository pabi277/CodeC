# Proposed Phase 74.1 — Settings, support and final consistency

**Status: owner-approved and implemented 2026-09-29 in the same chat as 65.1.** See [`docs/chat-phase74/README.md`](../chat-phase74/README.md). CI pending; no PR/merge.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/SettingsScreen.kt:126`](../../app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt#L126), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 124058 is a reference Account/shop screen, not CodeC requirements. AI/account/shop controls stay unbuilt; the reserved AI slot belongs to the owner.

## My recommendation

Keep the searchable sections and finish a truthful control inventory. Review misleading labels before adding preferences. Apply consistency checks across every screen, not a new theme.

## Bounded review checklist

64 Settings rows; search/empty/folded sections; theme/font/keyboard settings; storage/export; GitHub credentials; updates; About/licenses; feedback/exit survey; crash recovery; Templates; logs; destructive dialogs.

## Your thoughts — ask before code

**Should the exit-feedback prompt remain, become opt-in, or be removed? Should “Show the welcome screen again” be renamed or removed now that launch seeds a sample?**

Suggested starting point, not your decision: Discuss both explicitly: guide removal does not authorise deleting feedback or changing reset semantics. Keep credentials and destructive actions clearly separated.

**Owner instruction:** “All recommended approved”. Keep the exit-feedback prompt and “Show the welcome screen again” behavior unchanged. Additional approved choices: editor font default 16sp; every Settings group always starts collapsed; inline ghost text defaults off.

## Implementation and exit, after agreement

SettingsSearch, SettingsManager, FeedbackScreen, ExitFeedbackDialog, CrashReportOverlay, TemplatesScreen, LogsScreen, CodecTokens and CodecMotion.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
