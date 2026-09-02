# CodeC Website Phase W6.7 — Deploy (GitHub Pages) + verification + link sweep

**Status:** 📋 **PLANNED** · **Cost:** `[static]` + **the one allowed
exception** (D7): `.github/workflows/pages.yml` + one line in the root
`README.md`
· **Depends on:** W6.6 (polish + sweeps green)
· **Blocks:** nothing — this part makes the site **live**

---

## 1. Design

1. **Pages source (proposed; record the final choice in DECISIONS.md):**
   *main branch, `/website` folder* — simplest, and it matches D7's
   layout; a `gh-pages` branch only if the owner prefers (extra moving
   part, no benefit here).
2. **Workflow** — `.github/workflows/pages.yml`: deploy `website/` to
   GitHub Pages on push to `main` (standard Pages deployment; the file
   itself is added in W6.7 — it touches nothing else in the repo).
   The APK workflow (`build-apk.yml`) and the package-repo workflow are
   **not modified** — the Pages workflow is additive only.
3. **URL** — `https://pabi277.github.io/CodeC/` (project Pages URL for a
   folder under `main`); custom domain only if O2 says so.
4. **The one README line** — added in the same commit: a link to the site
   (e.g. under the intro: "Website & free course: <url>"). This is the
   single allowed cross-workstream edit (D7).

## 2. Implementation steps (verification BEFORE live)

1. **Pre-flight (the site is not public until these pass):**
   - W6.6's final self-dependent sweep is green (plan §5.5: grep — no
     external `src=`/`<link href=`/`@import`/`url()`; only `<a href>`
     carries http(s)).
   - **Offline render check:** the site loaded with networking disabled
     (devtools offline mode or a local file copy) — all 25 pages render
     fully (layout + styles + content). Result recorded in
     `chat-web6/OFFLINE_CHECK.md`.
   - **Link sweep:** every internal URL (25 pages) + every outbound link
     (github.com repo/README/Releases/Issues/JOURNEY, package repo URL,
     the two Termux-setup links from `/install`) resolves (200/redirect).
     Table of URL → status in `chat-web6/LINK_SWEEP.md`.
2. **Deploy:** add the Pages workflow; push (session branch — the owner
   merges to `main`, same gate as every phase; Pages goes live from
   `main`).
3. **Post-deploy verification (on the live URL):** open all 25 live URLs
   once (Home, 6 product, /learn, ch-01…ch-17); confirm the Pages build
   is green in Actions; record live-URL checks in `LINK_SWEEP.md`
   (live section).
4. **Close the arc:** update `web_docs/NEXT_STEPS.md` head state with the
   **live URL**; `WEB_JOURNEY.md` entry (W6 closed, site live);
   `web_prompt.md` state line (W6 complete); mark O2/O4/O7 as answered or
   still-open truthfully.

## 3. Exit condition (W6 & the whole W0→W6 arc)

```text
1. PRE-FLIGHT: self-dependent sweep PASS + OFFLINE_CHECK.md PASS +
   LINK_SWEEP.md all-green — committed BEFORE the deploy push.
2. Pages workflow green on the merged commit; all 25 live URLs opened
   once and recorded.
3. README one-line link present (the only cross-workstream edit).
4. Living docs carry the live URL (NEXT_STEPS head state, WEB_JOURNEY,
   web_prompt.md); plan §10 acceptance criteria checked off one-by-one in
   chat-web6/ (25 pages · traceability · self-dependent · 360/1440 ·
   course quality · deployed · no app/CI changes · docs updated).
5. Report: what changed, tip sha, run id, the live URL, any device-pass
   items still owed (ch-08 / P1+P5 transcripts) → STOP at the merge gate
   (the deploy itself waits for the owner's merge, like everything else).
```
