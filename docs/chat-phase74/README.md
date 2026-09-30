# Phase 74.1 — Settings, support and final consistency

**Owner approved both remaining discussion parts in this chat (2026-09-29), and approved all recommendations.** Decisions:

- Editor font size defaults to **16sp**. Existing saved values remain unchanged; the slider range remains 12–32sp.
- **Every Settings section starts collapsed** when the screen gets fresh state, including the three bespoke groups (GitHub Account, Package Repository & Trust, Terminal Extra-Keys & Shortcuts). A user may expand any section; a search still reveals matching rows. These view-state folds are not persisted as a preference.
- Inline ghost text defaults **off** for new/default settings. Existing explicit user choices remain respected.
- Keep the exit-feedback prompt and “Show the welcome screen again” control unchanged; no change to their semantics was authorized.
- Preserve the current look, keep credentials and destructive actions distinct, and add no new permissions, dependencies or telemetry.

## Implementation

Changed the DataStore fallbacks and first-composition values together to avoid a transient old default. Settings disclosure now initializes from all section titles, and every visible section can fold, including form-only groups. Search behavior remains authoritative while filtering. The header omits a misleading zero-row badge for bespoke groups. Focused unit/source-wiring tests pin these defaults, folding behavior and screen wiring.

The broader control inventory was reviewed against the current Settings screen and `docs/chat-phase38/SETTINGS_AUDIT.md`. A follow-up search audit found the three bespoke groups were discoverable only by section title. The bounded improvement adds a separate search index for their visible control labels and safe descriptive terms, while leaving the 64 real `Settings*` row catalog/audit count unchanged. It never indexes runtime values (including tokens, usernames, repository URLs, or status values) and does not render synthetic controls. Regression tests cover token, commit email, keyring/OpenPGP, and custom shortcut queries plus nonmatches. No device review is claimed.

## 2026-09-30 — bespoke form search follow-up

Owner authorized the recommended Settings search improvement. Added searchable descriptors for the custom Extra-Key shortcuts, repository trust/status, and GitHub credential forms. Matching a descriptor reveals the existing whole section (search remains authoritative over collapsed state); folded headers can show the match count. Only fixed labels/keywords are searched—never form contents or secrets. Existing default decisions, all 12 collapsed-on-entry groups, and their expand behavior remain unchanged. Tests updated in `SettingsSearchPolicyTest.kt` and `SettingsSearchWiringTest.kt`. The pull request is #96. Build APK round 1 `36673923025` failed on a new test expectation: `repository` legitimately matches both the repository-trust form descriptor and the GitHub token-permission descriptor. The assertion was corrected; Build APK round 2 ✅ GREEN `36674331533` on `f29f3f3`. CI validates the full host unit/screenshot suite and APK/lint workflow. Owner-authorized merge is in progress; record merge SHA and post-merge CI below.

Build APK round 1 `36607240454` failed at test compilation because a new test function omitted `()`; the declaration was corrected. Round 2 ✅ GREEN `36607641208` on `52420c1` (debug/release assembly, host unit/screenshot tests, lint per the workflow). The above CI run tested the original Phase 74 code together with the first typing patch; after the owner asked to undo the typing work, post-revert CI `36625653233` is green on `902c9f6`; it validates the restored branch, and does not claim the typing defects are solved. Phase 74 Settings changes remain untouched. No PR opened and no merge performed; see `rule.md` §3.
