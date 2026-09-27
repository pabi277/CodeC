# PART 54.3 — What is not in the shots, and what the owner already decided

> **Status:** ✅ DONE (2026-09-22, docs only). · **Effort:** S ·
> **The rule this part enforces** (roadmap law 4): *“If the control is in neither
> a shot nor a freshly fetched page, stop and ask. Do not invent a menu, a
> preview bar, or an action for `>>`.”*
>
> Three of the four items below are **questions**. One is a **decision already
> made**, written here so that 56 cannot drift back to it.

---

## 1. Decision (not a question): the bottom bar stays

**Owner, verbatim, 2026-09-22:** *“I don't think removing the full down ber is a
good choice i think only removing the project option is ok.”*

Restated as law for 55–58:

1. `FlatBottomBar` **stays**. The divider, the weighted row, the
   `navigationBarsPadding()`, the `NAV_HANDLE` anchor — all of it.
2. `EditorNavRevealHandle` **stays**, including the string **“Show tabs”**.
3. `NavBarPolicy.hideNavBar` **stays** (Phase 32's hide-while-typing).
4. **Exactly one option leaves the bar: Projects**, and only in **56**, and only
   after **55** can open the Projects screen from the side panel.
5. The remaining four are **Editor · Terminal · Packages · Settings**. Four tabs,
   not five. **No filler tab** to keep the bar at five. Terminal no longer sits
   in the centre — that is arithmetic, not a redesign.
6. The five/six shots that show **no bottom bar at all** are **not** an
   instruction. A later agent who deletes the bar because the shots lack it has
   failed the phase (the roadmap says this in those words).

**Why the review is even needed:** the shots are an SPCK editor with the rooms
in a side panel. Copying that *shape* is the plan. Copying the *absence of a bar*
is what the owner rejected. CodeC keeps its bar so the four rooms stay one tap
away; the panel adds Projects + Files + Search + Repository.

## 2. Question — the bottom row of the 3 × 2 Navigation card

The shot's card (`Screenshot_20260922_124049`, top row **Projects · Editor ·
Settings**, bottom row **Discover · My Labs · Change Log**). CodeC has no
Discover, no My Labs, no Change Log — the roadmap forbids all three.

The top row is therefore **fixed**: `Projects · Editor · Settings` (Projects is
the cell that becomes the new door to the Projects screen in 56).

The bottom row is **open**. The roadmap's own proposal, marked *“a proposal, not
a decision”*:

| Candidate | Why it fits |
|---|---|
| **Terminal** | A room that exists; keeps the bar's four rooms reachable even if a user hides the bar |
| **Packages** | Same; it is also the tour's `packages_card` surface |
| **Guide** | `onOpenGuide` already exists in the editor (`EditorScreen.kt` drawer + guide wiring) — a cell here would re-open the Phase 45 tour |

**Do not copy** Discover / My Labs / Change Log. **Do not** invent a fourth
bottom-row cell beyond the three that fit the 3-column grid.

## 3. Question — the fifth rail icon

Order in every shot: **triangle (Navigation) · folder (Files) · magnifier with
`<>` (Search) · branch (Repository) · person (Account)**. The first four map
straight onto CodeC: Navigation = the door to the rooms, Files = the tree,
Search = find, Repository = git/source control.

The fifth opens **the Account shop** (`Screenshot_20260922_124058`: avatar,
`Upgrade`, credits, AI model, credit cap). CodeC has no account and must not grow
a fake one. The drawing's README (`docs/spck-ui/README.md`) already contains the
ask: *“The fifth icon opens About, not a shop. Say if that icon should open
Settings instead.”*

Options put to the owner: **About** (`SettingsScreen`'s About section — version,
update check, feedback, guide re-run) · **Settings** (a second door to the same
screen) · **nothing** (four rail icons only).

## 4. Missing-shot list — asked, or explicitly deferred

| # | Not in any shot | Whose phase would need it | Status |
|---|---|---|---|
| 1 | **Play on `index.html`** — a run/preview screen, and where the page's own upper links (`Content` / `Home` / `More`) live | 58.3 | **ASK** (the owner's own row: *“the upper links are not phone-friendly … hamburger”*) |
| 2 | The **file `...` menu** (every Files row has one) | 55.3 | **ASK** — or 55.3 draws the `...` and wires it to the *existing* per-row actions, never a new invented menu |
| 3 | A **long-press on selected code** | 57.2 | **DEFER** — omit; nothing is added and no gesture changes |
| 4 | What **`>>`** does (bottom of the panel strip, and the last touch-row glyph) | 55.1 / 57.2 | **DEFER** — draw the glyph to match the shot only if tapping it performs **no** guessed action; otherwise omit and keep asking |
| 5 | What the tab row's **`▤↓`** icon does | 57.1 | **DEFER** — same rule as `>>` |
| 6 | What the **`⌘`-like** touch-row glyph does (7th of eight) | 57.2 | **DEFER** — same rule |
| 7 | What the predictive row's **small right-hand icon** does | 57.2 | **DEFER** — same rule |
| 8 | The screen **after** *Initialize Repository* | 55.3 | **DEFER** — 55.3 ships only the empty state, exactly as the shot shows |
| 9 | The **`...` on the NAVIGATION label**, and on the FILES header | 55.2/55.3 | **DEFER** — draw only where the shot shows it; wire only to existing actions |
| 10 | **Snake sample source** | 58.1 | **NOT NEEDED** — the roadmap already decided: *“Write a small original HTML snake. Do not transcribe SPCK's.”* |
| 11 | What replaces the **first-run welcome tiles** (`WelcomeScreen`, C/Python/HTML) if first open becomes the editor on snake | 58.1 | **ASK** — the shots show no first-run screen at all |
| 12 | The **fifth rail icon's** panel | 55.1 | **ASK** (§3) |
| 13 | The **bottom row of the card** | 55.2 | **ASK** (§2) |

### The deferral rule (so a later phase cannot re-open this)

*Deferring* means the later phase **omits the control**. It never means the agent
guesses an action, and it never means the phase silently drops a *shot* element
that **is** visible (the pill, the status line, the tab strip, the touch row).

## 5. Research that was refreshed, and what it changed

The roadmap requires a freshly fetched page **only** for a claim that is not in a
shot. Two claims in this series are of that kind, and both are **Phase 55/58
work**, so the fetch happens in those parts:

- the **green ▶ on `index.html` in Files** (launch-default marker) — needed by
  55.3;
- the **upper links** treatment — needed by 58.3.

Everything else in 54.1 comes from the pixels. **No claim in 54.1/54.2 rests on
a web page**, so no fetch was needed in 54, and none was invented to look busy.

## 6. What 55–58 may therefore build without asking again

- 55.1 the panel shell + the five-icon rail, with the **first four** panels wired
  and the **fifth** per §3's answer;
- 55.2 the 3 × 2 card with `Projects · Editor · Settings` on top and §2's answer
  below; the **Recent** list (badge only for real external entries);
- 55.3 Files (project name as root, chevrons, the selected-row bar, the
  launch-default ▶), Search (icons + empty), Repository (the one sentence + the
  blue button);
- 56.1 Projects off the bar; 56.2 the verification in
  [54.2](PART_54_2_TODAY.md) §5;
- 57.1 the top row without RUN/⋮, the tab row on its own line with the orange
  active edge; 57.2 the status line hidden while the IME is up, the touch row
  docked, the predictive row while the IME is up; 57.3 the pill;
- 58.1 first open on snake; 58.2 silent userland + one warning; 58.3 the
  hamburger, per the research pass's finding.

Everything else in this list is **ask-first**.
