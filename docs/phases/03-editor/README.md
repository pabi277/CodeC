# Editor & languages

The code editor core: typing feel, autocomplete, the CodeC Keys keyboard, TextMate colour, snippets/Emmet, LSP, file icons.

Parent map: [`docs/README.md`](../../README.md) · phase tracker: [`docs/phases/README.md`](../README.md)

## Phases in this category

| Phase | Title |
|---|---|
| [9](chat-phase9/) | Editor foundation |
| [12](chat-phase12/) | Python & multi-language intelligence |
| [15](chat-phase15/) | Spck-style editor & project experience (parts 15–17) |
| [22](chat-phase22/) | Editor touch smoothness & keyboard shortcuts |
| [25](chat-phase25/) | Mobile-first editor core |
| [26](chat-phase26/) | Typing experience 2.0 |
| [27](chat-phase27/) | Phone-native autocomplete |
| [28](chat-phase28/) | CodeC Keys — the in-app code keyboard |
| [29](chat-phase29/) | VS Code colour (TextMate) |
| [30](chat-phase30/) | Offline completeness — snippets + Emmet |
| [31](chat-phase31/) | IntelliSense as packages (LSP) |
| [32](chat-phase32/) | Phone canvas — see the code |
| [34](chat-phase34/) | Official file icons |
| [35](chat-phase35/) | Editor typing feel |
| [76](chat-phase76/) | ✅ AI Level 1 — read-only Gemini helper (fifth rail slot) |
| [77](chat-phase77/) | ✅ AI UI for phones — floating AI button + bottom chat sheet |
| [78](chat-phase78/) | ✅ AI Level 2 — whole-project context (read-only) |
| [79](chat-phase79/) | ✅ AI Level 3 — proposed edits, review, and undo |
| [80](chat-phase80/) | ✅ AI Level 4 — whole-project map, bounded tools, approved run loop (merged) |
| [81](chat-phase81/) | ✅ AI — Continue: get the rest of an answer that was cut off (merged) |
| [82 / 82B](chat-phase82/) | 🚧 AI — rate-limit resilience, bigger answers + Level 5A NVIDIA dev/test BYOK (merged; device acceptance postponed) |
| [83](chat-phase83/) | ✅ AI Level 6 — synthetic fixtures, offline baseline/replay (test-only; merged PR #109; Build APK CI 37051539267 green) |
| [84](chat-phase84/) | ✅ AI Level 7 — agent correctness (merged with Phase 85 via PR #110; Build APK CI 37084800939 green) |
| [85](chat-phase85/) | ✅ AI Level 8 — bounded-but-honest reads, read_files, restorable eviction and S11 working set (merged with Phase 84 via PR #110) |
| [86](chat-phase86/) | ✅ AI Level 9 — bounded task memory and planning (merged PR #111 to `main` @ `6838ea6`; post-merge Build APK `37109573383` green; device acceptance postponed to Level 12) |
| [87](chat-phase87/) | ✅ AI Level 10 — agent controls and options (nine bounded controls in the AI panel; **MERGED** via PR #112, 1 594 host cases, S9 held) |
| [88](chat-phase88/) | ✅ AI Level 11 — agent phone presentation (Markdown answers, https links behind a confirm, one truthful progress line, full result on tap; **MERGED** via PR #113 as `0fc2bfd` 2026-10-03; post-merge Build APK `37136523881` green; device acceptance postponed to Level 12) |
| [89](chat-phase89/) | ✅ AI Level 12 — evaluation and acceptance (implemented 2026-10-04; CI-green `37202771868` on `7c1c0e1`; the owner **ran** the device round and reported it — 19/21 Part-1 rows pass, **S2 failed**, B2 not exercised; matrix 14 rows at 3/3 (48/60 runs) with **T3-Gemini** and **T4-NVIDIA failed**, T7/T10 not exercised; Gemini on `gemini-3.1-flash-lite`; **not accepted**, failed rows await their own fix phases) |
| [92](chat-phase92/) | ✅ AI — the self-check (the owner, 2026-10-04: *"I am tired of testing — give some command and I will run and share what is wrong"*. **One command**: five checks the app judges itself — file access, remember a code word, **does a follow-up carry the conversation**, does an edit block parse, does a run request reach the card — and a **redacted report** (no prompts, no answers, no keys: sizes, labels and booleans only) to paste back. Plus the C9/C11 code-block contrast as pinned numbers. **92.1 (his first round, 2026-10-05): *"Not all test run"*** — the card's way on is now unconditional (*Next check* / *Skip check*), a skipped check is reported as *not run* and never as a pass, and the run carries itself from verdict to verdict so the only taps left are the four Sends) |
| [93](chat-phase93/) | ✅ AI — five owner fixes (2026-10-05, *"Ok Whatever you find fix plus i have some additional fixes"*): **one ➤ arrow for the whole flow** (it sends a preview, asks the project elsewhere, and is the last control of every bar) · the **permission failure named before it happens** (`READ_EXTERNAL_STORAGE` capped at API 32 — Android 13+ can never grant it — one Android adapter, one policy, a preflight sentence with the version-exact switch and a `Grant access` tap) · **`all 5 checks`** and the run-request latch so the self-check can pass its fifth check · **Welcome to CodeC** on the home · **one conversation per project, in memory only** (D6 unchanged). Plus the three audited bugs (the latch, the transcript budget's closing line, the invisible refusal). 22 new host cases + 6 updated suites; host sweep 491 pass / 0 fail; **not built, not merged**) |
| [91](chat-phase91/) | ✅ AI — the simple chat (the owner's Phase 90 round, 2026-10-04: **Simple** face by default, **Copy** on every reply, the **New question** button gone with the composer in its place, the quoted conversation closed with an instruction so both providers read it, and a code block that is visible in the dark theme; C4/C8/N1 recorded, not fixed) |
| [90](chat-phase90/) | ✅ AI — the conversation surface (implemented 2026-10-04 on the owner's *"Go"*: **New chat** with a confirm, **follow-up chats** over a bounded in-memory `AiChatSession` transcript with an exact earlier-turns disclosure, and the "real assistant" look — framed code blocks; 30 host cases green, `client.stream(` = 3 and `openUri(` = 2 pinned; device round [C1–C14](chat-phase90/DEVICE_ROUND.md) ⏳ owner-only; **not merged**) |
