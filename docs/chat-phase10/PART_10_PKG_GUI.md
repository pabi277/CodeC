# Phase 10.1 — GUI Package Catalog
Status: planned · [client-only] · depends 3/5
Context: ModulesScreen exists only for optional Clang module (superseded by pkg + repo); dead UI that confuses users.
D1: Replace Modules with pkg-backed catalog; each item = package from repo; INSTALL calls `pkg install <pkg>` in background; progress via TerminalSession/Output; errors parsed to friendly message (e.g., "network error — check queue" instead of raw apt); uninstall button; no new recipes needed.
Sources: ModulesScreen.kt, ModuleCatalog, ModuleInstaller, pkg CLI, repository metadata (codec-packages/).
Exit device: browse catalog; install nano from GUI; progress shown; error shown clearly; installed listed; uninstall works; terminal `pkg list` confirms.
Evidence: §5.1 host (UI flow), §5.2 CI, §5.3 device.
Not in 10: new package builds (those are Phase 12 repo-build for Python; Phase 10 is UI only).
