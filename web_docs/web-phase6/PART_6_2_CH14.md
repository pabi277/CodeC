# CodeC Website Phase W6.2 — Chapter 14: Web Projects & Live Preview

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4 (W4.2 facts: loopback server behavior, live reload,
  console location, fetch/modules support)
· **Target file:** `website/ch-14.html`

---

## 1. Content

- **Goal box:** build a real multi-file website (HTML + CSS + JS + data)
  and preview it on your phone — with live reload — no computer, no
  hosting.
- **Need:** Chapters 06 (projects), 12 (what `127.0.0.1` means) done.

### Steps

1. **The idea** — on a phone, "open index.html" is usually the dead end
  (file:// can't load siblings). CodeC's answer: the project folder is
  served by a **loopback HTTP server** at `http://127.0.0.1:<port>/` — the
  phone itself hosts your site for the app's browser.
2. **Create the project** — `+` → New Project → `my-site`; four files in
   the tree: `index.html`, `style.css`, `app.js`, `data.json`.
3. **The four files, minimal real** — each in a code block (~10 lines):
   HTML that links the CSS and the JS; CSS with one custom property; JS
   that does `fetch("data.json")` and renders one line; a one-key JSON
   object. (Relative paths *just work* — that's the whole point, step 1.)
4. **RUN is the preview** — open `index.html` → tap **RUN** ▶: the page
   renders in-app; the **console shows under the page** (a JS error? it's
   right there).
5. **Live reload** — change the CSS color → save (~2 s autosave) → the
   preview reloads itself; no retap; this is the fastest design loop a
   phone can have.
6. **A bit further** — ES modules work over the loopback (`<script
   type="module">`, one 3-line example); the `file://` fallback exists
   when the server can't start (plain paragraph: you lose sibling
   loading — that's why the server matters).
7. **Ship it later** — the project exports (chapter 06) as a ZIP/folder
   you can host anywhere; nothing in this chapter is CodeC-specific web
   tech — it's plain HTML/CSS/JS.

- **Try it:** (1) add a second button in `app.js` that increments a counter
  (watch live reload on save); (2) break the CSS link on purpose, read the
  console under the page, fix it; (3) add `module.js` imported by
   `app.js` and confirm the module ran (console.log line).
- **Mistakes:** typing the full `http://127.0.0.1:port/…` URL by hand
  (RUN opens it for you); expecting the preview on a *second* phone (the
  server is loopback — the phone itself, chapter 12); editing
  `index.html` while the preview shows another project's folder (the
  preview always follows the project you're in — the folder-switch fix from
  the app's history, stated as "it follows the project you're in");
  `fetch` on the `file://` fallback (won't load siblings — use RUN).

## 2. Implementation steps

1. Build `ch-14.html` (crumb "Chapter 14 of 17"); the 4-file example must
   be complete and consistent (cross-links between the files correct).
2. Server/reload facts from W4.2; source notes in `chat-web6/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-13, next → ch-15.
2. The 4-file example is internally consistent (would actually run —
   reviewer's note in chat-web6/).
3. Loopback/live-reload/console facts == W4.2 (noted); 360/1440 clean;
   sweep PASS.
```
