# CodeC Phase 6 Documentation — Terminal UX

Phase 6 is the **first of the new master roadmap (Phase 6–15)**, planned in
[`../TERMINAL_PLAN.md`](../TERMINAL_PLAN.md) §E (updated 2026-08-26) after
Phase 5 (5.1 KI fixes / 5.2 web preview / 5.3 capabilities) completed and
merged (PR #23). It addresses the concrete usability gaps found in the
Terminal source sweep (2026-08-26) — cutout/insets, extra-keys, wake lock,
URL tap-to-open, BEL, selection copy, static title — all without any
expensive rebuild (`[client-only]`).

## Parts & Status (as of planning — not started)

- **[Part 6.1 — Safe-area / cutout + extra-keys + wake lock + URL / BEL / title / selection copy](PART_6_TERMINAL_UX.md)** — planned; D1 and exit condition defined below.

If the user picks a different Phase 6 sub-part (e.g., only multi-terminal in
Phase 7, or projects in Phase 8), this folder is adjusted or replaced.

## Rules that carry forward

- No PR / merge / push to `main` without explicit owner command.
- Verify first (`git status`, source check, host-test) before any edit.
- Evidence before hypothesis (device transcript required for "done").
- All Phase 6 work is `[client-only]` — no `codec-packages` rebuild needed.
