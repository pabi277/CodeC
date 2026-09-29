# Phase 73.8 — Git page owns credentials, the install card, and beginner hints

**Status: 🚧 IMPLEMENTED (2026-09-29), CI ✅ GREEN `36593360018` on
`aa8912d`; session branch `arena/01a0eca9-codec`. Owner device pass owed,
not merged (rule.md §3).
Not one of the numbered UI-polish discussion drafts (65.1/74.1 remain
untouched) — this is a rule.md §4 lifecycle item from the owner's own
follow-up on the 73.7 build (see below).**

## The owner's follow-up (2026-09-29, on the 73.7 build)

After 73.7 the owner sent a punch-list, in their words: the push-menu
trigger should be ⋮ ("more button") because the share glyph reads as
"share this file"; the git install progress "is just a status bar" —
they want the package part's treatment, "a card saying installing git
like in package part, will show live % and two second delay between
every refresh or line, + user will have to wait… and will show live
line. If failed then failed and retry. But not redirected to
terminal"; the token flow must live "in the git page" (Settings is a
detour a beginner never finds); and the git UI is "hard but ok" for
newcomers — hints needed. Five scope questions were asked before
coding; the owner locked all five: (1) **package-style install card**
(userland stage + the installer's real %, git = elapsed + live line +
fail tail, no fake %, no Terminal redirect); (2) **⋮ trigger**; (3)
**repoint every stale text** that names Settings/Modules/Terminal for
credentials or install; (4) **three inline hints** (grey one-liners,
no popups); (5) **clone dialog opens credentials inline** (no project
exists yet at clone time, so the git page cannot host that one).

## What shipped

- **The install card** (`GitInstallGuidance` →
  `GitInstallCard`): `PackageItemCard`'s shape — a Card with a header
  row, the command, and an INSTALLING/FAILED badge. While the
  Linux-tools gate refuses, the card shows the USERLAND's live state:
  the Terminal tab's own stage words (`TerminalStatusLabel.label` —
  one wording, two screens), a determinate bar only while the
  installer reports a real download %, the gate's own refusal sentence
  as the note, and "Install Git unlocks when the Linux tools finish".
  Two label overrides where the shared wording's subject is wrong for
  this card: CHECKING ("Checking the Linux tools…", not "starting
  shell…") and FAILED (the shared "tap ⬇ to retry" points at a
  Terminal-tab button that isn't here). 73.2's "plain message pointing
  at Terminal" decision is retired. Once the gate allows, the card
  shows git's own install: elapsed + indeterminate bar (unchanged,
  `pkg` reports no %) + the installer's **live last line** read from
  the shared session's transcript (baseline-dropped to this install,
  one line, 140 chars) + the background note; on failure the last 6
  lines stay **in the box** under a "last lines" label, with RETRY —
  the "open Terminal" redirect is deleted from the message.
- **⋮ trigger**: the push menu opens from `MoreVert`, not the share
  glyph (the Share icon inside the menu stays).
- **Credentials on the git page**: the readiness NO_TOKEN row grew a
  GIT CREDENTIALS button next to the help link (same shape as the
  row's PUBLISH remedy); the push menu's credentials row and the
  Commit dialog's row already opened `GitCredentialsDialog`. Every
  credential/install guidance text was repointed at the git page:
  `GitReadiness` (3 messages), `GitErrors` (tokenMissing,
  tokenInvalid, repo-not-found, notInstalled), the publish 401/403
  paths, and the push-outcome messages. No user-visible text names
  Settings, Modules, or Terminal for these flows anymore.
- **Clone dialog inline credentials**: the token hint's link opens
  `GitCredentialsDialog` stacked above the clone draft (the URL draft
  underneath is kept; cloning reads the shared store, so a fresh
  token just works). "Manage" inside that dialog still jumps to
  Settings' own editor.
- **Three beginner hints** (one grey `labelSmall` line each, no
  popups, no coach marks): under UNSTAGED (what lands there, what
  Commit All does with it), in the Commit dialog ("stays on this
  device until you Push"), in the Push dialog ("uploads your commits
  to the remote").

## What was deliberately NOT done

- No fabricated install percentage: `pkg`/`apt` gives no machine %
  without fragile fd plumbing (73.2's finding stands); the owner
  accepted the honest box (real userland % where it exists, elapsed +
  live line for git) over experimental apt surgery.
- No "2-second delay" ticker change: the poll tick stays 1.5 s (the
  live line visibly changes line-by-line, which is the effect asked
  for).
- `TerminalUx` is only a test-class name — the stage-words API is
  `TerminalStatusLabel` (a wrong import from a stale note broke the
  first CI round; fixed same-day).
- 73.3's "GitHubActions…" stale-text archaeology is untouched
  (historical quotes in comments, verified intentional).

## Tests

- `GitInstallWiringTest`: gate test rewritten for the card (userland
  pins, determinate-bar-only-with-real-% pins), polling test gains
  transcript/baseline/tail pins, status-bar test follows the bar into
  the card (live-line pins), failed test gains tail pins, strings test
  gains the 5 card strings + the no-Terminal pin on the failed
  message.
- `GitPanelWiringTest`: new cases — guidance repoints (content pins
  + `assertFalse` on "Settings → GitHub Account" across the four
  sources), the row's credentials button, the clone inline dialog,
  the three hints + their strings.
- `GitSpckPanelWiringTest`: ⋮ trigger pin (scoped slice).
- `GitErrorsTest`: no-token and notInstalled pins follow the rewords.
- CI needed 3 rounds: (1) red — `TerminalUx` import + a stray
  duplicate `@Composable` (compile errors, fixed); (2) red — one
  missed `pkg install git` pin in `GitErrorsTest` (fixed); (3) green
  `36593360018`.

## Owed

- Owner device pass + merge command for 73.1–73.8 together (rule.md
  §3).
- Device cleanup (carried since 73.6): the stray `projects/.git` from
  the old drawer-init bug still needs `rm -rf` on the owner's device.
