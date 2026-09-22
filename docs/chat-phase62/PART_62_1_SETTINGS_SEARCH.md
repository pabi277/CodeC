# PART 62.1 — Settings, findable

**Roadmap line:** *“62 — Settings: find and group (spec §4) — Add: a search field at the top that
filters rows by their own labels (pure matcher…), and collapsible sections over the twelve headers
that already exist.”*
**Owner's answer:** start here (2026-09-22), and build the not-in-shots items as specified.

## 1. What the screen was

`SettingsScreen.kt` was 1,636 lines: one `Column(verticalScroll)` holding **12 sections** and
**66 control rows**, each row one call to one of five helpers — `SettingsSwitch`,
`SettingsSlider`, `SettingsDropdown`, `SettingsItem`, `SettingsAction` — each already taking its
label as `title`. There was no search anywhere in the file.

That shape is what made the job small: **every row's label and every row's section are already in
the source**, in the same order the user sees them, and they are already pinned by
`docs/chat-phase38/SETTINGS_AUDIT.md` — the 66-row *one row, one effect* table that
`SettingsAuditTest` reads (12 sections, 66 rows, table ↔ screen).

## 2. The design, and the three laws

**The catalog is generated from the screen, never hand-written.**
`ui/settings/SettingsSearch.kt` holds `SettingsCatalog.entries`: the 66 rows in screen order, each
with the section it sits under and the label the screen itself renders — produced by reading each
`Settings*` call's own `title = …` argument (literal or `stringResource`), not by copying the audit
doc's shorthand. That is why the catalog reads like the screen (*“Report the last crash (log
attached, nothing sent by itself)”*), and why a row cannot be searchable under a name the user
never sees.

**Law 1 — a query narrows; it never guesses.** `SettingsSearch.normalize` lower-cases and splits
on anything that is not a letter or a digit, so punctuation, quote marks and glyphs are simply not
part of the comparison; every token the user typed must appear in *some* haystack for that row.
The haystacks are the label, the row's own keywords (empty today, the extension point), and the
**section the row lives in** — which is what makes `appearance` show Appearance's four rows and
`compiler` show all five of Compiler's, while `cache font` still finds nothing.

**Law 2 — while the box has something in it, the query decides.** A section shows iff the query
names it or one of its rows; a row shows iff the query matches it. That includes the three
sections the catalog has no rows for — *GitHub Account*, *Package Repository & Trust*, *Terminal
Extra-Keys & Shortcuts* — whose content is a bespoke card the catalog cannot index: `github`
brings the GitHub section back, and clearing the box brings all three. This is also what makes the
empty state honest: **“nothing matched” means nothing at all** (no row *and* no section title),
which is why a query for `repository` shows the Package Repository card and not *“No settings
match”*.

**Law 3 — folding is remembered, never applied over a search.** With an empty box the fold rule
runs; the moment the user types, every matching row renders. A folded section must not swallow the
result the search just found, and the fold comes back when the box is cleared.

`rowVisible(query, title, folded)` is the single question the screen asks per row;
`sectionVisible(query, section)` the one it asks per header.

## 3. What shipped

| File | What |
|---|---|
| `ui/settings/SettingsSearch.kt` **(new, 292 lines)** | `SettingsEntry`, `SettingsCatalog` (66 rows, `allSectionTitles` — the screen's twelve, `sections` — the nine that hold rows, `entryFor`, `isControlSection`), `SettingsSearch` (`normalize`, `isActive`, `matchesText`, `matchesRow`, `rowVisible`, `matchCount`, `isEmptyResult`, `sectionVisible`, `sectionMatchCount`), `SettingsDisclosure` (`parse`/`serialize`/`toggle`/`expanded`), and the plain-data `SettingsViewState` |
| `ui/screens/SettingsScreen.kt` | `SettingsSearchField` (an `OutlinedTextField` with a leading lens and a ✕ that appears only when there is something to clear — the app's own search idiom), `SettingsNoMatch` (the hub's centred lens-and-line empty state), the screen's query + fold state, and the one `CompositionLocalProvider` that hands both to the rows |
| `res/values/strings.xml` | `settings_search_hint`, `settings_search_clear`, `settings_no_match` (with the query read back), `settings_section_expand`, `settings_section_collapse` |
| `SettingsSearchPolicyTest` **(new, 19 cases)** | the laws above, plus the catalog's honesty against the screen and the audit doc |
| `SettingsSearchWiringTest` **(new, 8 pins)** | the box, the state, the guard, the wrappers, the empty state, and (after CI round 1) the two structural pins: every section slice is a balanced block, and no declaration is cut from its own readers |

**Why the search does not re-implement the screen.** The rows stay exactly where they were: no
section was re-ordered, no row was moved into a results list, and the audit's 12-section/66-row
pin passes untouched. Filtering is a visibility decision each row makes for itself.

## 4. The catalog's honesty (how it stays true)

Four pins hold the catalog to the screen, both directions:

1. **Size:** the screen's `Settings*` call sites (the audit test's own counting rule) must equal
   `SettingsCatalog.entries.size` — 66 today.
2. **Provenance and order:** every catalog label must be rendered by the screen *somewhere*, and
   the screen's own order of those labels must be strictly increasing — the pin is anchored on
   each row's `title = …` argument, because a label may also be a section's **title** (there is a
   “CodeC Keys” *row* inside the CodeC Keys section) and a header is not a row.
3. **Sections:** the sections that hold rows, in the screen's header order, must equal the
   catalog's `sections`; and the screen's twelve headers must equal `allSectionTitles`.
4. **The audit table:** the doc's 66 numbered rows, grouped per section, must equal the catalog's
   rows per section — the same rows, counted the same way, so *one row, one effect* and *one row,
   one search result* cannot drift apart.

One derivation rule needed fixing while pinning, and it is recorded because it is the interesting
kind of bug: the screen writes row 11's label with **escaped quote marks**
(`"\"⌄ more\" opens the full completion panel"`), so a pin that searches for the raw label finds
nothing. The test escapes the label's own quote marks before searching, which is the rule the
generator used too.

## 5. What was not done

* **No state in DataStore.** The query and the fold set are `rememberSaveable` view state: a search
  box that remembers last week's query is not a feature. Nothing was added to any store, so
  `SettingsKeysHaveReadersTest` (the key → flow → reader walk) is untouched.
* **No second search implementation.** The hub's own `searchQuery`/`ProjectSearch` were not reused:
  they match file and project paths, this matches a curated catalog of view labels.
* **No row moved.** The 66 calls kept their order, their arguments and their effects.

## 6. The wiring

```
SettingsScreen
├── TopAppBar
├── SettingsSearchField(query, onQueryChange)          ← the box
├── CompositionLocalProvider(LocalSettingsView provides settingsView)
│   └── Column(verticalScroll)
│       ├── SettingsSection("Editor Settings") { … }    ← 12 sections, section-level hide
│       │   ├── SettingsSectionHeader("Editor Settings")  ← fold + count + chevron
│       │   └── SettingsSlider/Dropdown/Switch(… title = …)  ← row-level hide
│       └── if (SettingsSearch.isEmptyResult(settingsQuery)) SettingsNoMatch(settingsQuery)
```

The section wrapper, its boundary rules and the hoisted reads are PART_62_2 §1-§5.

`LocalSettingsView` is the **first `CompositionLocal` in this codebase**, and it is deliberately
the only one: one screen, no other reader, and the alternative was a view-state parameter on
twelve headers and sixty-six rows. Its default (“no query, nothing folded, folding does nothing”)
is only ever seen by a preview, and the wiring test pins that the screen provides it exactly once.

## 7. Tests

**27 passed / 0 failed** locally (`/tmp/p62`): 19 policy cases + 8 wiring pins. The broad
pure-source regression set (the pins that read `SettingsScreen.kt`, `strings.xml` and every pure
policy this checkout can compile on a host JVM) ran **883 passed / 0 failed** after the phase's one
census re-cut (PART_62_2 §6). CI round 1 was **red for a real reason** — the section wrappers cut
two declarations from their readers; the fix, the third boundary bug CI could not see, and the two
pins that now catch all three are PART_62_2 §5.

Compile-time note for the next agent: the host harness cannot compile Compose, so the screen's own
code is proven by CI's `assembleDebug`/unit-test step, not by the sandbox. Everything pure
(`SettingsSearch.kt`) is compiled and exercised locally.
