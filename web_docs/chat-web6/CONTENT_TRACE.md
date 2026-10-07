# Remaining-site content trace — 2026-10-07

Authoritative app/source snapshot **8467ff8c5b530a4ba592e92b0afa5000304d9a56**
(app tree **d4231f0**). [W4.2 Verified Facts](../chat-web4/VERIFIED_FACTS.md) was
written BEFORE course authoring:43 row groups, source paths and line numbers,
package-config SHA256, all19 chapters locked. Existing W2 ledger remains separate.
No PR42 code imported. No app/source/docs/workflow modifications.

## Product pages

| Page | Primary source groups / additions |
|---|---|
| Engines | README Run C / automatic engines / Termux; TROUBLESHOOTING §27; BETA B2/B8, ABI caveats. Current README overrides old switch-engine prose. |
| Packages | Actual CODEC_REPOSITORY_PACKAGES,33 roots; bootstrap seed separate; ShellEnvironment signed-by/atomic trust; ModuleCatalog package/executable names. Config is NOT an availability guarantee. |
| FAQ | BETA B1–B8 retained as eight identified answers; compiler/runtime/input, Git truth, backup, LAN, AI cost/history, privacy and update/report answers from README/guides + newer GitReadiness labels. |
| About | README editor/history/outputs/Git/first-run; RunArtifacts, RepoHygiene; targetSdk/minSdk/version from build.gradle.kts; published release size; no app screenshots or personal contacts. |
| AI | AI.md sections1–12 and DATA_AND_PRIVACY task-consent/memory/recipient details. All eight tool rows, all nine controls, D6/S9 and hard caps included. |
| Privacy | DATA_AND_PRIVACY first-run,14 permission rows and named readers, source-project backup, crash, provider and no_backup records. Older folder wording treated as permission disclosure, not deleted UI. |

## Chapters

Every chapter has goals, prerequisites, inline steps/code where relevant, expected
observations,1–3 exercises, common mistakes and prev/next. Chapter17 leads18,
18 leads19,19 returns to the course; no premature completion footer.

| Chapter | Sources / teaching decisions |
|---|---|
|01|README current intro/Arcade, optional setup, five tabs; ProjectTypes/Scaffold and FileManagerScreen create-to-editor; no forced download. HOME versus PREFIX corrected from old lesson premise.|
|02|README cc/./, active-file RUN, TCC ABI restrictions; W2 current hello workflow. Output filename stays explicit; no dot PATH.|
|03|README terminal/PT Y/extra keys; TerminalSessionManager8-cap; shell basics in disposable folders; no universal “RUN has no input” assertion.|
|04|Same engine facts as product page; small conservative C probe and error-specific remediation; Termux commands expressly in Termux.|
|05|W3 config table reused via local link; code checks all33 package data attributes against config; signed metadata kept intact.|
|06|Phase46 removal from README/FileManagerScreen; ProjectRunDetector; RunArtifacts and RepoHygiene; BETA/export and backup include-list.|
|07|README current editor, TextMate, snippets/Emmet/TAB-vs-Enter, phone keyboard default, optional Keys, format fallback and line guard. No automatic dependency installation promised.|
|08|Original conservative ANSI-C examples for all10 required sections, capstone, bounded input and symbolic pointer walk. Host C90 compile/output checks only; owner device gate remains.|
|09|Original Bash scripts, quoting, variables/conditions/loops/status; CodeC userland prerequisites; no replaced bash, hard-coded desktop shebang or broad deletion.|
|10|python root / python3 binary verified; original standard-library utility, fixtures and error handling. No Tkinter/pip dependency promised.|
|11|GitReadiness.kt current panel remedies override older README Settings-only directions; local commit vs remote push; manual GitHub remote creation, private default where Publish offered.|
|12|Spec variant A selected: curl seed, wget root, Git HTTPS; OpenSSH explicitly deferred. LAN/loopback from ServerEndpoints/ServerSharePanel/README. No ssh install/key recipe invented.|
|13|ShellEnvironment11 API scripts; CodecApiBridge battery percentage/nullable fields/sensor replies; CodecApiProtocol caps/markers; MainActivity camera path. Explicit permission/refusal/hardware limits.|
|14|README whole-project HTTP preview, relative files/modules/fetch/live reload/console; original4-file example with error handling and local-only assets. Corrected old blanket claim that file:// cannot load siblings: fetch/modules are the restricted part.|
|15|SettingsScreen459–516 exact shortcut labels; editor/terminal themes; CodecJsonParser build/run override; verified make/Clang/ripgrep/tmux tools. Intentionally unused-variable diagnostic, not executing undefined uninitialized-value code.|
|16|All5 specified projects: original menu calculator,4-file page, Python counts/top3, own Git workflow and Bash/API automation. Package upgrade needs explicit --upgrade plus YES; failed/null/range/speech cases stop honestly. P1/P5 device gate retained.|
|17|BETA/README/TROUBLESHOOTING, current SettingsScreen1330–1355 View/Export App Logs; non-destructive diagnostic ladder, third-launch safe mode, redacted report template; ch18 next.|
|18|AI.md exact setup/welcome/preview/timeline/Stop/Continue/drawer; complete harmless C example, honest expected—not guaranteed—answer and no-key reading path.|
|19|All8 tools, individual/global caps, refusal paths, Apply/Reject, Run/Skip, Undo bounds,9 controls/S9, D6 versus task memory, redaction/provider retention. Safe exercises use no real secrets.|

## Corrections and explicit scope choices

- User authorized one combined implementation/commit/CI watch (D33). This changes
  scheduling, not the self-dependent law or device/deployment gates.
- Package config includes33 curated roots; the GUI catalog can list additional
  tools. A catalog card alone does not prove a current downloadable package.
- Device API inventory is11, battery key percentage. TTS cap32KiB is a refusal,
  not truncation. Some CLI wrappers strip ERR: into human-readable stderr; course
  distinguishes protocol markers from exact screen wording.
- SettingsScreen Terminal Extra-Keys & Shortcuts uses comma-separated values and
  SAVE SHORTCUTS. Current Git Credentials is in Source Control’s ⋮ menu.
- User-named binary output stays put; CodeC-invented run output is temporary.
- P5’s optional package mutation is deliberately safer than an unconditional
  pkg upgrade -y. The owner transcript must state whether the upgrade path was
  tested; an untested portion cannot silently close acceptance.
- No F-Droid distribution promise for CodeC; F-Droid link is Termux-only.
- No course license invented; O7 stays pending. O1 screenshots, O2 domain, O3 tone,
  O5 in-app link also remain owner matters. L13 postponed/L14 unauthorized.
