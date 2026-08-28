# CodeC Phase 10 — GUI Package Catalog (Package & Command Hub)
**Status:** ✅ IMPLEMENTED on `arena/01a0482c-codec` (2026-08-28) · cost `[client-only]` · depends on Phase 3/5/6.
**Source refs:** `ModulesScreen.kt`, `ModuleCatalog.kt`, `TerminalSession.kt`, `TerminalViewModel.kt`, `MainActivity.kt`.
**D1 Implementation:**
- Complete Package & Command Hub screen (`ModulesScreen.kt` + `ModuleCatalog.kt`).
- 1-tap INSTALL, RUN, REINSTALL, UNINSTALL over live terminal session (`\r` line discipline).
- Quick Actions: `pkg update`, `pkg upgrade -y`, `codec-setup-storage`, `pkg status`, `pkg heal`, `pkg repair`.
- Status detection in `$PREFIX/bin` and `dpkg/status` (`INSTALLED ✓` vs `AVAILABLE`).
- Custom terminal command runner bar.
- CI verification: Run `33177852501` passed green.

