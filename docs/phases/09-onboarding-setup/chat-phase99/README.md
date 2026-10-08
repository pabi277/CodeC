# Phase 99 — art for every beat: six renders for the setup, five for the tour, one shared card

> **Status: ✅ IMPLEMENTED (2026-10-08) on `arena/db36a12a-codec`; CI ✅ GREEN on `0c12e8b`
> (Build APK [`37766270597`](https://github.com/pabi277/CodeC/actions/runs/37766270597));
> restyled by [Phase 100](../chat-phase100/README.md) and
> [Phase 101](../chat-phase101/README.md); merged to `main` via PR #118 (merge commit `fb439a9`).**
>
> **Authorization:** the same Pydroid 3 reference, re-sent with an emphasis on *"consistent 3D
> illustration per step"* and the animated loading beat. Reference only — the Pydroid composition
> was taken; its goal and level questions were refused by name.
>
> **The core rule of this phase: one illustration card, not two.** The tour and the setup are
> different screens with different colour rules (fixed stage vs live theme), and the temptation was
> a second rendering path for each. The phase ships **one** `StepArt` composable that both halves
> draw, with the art required by the type system and the reduced-motion reveal owned by the card.

## 1. What shipped

| Piece | What it is |
|---|---|
| **6 setup renders** | `setup_01_pick` … `setup_06_ready` — one per beat S2–S7 (WELCOME keeps the app mark), 900 × 900 `.webp`, **115 510 B total (112.8 KB)** |
| **5 tour renders** | `intro_01_offline` … `intro_05_privacy`, 900 × 900 `.webp` |
| **`StepArt`** | the shared card: a rounded `ART_CARD_BACKDROP` ground, `Radius.XL`, `Elevation.CARD`, the image, and the **required** `description` (there is no overload without one) |
| **`reveal`** | a `0f..1f` entrance the card applies as alpha + scale; on the tour it rides the same `Animatable` the timer pauses, so a paused page is a stopped page |
| **`SetupFlowArtTest` (6)** | every beat's art is wired, every `ART_*` used exactly once, `StepArt(` appears six times, there is **one** `StepArtSize`, and no `AsyncImage` anywhere — the art is bundled, not fetched |

## 2. Why the art is bundled at 900²

The screens show the card at `StepArtSize = 168.dp`; a 900 px master covers a 3× density screen
with room for the card's own scale, and the WebP encoder (q86) keeps the whole set inside a
download budget: **115 510 B for six** in the setup and **76 828 B (75.0 KB) for five** on the tour.
The alternative — vector art — was tried in Phases 90–97 and retired here: hand-written
`Path` values cannot look like a 3D render, and the owner's brief was exactly that look.

The names are static (`setup_01_pick`, not a numbered slot): a static resource name is one R8 can
see, count and (when nothing references it) remove.

## 3. The two halves, and the one place they differ

| | Tour (Phase 98/101) | Setup (Phase 99/100) |
|---|---|---|
| Ground | the **card** on the fixed black stage — and from Phase 101 the render is framed in a 168 dp tile with a gradient ring and a halo | the card, on the user's own surface |
| Art source | `INTRO_STORIES[i].art` | `SetupFlowScreen`'s per-beat `ART_*` constants |
| Reveal | the story's pausable `Animatable` | the step transition |

Everything else is the same code path. That is what makes `SetupFlowArtTest` possible: one
`StepArt(` count, one size, one ground.

## 4. The build beat, and what "animated loading" honestly means here

The reference showed a spinner and a progress bar over nothing in particular. CodeC's **BUILDING**
beat already knew the truth — `files written ÷ plan.files`, reported by `onFileWritten` — so the
phase kept the number and restyled its disclosure rather than animating a fiction. Phase 100 later
gave that bar the shared accent gradient; the value it displays is the same real one.

## 5. CI, honestly

Run [`37766270597`](https://github.com/pabi277/CodeC/actions/runs/37766270597) green on `0c12e8b`:
release artifact `6 819 560 B`, debug `26 533 344 B`, mapping `4 779 313 B`. The only repair in
this phase's family was Phase 100's — the art tests are *positional* (`StepArt(` counted in a file),
so any later edit to `SetupFlowScreen.kt` has to re-grep them.

## 6. Deliberately not done

- **No goal or level questions** (the reference had them, twice).
- **No second illustration path** — no `IntroArtwork`, no bespoke per-screen renderer.
- **No third-party art, no network fetch, no attribution burden** — the renders are the project's
  own, generated for it, and `StepArt` takes a drawable resource, not a URL.
- **No new icon set** for the setup's seven beats: the tour's custom 3D icons came later, in
  Phase 101, and the setup's beats are the six renders above.

## 7. Record

- The spec: [`PART_99_SETUP_ART.md`](PART_99_SETUP_ART.md)
- Masters: `uploads/setup/raw/01..06_*.png` and `uploads/intro/raw/` (working tree, not committed);
  the shipped files are the `.webp` in `app/src/main/res/drawable-nodpi/`
- The restyle over this art: [Phase 100](../chat-phase100/README.md),
  [Phase 101](../chat-phase101/README.md)
