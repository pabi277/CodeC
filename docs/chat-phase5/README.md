# CodeC Phase 5 Documentation

Phase 5 is the next phase after Phase 4 (Parts 4.1–4.8, complete and
device-verified 2026-08-26). Its planning-only skeleton is
[`../PHASE5_ROADMAP.md`](../PHASE5_ROADMAP.md). Candidate areas: more
Termux:API-style capabilities over the `CodeCApi` bridge, the two known
client fixes from the 4.5/4.6 post-review (KI-1, KI-2), and the deferred
GUI/catalog/root areas.

## Parts & Status

- **[Part 5.1 — Client fixes KI-1 & KI-2](PART_5_1_KI_FIXES.md):** ✅ **DONE
  (device-verified 2026-08-26).** KI-1: `pkg install` of an already-newest
  package reports success (treats apt's "0 newly installed" as success).
  KI-2: `$PREFIX` canonicalized to the dpkg-recorded `/data/data/…` spelling
  at shell setup, with the `CodeCApi` bridge made spelling-invariant.
- **[Part 5.2 — Web preview (HTML/CSS/JS in WebView)](PART_5_2_WEB_PREVIEW.md):** 🚧 **IN PROGRESS.**
  Preview an HTML file and its local CSS/JS in an in-app WebView, with a
  Preview action in the editor/file manager, live reload on save, and a JS
  console strip.

Each part gets its own record here with Decision D1, an exit condition, and
an evidence section (§5.x host + device), same as Parts 4.1–4.8.
