# Proposed Phase 72.1 — Web preview, console and viewport tools

**Status: discussion draft, not approved for implementation.** One part = one
future chat. No deadline, dependency, new control or replacement engine promised.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/WebPreviewScreen.kt:90`](../../app/src/main/java/com/codeci/ide/ui/screens/WebPreviewScreen.kt#L90), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** No preview reference shot. Phase 61 is implemented; remaining WebView/device evidence is not invented.

## My recommendation

Polish the newly built tools rather than building a second browser. Keep the webpage viewport primary and tools easy to dismiss.

## Bounded review checklist

Static/live page; load error/retry; hamburger links; reload; open browser; LAN sharing/QR; console empty/error/filter/resize; observed-request Network; zoom; fitted resolution; narrow landscape.

## Your thoughts — ask before code

**Should Console stay in the menu, or have a permanently visible shortcut?**

Suggested starting point, not your decision: Keep the menu entry unless console use is frequent. Do not call observed Network entries a full browser DevTools capture.

**Owner answer:** pending. Record it verbatim here when given; do not present the
recommendation as approval.

## Implementation and exit, after agreement

PreviewToolsPolicy, PreviewChromePolicy, PreviewToolsPanel, PreviewWebView and ServerRegistry; keep request redaction and no injected DOM inspector.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
