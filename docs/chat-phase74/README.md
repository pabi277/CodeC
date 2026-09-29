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

**CI pending** until the session-branch push triggers `Build APK`. No PR opened and no merge performed; see `rule.md` §3.
