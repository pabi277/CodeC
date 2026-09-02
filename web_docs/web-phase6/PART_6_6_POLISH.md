# CodeC Website Phase W6.6 — Polish pass (all 25 pages)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W6.1–W6.5 (all pages exist)
· **Target files:** any/all of `website/*.html` + `style.css` (cosmetic +
  correctness edits only — no new sections, no new features)

---

## 1. Design — the pass, in order

1. **Content re-verification (first — not last):** re-read `README.md` and
   the W4.2 verified-facts table; sweep every page for drift (package
   count, engine table, install paths, CodeCApi facts). Drift → fix to the
   repo's current truth in the same commit; note each fix in
   `chat-web6/POLISH_LOG.md`.
2. **Meta consistency:** every page has a unique `<title>` + `description`
   (≤160 chars); the pattern is recorded (so future pages stay consistent).
3. **Navigation audit:** every internal link on every page resolves to an
   existing file (final URLs — no 404s left); every chapter's prev/next +
   crumb "Chapter N of 17" is correct; the course table on `/learn` and
   the chapter crumbs agree; the header active-nav state is right on all
   25 pages.
4. **Visual pass at 360 px and 1440 px** (all pages): overflow, table
   wrapping, code-block scrolling, contrast (WCAG AA spot-check on body
   text, muted text, accent-on-dark), focus visibility for the mobile nav
   toggle, footer consistency.
5. **Tone pass:** no marketing fluff slipped in; roadmap mentions stay at
   ≤1 line per page; "Termux" is only ever the *optional engine* (never
   a prerequisite framing); install copy never implies a store.
6. **Self-dependent re-sweep** (plan §5.5) after every edit — the polish
   phase is where stray external assets sneak in; final result recorded.

## 2. Implementation steps

1. Run the six sub-passes in order; keep a running `POLISH_LOG.md`
   (page · change · reason) in `chat-web6/`.
2. No new pages, no new CSS components (fixes use existing classes).
3. Commit at the end of the pass (one coherent "polish" commit is fine).

## 3. Exit condition

```text
1. POLISH_LOG.md committed; drift fixes (if any) verified against README.
2. 25/25 pages: unique meta, correct nav, no internal 404s (checked with
   the file list as ground truth).
3. Contrast spot-check recorded (pass/fail per token); overflow clean at
   both widths.
4. Final self-dependent sweep PASS (recorded) → ready for W6.7 deploy.
```
