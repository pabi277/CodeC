# Phase 66 — Projects hub and creation (UI polish, owner priority)

**Owner instruction (2026-09-27, verbatim):**
> Start the discussion for Phase 66.1. Read `docs/UI_POLISH_REVIEW_20260927.md`
> and `docs/ui-polish-chats/PHASE_66_1_PROJECTS.md`. Keep the current look.
> Review the Projects hub, explain what already works, recommend specific
> improvements, and ask my thoughts before implementing. Do not start other
> phases or merge anything automatically.

**Status:** Part 66.1 implemented on `arena/01a0e220-codec`, code `a96fb25`,
**Android CI green on the first round**
([run 36311728346](https://github.com/pabi277/CodeC/actions/runs/36311728346));
not merged, no PR opened (owner: *"Do not start other phases or merge anything
automatically"*). Device behaviour not verified — see [Validation](#validation).

| Part | Scope | Status |
|---|---|---|
| [66.1](PART_66_1_PROJECTS_HUB.md) | (a) demo seeded once · (b) truthful hub dialogs · (c) hub wording/consistency | Implemented on the session branch |

## The discussion, as it happened

The chat began with a review of the hub read from source (the review's
[§3.2](../UI_POLISH_REVIEW_20260927.md) and the brief's *Review scope*): what
works, which defects can be reproduced by reading the code, and what should
stay. Four questions were then put to the owner as options. The owner's
answers, recorded as the option labels chosen (verbatim):

1. *Projects list: keep the current cards, or move to a denser list?* —
   **"Keep the current cards"**.
2. *Should a deleted `demo_flask` stay deleted (like `snake` already does)?* —
   **"Yes — stay deleted"**.
3. *Which bundle should Part 66.1 implement first?* —
   **"(a) demo + (b) dialogs + (c) wording — all in one part"**.
4. *After "Create" with a typed template (e.g. C Program), where should the
   user land?* — **"Keep today: hub file tree"**.

Nothing beyond those four answers was treated as approval. Items reviewed but
**not asked and therefore not implemented**: the clone dialog's non-functional
QR glyph, the filter chips' ≈36 dp touch height, the `Auto (detect)` default
that scaffolds an empty project, a real "last opened" history for *Recent*,
middle-ellipsis for long names. They remain open for a later chat.

## What already worked (kept, untouched)

- The hub's three list branches (loading skeleton / designed empty state /
  list), the filter chips and search, the project cards with name-derived
  marks, the ⋮ sheet (open, rename, export, delete, git actions) and the ＋
  sheet (new, import ZIP, clone).
- `snake`'s first-open seed already used a "seeded once" flag; Phase 66.1
  gives `demo_flask` the same law rather than a new mechanism.
- Every operation's failure was already reported — only its *surface* was
  wrong when a dialog was open (the same class of defect as the owner's
  Phase 40.1 clone bug).

## Design decisions fixed in code (Part 66.1)

- **Seed once, never "try once".** `DemoProjects.ensure` writes the marker
  `.demo-flask-seeded-v1` in the projects root **after** a complete seed; a
  failed seed leaves no marker, so the next launch tries again. A deleted
  `demo_flask` stays deleted. It can be created again only the ordinary way
  (New Project → *Flask Web Server*); no restore control was added.
- **One name policy, both sides.** `ProjectNameCheck` (pure) says *empty /
  invalid / taken* exactly as `ProjectManager.createProject` would after the
  tap. The dialogs read it for the field's error state; the ViewModel is still
  the last word.
- **A failure is shown where the user is looking.** `createProject`,
  `renameProject` and `importZip` take `onFailed`; the ViewModel posts the same
  message to the snackbar (for after the dialog closes) **and** to the dialog.
- **ZIP import promises the real name.** The dialog's hint ("…will be imported
  as `snake_2`") and the ViewModel's actual name both come from
  `ProjectNameCheck.importedNameFor` (`ProjectsHub.uniqueProjectName`, the
  clone path's scheme). The old `substringBeforeLast('.')` stripping is gone —
  a typed `v1.2` no longer becomes `v1`.
- **One vocabulary for age.** `ProjectsHub.relativeAge` delegates to
  `RecentProjects.ageLabel`, so a card and the side panel say the same words
  for the same project ("18 minutes ago", not "18 min ago" here and "18 minutes
  ago" there).
- **Nothing new in the visual system.** The rows keep their ●/○ glyphs (they
  gained radio-button semantics, not a widget); spacing is `CodecTokens.space`;
  the amber badge reads `CodecPalette.WARNING`; destructive confirms use the
  theme's error role through `ButtonDefaults.textButtonColors`.

## Follow-up — Back from Projects returns to the editor (owner device report)

**Owner (2026-09-27, verbatim):**
> One small issue when i go to projects and press back it's showing options of
> close the app but i want preview editor page than if i press back it will be back

**Cause, read from `MainActivity.kt`.** Projects left the bottom bar in
Phase 56, but its two doors from the editor — the side panel's *Projects* cell
(`onOpenProjectsHub`) and the drawer's *＋ New project…* (`onOpenProjects`) —
still navigated tab-style: `popUpTo(start) { saveState = true }`. Whenever the
app had started on the hub (the resume card — every launch after more than five
minutes away — or no file to resume), that `popUpTo` **popped the editor**, so
the hub had nothing under it and the back router's row 9 showed the exit
prompt. When the app had started in the editor the same press already went
back to it, which is why the defect looked intermittent.

**Fix.** The two doors now push the hub over the editor exactly like the side
panel's *Settings* cell does (`launchSingleTop`, no `popUpTo`; the ＋ door keeps
`restoreState = false`, the *Projects* door keeps `restoreState = true`). The
router did not change: with the editor underneath, row 8
(`canPopRoute → PopRoute`) returns to the editor the user left, and the exit
prompt is the next Back — *"than if i press back it will be back"*. *Packages*
is still a tab and keeps the tab idiom. Pinned by
`BackHandlerWiringTest.Projects opened from the editor is a page over it`.

**Accepted trade-off.** A user who hops editor → Projects → file → Projects …
through the side panel builds a stack that retraces every hop (the same
property the Settings door has had since Phase 16). Bounding it would need
inclusive pops that break "Back goes back", so it is left as the user's own
path; a later shell pass (65.1) can revisit if it ever shows up on a device.

**Verification.** Source-scan and router tests: `BackHandlerWiringTest`,
`BackRouterTest`, `BackRouterRootTest`, `ExitPromptPolicyTest`,
`DrawerWiringTest`, `SidePanelWiringTest`, `ResumeWiringTest` — **53 / 0**
locally (prevalidation shim), plus the 39 pure source-scan classes 197 / 0.
**CI green on the first round for the follow-up head `3762b7b`:
[run 36317041278](https://github.com/pabi277/CodeC/actions/runs/36317041278).**
Device: the owner's report is the only device observation; the fix is not
device-verified in this session.

## Validation

- Local JVM prevalidation with a temporary JUnit shim (kotlinc 2.2.10, Temurin
  25; **not** an Android or Gradle run — Compose files cannot be compiled in
  the sandbox): the pure production set (`ProjectNameCheck`, `DemoProjects`,
  `ProjectsHub`, `SidePanelPlan`, their dependencies) compiles; the focused
  hub tests plus every pure source-scan test in the module pass —
  **192 / 0** (25 classes: `DemoProjectSeedTest`, `ProjectNameCheckTest`,
  `ProjectsHubTest`, `HubDialogWiringTest`, `HubSurfaceTest`,
  `HubListPolicyTest`, `ProjectMarkWiringTest`, `TouchTargetTest`,
  `TokenAdoptionTest`, `TypeAdoptionTest`, `IconRoleTest`, …) and
  **202 / 0** across the 39 source-scan classes (including
  `ComposableAnnotationTest`).
- Two findings from that run were fixed before commit: one hub subtitle
  expectation still said "1 min ago"; one `"unknown error"` literal remained
  in the ViewModel.
- `FileManagerScreen.kt` and `FileManagerViewModel.kt` were reviewed by hand
  against the Compose 1.7 / Material 3 APIs used (`selectable`,
  `selectableGroup`, `FocusRequester`, `KeyboardActions`, `supportingText`,
  `ButtonDefaults.textButtonColors`); their compilation is CI evidence only.
- **Full Android CI GREEN, first round:** code `a96fb25`,
  [run 36311728346](https://github.com/pabi277/CodeC/actions/runs/36311728346)
  — host unit/screenshot tests, debug and release APKs, APK-set checks all
  passed. Debug APK 25,965,508 B; release APK 6,777,708 B
  (details in [PART_66_1 § CI](PART_66_1_PROJECTS_HUB.md#ci)).
- Device behaviour is **not** verified in this session (the owner declined a
  handset round for this delivery track; none is invented). What a device
  would still have to confirm is listed in the part document's *Not verified*.

## Gate

No PR, no merge, no `main` push, no Phase 67.1 without the owner's
instruction. The next discussion in the owner's order is
[67.1 — files, drawer and project search](../ui-polish-chats/PHASE_67_1_FILES_SEARCH.md).
