# Phase 61 — Preview tools

> **Owner update — 2026-09-27:** Top-level appearance looks good; further device
> testing for this delivery is declined, **not passed**. The owner explicitly
> authorised merging via [PR #86](https://github.com/pabi277/CodeC/pull/86),
> subject to final CI. Future detail-by-detail polishing phases will be defined
> in new chats. [Full owner feedback and handoff](../OWNER_HANDOFF_20260927.md).

Status: **BUILT, CI GREEN**; remaining device round declined by the owner (not run). Merge authorised via PR #86, subject to final CI.

## Reference lock and inventory (2026-09-27, before edits)

Re-read all seven real `docs/spck-ui/Screenshot_20260922_*.jpg` shots in a
contact sheet, with 124105 read full-size. 122157/124105 show the editor;
122203 files, 124049 navigation, 124052 search, 124055 repository, 124058
Account. None shows a preview console or a viewport chooser. The HTML source's
Content/Home/More belongs to the user's page, not CodeC chrome.

Reject: store art, `0*.png` drawings, SPCK Account/shop, and the withdrawn
“remove the whole bottom bar” sentence. Behaviour is our implementation;
no SPCK source/assets/engine copied. The owner's already-recorded answers
explicitly authorise unseen console filters, resize, Network, zoom/resolution.
Elements/DOM injection and Resources are NOT in this phase.

Before-edit inventory:
- `ui/screens/WebPreviewScreen.kt:239-320`: existing hamburger links + Refresh.
- Same file `:407-428`: console exists only when nonempty, fixed 120.dp, last
  60 strings displayed without structured levels.
- Same file `:394-406`: weighted WebView; `:506-518`: plain WebViewClient and
  existing WebChromeClient forwarding console messages.
- `ui/viewmodels/WebPreviewViewModel.kt:23-44`: 200-line StateFlow, existing
  clear and reload actions; `:66-82`: polling file watcher.
- `ui/services/PreviewChromePolicy.kt:89-103`: existing ordered link policy.
(All paths above under `app/src/main/java/com/codeci/ide/`.)

Parts: [console + Network](PART_61_1_CONSOLE.md),
[zoom + resolution](PART_61_2_VIEWPORT.md).

Gate: pure policy tests, real Compose layout test, WebView callback/wiring
checks, existing regressions, push session branch, CI green, then device
verification. Never claim JVM tests prove on-device WebView rendering.

## Implementation / local gate

Implemented: `PreviewToolsPolicy` (typed levels, bounded/redacted request
records, sizing/fit rules), `PreviewToolsPanel` (one lazy scroll owner, empty
state, filters, clear/close, accessible drag handle), `PreviewWebView` (native
zoom, observation-only request callback, capture-session disposal), and the
existing screen's menu/layout wiring. Native WebView is retained under an error
overlay so a corrected target can load again. Watchers now cancel when replaced
or stopped; a live URL with no watched file cannot leave an old static watcher
running. No editor layout or project file changes.

Local gate: **27 passed / 0 failed** using Kotlin compiler and a temporary JVM
JUnit assertion shim (not an Android build): policy 10, wiring 4, existing
menu policy 8, existing menu wiring 4, composable annotation scan 1. Strings XML
parses and `git diff --check` passes. CI-only cases: diagnostics lifecycle and
worker capture (2), real panel measurement/accessibility action (1), native
WebView callback/settings/disposal (1). Full Android compilation subsequently passed in CI (verdict below).

Capture is in-memory for the current native WebView session (a recreated view
starts fresh, including after rotation). Reload retains entries; filters don't
delete them. No traffic is persisted or sent to telemetry.

## CI verdict — 2026-09-27

**5707e38**, first round **36300428167**, ✅ passed host unit/screenshot tests,
debug APK, release APK and release-artifact checks (no failed steps).
[Build + APK artifacts](https://github.com/pabi277/CodeC/actions/runs/36300428167).
No new dependency. Debug 26,013,768 bytes; release 6,780,344 bytes.

Live anchors (under `app/src/main/java/com/codeci/ide/ui/`):
- `screens/WebPreviewScreen.kt:317-329`: the three new menu handlers;
  `:419-469`: bounded page/panel + measured/fitted viewport; `:438-441` native
  disposal; `:471-477` dialogs; `:532-534` unchanged reload signal.
- `components/PreviewToolsPanel.kt:31-122`: bounded lazy panel; `:65` accessible
  progress action, `:67` drag, `:126` Zoom, `:143` resolution chooser.
- `components/PreviewWebView.kt:28-33`: zoom settings and initial scale;
  `:35-37`: request observation returning null; `:61-69`: zoom without reload;
  `:72-79`: capture session ended before native disposal.
- `viewmodels/WebPreviewViewModel.kt:45-77`: synchronised sessions/append/clear;
  `:97-113`: one cancellable watcher.
- `services/PreviewToolsPolicy.kt:25-60`: tested data and geometry rules.

[Device round P1–P14](DEVICE_ROUND.md) **not run**, with our own minimal HTML
fixture. Stop at the gate: no PR/merge/main push. Next planned phase **63**
(verify existing git pane + per-file Discard), not implemented in this phase.
