# Phase tracker — every phase, one table

Phases are the unit of record in CodeC: one owner request → one research pass →
one roadmap → one `chat-phaseNN/` folder of specs and evidence. They are numbered
in execution order (`docs/getting-started/HOW_TO_CREATE_A_PHASE.md`).

**Find a phase by number below, or browse by topic via the category column.**

## By number

| # | Phase | Category |
|---|---|---|
| 1 | [Terminal — the first chat's record](01-terminal-userland/chat-phase1/) | 01-terminal-userland |
| 2 | [Bootstrap userland (BusyBox base)](01-terminal-userland/chat-phase2/) | 01-terminal-userland |
| 3 | [Package repository, CI & repository signing](02-packages-toolchains/chat-phase3/) | 02-packages-toolchains |
| 4 | [Package catalog, install confirmation & trust](02-packages-toolchains/chat-phase4/) | 02-packages-toolchains |
| 5 | [Capabilities, web preview & file share](07-platform-capabilities/chat-phase5/) | 07-platform-capabilities |
| 6 | [Terminal UX](01-terminal-userland/chat-phase6/) | 01-terminal-userland |
| 7 | [Multi-terminal sessions](01-terminal-userland/chat-phase7/) | 01-terminal-userland |
| 8 | [Projects & file tree (keystone)](04-projects-files/chat-phase8/) | 04-projects-files |
| 9 | [Editor foundation](03-editor/chat-phase9/) | 03-editor |
| 10 | [GUI package catalog (Package & Command Hub)](02-packages-toolchains/chat-phase10/) | 02-packages-toolchains |
| 11 | [Output panel & integrated run](05-run-output-preview/chat-phase11/) | 05-run-output-preview |
| 12 | [Python & multi-language intelligence](03-editor/chat-phase12/) | 03-editor |
| 13 | [GitHub integration](06-git-github/chat-phase13/) | 06-git-github |
| 14 | [Mixed-language & server WebViews](05-run-output-preview/chat-phase14/) | 05-run-output-preview |
| 15 | [Spck-style editor & project experience (parts 15–17)](03-editor/chat-phase15/) | 03-editor |
| 18 | [CodeCApi device capabilities & final polish](07-platform-capabilities/chat-phase18/) | 07-platform-capabilities |
| 19 | [Terminal parity (Termux-quality)](01-terminal-userland/chat-phase19/) | 01-terminal-userland |
| 20 | [Toolchain expansion (gcc/g++ in CI)](02-packages-toolchains/chat-phase20/) | 02-packages-toolchains |
| 21 | [Compiler engine redesign — LanguageRunProfile registry](02-packages-toolchains/chat-phase21/) | 02-packages-toolchains |
| 22 | [Editor touch smoothness & keyboard shortcuts](03-editor/chat-phase22/) | 03-editor |
| 23 | [Interactive run UX — inline PTY input](05-run-output-preview/chat-phase23/) | 05-run-output-preview |
| 24 | [Polish batch — feasible items from groups 3–5](10-app-polish-settings/chat-phase24/) | 10-app-polish-settings |
| 25 | [Mobile-first editor core](03-editor/chat-phase25/) | 03-editor |
| 26 | [Typing experience 2.0](03-editor/chat-phase26/) | 03-editor |
| 27 | [Phone-native autocomplete](03-editor/chat-phase27/) | 03-editor |
| 28 | [CodeC Keys — the in-app code keyboard](03-editor/chat-phase28/) | 03-editor |
| 29 | [VS Code colour (TextMate)](03-editor/chat-phase29/) | 03-editor |
| 30 | [Offline completeness — snippets + Emmet](03-editor/chat-phase30/) | 03-editor |
| 31 | [IntelliSense as packages (LSP)](03-editor/chat-phase31/) | 03-editor |
| 32 | [Phone canvas — see the code](03-editor/chat-phase32/) | 03-editor |
| 33 | [First hour — Pydroid-easy, Acode-install](09-onboarding-setup/chat-phase33/) | 09-onboarding-setup |
| 34 | [Official file icons](03-editor/chat-phase34/) | 03-editor |
| 35 | [Editor typing feel](03-editor/chat-phase35/) | 03-editor |
| 36 | [Terminal speed & feel](01-terminal-userland/chat-phase36/) | 01-terminal-userland |
| 37 | [Device as server — localhost on the LAN](07-platform-capabilities/chat-phase37/) | 07-platform-capabilities |
| 38 | [Identity — real app icon + Settings trim](10-app-polish-settings/chat-phase38/) | 10-app-polish-settings |
| 39 | [Outputs are temporary, never in your repository](05-run-output-preview/chat-phase39/) | 05-run-output-preview |
| 40 | [GitHub that tells the truth](06-git-github/chat-phase40/) | 06-git-github |
| 41 | [Feedback that reaches you (WhatsApp-first)](08-release-support/chat-phase41/) | 08-release-support |
| 42 | [Share-readiness — signing, size, updates](08-release-support/chat-phase42/) | 08-release-support |
| 43 | [❌ CANCELLED — 'Open a folder' removed from the app](13-archive/chat-phase43/) | 13-archive |
| 44 | [Setup you can see, and cannot half-finish](09-onboarding-setup/chat-phase44/) | 09-onboarding-setup |
| 45 | [Guide slides + coach marks (retired by Phase 64)](09-onboarding-setup/chat-phase45/) | 09-onboarding-setup |
| 46 | [Projects, not folders](04-projects-files/chat-phase46/) | 04-projects-files |
| 47 | [Editor chrome that behaves](11-phone-ui-parity/chat-phase47/) | 11-phone-ui-parity |
| 48 | [Nothing hides behind the keyboard](11-phone-ui-parity/chat-phase48/) | 11-phone-ui-parity |
| 49 | [Back does the obvious thing, everywhere](11-phone-ui-parity/chat-phase49/) | 11-phone-ui-parity |
| 50 | [The look — one CodeC design language](11-phone-ui-parity/chat-phase50/) | 11-phone-ui-parity |
| 51 | [The feel — the screens you touch every day](11-phone-ui-parity/chat-phase51/) | 11-phone-ui-parity |
| 52 | [The return — remembers you, never feels slow](11-phone-ui-parity/chat-phase52/) | 11-phone-ui-parity |
| 53 | [❌ CANCELLED — cross-device round (kept as history)](11-phone-ui-parity/chat-phase53/) | 11-phone-ui-parity |
| 54 | [Reference lock — the phone shots](11-phone-ui-parity/chat-phase54/) | 11-phone-ui-parity |
| 55 | [Add the side panel](11-phone-ui-parity/chat-phase55/) | 11-phone-ui-parity |
| 56 | [Remove only the Projects option](11-phone-ui-parity/chat-phase56/) | 11-phone-ui-parity |
| 57 | [Editor chrome](11-phone-ui-parity/chat-phase57/) | 11-phone-ui-parity |
| 58 | [First open, silent install, hamburger](09-onboarding-setup/chat-phase58/) | 09-onboarding-setup |
| 59 | [Projects hub — find, filter, identify](04-projects-files/chat-phase59/) | 04-projects-files |
| 60 | [Tabs and the coding row](11-phone-ui-parity/chat-phase60/) | 11-phone-ui-parity |
| 61 | [Preview tools](05-run-output-preview/chat-phase61/) | 05-run-output-preview |
| 62 | [Settings — find and group](10-app-polish-settings/chat-phase62/) | 10-app-polish-settings |
| 63 | [Verify Git + safe per-file Discard](06-git-github/chat-phase63/) | 06-git-github |
| 64 | [Remove installation UI locks and the guide](09-onboarding-setup/chat-phase64/) | 09-onboarding-setup |
| 65 | [Shell and navigation](12-ui-polish-program/chat-phase65/) | 12-ui-polish-program |
| 66 | [Projects hub and creation](12-ui-polish-program/chat-phase66/) | 12-ui-polish-program |
| 67 | [Files, drawer and project search](12-ui-polish-program/chat-phase67/) | 12-ui-polish-program |
| 68 | [Editor chrome, tabs and file actions](12-ui-polish-program/chat-phase68/) | 12-ui-polish-program |
| 69 | [Typing, keyboard and selection (69.1–69.4)](12-ui-polish-program/chat-phase69/) | 12-ui-polish-program |
| 70 | [Preview first — run output + preview (70.1 + 72.1)](12-ui-polish-program/chat-phase70/) | 12-ui-polish-program |
| 74 | [Settings, support and final consistency](12-ui-polish-program/chat-phase74/) | 12-ui-polish-program |
| 76 | [✅ AI Level 1 — read-only Gemini helper (device-passed, merged)](03-editor/chat-phase76/) | 03-editor |
| 77 | [✅ AI UI for phones — floating button + chat sheet (device-passed, merged)](03-editor/chat-phase77/) | 03-editor |
| 78 | [✅ AI Level 2 — whole-project context (device-passed, merged)](03-editor/chat-phase78/) | 03-editor |
| 79 | [✅ AI Level 3 — proposed edits, review, and undo (device-passed, merged)](03-editor/chat-phase79/) | 03-editor |
| 80 | [✅ AI Level 4 — whole-project map, bounded tools, approved run loop (device-passed, merged)](03-editor/chat-phase80/) | 03-editor |
| 81 | [✅ AI — Continue: get the rest of an answer that was cut off (device-passed, merged)](03-editor/chat-phase81/) | 03-editor |
| 82 / 82B | [🚧 AI — rate-limit resilience, bigger answers + Level 5A NVIDIA dev/test BYOK (merged; device acceptance postponed)](03-editor/chat-phase82/) | 03-editor |
| 83 | [✅ AI Level 6 — synthetic fixtures, offline baseline/replay (test-only; merged PR #109; Build APK CI 37051539267 green)](03-editor/chat-phase83/) | 03-editor |
| 84 | [✅ AI Level 7 — agent correctness (merged with Phase 85 via PR #110; Build APK CI 37084800939 green)](03-editor/chat-phase84/) | 03-editor |
| 85 | [✅ AI Level 8 — bounded-but-honest reads, read_files, restorable eviction and S11 working set (merged PR #110)](03-editor/chat-phase85/) | 03-editor |
| 86 | [✅ AI Level 9 — bounded task memory and planning (merged PR #111 to `main` @ `6838ea6`; post-merge Build APK `37109573383` green; device acceptance postponed to Level 12)](03-editor/chat-phase86/) | 03-editor |
| 87 | [✅ AI Level 10 — agent controls and options (nine bounded controls in the AI panel; MERGED via PR #112, 1 594 host cases, S9 held)](03-editor/chat-phase87/) | 03-editor |
| 88 | [✅ AI Level 11 — agent phone presentation (Markdown answers, https links behind a confirm, one truthful progress line, full result on tap; IMPLEMENTED and CI-GREEN 2026-10-03, not merged)](03-editor/chat-phase88/) | 03-editor |

## By category

| Category | Theme | Phases |
|---|---|---|
| [01-terminal-userland](01-terminal-userland/) | Terminal & Linux userland | 6 |
| [02-packages-toolchains](02-packages-toolchains/) | Packages & toolchains | 5 |
| [03-editor](03-editor/) | Editor & languages | 25 |
| [04-projects-files](04-projects-files/) | Projects & files | 3 |
| [05-run-output-preview](05-run-output-preview/) | Run, output & preview | 5 |
| [06-git-github](06-git-github/) | Git & GitHub | 3 |
| [07-platform-capabilities](07-platform-capabilities/) | Platform & capabilities | 3 |
| [08-release-support](08-release-support/) | Release & support | 2 |
| [09-onboarding-setup](09-onboarding-setup/) | Onboarding & setup | 5 |
| [10-app-polish-settings](10-app-polish-settings/) | App polish & settings | 3 |
| [11-phone-ui-parity](11-phone-ui-parity/) | Phone UI parity | 12 |
| [12-ui-polish-program](12-ui-polish-program/) | UI polish program (phases 65–75) | 7 |
| [13-archive](13-archive/) | Archive | 1 |

> Phase 16–17 are parts of the phase-15 folder. Phases 71, 72, 73 and 75 are
> briefs inside [12-ui-polish-program](12-ui-polish-program/) (70.1 + 72.1 share
> the phase-70 record). Next free number: check
> [`docs/getting-started/NEXT_STEPS.md`](../getting-started/NEXT_STEPS.md).
