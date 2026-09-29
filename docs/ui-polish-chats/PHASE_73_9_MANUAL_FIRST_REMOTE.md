# Phase 73.9 — Manual-first no-remote flow (origin by default)

**Status: ✅ IMPLEMENTED (2026-09-29), CI ✅ GREEN `36597542971` on
`e4f8d6a`; session branch `arena/01a0eca9-codec`. Owner device pass ✅ 2026-09-29, merged via PR #95.
Not one of the numbered UI-polish discussion drafts (65.1/74.1 remain
untouched) — this is a rule.md §4 lifecycle item from the owner's own
follow-up on the 73.8 build (see below).**

## The owner's follow-up (2026-09-29, on the 73.8 build)

After 73.8 the owner pointed out the premise flaw in the no-remote
flow: most users will not give their git token the repo-create
permission, so Publish fails for them. The directive, in their words:
"focus no remote user have to 1st create a repository and paste its
link in remote and set the name at origin default. Other things are
ok." No scope questions were needed — the flow was prescribed
exactly, and "other things are ok" fenced the rest.

## What shipped

- **Readiness row, manual-first**: the NO_REMOTE message now reads
  "Create the repository on GitHub first, then paste its link in the
  Remotes dialog — the name defaults to origin." Step 1 sits under
  the message as a "Create the repository on GitHub ↗" link (opens
  `github.com/new`); step 2 sits on the row as an ADD REMOTE button
  that pre-loads the remotes and opens the Remotes dialog (same door
  as the branch menu's Remotes item). The PUBLISH button it replaces
  is gone from the row.
- **`origin` by default**: the New Remote name field starts at
  `origin` (git convention for the first link) unless origin is
  already taken, in which case it starts empty as before.
- **`GitHelpLink` label param**: the link composable took only a URL
  and hardcoded the token label; it now takes an optional label (the
  two token call sites are unchanged).
- **Model honesty**: `actionId(NO_REMOTE)` is the new
  `ACTION_ADD_REMOTE`; the now-unreferenced `ACTION_PUBLISH_REPO`
  const is deleted.

## What was deliberately NOT done

- The Publish dialog, its engine, and its permission-asked flow are
  untouched ("other things are ok") — it stays reachable through the
  after-push "Still local" card for the minority whose tokens do
  carry the create permission. Proactive Publish from the readiness
  row is gone by design: a button that fails for most users is not a
  remedy.
- No new strings: the row button and link label follow the existing
  hardcoded-literal precedent (`PUBLISH`, the token link label).

## Tests

- `GitHubPhase40Test`: the push-readiness case pins
  `ACTION_ADD_REMOTE` now.
- `GitPanelWiringTest`: new `the no-remote flow is manual-first with
  origin as the default name` case — message content, the
  `github.com/new` help link with its label, the row's ADD REMOTE
  button (scoped slice: pre-load + open dialog +
  `assertFalse(showPublishDialog)`), and the origin-default
  expression in `NewRemoteDialog`.
- CI green first round, `36597542971` (no sandbox JDK/SDK — verified
  with a Python mirror of every new/changed assertion + CI as the
  compiler).

## Owed

- None — device pass ✅ 2026-09-29 (*"Everything looks good"*), merged with 73.1–73.9 together via PR #95 (the stray `projects/.git` cleanup retired with the pass).
