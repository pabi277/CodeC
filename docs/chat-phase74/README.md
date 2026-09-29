# Phase 74.1 — Settings, support and final consistency

**Owner approved both remaining discussion parts in this chat (2026-09-29), and approved all recommendations.** Decisions:

- Editor font size defaults to **16sp**. Existing saved values remain unchanged; the slider range remains 12–32sp.
- **Every Settings section starts collapsed** when the screen gets fresh state, including the three bespoke groups (GitHub Account, Package Repository & Trust, Terminal Extra-Keys & Shortcuts). A user may expand any section; a search still reveals matching rows. These view-state folds are not persisted as a preference.
- Inline ghost text defaults **off** for new/default settings. Existing explicit user choices remain respected.
- Keep the exit-feedback prompt and “Show the welcome screen again” control unchanged; no change to their semantics was authorized.
- Preserve the current look, keep credentials and destructive actions distinct, and add no new permissions, dependencies or telemetry.

## Implementation

Changed the DataStore fallbacks and first-composition values together to avoid a transient old default. Settings disclosure now initializes from all section titles, and every visible section can fold, including form-only groups. Search behavior remains authoritative while filtering. The header omits a misleading zero-row badge for bespoke groups. Focused unit/source-wiring tests pin these defaults, folding behavior and screen wiring.

The broader control inventory was reviewed against the current Settings screen and `docs/chat-phase38/SETTINGS_AUDIT.md`; this delivery makes only the agreed default/folding changes and does not invent replacement controls. No device review is claimed.

Build APK round 1 `36607240454` failed at test compilation because a new test function omitted `()`; the declaration was corrected. Round 2 ✅ GREEN `36607641208` on `52420c1` (debug/release assembly, host unit/screenshot tests, lint per the workflow). The above CI run tested the original Phase 74 code together with the first typing patch; after the owner asked to undo the typing work, the typing-only revert is pending a fresh CI run. Phase 74 Settings changes remain untouched. No PR opened and no merge performed; see `rule.md` §3.
