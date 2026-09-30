# PART 62.2 — Folded sections

**Roadmap line:** *“62 … collapsible sections over the twelve headers that already exist.”*
**Owner's answer:** the not-in-shots items are approved to be built as specified (2026-09-22).

## 1. What a fold has to hide

The twelve sections are not twelve clean blocks of `Settings*` rows. Several own **bespoke
content** the catalog cannot name: the two theme previews (`ThemePreview`,
`TerminalThemePreview`), the terminal extra-keys editor, the GitHub account card, the About
blocks, the developer rows. A fold that only hid rows would leave those floating with no header
above them — a filtered or folded screen that shows a preview card from a section whose title is
not on screen.

So the unit of hiding is the **section**, and the wrapper is one composable:

```kotlin
@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    if (!SettingsSearch.sectionVisible(LocalSettingsView.current.query, title)) return
    content()
}
```

Twelve call sites, each wrapping its own header, its own rows and its own bespoke block — and the
leading `Divider` too, so a hidden section does not leave its separator behind. The wrapper emits
no layout node, and its `content` lambda has no receiver, so every child keeps the enclosing
`ColumnScope` it had before: **no row was re-indented, reordered or rewritten**.

The wiring pin proves the wrapping structurally rather than by eye: the file is sliced at each
`SettingsSection("…") {` and **each slice must hold exactly its own header** — a section that
escaped its wrapper, or a header left outside, fails the build.

## 2. The header

`SettingsSectionHeader(title)` keeps its name and its twelve call sites and becomes the fold row:

* **Tappable only where a fold means something.** `foldable = SettingsCatalog.isControlSection(title)`
  — the nine sections with rows. The three whose content the catalog cannot index (*GitHub
  Account*, *Package Repository & Trust*, *Terminal Extra-Keys & Shortcuts*) get **no tap and no
  chevron**: folding them would hide nothing, and a control that does nothing is the one thing
  this app does not draw.
* **The chevron is the app's own idiom**, taken from the project drawer
  (`EditorProjectDrawer.kt:538`): `Icons.Default.ExpandMore` when open,
  `Icons.Default.KeyboardArrowRight` when folded. It carries a real `contentDescription`
  (`settings_section_expand` / `settings_section_collapse`), so a screen reader says what the tap
  does.
* **The count is what a folded header must not keep to itself.** A folded section shows how many
  rows it is holding; while the user is filtering it shows how many rows answered — the same
  number `SettingsSearch.sectionMatchCount` counted, never a second tally.

## 3. The state

```kotlin
var foldedSectionsCsv by rememberSaveable { mutableStateOf("") }
val foldedSections = remember(foldedSectionsCsv) { SettingsDisclosure.parse(foldedSectionsCsv) }
```

A unit-separated string of section names in `rememberSaveable`, i.e. it survives a rotation and is
forgotten when the screen is left. **Which sections a user has folded away is not a setting**, so
nothing was written to DataStore and no key was added — the same choice Phase 45.2 made for the
coach marks a user has seen.

## 4. The rule that matters most

**Folding is remembered, never applied over a search.** `SettingsSearch.rowVisible` returns true for
every row a query matches, folded section or not:

```kotlin
fun rowVisible(query: String, title: String, collapsed: Set<String>): Boolean {
    if (!matchesRow(query, title)) return false
    if (isActive(query)) return true                                  // a search result is never folded away
    val section = SettingsCatalog.entryFor(title)?.section ?: return true
    return SettingsDisclosure.expanded(section, collapsed)
}
```

The alternative — a folded section swallowing the row the search just found — is the failure mode
that makes a search look broken, and it is pinned from both ends (the policy case *“a folded
section never swallows the row the search just found”*, and the wiring pin on the guard every row
helper calls).

A row the catalog has **not** met fails open: it renders (rather than disappearing silently), and
it is still nobody's search result. The audit's own 66-row pin is what keeps that branch
unreachable in practice.

## 5. What CI caught first, and the two pins that now catch it

**CI round 1 was RED, and the product was wrong — not a pin.** The compile step failed inside the
phase's own file with eight errors, all of one kind:

```
SettingsScreen.kt:498:29 Unresolved reference 'editingMacros'
SettingsScreen.kt:487:35 @Composable invocations can only happen from the context of a @Composable function
```

The Terminal Extra-Keys section's two `remember`s are declared *just above its header*, so the
wrapper boundary — computed as “the divider before the header, else the header” — had put them
inside the **Terminal** section and their readers inside the extra-keys section.

Fixing that surfaced two more instances of the same class, and one the compiler cannot see:

| # | What | Why it happened | Fix |
|---|---|---|---|
| 1 | `editingMacros`, `macrosSaved` cut from their readers | the declarations precede the header | the boundary now swallows the contiguous declarations that belong to the section |
| 2 | `devModeUnlocked` and `showFilePaths` declared in **About**, read in **Developer Options** | this screen declares state where it is first used, and two reads are used by two sections | both are **hoisted** beside the other `collectAsState` reads (they were already evaluated on every recomposition — nothing else changes) |
| 3 | the `if (BuildConfig.DEBUG && devModeUnlocked) {` guard was left **outside** the Developer wrapper, and the Feedback wrapper's closing brace landed *inside* that `if` | the boundary rule stopped at the divider instead of the `if` line | the Developer section's wrapper now opens **before the `if`** and closes after its `}` — so the DEBUG guard is inside the fold, where it was |

**#3 is the one CI could not have caught:** the braces still balanced, the file still compiled, and
the DEBUG guard silently guarded an empty block — Developer Options would have shipped into release
builds. It was found by re-reading the inserted region, and it is the reason the phase now ships two
structural pins that would have failed:

* *every section slice is a balanced block* — each wrapper's brace pair is matched and its inner
  brace count must be zero, so a brace on the wrong line cannot pass;
* *no local declaration is split from its uses by a section wrapper* — the screen's own body is
  brace-matched, lambda parameters and doubly-declared names are skipped, and every remaining
  local's readers must lie inside the block the declaration lives in (i.e. exactly what the Kotlin
  compiler enforces).

Both pins were validated against the **pre-62 screen** as well: they report zero problems there,
so their silence on the new file means something.

**What was re-derived, not guessed.** The full list of cross-section declarations was computed by
scanning every local declaration in the screen's body against its readers: four names looked like
crossers, and only two really were (`theme` and `intent` were named-argument and shadowing noise —
`theme` was matched inside `com.codeci.ide.ui.theme` imports, `intent` by a *second* `intent`
declared in the developer flow). The scope checker that says so is a small brace-matching
simulation, and it reports **0 violations** on both the original file and the rebuilt one.

## 6. The one census this phase re-cut

`TouchTargetTest` pins an **exact count of `IconButton`s in the five core files**, with a comment
demanding that a phase which changes the number says why: *“A phase that changes this number again
must say why here, not just edit the digit.”* The Settings search field's ✕ clear button is an
`IconButton` — the **first in `SettingsScreen.kt`** — so the census went **17 → 18** (4 editor +
5 hub + 2 packages + 6 terminal + **1 settings**), and the pin's comment now carries the reason
instead of a bare digit. The 48 dp rule itself was never relaxed: the new button is a normal
`IconButton`, which reserves the floor by itself.

Everything else in the broad set stayed green untouched: `SettingsAuditTest` (12 sections, 66
rows), `TokenAdoptionTest` (the new chrome uses `CodecTokens.space`/`radius`, no raw dp),
`IconRoleTest` (no sized icon, every action icon names itself), `TypeAdoptionTest`,
`HapticWiringTest`, `KeyboardDefaultTest`, `PreviewChromeWiringTest`.

## 7. Tests

| Case | What it pins |
|---|---|
| *folding a section keeps its state across a rotation without inventing a setting* | toggle → serialize → parse round trip, neighbours untouched |
| *only sections that hold rows can fold* | the three form-only sections answer “expanded” for any fold set |
| *a folded section never swallows the row the search just found* | the override rule, both directions |
| *a row the catalog has not met is never hidden by a fold* | fail-open, and still not a search result |
| *a header folds its section, counts it, and only when there is something to fold* | the wiring: `isControlSection`, the conditional `clickable`, both chevrons, the count, the two strings |
| *every header sits inside its own section wrapper* | twelve wrappers, in order, one header per slice |

**27 passed / 0 failed** with PART_62_1's cases (8 pins here); the broad set **883 passed / 0 failed**.
