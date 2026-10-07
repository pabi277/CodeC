# W2 source ledger — 2026-10-07

Read-only source snapshot: session baseline **47c09a6fb69381f5f54e5973887b85846368c495**,
whose app/source tree is main **d4231f0d69633e6961976e7c7d32c13b9041d8d9**.
README first, then the relevant troubleshooting, beta, privacy and release guides;
current UI code resolves old design ambiguities. No app source or app docs edited.
Line numbers below refer to that snapshot, not future main.

## Install page

| Page section / claim | Source and location | Decision / limit |
|---|---|---|
| Release-first; app-v tags; universal filename; unknown-app permission | [README](../../README.md) lines 35–65, “Install the APK from GitHub” | Source ZIP is not the APK. Release / developer / updater route cards appear in that order; detailed developer material is separate from normal installation. |
| Current version, asset size, versionCode, checksum, publication | GitHub `gh release view app-v1.3.18 --json tagName,publishedAt,assets,body`; [published release](https://github.com/pabi277/CodeC/releases/tag/app-v1.3.18); [app/build.gradle.kts](../../app/build.gradle.kts) lines 18–36 | One asset, **CodeC-IDE-1.3.18-universal.apk**, **7,217,532 B**, **22**, published **2026-10-06T17:49:02Z**. SHA256 below. No use of historical 6.6 MB / -74%, no substitution of a fresh CI APK's byte count. |
| Developer Actions artifacts, debug/release keys | README lines 47–53; [release template](../../docs/guides/RELEASE_NOTES.md), download/upgrading guidance | Debug is development-only; branch release artifact is not necessarily the published release; sign-in may be needed. |
| Numeric version guard, SHA256, missing checksum opens Releases, user-triggered update | README lines 55–59 | Settings → About → Check for updates; no automatic background check, no userland APK confusion, no silent downgrade. |
| Fresh install when signing key changes; export before uninstall | [BETA](../../docs/guides/BETA.md) lines 13–25, 70–78; release template upgrading section | Do not tell users to uninstall without first exporting outside app-private storage. Same-key updates normally preserve data. |
| Backup/restore labels and scope | BETA “Your backup is one tap”; [strings.xml](../../app/src/main/res/values/strings.xml) lines 120–121; [privacy](../../docs/guides/DATA_AND_PRIVACY.md) lines 231–241 | Export all projects / Import projects backup. Source projects, not userland/settings/token. Reconnect after fresh install. |
| Android minimum and ABI limits | app/build.gradle.kts lines 18–36; README lines 70–85; BETA B-2/B-8 and API24/26 note | API24 minimum is NOT device certification. TCC arm64-v8a/x86_64 only; Clang module arm64-only. Do not copy old spec/BETA's implication that the arm64 module fixes 32-bit or promise all 32-bit features work. |
| Auto TCC → optional Clang → compatible Termux fallback; deleted picker/card | README lines 74–85, 257–275; [TROUBLESHOOTING](../../docs/guides/TROUBLESHOOTING.md) §27, lines 1029–1072 | Setup guidance lives in Output Panel when needed. No engine selection taught. |
| Termux setup commands/permission | README lines 259–275; TROUBLESHOOTING §27 | Exactly the README's echo, reload and pkg-update/install lines, expressly **inside Termux**, never CodeC. 0.109+ from F-Droid/GitHub. No official Termux repositories added to CodeC. |
| Privacy acknowledgement | privacy lines 41–54 | Skippable education; acknowledgement before normal onboarding entry, not Android permission or a network request. Safe-mode exception is not generalized away. |

APK SHA256:
`549a8c54cf9ff3fff4f6d985997bbb887f6b3b3098f4ab6ba68fefe7028a60d8`.
The website labels this as a **v1.3.18 snapshot** and tells readers to use a later
release's own digest for a later APK.

## Getting Started page

| Section / claim | Source and location | Decision / limit |
|---|---|---|
| Optional Linux, no startup download, setup doesn't lock navigation | README lines 110–128; current onboarding/setup descriptions | C/HTML first; Terminal setup only when tools need it. Network needed for initial download/package fetching, not the first static HTML example. |
| Three-step skippable intro, acknowledgement, Arcade, replay, resume | README lines 131–148; privacy lines 41–54 | Editable modular offline Snake/Block Party/Tic-Tac-Toe index. Seed once, don't overwrite edits or recreate deleted starter. No retired first-hour tiles/tour. |
| Projects + / create opens editor / Auto empty project | [FileManagerScreen](../../app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt) lines 746–864, 1971–2035; [ProjectScaffold](../../app/src/main/java/com/codeci/ide/ui/projects/ProjectScaffold.kt) lines 26–35; [ProjectTypes](../../app/src/main/java/com/codeci/ide/ui/projects/ProjectTypes.kt) lines 17–25 | Use FirstSteps / Auto (detect), then hello.c. Avoid accidentally teaching two generated main functions. + has three rows, not Open Folder. |
| Editor drawer New file | [EditorProjectDrawer](../../app/src/main/java/com/codeci/ide/ui/components/EditorProjectDrawer.kt) lines 151–163, 235 | Real toolbar action, no invented menu or deleted folder flow. |
| Autosave / active-file RUN | README line 197; [EditorViewModel](../../app/src/main/java/com/codeci/ide/ui/viewmodels/EditorViewModel.kt) lines 3560–3612; [ProjectRunDetector](../../app/src/main/java/com/codeci/ide/ui/projects/ProjectRunDetector.kt) lines 8–36, 72–82 | Let autosave finish; active hello.c runs even in an Auto project. Examples compile one file each. |
| Terminal cc + ./, output option last | README lines 87–103; rule.md invariants | Exact two command lines; no dot PATH, no cc shim replacement, no claim of desktop GCC compatibility. |
| Get the terminal into the correct project directory | FileManagerScreen lines 2220–2227; [TerminalHandoff](../../app/src/main/java/com/codeci/ide/ui/terminal/TerminalHandoff.kt) lines 95–111 | File tree hello.c ⋮ → Run in Terminal performs the cd/build/run handoff. Once finished, readers compile manually in that same folder. |
| Interactive scanf exercise | README line 103 and current runner/PTY context | Teach **Terminal** positively for this exercise. Do NOT repeat the universal old “RUN has no keyboard” claim: the old spec itself references inline PTY input. Plain expected transcript, not an app-device test claim. |
| Python install, binary, badge / action | README lines 150–158; [ModuleCatalog](../../app/src/main/java/com/codeci/ide/ui/modules/ModuleCatalog.kt) lines 147–154 | Real catalog package id python, display Python 3, binary/run python3, GUI install pkg install -y python. Manual pkg install python leaves any confirmation interactive. No fixed package count/version. |
| Static Web scaffold and HTML RUN | ProjectTypes lines 24–25; ProjectScaffold lines 32–35, 67–84; README lines 250–254 | index.html whole-project local server; relative CSS/JS/images and live save. HTML RUN is preview, not compilation. |
| LAN off by default, two distinct URLs and QR | [ServerSharePanel](../../app/src/main/java/com/codeci/ide/ui/components/ServerSharePanel.kt) lines 67–99, 160–239, 374; [ServerEndpoints](../../app/src/main/java/com/codeci/ide/ui/services/ServerEndpoints.kt) lines 8–25, 55 onward | On this phone vs Other devices; explicitly opt-in, same-Wi-Fi, keep server running. Loopback is not the other-device URL. Share only non-sensitive projects, turn off afterward. No public-hosting claim. |
| Five tabs / file vs whole workspace | README lines 166–176, 195–197, 246–254 | Projects / Editor / Terminal / Packages / Settings; project ⋮ Open in editor for full workspace. No deleted Open Folder. |
| Privacy and public support | privacy guide; README Issues/support links; D28 | Existing online privacy guide while W3 local page is pending; no personal email/phone published. |

## Clean-room and historical conflicts

- New HTML written for this phase in W1 chrome. PR #42 supplied no W2 code or facts.
- Current v2.3 amendments override old 6.6 MB, -74%, fixed setup/tour/tiles,
  obsolete 32-bit advice, old paths and personal contacts in retained v2.2 bodies.
- Old records stay in chat-web3/chat-web4. This ledger is **not** W4.2's required
  comprehensive Verified Facts Table; that gate and the later device transcripts
  remain mandatory.
- Download and CLI steps were traced through source; sandbox website/host-C checks
  do not establish an APK install, Android execution or LAN device pass.
