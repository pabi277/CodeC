# Phase 97 — the post-agreement setup flow (seven beats, a project the user picks)

**Owner brief, 2026-10-08.** *"See these screenshots after agreed to terms and
conditions i want something like this with a better approach that fit CodeC"* —
seven Pydroid 3 reference screenshots. Then, in the same session: *"i want to
make it looks more modern authentic"* · *"the game arena is a sample project but
i want to the user select their 1st project and work"* · *"i like all 7 page so i
don't want to decrease the number of the slides please reconsider your
decisions"* · *"add this somewhere the learning of the direct link of the
website"* · *"Ok implement start you can now change in app code"*.

**Status:** implemented, **not compiled or run in the authoring environment** (no
JDK, no Android SDK there — see *Evidence limits* at the end). Everything is
written to the house pattern so CI (`.github/workflows/build-apk.yml`) is the
first real compiler.

Design record: [`docs/research/ONBOARDING_PERSONALISATION_DESIGN_20261008.md`](../../../research/ONBOARDING_PERSONALISATION_DESIGN_20261008.md) ·
wireframes: [`docs/research/beginner-ux-mockups/onboarding.html`](../../../research/beginner-ux-mockups/onboarding.html) ·
predecessor audit: [`docs/research/BEGINNER_UX_AUDIT_20261007.md`](../../../research/BEGINNER_UX_AUDIT_20261007.md).

---

## 1. What ships

Seven beats, after the privacy agreement and before the shell:

| # | Beat | Answer | Consequence |
|---|---|---|---|
| S1 | Set up your workspace | *Let us go* / *Skip setup* | a plan, an honest "about 40 seconds", and a one-tap exit |
| S2 | What do you want to make first? | Arcade · C · Python · Web | creates **and opens** that project |
| S3 | Name your project (+ variant, where variants exist) | a name; a C template | the project folder, the breadcrumb, the hub card |
| S4 | Make the editor yours | S/M/L; Dark/Light/Auto | the mirror re-renders; the theme re-themes the **whole app** live |
| S5 | How CodeC helps you | plain words; hints; line numbers; wrap | four real settings, one shown as the Output panel will draw it |
| S6 | Building your project | *(nothing to answer)* | the real files, in write order; a real download only for Python |
| S7 | You are all set | *Start coding* / two doors | today's reveal path into the project the user named |

**Skip setup is the app as every earlier build left it**: `GameArenaSample.ensure`
(the legacy call, unchanged) and no settings written. The flow can therefore
never be slower than the code it replaced.

## 2. Files

New — `app/src/main/java/com/codeci/ide/ui/setup/`:

| File | What it owns | Android-free |
|---|---|---|
| `SetupFlowPolicy.kt` | the seven steps, the four starts, the C variants (from `TemplateProvider`), the plan the seeder executes, `skipChoice()`, the name verdict (delegating to `ProjectNameCheck`) | yes |
| `SetupFlowCopy.kt` | every user-visible word + `ALL_COPY`, `MAX_WORDS`, `BANNED_WORDS` | yes |
| `LearningLinks.kt` | the three website addresses and the words the doors share (the owner's *"learning of the direct link"*) | yes |
| `SetupFlowScreen.kt` | the seven beats, drawn | — |
| `SetupSeeding.kt` | the disk work: create the project, seed the arena into a named project, write the C template | — |

No new outbound plumbing: the doors route through **`ui/services/OpenInBrowser`**
(Phase 37's declared *one* `ACTION_VIEW` path — "no second `ACTION_VIEW` block
exists in the codebase"). The first draft added `ui/support/CodecLinks.kt`; it was
deleted in the same change once the existing helper was found, and
`SetupFlowWiringTest` now asserts the flow imports no `Intent` at all.

Changed:

- `MainActivity.kt` — the first-run branch now shows the intro, then the flow,
  then seeds/launches the chosen project; `startDestination` prefers the flow's
  route (`firstOpenRoute`); the abandon path (build failure) completes the gates
  and lands on the hub with nothing created.
- `SettingsManager.kt` — one new key, `setup_flow_complete`.
- `SettingsScreen.kt` — `SettingsLinkRow` (title + promise + action word) and the
  two learning rows in About.
- `FileManagerScreen.kt` — the empty hub's quiet reading line.
- `GameArenaSample.kt` — `seedInto(projectsRoot, projectName, readAsset,
  onFileWritten)`; `writeProject` gained a project name and the file callback.
  `ensure()` is untouched, marker and all.

## 3. The laws, and where they are pinned

| Law | Pinned by |
|---|---|
| The flow is seven beats, in order; the strip counts six | `SetupFlowPolicyTest` |
| **Skip ≡ today's behaviour** (the legacy call, the sample's name, no settings) | `SetupFlowPolicyTest`, `SetupFlowWiringTest` |
| Every start maps to a project + entry file + a real cost; only Python downloads | `SetupFlowPolicyTest` |
| The C variants are exactly the shipped templates; no faked variants | `SetupFlowPolicyTest` |
| The name beat uses the app's own validator | `SetupFlowPolicyTest` (EMPTY/INVALID/TAKEN) |
| Copy: ≤16 words, no banned words, ASCII only, no retired jargon | `SetupFlowCopyTest` |
| Addresses: https, same host, unique; the privacy doc carries the promise | `SetupFlowCopyTest` |
| One outbound door; three surfaces use it; the pure halves import no Android | `SetupFlowWiringTest` |
| One gate key, no profile; no guide machinery returns | `SetupFlowWiringTest` |
| The build beat reads the plan and the seeder's callbacks (no `delay(`) | `SetupFlowWiringTest` |
| The flow's controls are in the touch-target audit | `TouchTargetTest` (core-file list) |

`UnrestrictedUiWiringTest`'s first-run assertion was updated in the same change:
the legacy seeding no longer happens *in* `MainActivity` (it moved to
`SetupSeeding`, behind the skip path) and the flow's screens replace it in that
branch. The guide-removal assertions are untouched.

## 4. Deviations from the design record (deliberate, and why)

1. **One stored key, not two.** The design proposed `setup_start_choice`
   as well. It is not written: the choice already exists on disk *as the
   project* (name, type, files), and the Phase 38.2 audit deleted a key nothing
   read. A future "create another starter" row can read the project list
   instead.
2. **Arcade has no variant chips.** Snake / Block Party / Tic-Tac-Toe live
   inside one project (the arena's own menu), so a chip row there would be a
   choice that changes nothing — banned by the flow's own law 2. The card says
   *"Three games inside"* instead. C's five templates are the real variant row.
3. **S6's Python download is stated, not started.** Python's install stays on
   the existing first-run gate (Phase 21), triggered the moment the user presses
   ▶ — the flow says so in plain words and offers *"Use C instead"*, which
   re-runs the build as a C project (the `buildToken` bump). Starting the
   download from inside the flow needs the module view-model and is **Phase
   97.1**; promising a byte count the build beat cannot yet verify would have
   been the Pydroid screen-6 mistake.
4. **Motion is static in v1.** The design's step slide, staggered file rows and
   path-drawn checkmark are recorded for **97.1**: they need `CodecMotion`
   specs wired into the screen, and this environment cannot compile to check
   them. Everything else of the visual direction shipped: the mint wash, the
   mono samples, the glass cards, the 48 dp controls, the live mirror and the
   live re-theme.
5. **The browser path is Phase 37's, not a new one.** See the New-files note
   above: the first draft's `CodecLinks` was deleted in favour of
   `OpenInBrowser`, which already owns the app's single `ACTION_VIEW` block and
   its "a tap is never a dead end" fallback.
6. **S7's second door is the FAQ, not the sample.** The sample is one card in
   S2 and a tile in the hub; a door that says "try the games" while the user is
   mid-flow cannot honour itself without a second seeding path. The FAQ door is
   real, and it is the second address the audit found the app never mentioned.

## 5. What still bites (honest list)

- **Not compiled here.** The authoring environment has no JDK/Android SDK, so
  `./gradlew :app:assembleDebug` and the unit tests have not run. Expect the
  first CI run to find small things (an unused import, a parameter name).
- **`rememberCoroutineScope` + a second `ThemeManager`** instance in `MainApp`:
  DataStore is the single source of truth and both readers observe the same
  flow, but a reviewer may prefer hoisting `themeManager` into `MainApp`.
- **The name field's suggestions** are static chips; per-start suggestions
  (`My Snake` for the arcade) are a 97.1 nicety.
- **No device round.** The metrics the design names — taps to code, drop-off per
  screen, time to first green run — are unmeasured until the owner runs the
  device script in §7 of the design record.
- **S5's plain-words switch is stored as `plainWords` in `SetupPicks` but the
  Output panel's plain-word layer (audit P0-5) is not built yet**; the switch
  currently has no reader, so the flow's promise is one phase ahead of the
  renderer. Either P0-5 lands next or that control should be hidden until it
  does (it is one line in `HelpsStep`).
- Python chosen → the flow's plan shows `main.py` and the download line, but the
  first ▶ still triggers the Phase 21 gate exactly as before. Nothing regressed;
  nothing was promised twice.

## 6. Evidence limits

- The Pydroid flow is judged from the owner's seven screenshots (reference only;
  nothing copied).
- CodeC facts are from source at `ccae8d46` on branch `arena/db36a12a-codec`.
- **No compile, no test run, no device round in this environment.** CI is the
  first real check; the device script is the owner's.

## 7. Next (proposals, not authorised)

| # | Item |
|---|---|
| 97.1 | Flow polish: step motion on `CodecMotion`, staggered file rows, Python download inside S6, per-start name suggestions, hide the plain-words control until P0-5 ships |
| 97.2 | The Templates library's own door (`Screen.Templates` is still composed but unreachable) — the S2 row currently routes to the C variants instead |
| 98+ | The audit's remaining P0/P1 items (glossary pass, beginner error line, accessibility hardening) |
