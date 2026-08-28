# CodeC Phase 8 Documentation — Projects & File Tree (Keystone)

**Status:** Implementation started 2026-08-28 · device acceptance pending · **Cost:** `[client-only]`

Phase 8 is the **Keystone Architecture Phase** (`[client-only]`). It replaces flat file listing with a full-fledged hierarchical Project Tree model, adds `.codec/project.json` run configuration, native SAF file/folder import/export, and synchronizes Terminal working directories (`cwd`) with projects.

## Contents & References

- **[Part 8.1 — Projects, Hierarchical File Tree, & SAF Import/Export](PART_8_PROJECTS.md)** — Data model, tree rendering, SAF import/export architecture, `.codec/project.json` schema, and device acceptance test plan.

## Why Phase 8 is the Keystone
- **Phase 9 (Editor Foundation):** Relies on folder hierarchy for multi-file tabs and relative include paths.
- **Phase 11 (Output Panel & Run):** Reads `.codec/project.json` to know which command and executable to run.
- **Phase 12 (Multi-language / Python):** Uses project type presets (`type: "python"`, `type: "c"`).
- **Phase 13 (GitHub):** Clones Git repositories directly into new project folders.
