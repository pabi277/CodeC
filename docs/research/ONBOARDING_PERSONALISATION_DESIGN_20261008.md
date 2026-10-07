# Post-agreement setup — 7 screens, user-chosen first project, modern & authentic

> **Status:** ✅ **IMPLEMENTED as Phase 97** (owner-authorised 2026-10-08: *"Ok implement start you can now change in app code"*).
> The design below is the record of intent; the implementation, its deviations and its honest gaps are in
> [`docs/phases/09-onboarding-setup/chat-phase97/PART_97_SETUP_FLOW.md`](../../phases/09-onboarding-setup/chat-phase97/PART_97_SETUP_FLOW.md).
> Four deviations are recorded there (one stored key instead of two · no Arcade variant chips · the Python download is stated, not started · motion deferred to 97.1).
> **Owner's corrections this revision:** *"i want to make it looks more modern authentic"* · *"the game arena is a sample project but i want to the user select their 1st project and work"* · *"i like all 7 pages so i don't want to decrease the number of the slides please reconsider your decisions."*
> Reference: 7 Pydroid 3 screenshots (reference only, not copied). Predecessor audit: [`BEGINNER_UX_AUDIT_20261007.md`](BEGINNER_UX_AUDIT_20261007.md). Wireframes: [`beginner-ux-mockups/onboarding.html`](beginner-ux-mockups/onboarding.html).
>
> **What happened to revision 1's decisions**

| Rev 1 said | Rev 2 says | Why the owner was right |
|---|---|---|
| 4 screens, cut the rest | **7 screens** — the full arc, each beat earning its place | The owner likes the rhythm; the fix for a carousel is not fewer beats, it is *beats that all do work*. With a naming screen, a looks screen and a help screen, seven is the honest count. |
| Arcade auto-seeded, project picker as "S1 option" | **The user picks their first project and lands in it.** Arcade becomes one choice among four, and can be pre-selected without being forced. | "The game arena is *a sample project*" — correct. A demo is not a first project. Ownership starts at the picker, not after it. |
| Utilitarian token-grid layouts | **Modern & authentic visual direction** (§3): real product as the hero art, brand ink + mint glow, one loud element per screen, motion and haptics from the existing ladder | "Modern" is a visual language, not a layout. "Authentic" is the rule that makes it CodeC's: **never draw what the app can show.** |
| Level questions rejected, appendix variant | Still not asked as a *self-label*; its useful content moved into **S5 "How CodeC helps"**, whose switches are the level consequences | The owner can still have Pydroid's exact beat by swapping S5 for the level variant (Appendix D) — but the switches version is strictly more honest. |

---

## 1. The seven screens

| # | Screen | One idea | Real work it does | Time |
|---|---|---|---|---|
| **S1** | **Set up your workspace** | What happens next, and what it costs | none (a 3-line plan + honest "about 40 seconds") | 4 s |
| **S2** | **What do you want to make first?** | Pick the project | determines the whole flow after it | 8 s |
| **S3** | **Name your project** (+ start-from, where variants exist) | Make it *yours* | creates the name; picks the game/template | 12 s |
| **S4** | **Make the editor yours** | How it looks | text size, theme — on a live mirror of the editor | 8 s |
| **S5** | **How CodeC helps you** | How it behaves when things go wrong | plain-word errors, typing hints, line numbers, word wrap, AI stance | 10 s |
| **S6** | **Building your project** | Watch it be made | writes the real files; the only place a download can happen | 0-60 s |
| **S7** | **You're all set** | The receipt | project + settings recap, two doors, *Start coding* | 5 s |

**Tap paths.** Full path: 6 taps, zero typing required (the name is pre-filled). Fast path: **Skip setup** on S1 → the app behaves exactly as it does today (Arcade seeded, editor open) — so the flow can never be *slower* than the current build, it can only be richer. There is no path that ends without a runnable project.

**Progress.** A 6-segment hairline indicator under the top bar on S1-S6, none on S7. Back on every screen from S2. *Skip setup* one tap away on S1 only — after that the user has committed to naming a project, and quitting halfway would be worse than a continue.

---

## 2. What each screen's answer *does* (no decorative questions)

| Screen | Choice | Consequence, visible within seconds |
|---|---|---|
| S2 | CodeC Arcade · C · Python · Web page | which project is created in S6 and which file opens in the editor |
| S3 | Variant (Snake / Block Party / Tic-Tac-Toe; or one of the 5 C templates) | which file is written: `js/games/snake.js` seeded in the Arcade; the template's code in `main.c` |
| S3 | Project name (validated live) | the project folder, the editor breadcrumb, the hub card — **this is the "select your first project and work" beat** |
| S4 | Text size S/M/L | code + terminal size, previewed on the mirror |
| S4 | Theme Dark/Light/Auto | the flow itself re-themes instantly (`app_theme`, `editor_theme`, terminal theme) |
| S5 | Plain-word errors on/off | the Output panel's first line after the next failed build |
| S5 | Typing hints on/off | ghost text in the S4 mirror immediately (the mirror is live until the end of the flow) |
| S5 | Line numbers, word wrap | the editor immediately |
| S6 | Watching files appear | trust: the file list is the real `ProjectScaffold`/asset copy, row by row |
| S7 | Doors | templates (in-app) · 19-chapter course (link, owner decision) |

**Nothing is stored as a profile.** One new gate key + the chosen project id (§6). The Arcade demo remains reachable for anyone who wants it: a row on S7's template door and in the hub — it is a sample, and now it is labelled as one.

---

## 3. Modern & authentic — the visual language

### 3.1 The authenticity rule (the one that makes it CodeC's)

**Never draw what the app can show.** Pydroid's screens illustrate with emoji in white circles (a waving hand for "goal", a scientist for "level"), which render differently on every OEM and say nothing about the product. CodeC's hero art is always the product itself:

| Where | The art |
|---|---|
| S2 option cards | the real first lines of the file each option creates — `{"v":1,"title":"Snake"}` for the arena, `#include <stdio.h>` for C, `print("Hello!")` for Python, `<!DOCTYPE html>` for web — syntax-coloured by the shipped TextMate theme |
| S3 variants | real file names (`snake.js`, `hello_world.c`) and, for C templates, the template's own `concepts` list as plain words (“Variables · Arithmetic · Formatted printing”) |
| S4 | a **live mirror of the editor**: tab bar, breadcrumb carrying *their project name*, code, status bar — the same components, at real proportions |
| S5 | a sample compiler failure rendered exactly as the Output panel will render it, plain line on top, raw line beneath |
| S6 | the real file list appearing as it is written |
| S7 | the receipt — their project, their file, their settings |

The only drawn elements in the whole flow: the **`>_` mark** (`app_mark.xml`) and the **mint glow** behind it. No emoji, no stock illustration, no abstract blobs, no screenshots of other apps.

### 3.2 Brand

- **Ink `#101418`** (the mark's own tile) as the flow's canvas — one step deeper than the editor's `#121212`, so the flow reads as a *place*, not a settings page. Existing surface roles (`#1C1B1F` cards, `#24292E` chrome) sit on it unchanged.
- **Mint `#3DDC84`** — already the brand accent (`CodecPalette.DEFAULT_ACCENT`, the mark's glyph) and already Android's run-green: used for selection, primary action, and the glow. **One loud element per screen**: the glow appears on S1 and S7 only.
- **Glow spec:** radial gradient, mint at ≤12 % alpha, blurred radius ~40 % of the art width, behind hero art; it is decoration (`contentDescription = null`) and gone under reduced-motion… no: glow is static, it stays; only its 180 ms fade-in is gated.
- **Palette law holds:** the glow is the only new colour value in the flow, and it is mint at alpha over ink — measured against the existing `AppContrastTest` rules before it ships (no text sits on it; text sits on the surfaces).
- **Mono where code lives:** project names, file names, and code previews use JetBrains Mono; UI prose never does. That single rule is what makes the flow look like a *code app* rather than a survey.

### 3.3 Layout & type

- 24 dp gutters, 8 pt grid, generous vertical whitespace — the "premium" signal on a phone is **air and contrast**, not decoration.
- Type contrast, not type quantity: **display 28 sp semibold** (S1, S7 only), **title 22 sp semibold**, **body 15 sp**, **caption 12 sp** `MUTED_TEXT #9AA0A6`. One display line per screen, maximum.
- Cards 18 dp radius, 1 px hairline `#24272C`, inset white 3 % top highlight → they read as glass, not as grey boxes. Chips are pill (999 dp). Selected = mint 1.5 dp border + 8 % mint wash + a check glyph. Press = 150 ms spring scale to 0.98.
- CTA: 56 dp, 16 dp inset, bottom-anchored, sentence case ("Start coding" — no shouting caps), mint fill with ink text at the measured ratio, or mint outline when the user should read first.
- Progress: 6 × 2 dp segments, 8 dp wide, 6 dp apart, filled mint left-to-right; not a bar (a bar implies time; segments imply steps).

### 3.4 Motion & haptics (existing vocabulary only)

`CodecMotion` is the only place that may call `spring(`/`tween(` (`MotionWiringTest`), so the flow takes ready-made specs:

| Moment | Spec |
|---|---|
| Step change | shared-axis X slide+fade, `Duration.MEDIUM` (300 ms), `Easing.EMPHASIZED` (0.2, 0, 0, 1) |
| Selecting a card / chip | `spatialSpring` (damping 0.8, stiffness 380) on the check + border |
| S6 file rows | staggered 40 ms fade+slide, one per real file written |
| S7 checkmark | 320 ms path draw, then the receipt card settles on `effectsSpring` |
| Download ring | real progress, linear — never eased |
| Reduced motion | everything snaps (`MotionSpecs.INSTANT`), including the glow fade |

Haptics: **selection tick** when a card/chip is chosen, **success tick** on S7 — both are existing `CodecHaptics` moments (the eight-moment policy). No haptic fires on skip (no punishment), none on screen entry.

---

## 4. Screen specifications

340 dp reference width. Copy is draft, `SetupFlowCopy`-owned, subtitles ≤12 words.

### S1 · Set up your workspace

```
   ┌ glow ┐  ▣ the >_ mark, 56 dp
   Set up your workspace
   Three things and you're coding. About 40 seconds.

   1  Pick your first project
   2  Make the editor yours
   3  Watch CodeC build it

   [        Let's go        ]        ← mint, 56 dp
              Skip setup               ← quiet text button
```
No typing, no rules, an honest time cost, and a visible exit.

### S2 · What do you want to make first?

```
   1 ───── 2 ─ 3 ─ 4 ─ 5 ─ 6          ← segments, 1 of 6

   What do you want to make first?

   ┌──────────────────────────────┐
   │ ▦ CodeC Arcade          [✓]  │   A finished little game you can
   │   { "title": "Snake" }       │   play, then take apart.  Offline.
   └──────────────────────────────┘
   ┌──────────────────────────────┐
   │ C   #include <stdio.h>       │   The classic first program.
   │                              │   Built-in compiler. Offline.
   └──────────────────────────────┘
   ┌──────────────────────────────┐
   │ Py  print("Hello!")          │   Scripts and small tools.
   │                              │   Downloads once · about 40 MB.
   └──────────────────────────────┘
   ┌──────────────────────────────┐
   │ </> <!DOCTYPE html>          │   A page you can preview as you type.
   └──────────────────────────────┘
   ▸ 5 more C templates · Level 1-3      ← the templates door

   [        Continue        ]
```
Each card's "picture" is the actual first line of the file it creates — the most authentic possible preview, and the reason this screen looks like a code app instead of a quiz.

### S3 · Name your project

```
   ‹   3 ───── 4 ─ 5 ─ 6

   Name your project
   Start from:  [ Snake ][ Block Party ][ Tic-Tac-Toe ]     ← variants (where they exist)

   ┌──────────────────────────────┐
   │ MyFirstGame                  │   ← field, pre-filled, keyboard up
   └──────────────────────────────┘
    MyFirstGame / index.html          ← live breadcrumb preview (mono)
   [ My Snake ][ Playground ][ Test ]  ← suggestion chips

   [        Continue        ]
```

- The field uses **`ProjectNameCheck`** — the app's own verdict (EMPTY / INVALID / TAKEN) — so the error appears *before* the tap, in the field, which is the same bug class Phase 66.1 fixed for the New Project dialog.
- Variants by choice: **Arcade → Snake / Block Party / Tic-Tac-Toe** (three shipped games); **C → the 5 templates** (Hello World L1 · Calculator L1 · Array Sorting L2 · File I/O L2 · Linked List L3), each showing its `concepts` as plain words; **Python / Web → no variant row** (nothing shipped to offer, so nothing is faked — one of the two is where new content will be needed later).
- The C path writes the template's code into **`main.c`** (not `calculator.c`) so RUN ▶ behaves exactly as the C starter does; the template's `concepts` become a short comment header. This is also where the audit's "Templates-into-a-project-called-`default`" bug disappears by construction: the project is the one the user just named.

### S4 · Make the editor yours

```
   ‹   4 ───── 5 ─ 6

   ┌ live editor mirror ─────────────────────────────┐
   │ ☰  ⬤ MyFirstGame / index.html            ▶      │
   │ 1  <!DOCTYPE html>                              │
   │ 2  <canvas id="game"></canvas>                  │
   │ … status bar: Ln 2, Col 3 · HTML · UTF-8        │
   └─────────────────────────────────────────────────┘
   Text size        [ S ][ M ][ L ]        Code and terminal
   Theme            [ Dark ][ Light ][ Auto ]   Your whole app
   [        Continue        ]
```
The mirror is not a picture — it is the same chrome components, carrying the user's own project name, re-rendered on every tap. That is the "modern" that matters: the screen never explains a change it can *perform*.

### S5 · How CodeC helps you

```
   ‹   5 ───── 6

   When a build fails      [ Plain words ][ Raw text ]
       The plain sentence sits above the real compiler output.

   ┌ compiler failure, as CodeC will show it ────────┐
   │ The statement on line 4 has no ending.          │
   │ main.c:4:18: error: expected ';'                │
   └─────────────────────────────────────────────────┘

   Typing hints            [ On ][ Off ]     Grey suggestions — Tab accepts
   Line numbers            [ On ][ Off ]     C error lines point at numbers
   Word wrap long lines    [ On ][ Off ]     Nothing scrolls sideways

   The AI helper stays off            Settings ▸ AI when you want it
   [        Continue        ]
```
Four real settings, each with a one-line consequence, and the AI stance stated once, plainly: off until asked for. (Appendix D keeps Pydroid's literal "level" beat as a drop-in replacement for this screen if the owner wants it — but each of these switches is a *visible* consequence, which a level label is not.)

### S6 · Building your project

```
   ●  Creating MyFirstGame
   ✓  index.html
   ✓  js/games/snake.js
   ✓  .codec/project.json
   ── Python only ──────────────────────────
   ◐  Downloading Python 3    12 MB of 40 MB · about 1 minute left
      [ Skip — use C instead ]
```
Rows appear **only as the real work happens** (never before), the list is the actual write order, and a download shows real bytes with an escape that lands on bundled-TCC C. If the chosen project needs nothing, this screen is a sub-second flash — and if it is truly instant, it renders as a single "✓ Ready" row rather than being deleted (the beat must exist for the 7-screen rhythm; it just must never lie).

### S7 · You're all set

```
   ┌ glow ┐  ✓  You're set up

   ┌──────────────────────────────────┐
   │ ▦  MyFirstGame                   │
   │    index.html · opens in the editor
   │  [Text: M] [Dark] [Plain words] [Hints on]
   └──────────────────────────────────┘

   ▸ Try it: CodeC Arcade — 3 games, nothing to download
   ▸ Learn: 19 short chapters (website)
   Everything here lives in Settings.

   [        Start coding        ]
```
The receipt, the two doors, one sentence about the app. **Start coding** performs today's reveal path (shell → editor route → cursor in the entry file → RUN visible) with the success haptic.

---

## 5. Why this beats the reference (screen by screen, with the count kept at 7)

| Beat | Pydroid | CodeC, revision 2 |
|---|---|---|
| Welcome | Logo + slogan + CONTINUE | A **plan** with an honest time cost and a skip — no slogan |
| Goal | 4 answers, no visible effect | Picks the project that is created and opened |
| Level | 4 self-labels, no visible effect | **Not asked.** Its content lives in S5's four switches, whose effects are visible on the spot (or Appendix D if the beat is wanted) |
| Program types | 4 answers, no visible effect | Folded into S2's cost lines (“Offline” / “Downloads once · 40 MB”) |
| Preferences | 3 toggles, ~60 words of prose | **Two screens** (S4, S5), six controls, each previewed live, subtitles ≤12 words, 48 dp segments |
| “Creating your IDE” | Sparkles + a bar over nothing | S6: real files appearing, real bytes, real escape |
| “Your IDE is ready” | Feature list with crowns, *Premium libraries*, *No ads* | A receipt of the user's own artefacts + two learning doors |

Net: same 7 beats, zero decorative questions, no advertising, and the user leaves the flow inside a project **they named**.

---

## 6. Wiring, state, dependencies (research only)

**Hook point:** `MainActivity.kt:837-860` — today, on `firstRunAccepted`, the app seeds `codec-arcade`, saves `EditorLaunchState`, sets `first_launch_complete`, then reveals the shell. The flow inserts between *accepted* and *seed*; the seed becomes "the project S2/S3 chose", and `firstOpenSample` becomes `firstOpenChosen`.

**New keys (2, both in `SettingsManager`):**

| Key | Type | Meaning |
|---|---|---|
| `setup_flow_complete` | Boolean | flow shown and finished/skipped |
| `setup_start_choice` | String | `arcade:snake` \| `arcade:blocks` \| `arcade:ttt` \| `c:hello_world` \| … \| `python` \| `web` — the project, not a profile |

Settings writes reuse existing keys throughout: `font_size`, `terminal_font_size`, `app_theme`, `editor_theme`, `terminal_theme`, `completion_ghost`, `line_numbers`, `word_wrap`, and the audit's plain-word-errors key when that layer ships (the S5 control is hidden until it does).

**Behaviour change to authorise explicitly:** *CodeC Arcade stops being seeded on first run.* The demo stays available (S7 door + hub row + it remains in `GameArenaSample` with its existing "seeded once; a deleted CodeC Arcade stays deleted" marker), but `firstOpenSample` now means the user's own project. Existing installs are untouched.

**Doors, resolved against the audit:** the Templates library becomes reachable twice — S2's footer row ("5 more C templates") and S7's door — and S3 consumes the same `TemplateProvider` data for the C variants, so the library stops being dead code. The course door (S7) still needs the owner's outbound-link decision from the audit.

**Pure policy (host-testable, Android-free):** `SetupFlowPolicy` (7 steps, defaults, skip ≡ legacy, S3 variant table, S6 "does this need a download?"), `SetupFlowCopy` (all strings), plus thin Android calls for settings writes, `WelcomeStarters.ensureProject`, `ProjectNameCheck`, and `TemplateProvider`/arcade asset seeding.

---

## 7. Tests & measures

| Test | Pins |
|---|---|
| `SetupFlowPolicyTest` | 7 steps in order; skip ≡ today (Arcade seeded, no keys written); every S2/S3 option maps to a shipped asset/template; Python is the only download |
| `SetupFlowNameTest` | the field's verdict comes from `ProjectNameCheck`; an invalid/taken name blocks *Continue*; the name reaches the created project and the launched route |
| `SetupFlowCopyTest` | subtitles ≤12 words; banned words (`userland`, `premium`, `upgrade`, `unlock`, `credits`, `ad-free`, `level`); no emoji in any string |
| `SetupFlowVisualTest` | hero assets are product content (no drawable/emoji imports in the flow's files); the only new colour is the mint glow, measured |
| Roborazzi | all 7 screens at 1.0× / 1.3× / 2.0× font scale, dark and light |
| Device script | fresh install → agreement → **tap-through to first green run**, timed; then the same choosing Python and cancelling the download; then Skip; then the C template path |

**Metrics (device-local counters, printed in Logs — no analytics):** taps to code, drop-off per screen (S2 and S3 are the ones to watch), share who keep the pre-filled name, share who take the skip.

---

## 8. Risks

| Risk | Mitigation |
|---|---|
| 7 screens before code — length is the documented killer of carousels | Skip on S1 ≡ today's behaviour; every screen pre-answered; no typing required; progress segments; 40-second honest promise on S1 |
| Naming a project is the first thing a beginner must do | Pre-filled name + suggestion chips + the app's own validator; the field is the only text input in the flow |
| Two new preference keys creep into a profile | A gate and a project id; documented in `DATA_AND_PRIVACY.md`; nothing sent anywhere |
| "Modern" drifts into new design language | Only one new value (the mint glow) and it is measured; everything else is `CodecTokens`/`CodecType`/`CodecMotion`; `TokenAdoptionTest` and `MotionWiringTest` keep it honest |
| Arcade stops auto-seeding → a fresh install with no demo | It is now a labelled door in three places (S2's cards, S7, the hub), and Skip still seeds it |
| Phase 64.2's "no replacement onboarding" | Owner-authorised Phase 97; the flow explains nothing about how to use the app; no tips, no locks; the 5-beat intro is untouched |

---

## 9. Open questions

1. **Authorise Phase 97** — and does Arcade stop auto-seeding for fresh installs (the flow's whole point), or stay seeded as a hidden default behind Skip only?
2. **Arcade pre-selected in S2**, or neutral (nothing selected, Continue disabled until a choice)? A neutral S2 costs one more tap and gains one more deliberate decision — my recommendation is *pre-selected*, since Arcade is the offline-delight path.
3. **S5 vs Pydroid's level beat** — four consequence switches (recommended), or the Appendix D level variant, or both on separate screens (which would make 8)?
4. **Python in the picker** — keep it (with S6's real download and the C fallback), or hold Python to the Packages tab and offer only Arcade/C/Web here?
5. **Course door on S7** — needs the outbound-link decision + a `DATA_AND_PRIVACY.md` sentence.
6. **Light theme inside the flow** — S4 can switch the *flow* to light mid-onboarding. Keep that (impressive, honest), or preview light only inside the mirror and apply after?
7. **Name rules** — do we offer suggestion chips by project type (`My Snake`, `Playground`), and should the chosen name be renameable later in the hub (it already is)?

---

## Appendix A — Variant table (all shipped content, nothing invented)

| Choice | Variants | Real source |
|---|---|---|
| CodeC Arcade | Snake · Block Party · Tic-Tac-Toe | `assets/game-arena/js/games/{snake,blocks,tic-tac-toe}.js` |
| C | Hello World (L1) · Calculator (L1) · Array Sorting (L2) · File I/O (L2) · Linked List (L3), each with `concepts` | `ui/models/Template.kt` (`TemplateProvider`) |
| Python | none yet — the screen shows only the name | `WelcomeStarters` “Python Starter” |
| Web page | none yet | `WelcomeStarters` “Web Starter” |

> **Correction carried from the audit (2026-10-08):** the library ships **5** templates, not 6 as first written elsewhere in the audit; `TemplateProvider.templates` is the source of truth.

## Appendix B — Copy bank (draft)

- S1: *Set up your workspace* · *Three things and you're coding. About 40 seconds.* · rows: *Pick your first project* / *Make the editor yours* / *Watch CodeC build it* · buttons: *Let's go* / *Skip setup*
- S2: *What do you want to make first?* · option notes: *A finished little game you can play, then take apart.* · *The classic first program.* · *Scripts and small tools.* · *A page you can preview as you type.* · costs: *Offline* / *Built-in compiler* / *Downloads once · about 40 MB* / *Live preview* · door: *5 more C templates · Level 1-3*
- S3: *Name your project* · *Start from:* · breadcrumb: *MyFirstGame / index.html*
- S4: *Make the editor yours* · *Text size — Code and terminal* · *Theme — Your whole app*
- S5: *When a build fails — The plain sentence sits above the real compiler output.* · *Typing hints — Grey suggestions; Tab accepts* · *Line numbers — C error lines point at numbers* · *Word wrap long lines — Nothing scrolls sideways* · *The AI helper stays off · Settings ▸ AI when you want it*
- S6: *Creating MyFirstGame* · *Downloading Python 3 — 12 MB of 40 MB, about 1 minute left* · *Skip — use C instead*
- S7: *You're set up* · *opens in the editor* · *Try it: CodeC Arcade — 3 games, nothing to download* · *Learn: 19 short chapters* · *Everything here lives in Settings.* · *Start coding*

## Appendix D — If the owner wants Pydroid's literal level beat

One screen, in S5's slot, three answers, and **each answer's consequences are visible on the very next interaction**:

- **New to code** → text size L, plain-word errors ON, hints ON, line numbers ON; S7's first door is the course.
- **I've written some code** → shipped defaults; S7's first door is the templates.
- **I code often** → shipped defaults, hints OFF; S7 shows no doors.

Rules that keep it honest: the labels describe *experience with code*, nothing is stored after the flow (the answers only set S4/S5 defaults and S7's door order), and every consequence is changeable on the spot. If any part of that stops being true, delete the screen — do not ask a question that does nothing.

---

*Wireframes: [`beginner-ux-mockups/onboarding.html`](beginner-ux-mockups/onboarding.html). Predecessor audit: [`BEGINNER_UX_AUDIT_20261007.md`](BEGINNER_UX_AUDIT_20261007.md). Nothing here authorises implementation; Phase 64.2's exit condition requires the owner's explicit word and a phase number (97).*
