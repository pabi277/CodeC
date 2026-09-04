# CodeC Phase 24 — Polish Batch: Feasible Items from Groups 3-5

**Status:** 🚧 **IN PROGRESS** — owner's "Start Phase 24" command received (2026-09-03); implementation is on `arena/01a06784-codec`. Eight parts implemented (E.1–E.4, E.6–E.9); **E.5 tablet two-pane is deferred**. **CI green `33768581748`** (+ later doc-only run `33769074554`). **Device round 1 (2026-09-04): E.1 ✅ / E.2 ✅ / E.4 ✅ / E.6 ✅ / E.7 ✅ / E.8 ✅ / E.9 ✅; E.3 ⏳ not possible this round (needs BT keyboard/tablet); E.5 ⏸ deferred.**
· **Cost:** `[client-only]` — all items are pure Kotlin/Compose; no `[repo-build]`
  (except E.2 formatter, which needs the tools from Phase 20 to be installed)
· **Depends on:** Phase 21 (registry with `formatterTemplate`); Phase 22 (IME keys infrastructure
  for E.5); Phase 20.1 (language tools installed for E.2 formatter to work on-device)
· **Blocks:** nothing

> **Owner:** "take ideas 3, 4, 5 now (the feasible / low-cost ones)"
>
> Full research & design rationale:
> [`docs/RESEARCH_NEXT_PHASES.md`](../RESEARCH_NEXT_PHASES.md) §Phase 24.

---

## Why this exists

After the compiler redesign (Phase 21) and editor smoothness (Phase 22), a batch
of low-cost, high-value polish items improves daily usability significantly.
These were researched in `RESEARCH_NEXT_PHASES.md` and classified as feasible
(no X11, no root, no new native code required).

---

## The nine parts

| Part | Title | Effort | Doc |
|---|---|---|---|
| **E.1** | Per-language code formatter (Format menu) | XS | [PART_24_1_FORMATTER.md](PART_24_1_FORMATTER.md) |
| **E.2** | Background-run notification (foreground service) | S | [PART_24_2_NOTIFICATION.md](PART_24_2_NOTIFICATION.md) |
| **E.3** | Hardware keyboard shortcuts | S | [PART_24_3_HW_SHORTCUTS.md](PART_24_3_HW_SHORTCUTS.md) |
| **E.4** | Project ZIP share (Export + Share intent) | XS | [PART_24_4_ZIP_SHARE.md](PART_24_4_ZIP_SHARE.md) |
| **E.5** | Tablet two-pane layout (`WindowSizeClass`) | S | [PART_24_5_TABLET.md](PART_24_5_TABLET.md) |
| **E.6** | Test-runner UI (pytest / go test output tab) | S | [PART_24_6_TEST_RUNNER.md](PART_24_6_TEST_RUNNER.md) |
| **E.7** | "Open with CodeC" intent filter | XS | [PART_24_7_OPEN_WITH.md](PART_24_7_OPEN_WITH.md) |
| **E.8** | Adaptive theme (auto follow system dark/light) | XS | [PART_24_8_ADAPTIVE_THEME.md](PART_24_8_ADAPTIVE_THEME.md) |
| **E.9** | Per-project `.codec.json` run-config override | S | [PART_24_9_PROJECT_CONFIG.md](PART_24_9_PROJECT_CONFIG.md) |

---

## Device round 1 (2026-09-04, owner on-device)

| Part | Result | Notes |
|---|---|---|
| **E.1** Formatter | ✅ PASS | Includes Python: `⋮ → Format` on an `if/else` .py file corrected indentation (owner: "Formater pass"). Black-style snippet (`def add(a,b):`, `x=1+2`, `name='Alice'`, long `y=[...]`) was provided to confirm black output beyond indentation. |
| **E.2** Notification | ✅ PASS | Owner: "I tested the notification part it's working fine" (5 s foreground notification, Stop action, no notification for short runs). |
| **E.4** Share as ZIP | ✅ PASS | Projects `⋮ → Share as ZIP` opened the share sheet / received ZIP. |
| **E.6** Test runner | ✅ PASS | `Test ▷` button, pytest output, PASSED/FALLED line colours. |
| **E.7** Open with CodeC | ✅ PASS | Shared `.c`/`.py`/`.zip` opened/imported. |
| **E.8** Adaptive theme | ✅ PASS | Auto followed system dark/light live; explicit Dark override held. |
| **E.9** `.codec.json` | ✅ PASS | `⋮ → Edit run config` wrote `.codec.json`, multi-file build ran, deletion fell back to registry. |
| **E.3** Hardware shortcuts | ⏳ NOT VERIFIED this round | Owner: "E.3 is not possible in this time" — needs a Bluetooth keyboard / tablet. Code is CI-green + host-tested (`EditorLineOpsTest`, `RunForegroundPolicyTest`), but the physical-key §3 recipes (Ctrl+R, Ctrl+/, Ctrl+D, Ctrl+W, Ctrl+Tab, F5) were not run. |
| **E.5** Tablet two-pane | ⏸ DEFERRED | Needs `EditorScreen` body extracted from the `ModalNavigationDrawer` (or a window-size dependency); structural refactor deferred pending owner confirmation. |

---

## ⚖️ Ground rules

- **Phone-first:** all parts must work well on a phone before being optimised
  for tablet (E.5 is the one exception, which targets tablets explicitly but
  must not regress on phones).
- **No PR/merge and no push to `main` without the owner's explicit command.**
- **Client-only** for all nine parts: no `[repo-build]`, no bootstrap changes.
  E.2 (notification) uses `POST_NOTIFICATIONS` which is already declared (Phase 4.8).
- Each part is **independently shippable** — they can be done in any order or
  batched into one commit. Decide at implementation time based on CI capacity.
- All new logic must be **host-unit-testable** where possible.

## ⚠️ Deferred items (not in Phase 24, for the record)

| Item | Why deferred |
|---|---|
| X11 / SDL / Qt GUI packages | No X11 server; explicit policy exclusion |
| Kivy / PyQt Android binding | Requires X11 or Wayland; rabbit hole |
| Root-based acceleration | Out of scope by policy |
| Full Termux catalog mirror | Cardinality; wait for Phase 20 to settle |
| REPL mode (B.2 ↑/↓ history) | Tracked in `RESEARCH_NEXT_PHASES.md` §4.10 |
| Multiple cursors | L effort; `RESEARCH_NEXT_PHASES.md` §4.13 |

## Standing rules (unchanged)

- CI (`Build APK`) = assemble + unit tests + lint — the only test executor.
- Verify state before acting; a part is done only when device-verified.
- No regression to any completed phase.
