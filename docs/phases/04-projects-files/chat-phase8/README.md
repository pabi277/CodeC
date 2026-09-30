# CodeC Phase 8 Documentation — Projects & File Tree (Keystone)

**Status:** ✅ Complete — implementation merged in PR #27 (`348eb03`), core device
workflows and the final export → re-import-as-a-new-project round trip owner-confirmed
on 2026-08-29. Phase 8 acceptance gate is fully closed.
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

Phase 8 is fully accepted: implementation merged in PR #27, and the final
export/re-import device round trip was owner-confirmed on 2026-08-29. The Phase 9
Editor Foundation work is therefore unblocked; it is being implemented on
`arena/01a04c1c-codec`. Phase 11 (Output Panel & Run), Phase 12 (Multi-language/
Python), and Phase 13 (GitHub) continue to depend on the project model.
