# CodeC Phase 13 Documentation — GitHub Integration

Phase 13 delivers native **GitHub & Git Version Control Integration** (`[client-only]`):
- Visual Repository Cloning from GitHub.
- Source Control pane with status badges (Modified, Added, Untracked, Deleted).
- 1-tap Commit & Push with secure credential storage.
- Inline Diff Viewer for modified files.

**Status: IMPLEMENTED (2026-08-30, `arena/01a053b3-codec`)** — engine, UI,
Settings card, and 37 new host tests committed; `Build APK` CI is the test
executor (the sandbox has no JVM). Device recipe in
[`PART_13_GITHUB.md` §7](PART_13_GITHUB.md) is the exit gate.

## Contents & References
- **[Part 13.1 — Git Client, GitHub Token Storage & Clone/Commit/Push UI](PART_13_GITHUB.md)**
  (§6 implementation record + design decisions D1–D7, §7 device recipe)
