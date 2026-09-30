# Proposed Phase 66.1 — Projects hub and creation

**Status: discussed and agreed on 2026-09-27; implemented on
`arena/01a0e220-codec` (Android CI green: `a96fb25`
[36311728346](https://github.com/pabi277/CodeC/actions/runs/36311728346),
`3762b7b` [36317041278](https://github.com/pabi277/CodeC/actions/runs/36317041278));
owner device-verified and merge-authorised (*"Device verified merge it to the
main"*) via [PR #88](https://github.com/pabi277/CodeC/pull/88) — see
[`docs/phases/12-ui-polish-program/chat-phase66/`](chat-phase66/README.md).**
The owner's answers are recorded verbatim in *Your thoughts* below. Not merged;
no PR opened (owner instruction: *"Do not start other phases or merge anything
automatically"*). One part = one chat. No deadline, dependency, new control or
replacement engine promised.

## Owner priority — 2026-09-27

The owner chose **Projects and files** as the first future polish chat, keeping
the current visual direction. This makes this brief the suggested starting
point; it does not approve a cards-to-list redesign or change demo deletion.
The detailed question below still needs the owner's answer.

## Copy into a new chat

> Review `docs/journal` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/FileManagerScreen.kt:155`](../../../app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt#L155), reviewed on 2026-09-27.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 124049 shows recent projects, not the whole hub. Hub changes need the owner’s preference; no invented screenshot parity.

## My recommendation

Keep All / Recent / Create and name-derived marks. Improve hierarchy, naming, empty states and progress only where the owner identifies friction.

## Bounded review checklist

Search/filter combinations; long and duplicate-looking names; recent ordering; create/template sheets; clone/import/export; failure and permission states; resume card.

## Your thoughts — ask before code

**For projects, do you prefer the current cards or a denser list? Should deleted bundled demos stay deleted?**

Suggested starting point, not your decision: Keep cards for now; discuss persistent demo re-seeding before changing it.

**Owner answers — 2026-09-27 (chosen from the options offered in chat, recorded
verbatim as the option labels the owner selected):**

1. *Projects list: keep the current cards, or move to a denser list?* —
   **"Keep the current cards"**.
2. *Should a deleted demo_flask stay deleted (like snake already does)?* —
   **"Yes — stay deleted"** (not the "stay deleted, but restorable" option).
3. *Which bundle should Part 66.1 implement first?* —
   **"(a) demo + (b) dialogs + (c) wording — all in one part"**.
4. *After 'Create' with a typed template (e.g., C Program), where should the
   user land?* — **"Keep today: hub file tree"**. *Superseded 2026-09-28:* the
   owner chose **"Same, and 'Create project' also lands in the editor"** when a
   card's tap became the editor by default — see
   [`../chat-phase70/README.md`](chat-phase70/README.md) §Round 5; owner-tested
   2026-09-29 and merged via [PR #93](https://github.com/pabi277/CodeC/pull/93).

Not asked as options, therefore **not decided and not implemented** here: the
clone dialog's non-functional QR glyph, the filter chips' ≈36 dp touch height,
the `Auto (detect)` default that scaffolds an empty project, and a real
"last opened" history for *Recent*. They stay open for a later chat.

## Implementation and exit, after agreement

ProjectsHub, ProjectMark, ProjectTransfer, WelcomeStarters and DemoProjects; preserve file-safe imports and real recency.

Use existing platform/components and pure policies first. Add focused regression
coverage for the agreed behaviour, check loading/empty/failure states, long text,
Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on
the session branch. Report separately what source tests, Android tests and real
device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default.
Stop after this agreed part and update its record; do not roll into the next chat.
