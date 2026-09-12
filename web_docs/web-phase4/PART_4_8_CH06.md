# CodeC Website Phase W4.8 — Chapter 06: Files & Projects (safe walk + ProjectLink + export-all)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** M
· **Depends on:** W4.2 gate v2.2
· **Target file:** `website/ch-06.html`

> **v2.2 update (2026-09-12):** safe folder walk bounded Throwable-safe TreeWalkPolicy budgets progress+cancel per-provider failure as message Throwable at boundary grant persisted takePersistableUriPermission never called today; ProjectLink + ProjectLinkPolicy + persisted SAF grant + noexec mirror sync in on open/save push-set on save-back excludes CodeC own outputs 39.1 must land first; export-all ZIP over both project roots byte-identical; two project roots filesDir + externalFilesDir candidate; ZIP caps 10k entries 128 MB total-bytes cap path-escape; outputs temporary RunArtifacts + RepoHygiene ~60 patterns .codec/ user's .gitignore wins.

---

## 1. Content

- **Goal box:** create a real project; move files between single-files and projects; import and export (including export-all); know exactly where everything lives (two roots); understand safe folder walk + open folder as project + noexec mirror + outputs temporary never in repo.
- **Need:** Chapters 02–03 done.

### Steps

1. **Two home bases** — *single files* (quick experiments, where chapter 02 happened) vs *projects* (real work, private folders `filesDir/CodeC/projects/<name>` — exact path from W4.2 facts + externalFilesDir fallback `getExternalFilesDir(null)/CodeC/projects` candidate list FileManager.projectDirCandidates). When to graduate a file: "Save to project…" from file sheet. Projects live in app-private storage so `./a.out` executable; emulated storage mounted noexec so linked project runs from internal mirror.
2. **The Projects hub** — card list (type mark, branch · file count · age, change badge, amber **↑N** when commits never reached remote — preview of chapter 11, official file icons Seti MIT Phase 34), filter chips + search, and ONE `+` sheet with four doors: **New Project / Clone Git Repo / Import ZIP / Open Folder** (one line each; clone = chapter 11 deep dive). Mention first-hour tiles Phase 33.1 three starter tiles C/Python/HTML DataStore first_launch_complete.
3. **Create a project** — `+` → New Project → name it `first-project` → open it; nav-drawer file tree appears with in-tree git status letters + official file icons.
4. **Move a file into it** — from single files: "Save to project…" → `first-project`; file now lives in tree; tabs re-key, terminal follows project; app opens straight into file you left in (first launch → Projects hub) autosave ~2s after stop typing.
5. **Import & export v2.2** — Import ZIP (from shared file) with caps MAX_ZIP_ENTRIES 10k MAX_ZIP_ENTRY_BYTES 128 MB total-bytes cap path-escape check per entry finally temporaryZip.delete(), Open Folder (SAF picker ActivityResultContracts.OpenDocumentTree) with safe walk: plain recursion copyDocumentChildren no visited set no depth/file/byte budget no cancellation opens every file stream serially caller catches only Exception — provider returning parent as own child is StackOverflowError Error not Exception kills app (owner second sentence) plausible code-visible cause; replaced with iterative walk planned by pure TreeWalkPolicy budgets progress+cancel per-provider failure as message Throwable at boundary grant persisted takePersistableUriPermission zero call sites today (Phase 43.1). Export project (SAF/ZIP) and **export-all** Projects hub → ⋮ → Export all projects backup ZIP over both project roots (filesDir + externalFilesDir candidate list) byte-identical round-trip host test ProjectTransferExportAllTest re-imports into clean install; ZIP share via FileProvider.
6. **Open folder as project (ProjectLink) v2.2** — Phase 43.2: ProjectLink(projectName, treeUri, …) + pure ProjectLinkPolicy.decide take/refuse-with-reason/re-pick when grant gone, honest limitation: emulated storage mounted noexec so linked project **runs from internal mirror** (sync in on open/save, push-set on save-back excludes CodeC own outputs) while user's folder stays source of truth — 39.1 must land first or mirror would push build artifacts into folder user owns. All-files-access open in place variant considered and rejected as default four reasons + one per-project flag may still earn place. Persisted SAF grant takePersistableUriPermission mandatory for anything that must survive reboot flags on intent not grant. Kill app and reopen: linked folder still works (persisted grant). Revoke permission in system settings: CodeC says "link to <folder> is gone" offers re-pick — must not crash and must not silently edit stale mirror.
7. **What git sees v2.2** — build outputs (`a.out`, `bin/`, `dist/`, …) kept out of git automatically by repo-local ignore + **RunArtifacts** routes every language's build output to `filesDir/CodeC/temp/runs/<stamp>/` + **TempGc** prunes age/capacity/newest-N on start and after Stop (Phase 39) — your `.gitignore` never touched; **RepoHygiene** ~60 patterns incl .codec/ .codec.json derived from CC0 github/gitignore templates plus CodeC own applied at single choke point stageAll instead of one call site in GitControlViewModel.refresh() with git rm --cached for anything already tracked and what will be committed list before commit button; user's own .gitignore always wins law kept and tested (plain paragraph; depth link ch-11).

- **Try it:** (1) create `second-project`, move `hello.c` into it, open it from Projects hub (app opens straight into file you left in); (2) export `first-project` as file and find it in file manager; (3) export-all ZIP (⋮ → Export all projects) and note two roots; (4) compile inside project (`cc hello.c -o a.out` from Term) and confirm project's ignore keeps `a.out` out of git view + RunArtifacts temp/runs/<stamp>/; (5) open a folder from Download/ as project via ProjectLink (if Phase 43 shipped) and observe mirror + persisted grant.
- **Mistakes:** editing file while switching folders (autosave + in-editor folder switch handles it — preview/terminal follow project you're in not stale route); looking for projects in public storage (they're app-private — that's why ./a.out runs; linked folder runs via mirror because noexec); ZIP import with nested folders (open ZIP top-level folder not ZIP in picker); huge folder slow B-1 (open subfolder); expecting built-in TCC on 32-bit (null by design); expecting outputs in project (now temp/runs).

## 2. Implementation steps

1. Build `ch-06.html` (crumb "Chapter 6 of 17") with v2.2 facts.
2. UI names/paths from W4.2 v2.2 facts (two roots, safe walk TreeWalkPolicy, ProjectLink persisted grant, export-all over both roots, RunArtifacts temp/runs, RepoHygiene ~60 patterns .codec/, user's .gitignore wins); source notes in `chat-web4/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-05, next → ch-07.
2. Project path (filesDir + externalFilesDir candidate), + sheet items (New/Clone/Import ZIP/Open Folder), safe walk (TreeWalkPolicy budgets progress+cancel Throwable boundary persisted grant), ProjectLink (persisted SAF grant noexec mirror sync in on open/save), export-all over both roots byte-identical, ZIP caps 10k/128 MB, RunArtifacts temp/runs/<stamp>/ + TempGc, RepoHygiene ~60 patterns incl .codec/ enforced inside stageAll user's .gitignore wins == W4.2 v2.2 facts (noted).
3. W4 total check: /learn + ch-01…ch-06 all render, template consistent, O6 closed, verified-facts table v2.2 committed → W4 COMPLETE; report + merge gate.
```
