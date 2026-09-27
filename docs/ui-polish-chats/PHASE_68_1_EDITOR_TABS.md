# Phase 68.1 — Editor chrome, tabs and file actions

**Status: implemented and CI ✅ GREEN on `arena/01a0e444-codec`
([run 36343503123](https://github.com/pabi277/CodeC/actions/runs/36343503123),
final code `7ca3880`).** The tree of `arena/01a0e36f-codec` was mirrored
byte-for-byte and finished there so the owner does not need to return to the
earlier chat. One test repair was needed: the `TabRowRevealStrip(` exact-match
literal had been given a trailing `)` that could never coexist with the
`modifier = Modifier.weight(1f)` argument the sibling test requires — reverted
to the `)`-free form `main` uses. Tab menu shape is the split doors (close-only
bar menu + sort door in the editor), pinned by the tests. One part = one
future chat. No deadline, dependency, new control or replacement engine
promised. Owner verified compact Spck parity via new shots
`Screenshot_20260927_2049xx`. No PR opened; handset verification recommended.

## Copy into a new chat

> Review `docs/UI_POLISH_REVIEW_20260927.md` and this brief. Re-read the current
> implementation and real reference shots. Tell me what already works, what
> detail you recommend changing, and ask for my thoughts before implementing.
> Keep Phase 64’s no-guide/no-installation-UI-lock decision. Do not merge or open
> a PR without my instruction.

## First move: current evidence

Main entry: [`ui/screens/EditorScreen.kt:217`](../../app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt#L217), reviewed on 2026-09-27 and 2026-09-27 with new shots.
Recheck line numbers against the future checkout; these are this review’s anchors.

**Reference boundary:** 122157 and 124105 show the actual top/tab rows, plus new 204955/204945/204937 (README.md, index.json menu, JSON symbols) for compactness and the `<>` button. Do not use the earlier drawn mockups as higher authority.

## My recommendation

Keep the thin top bar, separate bounded tab row, and menu outside the fold. Focus on overflow, discoverability, active/dirty state and long file names.

## Bounded review checklist

No file; one/many tabs; dirty marker; sorting/close-unmodified; hide and reveal; find/replace; rename; format; diagnostics; save failure; line endings; single-file save-to-project.

## Your thoughts — ask before code

**Which actions do you want visible without opening the menu: Save, Undo/Redo, Format, or just the existing Search and Run?**

Suggested starting point, not your decision: Keep Search and Run on top; avoid adding a second permanent toolbar until you choose priority actions.

**Owner answers — 2026-09-27 (verbatim option labels + free text):**

From new Spck shots analysis:

1. Top bar breadcrumb? — **B** — Show breadcrumb `folder > file` when nested (like `data > index.json`)
2. Tab bar compactness? — **B** — Reduce to 40-42dp / 8dp padding — matches Spck, +8dp code height
3. Tab menu polish? — **B** — Add icons + groups + ✓ for active sort + Queue (Unsorted) (Spck parity)
4. Button beside Run `<>` for MD/JSON? — **C** — Reuse Run to open MD preview (like HTML), no extra button
5. Active/dirty look? — **C** — Italic active + subtle underline + fixed dot — closer to Spck
6. Status bar density? — **B** — Make more compact 2-3dp vertical, like Spck `Ln 1104, Col 20   Sp:2`

Free text: **"I don't want to use any extra spaces just make it like spck"**

Interpretation: compact Spck parity, no extra spaces, no extra `<>` button, MD preview via Run.

## Implementation and exit, after agreement

EditorShellUi, TabClosePolicy, TabSortPolicy, EditorChrome, EditorTabBar, EditorStatusBar, WebFileSupport, EditorScreen; protect compactness, no extra spaces.

- Tab bar 40dp (was 48dp), inner padding 8dp (was 12dp), icon 14dp, italic active, subtle underline (onSurface 0.35 alpha, 2dp), fixed 6dp dirty dot (no layout shift)
- Top bar 48dp custom Row (was 64dp TopAppBar), breadcrumb `a/b/c` → `a > b > c`, titleSmall, ellipsis, 20dp icons, no extra spaces
- Tab menu: Quick Close, Close Others, Close Unmodified, Close All, Copy path, sorts (Queue, Alphabetically, Path, Ext.) with icons + checkmark for currentSort, Hide Tabs — Spck parity from shot 204945
- TabSort: added QUEUE, MENU_ORDER = QUEUE, NAME, PATH, EXTENSION
- Status bar: vertical padding 2dp (was 5dp), horizontal 8dp (was 12/10)
- MD preview: WebFileSupport.isMarkdown + isPreviewable, previewEntryOrNull/runOpenFile/runDefaultFile use isPreviewable, WebPreviewScreen allows HTML+MD
- No new dependency, permission, preference, telemetry. Phase 64 (no guide/install locks) and Phase 66-67 (Files-first drawer, filename search, nested create, download/share) intact.

Use existing platform/components and pure policies first. Add focused regression coverage for the agreed behaviour, check loading/empty/failure states, long text, Back, touch targets, light/dark, narrow layouts and keyboard overlap. Run CI on the session branch. Report separately what source tests, Android tests and real device evidence establish. Do not make the owner run declined device rounds.

No new dependencies, permissions, persistent preferences or telemetry by default. Stop after this agreed part and update its record; do not roll into the next chat.
