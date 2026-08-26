# CodeC Phase 5 roadmap

**Status (2026-08-26):** 🚧 **STARTED — Parts 5.1, 5.2, and 5.3 ✅ DONE
(device-verified 2026-08-26).** Phase 4 (Parts 4.1–4.8) is complete and
device-verified; its roadmap now lives in
[`chat-phase4/PHASE4_ROADMAP.md`](chat-phase4/PHASE4_ROADMAP.md). This file
is the same kind of **roadmap, not an execution plan** — it names candidate
areas and their rough provenance. It does **not** pin down exact technical
decisions, UI designs, package lists, or exit-condition wording; those get
decided (and written down in `docs/chat-phase5/`) when a part is actually
picked up, using the same verify-first / evidence-before-hypothesis
discipline used throughout Phases 3–4. **Do not treat anything below as a
spec; treat it as a starting scope to confirm with the owner.**

Parts 5.1 (the two known client fixes), 5.2 (web preview), and 5.3 (the
CodeCApi capability batch) are all **DONE (device-verified 2026-08-26)**;
their decisions, exit conditions, and evidence live in
[`chat-phase5/`](chat-phase5/README.md).

## Why parts are sized the way they are

Each part below is scoped so a single fresh chat session can plausibly take
it from "not started" to "exit condition met and verified" in roughly
**50–70 agent replies** — enough for the usual cycle of reading context,
proposing an approach, writing code, running host tests, and iterating on
real device/CI feedback, without running so long that context or focus
degrades.

This is a budget, not a target to hit exactly. Two consequences:

- If a part turns out bigger once its real scope is understood, **split it**
  into numbered sub-parts (exactly how Phase 4's Android-integration slice
  spawned 4.7 → 4.8 → …).
- Nothing here is a fixed sequence except the stated dependencies. Pick
  whichever part the owner wants next.

## Candidate areas (from Phase 3/4 open items — nothing is committed)

| Candidate | Where it comes from | Notes |
|---|---|---|
| **More Termux:API-style capabilities** (share sheet, open URL, vibrate, toast, sensors/camera/intents…) | Phase 4.7 §8 recipe; `TERMINAL_PLAN.md` §12 "out of scope (future)" | Each = one `CodeCApi` wire op + one CLI script + `BOOTSTRAP_VERSION` bump; the clipboard (4.7) and notification/permission (4.8) paths are proven — permission-free capabilities (toast, share, open URL) or a permission-bearing one (vibrate) can be picked next. |
| **Known client fixes from the 4.5/4.6 post-review (KI-1, KI-2)** | `NEXT_STEPS.md` / `PART_4_5_4_6_POST_IMPLEMENTATION_REVIEW.md` | KI-1: `pkg install` reports failure when the target is already the newest version (treat apt's "0 newly installed" as success). KI-2: device `$PREFIX` (`/data/user/0/…`) vs dpkg-recorded `/data/data/…` spelling confuses manual `update-alternatives` calls (canonicalize `PREFIX` at shell setup). Small, low-risk first parts. |
| **X11 / GUI packages (SDL, Qt)** | `TERMINAL_PLAN.md` §12 | Deliberately deferred: the terminal is text-first. Only if real demand appears. |
| **Full Termux catalog mirroring** | `TERMINAL_PLAN.md` §12 | Large; needs an explicit scope decision (repository-only vs bootstrap, cardinality). |
| **Root-based acceleration** | `TERMINAL_PLAN.md` §12 | Out of scope by policy; do not start without explicit owner direction. |

## Not a Phase 5 part, but still open

- **Optional x86_64 repeat of the Phase 3 Part D clean-device test.** Phase
  3's exit condition was met on aarch64; an x86_64 repeat was never run
  because no x86_64 device was available (see
  `chat-phase3/PHASE3_PLAN.md` §5, M3). Do opportunistically whenever an
  x86_64 device is available. It does not block any Phase 5 part and is not
  itself renumbered as one.

## Ordering / dependency summary

| Part | Depends on | Complexity | Est. replies | Status |
|---|---|---|---|---|
| **5.1 — KI-1 & KI-2 client fixes** | none (client-side only, no rebuild) | small / low-risk | ~10 | ✅ DONE (device-verified 2026-08-26) |
| **5.2 — Web preview (HTML/CSS/JS in WebView)** | none (client-side only, no rebuild) | medium | ~20 | ✅ DONE (device-verified 2026-08-26) |
| **5.3 — CodeCApi batch (toast/share/open-URL/vibrate)** | none (client-side only, no rebuild) | medium | ~15 | ✅ DONE (device-verified 2026-08-26) |
| *(further parts decided when picked up)* | — | — | — | — |

## Rules that carry forward to Phase 5

- Each part gets its own record in `docs/chat-phase5/` with Decision D1 and
  an evidence section (§5.x host + device), same as Parts 4.1–4.8.
- Exit condition must be met and verified — code written is NOT completion.
- Never trigger an expensive action (~60–100 min package rebuild, release,
  destructive device test, force-push) without explicit owner confirmation.
- Keep all Phase 3/4 invariants (`prompt.md` self-distrust protocol, §5):
  no `.` on `PATH`, no `build-package.sh -I`, no `com.termux` binaries or
  official repos, no overwriting `cc`/real ELF `bash`, TCC link order with
  `-o` last, no bootstrap bundled in the APK, signed repository metadata.
- One PR at a time; **no PR creation and no merge without an explicit
  owner command in chat** (owner standing rule, 2026-08-26).
