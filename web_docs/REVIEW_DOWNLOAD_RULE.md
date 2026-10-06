# Mandatory end-of-phase website download — owner law

**Effective 2026-10-07 · D31 · all website phases W1–W6 and subsequent website phases.**
Owner's literal instruction: **“Now make this a hardcore rule every phase end
the download able zip link provide”**, followed by **“Now start w2”**.

A website phase is **not handed off as complete** until the owner has a direct,
phone-usable GitHub ZIP download link for the current website. An Arena file card,
workspace path, screenshot, authenticated sandbox URL, git patch or whole-repo ZIP
is **not a substitute**. Review must work **before merging or deploying**.

## Required procedure for every phase

1. Finish the phase's content and applicable checks. Keep all earlier working
   pages; preserve the existing shared chrome and source laws.
2. Package **the entire current `website/` contents**, not just the phase delta.
   Files at archive root: index.html, style.css, favicon.svg and all implemented
   pages/assets with their relative subdirectories preserved. No enclosing folder
   required. No app files, docs, APKs, credentials, tooling, caches or nested ZIPs.
   Refuse symlinks or files outside website/. Do not invent placeholders for later pages.
3. Name the snapshot **CodeC-website-WN.zip**, store in that phase's web record
   folder **outside website/**. It is a review artifact, never a runtime asset or
   site build dependency. Keep past phase archives labelled historical; do not
   silently serve an old one as the newest.
4. Test ZIP CRCs and archive path safety; compare exact file list and bytes with
   current website/. Extract the ZIP and test the delivered pages offline,
   including local CSS/icon and links between implemented pages. Record expected
   future 404s separately, not as passing links. Record size and SHA256.
5. Commit the artifact with the phase/web living records; push only the current
   Arena session branch. **No PR, merge, release or Pages deployment is needed**
   to make a public repository branch file downloadable.
6. Confirm the uploaded file through GitHub (size + decoded bytes/hash). Prefer
   a commit-pinned direct raw URL in the final report so it cannot silently drift.
   Provide the GitHub file-page URL as a fallback. Try an unauthenticated GET;
   if sandbox egress prevents it, disclose that limitation and separately record
   the GitHub API readback. Never claim an unperformed public-download test.
7. **Final report must include the clickable direct ZIP link**, phase/commit,
   checks, remaining scope and phone instructions: download → CodeC Projects →
   + → Import ZIP → index.html → RUN. GitHub outbound links need internet; the
   site itself renders locally. State unbuilt pages honestly.
8. If a phase is blocked or partially complete, still offer the current review
   snapshot with an explicit **partial / not complete** label; never claim the
   phase gate closed merely because a ZIP exists. Refresh on later corrections.

## Gate and scope unchanged

- ZIP delivery is mandatory **in addition to**, not instead of, phase checks,
  source trace, W4.2 verification, W5/W6 owner device transcripts and W6 Pages gates.
- No PR/merge or deployment without the owner's explicit command. Deploy preparation
  is currently paused; review archives do not change that decision.
- `rule.md` remains the shared manual; this website-specific law lives in web_docs
  to avoid editing app-workstream files. All phase READMEs and the master acceptance
  criteria reference it; web_prompt and NEXT_STEPS carry it to future sessions.
- W1 archive in chat-web4 remains its historical three-file snapshot. W2 and each
  later phase must have their own archive with the whole then-current site.
