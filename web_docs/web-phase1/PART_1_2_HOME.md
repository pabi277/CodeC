# CodeC Website Phase W1.2 — Home page

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** M
· **Depends on:** W1.1 (chrome + stylesheet)
· **Target file:** `website/index.html` (replaces the W1.1 skeleton main)

> Source: `README.md` (intro + feature sections) for every claim; termux.dev
> landing structure (public) for the page shape.

---

## 1. Design — page structure (top to bottom)

1. **Hero** — `<h1>`: "CodeC — a C programming IDE for your Android phone."
   + one paragraph (≈3 sentences, distilled from README intro): built-in
   offline C compiler, real terminal, package hub, in-app web preview.
2. **CTA row** — primary button **"Get the APK on GitHub"** →
   `https://github.com/pabi277/CodeC/releases` (canonical Releases URL —
   never an artifact URL, they rot); secondary button **"Read the README"**
   → `https://github.com/pabi277/CodeC`.
3. **Feature callout grid** — 6 cards (2-col → 1-col), each a short title +
   2–3 sentences + one link. Copy per plan §3.1, every sentence sourced:

   | Card | Claim basis (README) | Link |
   |---|---|---|
   | Built-in compiler | TCC embedded in APK; offline, instant, no Termux; arm64 + x86_64 | `engines.html` |
   | Real terminal | VT/ANSI terminal, PTY; `cc file.c` → `./a.out` | `start.html` |
   | Package hub | 25+ signed packages, 1-tap install & run, live badges | `packages.html` |
   | Spck-grade editor | Projects hub, tabs, file tree, honest git, autosave | `about.html` |
   | Web preview | HTML projects preview in-app, live reload, console | `start.html` |
   | Always updatable | Settings → Install APK from GitHub; Releases/CI builds | `install.html` |

4. **Learning banner** (`.learning-banner`, visually distinct — the course
   is a first-class citizen): "New to the command line — or to C? **Master
   CodeC from Zero to Advanced.** 17 hands-on chapters, free, on this site."
   → `learn.html`.
5. **Footnote strip** — "Free & open source · Built-in compiler · No Termux
   required" + GitHub link.

### Meta

- `<title>`: "CodeC — C programming IDE for Android".
- `<meta name="description">`: the hero paragraph, ≤160 chars.

## 2. Implementation steps

1. Write the page inside the W1.1 chrome.
2. Write card copy (2–3 sentences each); for **each card record its source
   line** in `chat-web2/` (traceability requirement, plan §4).
3. Add meta tags; verify the CTA URLs resolve (200).
4. Re-run the self-dependent sweep (plan §5.5) — record in `chat-web2/`.

## 3. Exit condition

```text
1. At 360 px and 1440 px: hero → CTA → 6 cards → learn banner → footnote,
   in order, no overflow, no console errors.
2. "Get the APK on GitHub" → the Releases page (200); "Read the README" →
   repo root (200).
3. Learn banner → learn.html uses the final URL (expected 404 until W4 —
   full resolution happens in the W6 sweep; noted in chat-web2/).
4. Every card claim has a recorded source line; self-dependent sweep PASS.
5. W1 total check: Home + chrome render at 360/1440 with zero external
   resources → W1 COMPLETE; report + stop at the merge gate.
```
