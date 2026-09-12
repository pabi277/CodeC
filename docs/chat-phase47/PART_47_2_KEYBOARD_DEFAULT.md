# CodeC Phase 47.2 — The system keyboard is the default; CodeC Keys is an opt-in

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S ·
> **Owner row (verbatim):** *"System keyboard make default user can change to app
> keyboard if they want"*

## What changes, and what does not

| | Today | After 47.2 |
|---|---|---|
| `codec_keys_enabled` default | `true` (`SettingsManager.kt:126`: `it[CODEC_KEYS_ENABLED] ?: true`) | **`false`** |
| System IME in the editor | switched **off** while CodeC Keys is up (`EditorScreen.kt:401`) | on by default; off only when the user turned CodeC Keys on |
| CodeC Keys itself | full feature (Phases 26/27/28/30) | **unchanged, byte for byte** |
| Extra-keys strip | shown when CodeC Keys is off (`EditorScreen.kt:1572`, `:1683`) | the default code-keys surface, as it was before Phase 28 |
| Users who already chose | — | **keep their choice** (a stored value always wins) |

Nothing in `ui/keyboard/` changes: `KeyboardDefaults`, `KeyboardRouter`,
`CodecKeyboard`, the layout JSON override and the space-bar caret trackpad all
stay exactly as they are. This is a default-value + copy change with a
compatibility rule.

## Why the owner is right, and what the earlier decision was

The current default is an explicit earlier owner decision — the code says so at
`SettingsScreen.kt:236-238`:

```kotlin
// and leaving the editor restores it: exit condition 5 — "OFF →
// system IME returns exactly as before". DEFAULT ON per owner
// round 2 — turn this off any time to go back to the strip + IME.
```

and `docs/PHONE_UX_ANALYSIS.md` §5 lists CodeC Keys under *"what I would not
change … default ON was right"*. **47.2 reverses that one clause, on the
owner's instruction, and the reversal is recorded here so the next chat does not
"restore" it.** The reasoning that made default-ON attractive was power-user
density (digits/symbols one flick away); the reasoning for default-OFF is the
test phase: a first-time user meets a keyboard that is not their keyboard, has
no muscle memory for it, and has no idea it is optional. Default-OFF + one
coach mark + one Settings row gives the same feature with none of the surprise.
`PHONE_UX_ANALYSIS.md`'s list is a recommendation, not a law; `rule.md` §6's
invariants do not mention the keyboard default.

## Design

### 1. The default

```kotlin
val codecKeysEnabledFlow: Flow<Boolean> =
    context.dataStore.data.map { it[CODEC_KEYS_ENABLED] ?: false }   // 47.2
```

**Compatibility rule (the important part):** `false` is only the value for a
key that was **never written**. Anyone who toggled the switch has a stored value
and keeps it. No migration, no version bump, no "we changed your setting" —
because nothing is changed for anyone who ever expressed a preference.

**The trap to avoid:** do **not** "fix" this by writing `false` on first launch.
A write would overwrite an upgrader's stored `true` on any device where the
DataStore round-trip loses the write ordering, and it would make the default
impossible to distinguish from a choice later. Absent-means-default is the whole
mechanism.

### 2. The copy (Settings)

The `SettingsItem` subtitle (`SettingsScreen.kt:240-246`) currently opens with
*"A data-driven code-QWERTY the app draws itself…"* — accurate, and too long to
answer the only question a new user has (*"why is my keyboard different?"*).
47.2's version leads with the default:

> **Dedicated in-app code keyboard**
> *Off by default — CodeC uses your phone's keyboard. Turn it on for a
> code-QWERTY the app draws itself: flick up for digits and symbols, hold for
> popups, ⌫ hold-repeats, flick-up on ⌫ deletes a word. It exists only inside
> the editor and is not a system IME.*

Same switch, same section header, same sub-controls (keep-open, haptics, height,
live preview) — so **`SettingsAuditTest`'s counts do not change** and the audit
doc needs no new row. The stale comment at `:236-238` is rewritten in the same
commit (a comment that says "DEFAULT ON per owner round 2" next to a `?: false`
is how the next agent re-breaks it).

### 3. The two places that teach it

- **Guide slide 1 or 2** (Phase 45.1): one sentence — *"CodeC uses your phone's
  keyboard. Settings → CodeC Keys turns on a code keyboard with digits one flick
  away."* (Final slide assignment is 45.1's to make; the sentence is this one.)
- **Editor coach mark** (Phase 45.2): none by default. A mark pointing at a
  keyboard the user already knows is noise. Instead: the **first time the user
  opens Settings → CodeC Keys**, nothing special happens — the row's own subtitle
  is the teaching. (Recorded as a deliberate "no".)

### 4. What must keep working unchanged

- Leaving the editor restores the system IME for other apps
  (`EditorScreen.kt:402-404`, `onDispose { setSoftKeyboardEnabled(true) }`).
- An interactive run waiting for stdin hands the IME back (the 23.2 law,
  `KeysStayPolicy.isVisible(waitingForInput = …)` at `:360-365`).
- The 5-tab bar's hide/show logic (`NavBarPolicy.hideNavBar`,
  `MainActivity.kt:735-747`) — it takes `keysVisible` (CodeC Keys) and
  `imeVisible` (system IME) as separate inputs, so both defaults keep working;
  with CodeC Keys off the bar hides on the IME, exactly as pre-Phase 32.

## Exit condition

```text
1. Fresh install → editor: the SYSTEM keyboard appears; the extra-keys strip is
   above it; ☰ / RUN ▶ / tabs all reachable.
2. Settings → CodeC Keys → ON: the code keyboard appears immediately, with
   ghost-accept caps, popups, haptics and the space-bar caret drag all working.
3. OFF again: the system keyboard returns, no restart needed.
4. An install that had CodeC Keys ON before the update still has it ON after.
5. An interactive run (e.g. `scanf`) gets the system keyboard for stdin input
   with CodeC Keys ON (the 23.2 law).
6. Another app's keyboard is unaffected after leaving the editor.
7. The Settings copy says "Off by default" and no code comment claims DEFAULT ON.
PASS = all seven; 4 is the compatibility promise.
```

## Tests (plan)

- `KeyboardDefaultTest` (host):
  - the store's default for an **absent** key is `false`;
  - a stored `true` yields `true` (the upgrader promise);
  - a stored `false` yields `false`;
  - source-scan: `SettingsManager.kt` contains `CODEC_KEYS_ENABLED] ?: false`
    (so a careless revert to `?: true` fails the build), and no code writes the
    key at startup (the "never write the default" rule).
- `ImeLeverTest` (source scan): `setSoftKeyboardEnabled(` appears only in
  `EditorScreen.kt` at the two known call sites (`:401` and the dispose at
  `:403`) — the single-lever invariant, so a future feature cannot switch the
  IME from somewhere new.
- `KeysStayPolicyTest` extended: with `codecKeysOn = false`, the strip's
  visibility still follows `waitingForInput` and `explicitlyCollapsed`
  (regression guard for the default flip).
- `SettingsAuditTest`: unchanged counts — asserted by simply running it.

## Sources (record)

- CodeC 2026-09-12: `SettingsManager.kt:59,126`, `SettingsScreen.kt:236-270`,
  `EditorScreen.kt:360-365,401-404,1572,1683`, `ui/keyboard/*`,
  `ui/editor/KeysStayPolicy.kt`, `ui/editor/NavBarPolicy.kt`,
  `MainActivity.kt:735-747`.
- `docs/EDITOR_MOBILE_RESEARCH.md` §9 (the CodeC Keys design history and the
  L1/L2 decision) — read so this part changes the default and nothing else.
- `docs/PHONE_UX_ANALYSIS.md` §5 (the earlier "default ON was right"
  recommendation, explicitly reversed here on the owner's instruction).

## Deferred / rejected with reasons

- **Per-language or per-project keyboard preference** — a Settings novel for a
  preference nobody asked to vary.
- **Asking on first run ("which keyboard do you want?")** — a first-run choice
  between two things the user cannot yet evaluate; a default plus a visible
  switch is the honest version.
- **Removing CodeC Keys** — it is real value for regular users and it is already
  built and tested; the owner asked for a default change, not a deletion.
- **Making the code keyboard a real system IME (`InputMethodService`)** — a
  large, permission-sensitive surface (it would type into *other* apps);
  `EDITOR_MOBILE_RESEARCH.md` §9 rejected it then and nothing changed.
