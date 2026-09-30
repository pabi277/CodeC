# Phase 64.2 — remove the guide, not just its first-launch trigger

**Status:** implemented · **Cost:** client-only · **Effort:** M

## First move: evidence, not code

Before-edit base: `ef0e030`, 2026-09-27.
- `MainActivity.kt:820/:887/:1626`: persisted slide gate and coach overlay.
- `ui/guide/`: GuidePlan, GuideScreen, CoachMarkPlan, CoachMarks.
- `ui/screens/EditorScreen.kt:1000/:1186`: typing-tip modal and forced Files
  panel for an active tour beat.
- `ui/settings/SettingsManager.kt:95/:198`: guide and seen-mark persistence.
- `ui/screens/SettingsScreen.kt:1073`: Help & guide / Reset tips; corresponding
  rows in SettingsSearch and the Settings audit inventory.

Paths are relative to `app/src/main/java/com/codeci/ide/`.

## Design

Delete the feature, including all route callbacks, anchors, guide-only state,
preferences API and menu/search entries. Startup only reads the existing sample
seed flag. Unknown old guide preference values are ignored, not migrated by
clearing unrelated settings. Preserve sample projects and normal navigation.

## The Android edge

Remove overlays/anchor modifiers from editor, drawer, bottom bar, reveal handle,
Packages, Terminal and Preview. Remove the typing-tip dialog. Keep editor modal
state needed by Back routing, even though it no longer feeds a coach overlay.
Navigation card keeps three-column alignment with a non-interactive spacer.

## Exit condition

Fresh installs and upgrades have no slides, guided spotlight, typing tutorial,
or Guide/reset menu entry. No dead Settings search result. No navigation cell
that pretends the deleted guide still exists. No project or setting reset.

## Tests / sources

`UnrestrictedUiWiringTest` (guide absence, startup, navigation space), Settings
catalog/audit/reader tests, Back handler and side-panel tests. Source evidence
above. Existing reference shots: `docs/reference/spck-ui/Screenshot_20260922_*.jpg`;
reviewed this session, interpreted through the owner's later removal request.

## Deferred / rejected

No new help screen, replacement onboarding, or reset switch. The existing
sample/demo generation policy is not silently changed; future chat may ask
whether deleted demo projects should stay deleted. Written repository docs are
not an in-app guide system and remain available.
