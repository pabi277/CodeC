# Phase 98 — the illustrated tour: five pages, one deck, and an honest progress pill

> **Status: ✅ IMPLEMENTED (2026-10-08) on `arena/db36a12a-codec`; CI ✅ GREEN on `723cb40`
> (Build APK [`37762890430`](https://github.com/pabi277/CodeC/actions/runs/37762890430));
> restyled by [Phase 100](../chat-phase100/README.md) and
> [Phase 101](../chat-phase101/README.md); merged to `main` via PR #118.**
>
> **Authorization:** the owner's brief — the illustration half of *"something like this with a
> better approach that fit CodeC"*, plus *"i like all 7 page so i don't want to decrease the
> number of the slides"*: the deck is five pages and the setup flow's seven beats are untouched.
>
> **The deck is one story per promise, and the last page is the agreement** — that is the whole
> architecture. Four pages describe what the app does; the fifth asks permission to do it, and the
> timer deliberately stops being a timer there.

## 1. The five pages

| # | Eyebrow chip | Heading | What it promises |
|---|---|---|---|
| 1 | `WORKS OFFLINE` | Code anywhere | compiles on the phone — no account, no internet |
| 2 | `THE EDIT-RUN LOOP` | Write, run, fix | edit, run, and read the compiler errors in one place |
| 3 | `LINUX TOOLS` | A real Linux toolbox | Python, Node and git download only when asked |
| 4 | `SAMPLE PROJECTS` | Start from a sample | Snake, Block Party and Tic-Tac-Toe ship with the app |
| 5 | `BEFORE YOU START` | (the privacy agreement) | what is stored, what is never sent, and one checkbox |

`INTRO_STORIES` is the deck; `IntroStoriesTest` holds the words, the order and the art, and
`FirstOpenGameArenaTest` holds the behaviour (swipe, the timer and its pause, the skip, the gate).
The five illustrations are one `R.drawable.intro_0*` each, referenced by name in the promised
order, and the test checks the files really exist (`> 8 192 B` each) — a typo in a drawable name
would otherwise only fail at compile or on a device.

## 2. The shape of a page

One centred column inside a card: the art, the **eyebrow chip** (Phase 99 gave the setup's beats
the same chip, so the two halves are one design), a bold heading, a body line, and — under the
card — the progress pill and the controls. The card's job is to make art, words and controls read
as **one object** rather than three floating things.

## 3. The progress pill, which is the only clever part

Most onboarding dots are decoration. These ones are a **real timer**:

- the current pill fills with `CodecMotion.storyTimer(remainingMs)` over the page's real window
  (`CodecMotion.Duration.STORY` — **10 s** here, **8 s** from Phase 101);
- `progressBarRangeInfo` is set by hand, so a screen reader hears *"Story 3 of 5, 40 percent of its
  reading window"* instead of nothing;
- **the elapsed time survives a pause**, because the resume re-computes `remainingMs` from the
  value the `Animatable` already holds — a pause is a pause, not a restart;
- the timer is **elapsed story time, not decorative movement**: it keeps running with Android's
  reduced-motion switch on, because a reader who needs the time still needs the time.

The last page never auto-advances; the hint says so (*"the timer waits here for your agreement"*),
and the checkbox is the only way forward.

## 4. The controls

- **Swipe left or right** — `detectHorizontalDragGestures` on the page body with a `Space.XXL`
  (32 dp) threshold,
  ignored while the privacy dialog is up (`!preparing && !showPrivacyDetails`).
- **Next / Back** — `OutlinedButton` + `GradientButton`, **Back only from page 2** (`BackHandler`
  through `BackRouter.decide(BackState(firstRunIntroPage = page))`, so the system Back and the
  on-screen Back cannot disagree).
- **Skip to agreement** — top right, from any page before the last.
- **Pause / Resume** — a 48 dp text button beside the hint (one visible word from Phase 101).
- **`FIRST_RUN_LOGO_DURATION_MS = 2_000L`** — the animated opening, unchanged since Phase 58.

## 5. CI, honestly

Run [`37762890430`](https://github.com/pabi277/CodeC/actions/runs/37762890430) green on `723cb40`:
release artifact `6 699 998 B`, debug `26 413 755 B`, mapping `4 777 584 B`. No failed round of its
own — this phase compiled on the first push, which is worth noting only because it did not stay
that way: Phase 100 and 101 each needed repairs to *tests*, twice in the same family
(`String.count` takes a character predicate; `Regex.findFirstMatchIn` does not exist).

## 6. Deliberately not done

- **No second tour, no re-entry.** The tour shows once (`FIRST_RUN_LOGO_DURATION_MS` +
  `setFirstLaunchComplete`), and there is no "show the tour again" switch.
- **No five vector-art composables.** The Phase 90–97 vector art was retired with this phase;
  `IntroStoriesTest` fails if any of those names comes back alongside the bundled art.
- **No copy outside `INTRO_STORIES`.** The screen's own strings are the two controls and the hint.
- **No "level" or store words.** `IntroStoriesTest` re-uses the setup's banned list verbatim.

## 7. Record

- The spec: [`PART_98_ILLUSTRATED_TOUR.md`](PART_98_ILLUSTRATED_TOUR.md)
- The art's own phase: [Phase 99](../chat-phase99/README.md); the restyle:
  [Phase 100](../chat-phase100/README.md), [Phase 101](../chat-phase101/README.md)
