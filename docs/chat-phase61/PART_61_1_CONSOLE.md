# 61.1 — Console and Network

Plan before code:
- Console menu entry toggles an initially closed diagnostic panel, including
  when both logs are empty. Tabs: Console / Network. Clear affects selected
  tab; Close restores page space. Log (including debug), Info, Warn, Error
  filters start all enabled; none enabled is allowed and has an empty state.
- Structured console entries, max 200, each string bounded. Filtering is view
  policy, never deletion. Network also max 200, safe worker-thread capture.
- Drag the panel's accessible resize handle; slider accessibility action is
  available too. Clamp to available page+panel height, max 65%, no stolen full
  viewport. Requested height survives close/open; short screens can scroll
  panel controls. The parent owns available height, not screenHeightDp.
- Network uses `shouldInterceptRequest(WebView, WebResourceRequest)` and returns
  null. HTTP(S) observations only, method + address + main/subresource. No
  second HTTP request, bodies, headers, cookies, status, duration or success
  claims. Strip URL credentials/query/fragment; label omissions in UI.
- Captured events are in memory, isolated by session; stale disposed WebView
  callbacks are ignored. Reload retains observations until Clear.

API evidence: Android WebViewClient reference (read 2026-09-27):
https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest)
Callback runs off UI thread; returning null preserves normal loading; redirects
report initial URL only; javascript/blob and packaged-asset URLs aren't covered.
This is an observed-request log, not a full browser devtools waterfall.

Device checks: open empty panel; log each level; toggle each/all off/on; drag
both limits, rotate/raise keyboard; Console/Network independently clear; navigate
and refresh CSS/JS/fetch page; ensure requests still load once; reopen another
project and confirm old callbacks cannot populate the new session.


## Implementation / test verdict

Built in `5707e38`, CI ✅ `36300428167`. `PreviewToolsPanel` is one bounded
LazyColumn, so headers remain scroll-reachable and off-screen logs aren't all
composed. Session guards reject stale callbacks; filters do not delete data.
Pure tests + worker/session tests + real WebView callback test + Compose
measurement/accessibility test passed in the full CI host test task.
Device P1–P7, P11–P14 pending; see [DEVICE_ROUND](DEVICE_ROUND.md).
