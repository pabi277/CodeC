# CodeC Phase 8 Documentation — Projects & File Tree (Keystone)

**Status:** ✅ Implementation complete in PR #27 · **core device workflows confirmed; final export/re-import round trip remains the merge gate**
**Completed:** 2026-08-29 · **Cost:** `[client-only]`

Phase 8 is the **Keystone Architecture Phase**. It replaces the flat file
listing with a hierarchical Project Tree, adds `.codec/project.json` run
configuration, native SAF file/folder import and explicit ZIP export, complete
extension-agnostic ZIP extraction, terminal/editor project integration, and a
web-project default HTML entry.

## Contents & References

- **[Part 8.1 — Projects, Hierarchical File Tree, & SAF Import/Export](PART_8_PROJECTS.md)** — Architecture, implementation, and acceptance recipe.
- **[Design Decisions & Completion Record](PART_8_DESIGN_DECISIONS.md)** — Security/privacy decisions, owner device evidence, and the remaining merge gate.
- **[Completion and Acceptance Record](PART_8_COMPLETION.md)** — Scope, evidence matrix, and the final export/re-import device check.

## Delivered in PR #27

- Private project lifecycle and nested file tree.
- SAF folder/file import and explicit ZIP export.
- ZIP import that preserves all file types, nested paths, Unicode, spaces,
  extensionless files, and binary files.
- Central-directory ZIP reading for problematic SAF-produced archives.
- Project-aware editor routes and breadcrumbs.
- Terminal project listing and project-relative build/run handoff.
- Projects overflow menu, refresh/collapse-all, and HTML default-run entry.

## Phase Dependencies

Phase 8 implementation is complete, so the Phase 9 Editor Foundation work is
now unblocked after the final export/re-import device check. Phase 11 (Output
Panel & Run), Phase 12 (Multi-language/Python), and Phase 13 (GitHub) continue
to depend on the project model.
