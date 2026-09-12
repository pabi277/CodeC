# CodeC Phase 47 — Editor chrome that behaves

> **Status:** 📋 PLANNED (researched + specced, no code) · **Cost:**
> `[client-only]` · **Effort:** M · **Owner rows (verbatim):**
> *"i. the file ber can't close without opening any file"* · *"ii. I can switch
> project but can't directly open folder"* · *"iii. System keyboard make default
> user can change to app keyboard if they want"*
>
> **Owner clarification (2026-09-12):** 4.ii = **in-drawer project picker** —
> the project list opens right inside the drawer, no navigation.

```text
  47.1  The drawer you can always close, with the project list inside it
  47.2  The system keyboard is the default; CodeC Keys is an opt-in
```

| Part | Title | Effort | Status |
|---|---|---|---|
| [47.1](PART_47_1_DRAWER.md) | Closable drawer + in-drawer project picker | M | 📋 PLANNED |
| [47.2](PART_47_2_KEYBOARD_DEFAULT.md) | System keyboard by default | S | 📋 PLANNED |

---

## What exists today (evidence, read 2026-09-12 against `main` @ `f3a6e32`)

### 4.i — "the file bar can't close without opening any file"

`EditorScreen.kt:900-1010` wraps the editor in a `ModalNavigationDrawer` whose
content is `EditorProjectDrawer`. Every exit path from that drawer requires an
action on a *file*:

| Exit path | Verdict |
|---|---|
| Tap a file row → `onOpenEntry` closes it (`:958-966`) | ✅ works — **this is the only obvious one** |
| Tap the header's project name → `onSwitchProject` closes it (`:917-922`) | ⚠️ closes, but it *also* opens the project picker dialog — not "close" |
| Tap the scrim | ⚠️ Material3 default; on a phone the sheet is up to 360 dp wide, so the scrim strip is thin and easy to miss |
| Tap ☰ again | ❌ impossible — the ☰ is behind the scrim while the drawer is open |
| Edge-swipe to close | ❌ **disabled on purpose** whenever a file is open: `gesturesEnabled = activeTabPath == null && currentFileName.isEmpty()` (`:905-907`, a Phase 25.2 fix for the gutter/scroll conflict) |
| Back | ⚠️ owner reports the app exits (see 49 — reproduced and fixed there) |

So the owner's sentence is literally true: **with a file open, there is no
affordance whose only job is "close this".**

### 4.ii — "I can switch project but can't directly open folder"

The dialog the drawer header opens is `showContextPicker`
(`EditorScreen.kt:1916-1973`). Its **title is `"Open folder"`** (line 1922) and
its body says *"The editor works inside one folder at a time."* — but the list
it shows is **"Single files" + the CodeC projects** (`ProjectManager.listProjects()`).
There is no folder picker in it, and (after Phase 46.1) none anywhere in the app.

That is the confusion in one line: **a dialog titled "Open folder" that cannot
open a folder.** The owner's 4.ii is not a feature request for SAF — it is a
request for the switcher to be what it says it is, and to be reachable without a
modal. Owner's chosen shape: **the list lives in the drawer**.

### 4.iii — the app keyboard is the default

`SettingsManager.kt:126`: `codecKeysEnabledFlow = … { it[CODEC_KEYS_ENABLED] ?: true }`
— **CodeC Keys is ON by default**, and `EditorScreen.kt:401`
(`soraEditor.setSoftKeyboardEnabled(!codecKeysUp)`) switches the **system IME
off** while it is up. The Settings row exists
(`SettingsScreen.kt:239-270`, section "CodeC Keys", switch
"Dedicated in-app code keyboard"), so this is a **default-value change plus
honest copy**, not a new feature.

## The two rules this phase must not break

1. **CodeC Keys must stay fully working as an opt-in.** Phases 26/27/28/30 built
   ghost accept, chip strip, popups, haptics and the space-bar trackpad on it;
   `docs/PHONE_UX_ANALYSIS.md` §5 lists it under *"what I would not change"*.
   47.2 changes **who gets it by default**, never what it does.
2. **A stored preference always wins over a new default.** Users who already
   chose CodeC Keys keep it — the DataStore key is only absent for people who
   never touched the switch, and for them the system keyboard is the new
   answer.

## Design in one picture

```text
 47.1  ☰ opens the drawer
       ┌─────────────────────────────────────────────┐
       │  <project name>            [⌥ branch]  [✕]  │  ← NEW: explicit close
       │  ─────────────────────────────────────────  │
       │  ▸ PROJECTS            (tap to expand)      │  ← NEW: in-drawer list
       │      ● myapp      ○ Single files            │     (Single files + every project)
       │  ─────────────────────────────────────────  │
       │  New File · New Folder · Refresh · Collapse  │
       │  … the tree …                                │
       │  Source Control · Switch Branch · Guide      │  (Guide = Phase 45.1)
       └─────────────────────────────────────────────┘
       Back / ✕ / scrim all close it; back is 49's BackRouter.
       The "Open folder"-titled dialog is RETIRED (its list moves into the drawer).

 47.2  codec_keys_enabled default: true → false
       Settings copy: "Off by default. Turn on for the code keyboard
       (flick for digits, one tap per symbol)."
       First-run guide slide + editor coach mark mention it.
```

## Risks to watch

- **Losing the project switcher's discoverability.** The header tap currently
  opens the picker; if the list moves into the drawer, the header tap should
  expand that list (not open a dialog). One behaviour, two entry points.
- **Switching projects mid-edit.** `switchContext` (`EditorViewModel`) already
  handles the tab/context change; the drawer path must call the **same**
  function so the two entry points cannot drift.
- **A user who liked CodeC Keys and updates.** Mitigated by the
  "stored value wins" rule; the device round must check an *upgraded* install,
  not only a fresh one.
- **The keys row vs the system IME.** With CodeC Keys off, the extra-keys strip
  (`keysVisible && imeVisible`, `EditorScreen.kt:1683`) becomes the only code
  keys — which is the pre-Phase-28 behaviour and already works; the strip's
  visibility policy (`KeysStayPolicy`) is unchanged.

## Exit condition

```text
47.1
1. ☰ opens the drawer; ✕ closes it and does nothing else.
2. Scrim tap and Back both close it (Back is verified again in 49's matrix).
3. The drawer's PROJECTS row expands to "Single files" + every project; tapping
   one switches the editor's context, closes the drawer, and shows that
   project's tree — with no dialog anywhere.
4. No dialog in the app is titled "Open folder" (grep + device check).
5. Switching to "Single files" works exactly as the old dialog did.
47.2
6. Fresh install: the SYSTEM keyboard appears in the editor; the extra-keys
   strip is above it.
7. Settings → CodeC Keys → ON: the code keyboard appears and works exactly as
   before (ghost accept, popups, haptics, space-bar caret drag).
8. An install that already had CodeC Keys ON keeps it ON after the update.
9. Leaving the editor always restores the system keyboard for other apps
   (the existing `onDispose { setSoftKeyboardEnabled(true) }`, `:402-404`).
PASS = all nine.
```

## Tests (plan)

- `DrawerPolicyTest` (host): the close-affordance matrix (✕ / scrim / back /
  file tap / project tap → closed), and "a project switch always closes the
  drawer".
- `DrawerProjectListTest` (host): the list content and order ("Single files"
  first, then projects alphabetically), the current-context marker, and the
  empty-projects copy.
- `KeyboardDefaultTest` (host, source-scan + policy): the default for
  `codec_keys_enabled` is `false`; a stored `true` still yields CodeC Keys;
  `setSoftKeyboardEnabled(!codecKeysUp)` remains the only IME lever; the
  dispose path restores it.
- `SettingsAuditTest` unchanged (no new controls — 47.2 only changes a default
  and copy; if the copy edit adds a control, the audit doc updates in the same
  commit).

## Sources

- CodeC 2026-09-12: `EditorScreen.kt:342,360-365,401-404,900-1010,905-907,
  917-922,958-966,1572,1683,1916-1973`, `EditorProjectDrawer.kt:96-215`,
  `SettingsManager.kt:126`, `SettingsScreen.kt:239-270`,
  `ui/editor/KeysStayPolicy.kt`, `ui/editor/NavBarPolicy.kt`.
- `docs/PHASE44_50_UX_RESEARCH.md` §6 (back-handling context for 47.1's close
  paths — the *policy* lands in Phase 49; 47.1 ships the visible affordance).
- `docs/EDITOR_MOBILE_RESEARCH.md` §9 (CodeC Keys design history) and
  `docs/PHONE_UX_ANALYSIS.md` §5 (what must not regress).

## Deferred, recorded on purpose

- **A resizable/persistent (non-modal) drawer** — on a phone there is no room;
  that is a tablet/foldable feature and belongs with a layout phase.
- **Remembering the drawer's expanded state per project** — nice, unasked for.
- **Per-language default keyboard** — a per-language preference is a Settings
  novel; one global default is the honest scope.
- **Moving the project switcher out of the drawer entirely (a top-bar chip)** —
  the owner chose the drawer; a chip would cost top-bar width the tabs need.
