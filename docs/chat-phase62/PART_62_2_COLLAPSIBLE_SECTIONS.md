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

## 5. The one census this phase re-cut

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

## 6. Tests

| Case | What it pins |
|---|---|
| *folding a section keeps its state across a rotation without inventing a setting* | toggle → serialize → parse round trip, neighbours untouched |
| *only sections that hold rows can fold* | the three form-only sections answer “expanded” for any fold set |
| *a folded section never swallows the row the search just found* | the override rule, both directions |
| *a row the catalog has not met is never hidden by a fold* | fail-open, and still not a search result |
| *a header folds its section, counts it, and only when there is something to fold* | the wiring: `isControlSection`, the conditional `clickable`, both chevrons, the count, the two strings |
| *every header sits inside its own section wrapper* | twelve wrappers, in order, one header per slice |

**25 passed / 0 failed** with PART_62_1's cases; the broad set **881 passed / 0 failed**.
