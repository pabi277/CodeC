# Phase 49.3 — Web Preview Back follows page history

> **Status:** ✅ IMPLEMENTED; Build APK CI run [`36724487487`](https://github.com/pabi277/CodeC/actions/runs/36724487487) passed host unit/screenshot tests and debug/release APK builds. No device-specific verification is claimed here.
>
> **Request:** Back from a WebView should visit its previous web page before leaving the preview for the editor. Apply the same behavior to Android system Back and the preview toolbar arrow.

## Behavior

1. If the preview's WebView has a previous history entry, system Back calls the WebView's native `goBack()` once. A later press checks the updated history again, so pages are traversed one at a time.
2. The toolbar Back arrow uses the same history-first decision.
3. If WebView history is empty, the preview route is popped through its existing `onNavigateBack` callback.
4. The system handler leaves the IME and open dropdown/dialog surfaces to their existing owners. The explicit toolbar arrow still uses the history-first path.

## Implementation

The policy remains centralized in `ui/navigation/BackRouter.kt`:

- `BackState.webViewCanGoBack` carries the current WebView history fact.
- `BackAction.GoBackInWebView` wins before `PopRoute`.
- The preview reads `webView.canGoBack()` when Back is pressed rather than caching history in Compose state. This avoids adding a history observer whose state could lag native WebView navigation.
- Both preview Back affordances call the same screen-local dispatcher. It maps `GoBackInWebView` to `webView.goBack()` and `PopRoute` to `onNavigateBack()`.
- System Back remains disabled while the keyboard or a dropdown/dialog is active, so those surfaces retain their existing Back handling.

The current shared precedence rows relevant to navigation are: row 9, keyboard → platform (`None`); row 10, previous WebView page → `GoBackInWebView`; row 11, available app route → `PopRoute`; row 12, root exit prompt/exit; row 13, otherwise let the library own Back. Earlier modal, editor, onboarding, and panel rows retain their prior precedence.

No page-loading rules, WebView security settings, game assets, or preview-server behavior changed as part of this follow-up.

## Tests and verification

- `BackRouterTest`: a modal retains Back before WebView history; the keyboard is not consumed; WebView history wins over route pop; empty WebView history falls through to route pop.
- `BackHandlerWiringTest`: the screen's system handler and toolbar button both use the history-first router path, and the complete app handler census includes `WebPreviewScreen`.
- Build APK run [`36724487487`](https://github.com/pabi277/CodeC/actions/runs/36724487487): host unit/screenshot tests, debug APK assembly, and release APK assembly passed.

The manual acceptance check is to open a multi-page site in CodeC Preview, follow two in-page links, then press system Back and the toolbar arrow in turn. Each press should return exactly one page; after reaching the first page, the next press should return to the editor.
