# Blank editor — 2026-09-27 device report

Priority: blocker before phases 61/63.

Owner confirms the current branch build, every file affected, no gutter or
status strip, soft keyboard opens but typing is not visible. Changing theme
briefly restored code; changing project brought the blank area back. The
attached screenshot shows the file tabs vertically centred below the top bar.

## Confirmed structural defect

`EditorScreen` puts the tabs in an unweighted Crossfade/Row before the weighted
code Box. `EditorTabBar` retained its outer `fillMaxHeight()` from its old home
inside the bounded TopAppBar title. The Column measures unweighted children
first. The tab strip therefore requests all remaining height, centres its
labels there, and leaves no height for the weighted editor. Checking sibling
order alone (the earlier investigation) did NOT rule out height starvation.

Fix: bound the outer strip to 48.dp. Inner tab boxes still fill that bounded
height. Hide-tabs/reveal/menu behaviour, editor contents, settings, themes and
Sora replay are unchanged. No data reset or file changes are required.

`EditorTabHeightTest` measures the actual strip inside the Crossfade/Row/Column
arrangement: a 400.dp column minus a 64.dp header and a 48.dp strip must leave
288.dp for the code surface. This catches the nested fill-height bug that
source-order checks missed.

Validation: diff whitespace check passed. Android/Compose test execution and
APK build require CI (no local Android toolchain). Device confirmation still
required: open file, switch projects repeatedly, change theme, type, and toggle
Hide tabs / Show tabs. A passing build is not proof of device rendering.

## CI verdict

Code commit `5464981`: CI run `36295679258` passed, including host unit /
screenshot tests (the new measurement regression) and debug/release APK builds.
Run: https://github.com/pabi277/CodeC/actions/runs/36295679258

Device verification remains pending. No PR opened and no merge performed.
