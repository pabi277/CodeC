# Phase 5 Part 5.2 — Web preview (HTML/CSS/JS in an in-app WebView)

**Status: 🚧 IN PROGRESS (design recorded; code + host tests next).** This is
the "web preview" candidate from `IDEA_BACKLOG.md` §F (the owner's #1
recommended "biggest feel for least work") and
[`../PHASE5_ROADMAP.md`](../PHASE5_ROADMAP.md). It is **client-side only** —
no userland change, no package rebuild, no repository re-publish.

---

## 1. Decision D1 — scope of this part

The backlog item F has several slices. This part implements **slice 1** only:
a static-site preview of an **HTML file and its sibling CSS/JS/images** in an
in-app `WebView`, plus the two entry points and a light live-reload/console
loop. The bigger slices (multi-page "whole project" local server, Spck-style
device toolbar, console *error* panel with click-to-source) are **out of
scope here** and listed in §6.

| Decision | Choice |
|---|---|
| Renderer | Android `WebView` (Chromium), loaded with `loadUrl("file://…")` so the document's own URL is the HTML file and relative `<link href="style.css">` / `<script src="script.js">` / `<img src="…">` resolve from the project directory automatically. No local server needed for static sites. |
| File access | `javaScriptEnabled = true`, `domStorageEnabled = true`, `allowFileAccess = true`, `allowFileAccessFromFileURLs = true` (needed for a `file://` page to load sibling `file://` assets), `allowUniversalAccessFromFileURLs = false` (stays locked down). targetSdk is 28, so file access is the platform default and needs no runtime permission. |
| Entry point A — Editor | A **Preview** (eye) action in the editor's TopAppBar, shown only when the open file is `.html`/`.htm`. It saves the buffer, then navigates to the preview. |
| Entry point B — File Manager | A **Preview** action in each web file's ⋮ menu (shown only for `.html`/`.htm`). |
| Live reload | The preview polls the HTML file's `lastModified()` (~700 ms) and reloads the WebView when it changes, so an editor Save (or a terminal `echo > index.html`) is reflected without re-navigating. A manual **Refresh** button is always available. |
| JS console | A bottom strip surfaces `WebChromeClient.onConsoleMessage` (`log`/`error`/`warn`/`info`), keeping the last 200 lines. (Console errors are shown here; the click-to-source output panel is deferred.) |
| Web files as first-class | `.html`/`.htm`/`.css`/`.js` join `.c` as listable/creatable project files. A bare name still defaults to `.c` (C behavior unchanged); a name with a recognized web extension keeps it. The editor no longer forces `.c` onto an already-`.html`/`.css`/`.js` name. |

### Why not the local-server route now

The backlog's alternative (run `python3 -m http.server` in the userland and
point the WebView at `http://127.0.0.1:8080`) is more faithful for
"whole-project" sites with absolute `/` paths and fetch/XHR, but it couples
preview to the userland install, port management, and lifecycle. Static
`file://` preview covers the primary use case (an HTML page with local CSS/JS)
with zero server, so it is the right first slice.

## 2. Implementation map

- **New** `ui/utils/WebFileSupport.kt` — pure, host-testable:
  - `isHtml(name)` / `isWeb(name)` (`.html`, `.htm`, `.css`, `.js`);
  - `normalizeFileName(name)` — keep `.c`/web extensions, else append `.c`
    (the single source of truth for "don't clobber web extensions");
  - `starterContent(name)` — per-type template for `createFile`.
- **New** `ui/viewmodels/WebPreviewViewModel.kt` — `console` + `reloadTick`
  + `error` StateFlows, `watch(file)` mtime-polling coroutine, `addConsole`,
  `requestReload`, `reportError`/`clearError`.
- **New** `ui/screens/WebPreviewScreen.kt` — `AndroidView` WebView + toolbar
  (back / title / refresh) + console strip + the load/reload `LaunchedEffect`s.
- **Edit** `ui/navigation/Screen.kt` — add `Screen.Preview` route
  (`preview?fileName={fileName}`).
- **Edit** `MainActivity.kt` — nav destination for `Preview`; wire
  `onOpenPreview` from the Editor and `onPreviewFile` from the File Manager.
- **Edit** `ui/screens/EditorScreen.kt` — Preview action (HTML only).
- **Edit** `ui/screens/FileManagerScreen.kt` — Preview action in the ⋮ menu
  (HTML only).
- **Edit** `ui/viewmodels/EditorViewModel.kt` — use `normalizeFileName` in
  `updateFileName`/`saveFile` (replaces the hardcoded `.c` forcing).
- **Edit** `ui/viewmodels/FileManagerViewModel.kt` — use `normalizeFileName` +
  `starterContent` in `createFile`/`renameFile`.
- **Edit** `ui/utils/FileManager.kt` — `listFiles()` also lists web files.
- **New** test `WebFileSupportTest.kt` — the pure logic above.

## 3. Invariants (none weakened — checked)

- No compiler/userland change; TCC, `cc`, `pkg`, the `CodeCApi` bridge, and the
  bootstrap installer are untouched.
- `allowUniversalAccessFromFileURLs` stays `false` (no loosening of the WebView
  security surface); only the user's own project directory is ever loaded.
- The `.c` default for a bare new-file name is preserved, so the C flow's
  behavior is unchanged; the only new behavior is that an explicitly web-typed
  name keeps its extension.

## 4. Exit condition

On a real device with the new APK:

1. Create `index.html`, `style.css`, `script.js` in the project dir (via File
   Manager "New File" with those names, or the terminal `$CODEC_PROJECTS`).
   `index.html` references `style.css` and `script.js` relatively.
2. Open `index.html` in the editor → tap **Preview** → the page renders with
   the CSS applied and the JS executed (e.g. `document.title`/a DOM node
   updated, and a `console.log` line appears in the bottom console strip).
3. Edit `style.css` or `index.html`, **Save**, return to Preview → the change
   appears (live reload) without re-navigating; the manual Refresh button also
   works.
4. File Manager shows the web files, and its ⋮ → **Preview** opens the page.
5. A non-HTML file never shows the Preview action; a bare "New File" name still
   creates a `.c` file (no C regression).

### Device verification recipe (for the owner — exact copy-paste)

```sh
# In the CodeC terminal (or via File Manager > New File):
cd "$CODEC_PROJECTS"
cat > index.html <<'EOF'
<!doctype html>
<html><head><link rel="stylesheet" href="style.css"><title>hi</title></head>
<body><h1 id="t">hello</h1><script src="script.js"></script></body></html>
EOF
cat > style.css <<'EOF'
body { background: #123; } h1 { color: gold; }
EOF
cat > script.js <<'EOF'
document.title = "preview-ok";
document.getElementById("t").textContent = "JS ran!";
console.log("script.js executed");
EOF
```

Then in the app:

1. File Manager → confirm `index.html`, `style.css`, `script.js` are listed.
2. Open `index.html` (tap it) → the editor shows the HTML → tap the **eye**
   (Preview) → expect the page to show "JS ran!" in gold on a dark blue
   background, with the title "preview-ok".
3. Console strip at the bottom shows `log: script.js executed`.
4. Edit `style.css` → `h1 { color: lime; }` → Save → go back to Preview (or
   already there) → the heading turns green (live reload).
5. Tap **Refresh** → reloads.

## 5. Evidence

### 5.1 Host (this session)

Pure logic (`WebFileSupport`) unit tests. The WebView itself is Android-only
and cannot run on the host JVM; it is exercised only on device.

### 5.2 CI

Push → "Build APK" (assemble + unit tests + lint) must be green.

### 5.3 Device

The §4 recipe; to be filled in with the owner's transcript.

## 6. Out of scope (future slices)

- Local-server "whole project" preview (`python3 -m http.server` in the
  userland + WebView at `http://127.0.0.1:PORT`) for absolute paths / fetch /
  XHR / multi-page sites.
- Spck-style device-width toolbar, viewport toggling.
- Console **error** click-to-source / a full output panel.
- Live reload without polling (e.g. `FileObserver`).
