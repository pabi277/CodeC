# CodeC Phase 55 — device round (P1-P10)

> **Not run.** Written with the phase (2026-09-22) so the owner can run it against the
> `Build APK` artifact of this branch, exactly in the Phase 50/51/52 house format.
>
> **Record format:** `P<n> — device / OS / theme (dark|light) / result (PASS|FAIL) / one sentence.`
> Paste it into the `## Test log (Phase 55 — the side panel)` section below.
>
> **Before you start:** install the branch APK (debug artifact; it updates only over other
> debug builds), open the editor, and — for P6-P8 — have at least two projects
> (the demo project and any project you have opened before).

| # | What to do | What must happen |
|---|---|---|
| P1 | Open a project with a file, tap **☰** | A panel slides in over ≈85 % of the width; a strip of the editor (including the green ▶) stays visible on the right; nothing is dimmed into unreadability |
| P2 | Look at the rail | Five icons in this order: outline triangle (**underlined**, because Navigation is the open slot), folder, magnifier, branch, person/wand. Tapping each of the first four changes the panel; the fifth does **nothing** (it is reserved for AI) |
| P3 | With the panel open, tap the strip on the right | The panel closes and the editor is back (deviation 1 — the modal scrim). Then reopen and tap **▶**: the run must behave exactly as before |
| P4 | Open the panel, then press **system back** | The panel closes; a second back leaves the editor as it always did (the unsaved-changes dialog first if the buffer is dirty) |
| P5 | Navigation slot | One bordered card, **3 columns × 2 rows**: `Projects · Editor · Settings` on top (Editor raised and highlit) and `Terminal · Packages · Guide` below |
| P6 | Tap **Projects** in the card | The Projects screen opens with **no** `+ New project` sheet; the panel is gone; back returns to the editor |
| P7 | Back in the panel's Navigation slot, look at **RECENT** | Newest first, each row `name` + an age (`18 minutes ago`, `1 week ago`); the `External` badge appears **only** on a project that came in from outside (import/clone into external storage) |
| P8 | Tap **Files**; open a file from the tree; then **Search**, type a word that exists in the project | The tree behaves exactly as before (root = project name, ▶ on the launch-default file). Search lists `path:line` rows; a tap opens that file at that line and the panel closes. With an empty field, `RESULTS` is empty |
| P9 | Search: toggle `Aa`, then `.*` (with a regex like `\bdiv\b`), then `Ab|`; type a broken regex like `([` | Each toggle changes the results the way it says; the broken regex shows **nothing** (no crash, no red wall). A query with no hits prints one muted line |
| P10 | **Repository** slot, in a project with no repo, then in one that has one | No repo: the sentence, then the centred **Initialize Repository** button → it opens the Terminal with `git init` ready (nothing else). With a repo: branch, `Changes: N`, and a **Source Control** button that opens the same sheet as the drawer footer's row |

## Test log (Phase 55 — the side panel)

_(empty — the owner runs this round)_

## What this round is *not* allowed to see

- A panel that deletes the bottom bar, the “Show tabs” handle, or any of the five tabs. The
  owner's decision was *“only removing the project option is ok”*, and even that is **Phase 56**.
- `Discover`, `My Labs`, `Change Log`, `Upgrade`, an Account page or credits anywhere in the panel.
- A fifth rail slot that opens something. It is reserved for the owner's AI plan.
