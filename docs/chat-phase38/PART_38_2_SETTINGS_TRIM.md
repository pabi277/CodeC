# CodeC Phase 38.2 — Settings trim: the Termux bridge goes, the fallback stays

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** S ·
> **Owner row (verbatim):** *"From the settings remove unessesary Termux bridge"*

## Symptom

Settings has a **"Termux Engine"** section (`SettingsScreen.kt:320-366`): a
status line, an **OPEN TERMUX** button, a **CHECK** button that shells into
Termux to probe it, and a four-step instruction paragraph telling the user to
install Termux from F-Droid, edit `~/.termux/termux.properties`, grant *Run
commands in Termux* in system settings, and `pkg update && pkg install clang`.
For anyone who has never heard of Termux — i.e. every tester the owner is about
to hand this app to — that section is not a setting, it is a warning label.

And it isn't a *setting* at all: `CompilerService.BACKEND_AUTO` is the only
value the app has passed since Phase 21 removed the backend picker (the code
says so in its own comment), so the user cannot choose anything here. The
useful information — "on some devices the download compiler can't be exec'd,
so CodeC borrows Termux's clang" — belongs in the *error path* that needs it.

## Design

**1. Delete the section, keep the mechanism.**

- Remove the "Termux Engine" `SettingsSectionHeader` + its card, the
  `TermuxUiState` data class, `loadTermuxState`, `buildTermuxStatusText`,
  `formatProbe` (`:1195-1230`), the `TermuxCompiler` import at `:80`, and the
  now-orphaned strings.
- Keep `ui/services/TermuxCompiler.kt` **and** every `CompilerService` call
  site: `isTermuxInstalled` guards, `buildCompileScript`, `runCommand`,
  `absoluteProgramPath`, `buildRunScript`. This is not a removal of a feature,
  it is the removal of a *panel* — the fallback that saves a user whose
  downloaded clang cannot be exec'd stays exactly as it is.
- Keep `com.termux.permission.RUN_COMMAND` and the `<queries>` package entry:
  a permission the *user* grants in system settings for a path that still
  exists must stay declared, or the fallback silently stops working for the
  devices that need it. The doc says this out loud because "remove the Termux
  bridge" invites the opposite reading.
- The two remaining Termux mentions in the compiler explanation copy
  (`:1187`, `:1190`) become one sentence, in About-style prose, not a card:
  *"If the built-in compiler cannot run on your device, CodeC can use a
  compatible terminal app's compiler automatically."*

**2. The guidance appears where it is needed.** When a C compile fails and the
diagnosis is *exec permission denied* (the `Permission denied` signature this
app has met before), the Output Panel error gets the two-line remedy — the
same four steps, but rendered when the user is holding the broken thing.
Implementation rule: the text lives in one place (`CompilerDiagnostics`-style
pure helper returning a `String?` for a given stderr), so Settings and the
error path cannot drift, and the *string is not duplicated*.

**3. Audit the rest of Settings — one row, one effect.** `SettingsScreen` has
13 sections: Editor Settings · CodeC Keys · **Compiler Settings** · **Built-in
Compiler** · **Termux Engine** · Terminal · Terminal Extra-Keys & Shortcuts ·
Package Repository & Trust · GitHub Account · Appearance · Storage · About ·
Developer Options. Three compiler sections for one automatic policy is where
the confusion came from, so this part also produces a `SettingsAudit` table in
`docs/chat-phase38/` — one row per control: *what it changes, who can observe
it, does anything read the stored value?* — and applies the verdict:

- **merge** the compiler-related rows that both describe the same automatic
  choice into a single *Compiler* section (Built-in Compiler's real setting +
  the status line), and
- **delete** anything whose stored value has no reader (a dead `Switch` whose
  key is never queried is worse than no switch, because the user believes it
  did something).
- **keep** Package Repository & Trust (it drives the Part D trust material) and
  GitHub Account (40.1's readiness reads it), each *stated* as kept with its
  reader named — so the next audit does not re-open them.

The audit is a table in a doc + a host test, not a sweep: the test enumerates
every key written by `SettingsManager` and asserts each one is read somewhere
in `app/src/main` (a source-scanning test in the same spirit as
`EditorKeySetTest`/`DemoProjectSeedTest`, which read real repo files from the JVM). A key with no reader **fails the
build**, which is how this stays fixed instead of rotting back.

## Exit condition

```text
(Device)
1. Settings has no Termux section; scrolling from "Built-in Compiler" to
   "Terminal" shows no orphaned gap or duplicated heading.
2. A C run still works on a device where the bundled TCC is the engine (normal
   case) and — if the owner can reproduce it — where clang must come from
   Termux: the fallback runs, and only the FAILURE path mentions Termux.
3. Forcing the failure (uninstall Termux / revoke the permission on a device
   that needed it) shows the remedy text with the four steps, in the Output
   Panel, not in Settings.
4. Every remaining Settings row demonstrably changes something observable
   (the owner checks the audit table row by row on the device — that is the
   point of the table).
5. App size / behaviour unchanged otherwise; CI green with no new lint finding
   (a deleted string that is still referenced is the classic break here).
PASS = 1, 4, 5 always; 2-3 as far as the owner's devices allow.
```

## Tests (plan)

- `SettingsKeysHaveReadersTest` (host, source-scanning): collect every
  `ui/settings/SettingsManager.kt` key from the source text, and assert each key
  literal appears in at least one non-test source **outside** the store itself.
  The audit's enforcement, and the mechanism that catches the *next* dead row.
  Scope note for whoever implements it: `GitCredentialsStore` (`GIT_TOKEN`,
  `GIT_USERNAME`, `GIT_AUTHOR_NAME`, `GIT_AUTHOR_EMAIL`) lives on the same
  shared `preferencesDataStore(name = "settings")` from
  `ui/theme/ThemeManager.kt:12`, so its four keys must be in the scanned set —
  they are read, but from a different class, and a test that only scans
  `SettingsManager` will report them as dead. Enumerate the *stores*, not one
  file.
- `SettingsAuditTest` (host): the doc's table row count equals the number of
  controls in `SettingsScreen` (a cheap guard so the table cannot silently go
  stale — it counts `SettingsSectionHeader(` occurrences and rows).
- Existing compiler tests keep passing: any test that asserted the
  *Settings* presence of Termux (if one exists) is updated in this commit,
  because the feature's absence from Settings is now the intended behaviour.
- No new pure logic except `CompilerRemediation.textFor(stderr)` → covered by
  `CompilerRemediationTest` (the `Permission denied` signature yields the
  Termux remedy; an unrelated `error: unknown type name` yields nothing).

## Sources (record)

- CodeC code, 2026-09-10: `SettingsScreen.kt:320-366` (the card), `:1187-1190`
  (the prose), `:1195-1230` (the state/probe helpers), the 13
  `SettingsSectionHeader` call sites, `CompilerService.kt:201/231/474-575`
  (Termux as a fallback, guarded by `isTermuxInstalled`), `BACKEND_AUTO`'s
  comment about Phase 21 removing the picker, `AndroidManifest.xml:28-34`
  (the permission + `<queries>` rationale text), `SettingsManager` (key set for
  the audit), `CompilerDiagnostics` (where the remedy line belongs).
- No third-party dependency is added or removed by this part — the whole point
  is subtraction. (`rule.md` §6 note: nothing here touches `com.termux`
  packages or repos; the bridge is an *intent to the user's own Termux app*,
  behaviour-only, which is what the manifest comment already records.)

## Deferred / rejected with reasons

- **Deleting `TermuxCompiler` and the fallback entirely** — that's the literal
  reading of "remove unessesary Termux bridge" and it is wrong: on devices
  where the downloaded compiler cannot be exec'd, it is the *only* working
  clang path, and users in that situation are exactly who a beta needs to
  keep. Re-open only if a device round shows the path is dead in practice.
- **Removing `com.termux.permission.RUN_COMMAND` from the manifest** — while
  the fallback exists, removing it turns a working path into a silent
  `SecurityException`.
- **A "Show advanced engines" toggle in Settings** — re-adds the card behind a
  disclosure; the audit rule is "one row, one effect", and this row had none.
- **Moving the four steps to `docs/` only** — a user who never opens the repo
  is the audience; the error path is where they are. The `docs/` copy is
  written too (it's the source of the text), so it exists in both places with
  one owner: the code-generated error text quotes the doc's wording, not the
  reverse.
