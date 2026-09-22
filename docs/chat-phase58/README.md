# Phase 58 — first open, silent install, hamburger

**Owner row of record:** *“First open = editor on sample snake code we write (not a picker
landing on a locked terminal). Userland installs silently; one warning when a run needs a
download before userland is ready. The HTML page view's upper links want a hamburger
treatment.”* — plus *“if need say me”* (ask before inventing a screen) and *“take the
reference seriously”*.

| Part | Title | Status |
|---|---|---|
| 58.1 | Open on snake | 🚧 IMPLEMENTED — [PART_58_1](PART_58_1_SNAKE_FIRST_OPEN.md) |
| 58.2 | Silent userland, one warning | 🚧 IMPLEMENTED — [PART_58_2](PART_58_2_SILENT_USERLAND.md) |
| 58.3 | Upper links | 🚧 IMPLEMENTED — [PART_58_3](PART_58_3_PREVIEW_HAMBURGER.md) — the owner chose **CodeC's preview chrome** when the research pass found no Play shot and asked |

## What §58 asked for, and where it stands

* **Remove** the setup bar as a permanent strip → **done** (58.2: `SetupBar.kt` and its three
  policy functions deleted, with a “Do not re-add the strip.” block in their place).
* **Remove** the first-open path that lands on the Projects hub when there is no last file →
  **done** (58.1: the launch seeds `snake`, saves it as the launch state, and starts in the
  editor on `index.html`, above the resume offer).
* **Add** a snake sample, opened in the editor on first launch → **done** (58.1: an original,
  standalone `index.html` — canvas snake, keys *and* thumb pad, nothing to download).
* **Add** one warning pill in the standing case → **done** (58.2:
  `NoticePolicy.userlandWarning` → `NoticeKind.USERLAND_NOT_READY` →
  *“Finish installing the Linux tools first — open Terminal.”*).
* **The hamburger** → **done** (58.3), and *asked* rather than invented. The research pass
  re-read all seven shots: Content / Home / More are buttons **inside the page's own
  `index.html`** (`124105` shows their markup, `<span>More</span>`), so they are a user's file,
  not CodeC chrome; and no shot shows CodeC's preview screen (`124052`, long suspected of being
  a Play shot, is the editor's code area again — see PART_58_3 §1.1). The roadmap says ask, so
  the two candidates were put to the owner with the evidence, and he chose **CodeC's preview
  chrome**: the preview's address row + peer/LAN panel are now one ☰ menu (address row stays as
  the readout), with the panel itself reachable as its own item. Nothing was removed and nothing
  was re-implemented — the menu calls the same actions the panel did.

## Exit criteria, checked against this checkout

| Exit line (§58) | Where it stands |
|---|---|
| a fresh install opens the editor on snake, not the hub, and **not a locked terminal** | `MainActivity`'s first-launch seed + `firstOpenSample` start branch; the divert effect deleted (58.2) |
| the bottom bar is there, without a Projects tab | Phase 56 (unchanged here) |
| userland can be downloading with **no setup strip** on the editor | the strip is deleted, not hidden (58.2) |
| running that HTML previews it | `SnakeSample.ENTRY_FILE` is a `.html`; `RunDecision.WebPreview` is decided before any tool probe (pinned in 58.2) |
| running a language that needs a download, before userland is ready, says to finish the userland install first | `EditorViewModel.promptInstall` → `NoticePolicy.userlandWarning` → the pill |
| the hamburger change matches whichever of the two the research pass proved, and says which | **done** — 58.3: **CodeC's preview chrome** (the owner's own answer, after the pass proved the shots' Content/Home/More live inside the *page*, and that no Play shot exists). PART_58_3 §1 records the evidence and §1.1 records the near-miss shot |
| **device pass** | owed (see below) |

## Device round owed

The phase exit still says *“Device pass required: Yes.”* The rows are in
[`DEVICE_ROUND.md`](DEVICE_ROUND.md) (S1 fresh install, S2 the one warning, S3 the preview,
S4-S6 the hamburger), on top of the editor-chrome rows in
[`../chat-phase57/DEVICE_ROUND.md`](../chat-phase57/DEVICE_ROUND.md) (P1-P10 / Q1-Q6 / R1-R11).
Neither round has been run.

## CI

| Commit | Round | Verdict |
|---|---|---|
| `22b9c2e` (58.1 + 58.2) | `35712790369` | ✅ **green first try** — host unit + screenshot tests, debug + both release APKs built and uploaded |
| `ebc1296` (58.3) | `35713903141` | ✅ **green first try** — same jobs |

No pin failures in either round, which is the point of the local host runs: the two censuses
this phase invalidated (the terminal-navigation count and the `before()` anchor inside
`decide()`) were caught and re-cut **before** the push, with their reasons written into the
tests.

## Records

* Research and the `file:line` inventory for the series: [`../PHASE54_58_PHONE_UI_ROADMAP.md`](../PHASE54_58_PHONE_UI_ROADMAP.md)
  §58, and the shots in `docs/spck-ui/` (the `0*.png` drawings are **not** the reference).
* Phase 57's chrome rows and device rounds: [`../chat-phase57/`](../chat-phase57/README.md).
