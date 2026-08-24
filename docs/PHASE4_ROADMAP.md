# CodeC Phase 4 roadmap

**Status:** planning only. **No Phase 4 code has been written.** Phase 3
(package management: `pkg`, apt/dpkg bootstrap, signed repository) is complete
and device-verified — see [`JOURNEY.md`](JOURNEY.md) and
[`PHASE3_DEVICE_ACCEPTANCE.md`](PHASE3_DEVICE_ACCEPTANCE.md). This document is
the entry point for Phase 4: polish and expansion on top of that foundation.

This is deliberately a **roadmap**, not an execution plan like
[`PHASE3_PLAN.md`](PHASE3_PLAN.md)/[`NEXT_STEPS.md`](NEXT_STEPS.md). It names
parts, their rough scope, and their dependencies. It does **not** pin down
exact technical decisions, UI designs, package lists, or exit-condition
wording — those get decided (and written down, updated in this file) when a
part is actually picked up, using the same verify-first / evidence-before-
hypothesis discipline used throughout Phase 3. Do not treat anything below as
a spec; treat it as a starting scope.

## Why parts are sized the way they are

Each part below is scoped so a single fresh chat session can plausibly take it
from "not started" to "exit condition met and verified" in roughly
**50–70 agent replies** — enough for the usual cycle of reading context,
proposing an approach, writing code, running host tests, and iterating on real
device/CI feedback, without running so long that context or focus degrades.

This is a budget, not a target to hit exactly. Two consequences:

- If a part turns out bigger once its real scope is understood, **split it**
  into sequential sub-parts at that time (e.g. `K1`, `K2`) rather than letting
  one chat run indefinitely long or cutting corners to fit.
- If a part turns out smaller, that is fine — "done" is still governed by its
  exit condition, not by using the full budget.

Some items below are pre-split into multiple parts already, because their
Phase 3 analogue (build → publish → device-verify) is known from this project's
history to need more than one part's worth of work.

## How to use this file

1. Read [`JOURNEY.md`](JOURNEY.md) and [`NEXT_STEPS.md`](NEXT_STEPS.md) first
   to confirm Phase 3's state has not regressed.
2. Pick **one** part below. Confirm with the project owner which one before
   starting, and confirm current repo/PR/CI state — do not assume anything in
   this file is still accurate; it will drift as work happens elsewhere.
3. At the start of that part's work, write down the specific technical
   decisions this file left open (in the part's own PR/docs, not necessarily
   back in this file), then build, test, and verify exactly like Phase 3:
   evidence before hypothesis, no unverified "done" claims, respect the
   existing invariants (`PHASE3_PLAN.md` §1, `chat-phase1/SOLUTIONS.md`,
   `chat-phase2/SOLUTIONS.md`).
4. Update this roadmap's status line for that part when it is actually
   finished and verified — not before.

## Parts

### Part 4.1 — Shared-storage access

**Basic goal.** Give terminal programs a way to read/write user-designated
shared storage (e.g. `~/storage/downloads`-style access to Downloads/
Pictures/etc.), analogous to Termux's `termux-setup-storage`, using Android's
current scoped-storage model and explicit user consent.

**Complexity:** small. **Est. ~50–70 replies.** Single, fairly self-contained
feature; no dependency on other Phase 4 parts.

**Left open on purpose:** whether this is a SAF-backed bridge, a
`MediaStore`-based helper, or something else; exactly which shared
directories are exposed and how they appear in `$HOME`; how/when the
permission is requested and re-requested if revoked.

---

### Part 4.2 — Package-install confirmation UX

**Basic goal.** Before `pkg install`/`dpkg` actually mutates the system, show
the user what is about to happen (package set, versions, download size) and
require explicit confirmation, making the existing internal `pkg` preflight
checks user-visible instead of silent.

**Complexity:** small. **Est. ~50–70 replies.**

**Left open on purpose:** the exact UI surface (in-terminal prompt vs. a
dialog), how this interacts with scripted/non-interactive `pkg` invocations
and the existing test suite, and how much detail to show by default vs. on
request.

---

### Part 4.3 — Trust/channel indicator UX

**Basic goal.** Let a user tell at a glance, in Settings and/or the terminal,
whether they are talking to the signed CodeC repository and which channel/
release they are on, instead of that information being visible only via raw
`pkg`/`apt` output.

**Complexity:** small. **Est. ~50–70 replies.** Mostly UI plus reading
already-published repository/keyring metadata; no new backend trust model is
implied — Part D's signing design already exists and should not be reopened.

**Left open on purpose:** exact wording/placement, and whether "channel" needs
to become a first-class concept beyond today's single development channel.

---

### Part 4.4 — Terminal/editor settings parity

**Basic goal.** Close gaps between the terminal's theme/font/environment
options and the editor's existing `SettingsScreen`/`ThemeManager` so the two
feel like one coherent product rather than two separately configured
surfaces.

**Complexity:** small–medium. **Est. ~50–70 replies** — start this part with a
short inventory of the actual current gaps (do not assume a list here); if
that inventory reveals more than fits the budget, split into `4.4a`/`4.4b`
at that point.

**Left open on purpose:** which specific settings need to move/merge, and
whether terminal-specific options (e.g. PTY-related) stay separate by design.

---

### Part 4.5 / 4.6 — Expand the curated package catalog (round 2)

**Basic goal.** Grow the Phase 3 package set beyond the original curated roots
(`nano less coreutils grep sed gawk gzip tar make libmagic`, per
[`PHASE3_PLAN.md`](PHASE3_PLAN.md) §4 and [`codec-packages/README.md`](../codec-packages/README.md))
with more commonly requested tools, using the same source-build /
dependency-closure / signed-repository / device-acceptance discipline already
proven in Phase 3 — not a new mechanism.

**Complexity:** medium–large, **pre-split into two parts** because Phase 3's
own history shows the build side and the device-acceptance side are each a
full part's worth of work on their own:

- **Part 4.5 — build and validate the expanded closure in CI.** Est. ~50–70
  replies. Candidate package list, recipe overrides if needed, host-test
  coverage, and a green CI build — no device testing yet.
- **Part 4.6 — publish and device-verify the expanded repository/bootstrap.**
  Est. ~50–70 replies. Depends on 4.5. Mirrors Part D's publish +
  clean/upgrade-device acceptance pattern, including the same "never dispatch
  an expensive/destructive step without explicit confirmation" rule.

**Left open on purpose:** exactly which packages, and whether new packages
ship inside the bootstrap or are only ever installed later via `pkg` from the
repository (most should probably be the latter, to keep the bootstrap small —
confirm this when the part starts).

---

### Part 4.7 — Termux:API-style Android integration (foundation slice)

**Basic goal.** A first, narrow slice of Android-native capability exposed to
terminal programs (for example, a single low-risk capability such as
clipboard access or a vibrate/notify call — the exact first capability is
chosen when this part starts), establishing a reusable pattern (permission
handling, the bridge/IPC mechanism, and a CLI command shape) that later
capabilities can follow without redesigning the plumbing each time.

**Complexity:** large epic. **This entry only scopes a first foundation
slice at ~50–70 replies.** [`TERMINAL_PLAN.md`](TERMINAL_PLAN.md) §12 lists
this area as explicitly out of scope for Phase 3, and it is the least-defined
item in this roadmap — treat it as lower priority than Parts 4.1–4.6, and
expect it to spawn further numbered parts (`4.8`, `4.9`, ...) once the
foundation slice establishes the pattern and a real backlog of specific
capabilities exists. Do not attempt the whole surface area in one part.

---

## Not a Phase 4 part, but still open

- **Optional x86_64 repeat of the Part D clean-device test.** Phase 3's exit
  condition was met on aarch64; an x86_64 repeat was never run because no
  x86_64 device was available (see [`PHASE3_PLAN.md`](PHASE3_PLAN.md) §5, M3).
  This can be done opportunistically whenever an x86_64 device is available.
  It does not block any Phase 4 part and is not itself renumbered as one.

## Ordering / dependency summary

| Part | Depends on | Complexity | Est. replies |
|---|---|---|---|
| 4.1 — storage access | none | small | ~50–70 |
| 4.2 — install confirmation UX | none | small | ~50–70 |
| 4.3 — trust/channel indicator UX | none (reads existing Part D metadata) | small | ~50–70 |
| 4.4 — settings/theme parity | none | small–medium | ~50–70 (may split) |
| 4.5 — expanded package build (CI) | none | medium | ~50–70 |
| 4.6 — expanded package publish + device accept | 4.5 | medium | ~50–70 |
| 4.7 — Android integration foundation slice | none | large (epic seed) | ~50–70 (first slice only) |

None of these parts block each other except 4.6 on 4.5. Pick whichever the
project owner wants next; nothing here is a fixed sequence.
