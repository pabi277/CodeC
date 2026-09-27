# 61.2 — Zoom and screen resolution

Plan before code:
- Existing hamburger gains Zoom and Screen resolution dialogs. Existing copy,
  browser, LAN, server controls and Refresh stay on their existing handlers.
- Zoom presets 50/75/100/125/150/200 percent. Native WebView zoom support;
  initial scale configured for subsequent loads, zoomBy for the current page
  without a reload (forms must not be re-submitted). Pinch remains available;
  selecting a preset re-applies that target, including 100% reset.
- Resolution chooser: Device (default), 360×640, 768×1024, 1280×720 logical dp.
  A bounded native WebView is measured at that viewport; a Compose layer fits
  it in available space without changing its measured dimensions. Device
  uses available bounds, including panel/IME changes. Preview remains clipped
  to its page area. No user-agent/DPR/device emulation and no page injection;
  page viewport meta and WebView's own zoom limits still apply.
- Explain logical dp and fit-to-screen in dialog. Viewport and zoom are
  screen-session settings, not project-file mutations or global preferences.

Device checks: responsive page with viewport meta; verify each size against
window.innerWidth/innerHeight at 100%; zoom in/out/reset without reload; change
resolution while panel open, rotate, touch links and form fields in fitted
viewport; Refresh, live reload and Open in browser still work.


## Implementation / test verdict

Built in `5707e38`, CI ✅ `36300428167`. `PreviewWebView.applyZoom` applies
initial scale for later loads and zoomBy for the current page. The screen uses
requiredSize for native viewport measurement, graphicsLayer for visual fitting,
and clips the page to its remaining area. No injected script or duplicate
WebView. Geometry policy, native settings/callback and wiring tests are green;
on-device rendering/touch/zoom behaviour is still pending (P8–P11).
