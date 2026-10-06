# W4.2 VERIFIED FACTS — remaining-site batch

**Verified before writing any course pages, 2026-10-07.** Source snapshot
**8467ff8c5b530a4ba592e92b0afa5000304d9a56**, app tree **d4231f0**.
Owner authorized one remaining-site batch / one end commit (D33), superseding
separate phase-start commands and intermediate verification commits only.
This table is written FIRST and lands in that single batch commit. No Android
transcript is inferred; ch08 and P1/P5 remain owner gates.

All rows below are SOURCE-VERIFIED, not device acceptance. W2 release metadata
readback is in chat-web5/W2_SOURCES.md. Package list is configuration, not a
live repository availability guarantee. The 19-chapter teaching set is locked.

| Fact | Verified value / teaching constraint | Source at the pinned snapshot |
|---|---|---|
| Release | app-v1.3.18, versionCode22, minSdk24; APK7,217,532B; W2 GitHub release readback | [app/build.gradle.kts:18](../../app/build.gradle.kts) |
| First run | Skippable education, acknowledgement, editable Arcade once; returning last file | [README.md:131](../../README.md) |
| Optional Linux | No first-launch userland download; opt-in Terminal; no navigation lock | [README.md:109](../../README.md) |
| Auto engines | TCC arm64-v8a/x86_64, Clang module arm64; fallback automatic; no picker | [README.md:72](../../README.md) |
| Termux setup | 0.109+; commands run in Termux only; Android RUN_COMMAND permission | [README.md:265](../../README.md) |
| Package roots | 33 configured curated roots; not 33 binaries or all currently installable packages | [codec-packages/properties.codec.sh:148](../../codec-packages/properties.codec.sh) |
| Network variant | Chapter12 variant A: curl bootstrap seed, wget curated; OpenSSH explicitly deferred | [codec-packages/properties.codec.sh:103](../../codec-packages/properties.codec.sh) |
| Executable mapping | python→python3; libllvm builds clang; lua54→lua; libmagic→file; npm separate | [codec-packages/properties.codec.sh:122](../../codec-packages/properties.codec.sh) |
| Signed repository | signed-by OpenPGP, fail closed; no trusted=yes or official Termux packages | [app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt:770](../../app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt) |
| Terminal | VT/ANSI PTY, cc TCC, one command/line, explicit ./; input exercises in Terminal | [README.md:86](../../README.md) |
| Terminal sessions | 8 sessions default; sessions rename/close; no Android immortality promise | [app/src/main/java/com/codeci/ide/ui/terminal/TerminalSessionManager.kt:62](../../app/src/main/java/com/codeci/ide/ui/terminal/TerminalSessionManager.kt) |
| Device script inventory | 11 API CLI scripts plus separate codec-setup-storage; not stale5/8 total | [app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt:2192](../../app/src/main/java/com/codeci/ide/ui/terminal/ShellEnvironment.kt) |
| Battery shape | percentage nullable; status, temperature, health, voltage, plugged | [app/src/main/java/com/codeci/ide/ui/terminal/CodecApiBridge.kt:572](../../app/src/main/java/com/codeci/ide/ui/terminal/CodecApiBridge.kt) |
| Sensors | accelerometer/gyroscope/light; unavailable is honest ERR | [app/src/main/java/com/codeci/ide/ui/terminal/CodecApiBridge.kt:584](../../app/src/main/java/com/codeci/ide/ui/terminal/CodecApiBridge.kt) |
| Speech limit | 32768 bytes; oversized request returns ERR, not silent truncation | [app/src/main/java/com/codeci/ide/ui/terminal/CodecApiBridge.kt:553](../../app/src/main/java/com/codeci/ide/ui/terminal/CodecApiBridge.kt) |
| Camera and permission markers | NEED_PERMISSION and CAPTURING are intermediate; OK:path or ERR final; basename whitelist | [app/src/main/java/com/codeci/ide/ui/terminal/CodecApiProtocol.kt:75](../../app/src/main/java/com/codeci/ide/ui/terminal/CodecApiProtocol.kt) |
| Intent limits | view/dial/send only; URI allowlist, no arbitrary component | [app/src/main/java/com/codeci/ide/ui/terminal/CodecApiBridge.kt:648](../../app/src/main/java/com/codeci/ide/ui/terminal/CodecApiBridge.kt) |
| Files and Phase46 | New/Clone/Import ZIP; Open Folder removed; single-file vs whole-project distinct | [README.md:169](../../README.md) |
| Editor | Autosave, Format fallback, snippets/Emmet, TAB accepts, Enter newline, Dark+ | [README.md:229](../../README.md) |
| Current Settings | Font and terminal/editor theme controls; read actual screen, no retired engine card | [app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt:154](../../app/src/main/java/com/codeci/ide/ui/screens/SettingsScreen.kt) |
| Local preview | Whole-project loopback, relative CSS/JS/fetch/modules, live save, console | [README.md:251](../../README.md) |
| LAN | Opt-in two URLs; Other devices not loopback; QR, foreground service; network peers can read served files | [app/src/main/java/com/codeci/ide/ui/components/ServerSharePanel.kt:176](../../app/src/main/java/com/codeci/ide/ui/components/ServerSharePanel.kt) |
| Git readiness | Panel INSTALL GIT/init; credentials in ⋮ Git Credentials; manual-first remote creation | [app/src/main/java/com/codeci/ide/ui/projects/GitReadiness.kt:96](../../app/src/main/java/com/codeci/ide/ui/projects/GitReadiness.kt) |
| Git truth | Commit local vs push remote; no-upstream first push, failed push not success | [README.md:200](../../README.md) |
| Publish | Source-supported GitHub repository creation; private default; manual GitHub creation preferred | [app/src/main/java/com/codeci/ide/ui/projects/GitHubPublish.kt:82](../../app/src/main/java/com/codeci/ide/ui/projects/GitHubPublish.kt) |
| Outputs | App-invented artifacts CodeC/temp/runs; named -o output stays requested location | [app/src/main/java/com/codeci/ide/ui/services/RunArtifacts.kt:9](../../app/src/main/java/com/codeci/ide/ui/services/RunArtifacts.kt) |
| Git ignore | Rules .git/info/exclude; never overwrite user .gitignore; untrack does not delete disk file | [app/src/main/java/com/codeci/ide/ui/projects/RepoHygiene.kt:14](../../app/src/main/java/com/codeci/ide/ui/projects/RepoHygiene.kt) |
| Backup | Auto Backup only CodeC/projects, no userland/settings/token | [app/src/main/res/xml/backup_rules.xml:40](../../app/src/main/res/xml/backup_rules.xml) |
| Export/crash/beta | Export all projects ZIP, Import projects backup; third-launch safe mode; B1–B8 qualified | [docs/guides/BETA.md:67](../../docs/guides/BETA.md) |
| Privacy correction | With ONE approval app can read shared files; do not resurrect removed Open Folder UI | [docs/guides/DATA_AND_PRIVACY.md:3](../../docs/guides/DATA_AND_PRIVACY.md) |
| Permissions | 14 declared rows, each with named reader; no telemetry; user-started network | [docs/guides/DATA_AND_PRIVACY.md:192](../../docs/guides/DATA_AND_PRIVACY.md) |
| AI provider/key | BYOK Gemini default; NVIDIA manual dev/test; no server/shared key/proxy; provider billing/terms | [docs/guides/AI.md:13](../../docs/guides/AI.md) |
| AI preview/followups | Exact preview then Send; task scoped follow-up reads can send without another tap | [docs/guides/DATA_AND_PRIVACY.md:69](../../docs/guides/DATA_AND_PRIVACY.md) |
| Eight tools | list_files/search_project/read_file/read_files/find_files/outline_file/read_run_output/request_run | [docs/guides/AI.md:87](../../docs/guides/AI.md) |
| Tool caps/refusals | 200 paths,40 hits/300 files,400 lines/24000 chars,8 files,80-char glob; secret/output/symlink refusal | [docs/guides/AI.md:94](../../docs/guides/AI.md) |
| Apply/Run/Undo | No write/exec tools; Apply/Reject and Run/Skip; last-task Undo≤5files/256KB with conflicts | [docs/guides/AI.md:111](../../docs/guides/AI.md) |
| D6 | Raw chat/titles memory only;≤20 conversations/project;8 carried turns | [docs/guides/AI.md:124](../../docs/guides/AI.md) |
| Task memory exception | Bounded admitted snapshots/findings at no_backup/ai/task; not raw chat;5files/160KiB content/256KiB total | [docs/guides/DATA_AND_PRIVACY.md:123](../../docs/guides/DATA_AND_PRIVACY.md) |
| Nine controls/S9 | Controls tune inside caps; inspection always on; read window50–400 default400 | [docs/guides/AI.md:135](../../docs/guides/AI.md) |
| Global caps | 12 model turns,24 calls,2 approved runs,24000-char reads,8000-char results,8-file batch,3 repeats | [docs/guides/AI.md:147](../../docs/guides/AI.md) |
| Keys/redaction/retention | Keystore AES-GCM no_backup keys; output/error secret scan; vendor retention not zero | [docs/guides/DATA_AND_PRIVACY.md:98](../../docs/guides/DATA_AND_PRIVACY.md) |
| Shipped vs roadmap | Phases1–96 / v1.3.18; L13 postponed,L14 unauthorized; no autonomy/local-model promise | [docs/guides/AI.md:172](../../docs/guides/AI.md) |
| Brand | Local >_ icon byte-identical to original SVG; no remote assets | [docs/brand/icon/codec-mark.svg:31](../../docs/brand/icon/codec-mark.svg) |

## Locked package inventory

`nano`, `less`, `coreutils`, `grep`, `sed`, `gawk`, `gzip`, `tar`, `make`, `libmagic`, `git`, `wget`, `bat`, `ripgrep`, `fd`, `htop`, `tmux`, `tree`, `patch`, `diffutils`, `zstd`, `m4`, `autoconf`, `automake`, `libtool`, `python`, `python-pip`, `libllvm`, `nodejs`, `npm`, `php`, `ruby`, `lua54`.

Config SHA256: `639d4de44c82edbda14a5dec89f35060acb739cb83968a2b26622fe321974d47`.

## Corrections to retained historical prose

- OpenSSH is deferred. Chapter12 uses variant A with curl/wget, explains SSH is
  not promised and uses HTTPS Git. No chapter removed.
- 11 API scripts, not five/eight; battery field percentage, not level. TTS
  oversize is ERR, not truncation. Hardware/voice availability is not guaranteed.
- GitReadiness newer than README: Git Credentials in panel ⋮; INSTALL GIT and
  Initialize repository inline. Create remote on GitHub first; publish needs
  additional token rights, and private is the default where offered.
- Privacy's older shared-folder wording is a permission disclosure, NOT an
  instruction to use deleted Open Folder. Teach New/Clone/Import ZIP only.
- Source verification cannot establish APK install, network deployment, physical
  device output, AI answer quality or provider zero retention.

W3 package table is generated from this exact list; the final static check compares
all33 names with the config. No drift inherited from PR42 or an earlier website.
