# CodeC vs other phone IDEs — what I would change if this were my project

> **Status:** 📋 **ANALYSIS ONLY — NO APP CODE** (2026-09-05).  
> Owner: *If it’s your project, what would you change so phone users find it easy? Thorough research; compare the codebase with other phone coding apps.*
>
> Grounded in this repo (`EditorScreen.kt` ~2 058 lines, `LanguageRegistry` vs `LanguageType`, Phases 15–28) plus public behavior of Acode, Spck, Pydroid 3, Squircle CE, Termux, Coding C / C4droid, AndroidIDE. Closed-source apps = **visible behavior only**.

---

## 0. One-sentence thesis

CodeC is already a **stronger engine** than most phone editors (real compiler, real PTY, signed packages, Sora-speed typing, CodeC Keys). It is a **weaker first-hour product** than Pydroid / Coding C / Spck because the *easy path* is still “know that this used to be a C IDE, open the right tab, survive mediocre colour and empty suggestions.” Phone ease is not more features — it is **one obvious loop: open → see colour that looks like code → get completions you can tap → RUN → see result.**

---

## 1. How phone coding actually fails (research, not taste)

Cross-source consensus ([r/androidapps](https://www.reddit.com/r/androidapps/comments/1mk15lc/dies_anyone_know_safe_open_source_code_editing/), [bestappsforandroid 2026](https://bestappsforandroid.com/best-code-editor-apps-for-android/), Spck Play reviews, Acode store, Pydroid tutorials):

| Pain | Why phones, not laptops |
|---|---|
| **Soft keyboard steals half the screen** | Every extra toolbar row is a line of code you cannot see. |
| **Thumbs, not Tab/Ctrl** | Completions that need Tab/arrows are dead (you already proved this → Phase 27). |
| **Fat chrome** | 5-tab bars, drawers, overflow menus, Settings novels. |
| **“Install the toolchain” as homework** | Termux/Acode power users accept it; learners bounce. Pydroid/Coding C win because **Run works offline on first tap**. |
| **Colour that doesn’t look like VS Code** | Users judge quality in 3 seconds. Regex crayons read as “toy.” |
| **Suggestions that lie by omission** | 8 snippets is worse than none — you think the IDE is broken. |
| **Context loss** | Rotate, IME open/close, tab switch, git sheet — state must survive. |

Apps that *feel* easy on a phone all protect that loop. Apps that *feel* hard dump a desktop IDE onto 6 inches.

---

## 2. Competitor map (honest, 2026)

| App | Model | Easy on phone? | Run on device? | Colour / complete | What CodeC already beats | What they still do better for *ease* |
|---|---|---|---|---|---|---|
| **Pydroid 3** | One language, yellow ▶ | **Best first 30s** | Yes, batteries included | Decent highlight + complete | We run C *and* python *and* a full userland | Empty editor + one ▶. Pip GUI. Examples folder. No “install userland” speech. |
| **Coding C / C4droid** | One language, TCC | Same as Pydroid for C | Yes, APK compiler | Simple but consistent | We *are* this for C (TCC in APK) **plus** projects/git/term | Zero setup narrative. Symbol bar only. No 5-tab IDE. |
| **Spck** | Web + Git, closed | **Best chrome** | Preview, not general run | TS complete, extra-keys, snippets | We cloned the skin (Phase 15–17). We actually compile. | Colour looks premium. Completions feel “smart.” Clone → edit → preview is one story. Reviews: “Acode is heavier with extensions.” |
| **Acode** | Ace + plugin store, MIT | Easy *after* plugins | Via plugins / Alpine | 100 Ace modes; LSP is a plugin | Native Sora is faster (your 25.1 bench). Real clang/TCC. | **Install more languages / LSP from a store.** First-open colour for many files. Emmet, Prettier, Snippets as taps. |
| **Squircle CE** | Sora editor, Apache | Best *editor* feel | **No run** | TextMate-class via Sora | We run code. We have Keys/ghost. | Themes, pair skip, sticky scroll, no “C IDE” identity. People pick it when they only need to edit. |
| **Termux** | CLI OS | Hard | Yes | nano/vim | We wrap this as Packages + GUI run | Power users; not a phone *editor*. |
| **AndroidIDE** | Full Android IDE, GPL, archived | Hard | Gradle on device | Sora + LSP | Smaller, cleaner | Real Java complete (too heavy for CodeC’s audience). |
| **Replit / GitHub Mobile** | Cloud | Easy if online | Cloud | VS Code-ish | Offline, privacy, real device APIs | Zero install; not an on-device IDE. |

**Position if I owned CodeC:**  
*Pydroid-easy first run + Spck chrome + Acode install-for-power + Sora speed + Termux under the hood.*  
We have the last two. We are halfway on Spck chrome. We are weak on Pydroid-easy and Acode-install-for-brains.

---

## 3. CodeC as it actually is (codebase, not README)

README still says **“A C programming IDE for Android.”** The product is already:

- 12 **run** profiles (`LanguageRegistry`: C, C++, Python, JS, TS, Go, Rust, PHP, Ruby, Lua, Shell, HTML)
- 9 **colour** buckets (`LanguageType`) — Go/Rust/PHP/Ruby/Lua = plain text
- Completions: 8 items, this-file identifiers, ~10 snippets (`CodeCompletionEngine`)
- Editor: Sora 0.24.6, ghost + chip strip, CodeC Keys (default ON), Spck drawer
- Bottom bar: **Projects · Editor · Terminal · Packages · Settings** (5 destinations; Templates/Logs exist off-bar)
- `EditorScreen.kt` ~2k lines, `SettingsScreen.kt` ~1.3k lines
- First C file: **TCC in APK, offline RUN** — this is the Pydroid-class jewel and it is buried under “universal IDE” chrome

**Identity split is the #1 ease bug.** A new user cannot answer “what is this app?” in one sentence. C users get a web/git IDE. Web users get a C compiler. Python users get “install python?” on first RUN. That is Termux energy, not Pydroid energy.

---

## 4. If it were my project — changes, ranked by *phone ease* (not coolness)

I would not add more IDE features until these are true. Order is what I would ship.

### P0 — The 30-second loop (Pydroid / Coding C)

**Change 1. Launch into a working file, not a decision.**  
Today: last file or Projects hub. I’d ship a **first-run card**: three tiles only — *C (works offline)* · *Python* · *HTML preview*. Tap C → `main.c` template already in the buffer, caret in `main`, Keys visible, **RUN ▶** in the thumb zone. No Packages, no userland speech, no “install clang.”

**Change 2. RUN result on the same screen, always.**  
Output panel already exists (Phase 11). On a phone it must **steal the bottom third on first run**, then collapse to a peek — Pydroid’s yellow ▶ + console. Never send a beginner to the Terminal tab to see `hello`.

**Change 3. Kill the “C IDE” vs “universal” lie in chrome.**  
Rename in the user’s face: **CodeC** = “code on your phone.” Language is the *file*, not the app. Bottom bar label “Packages” is Termux. For beginners I’d hide Packages behind Settings until they hit a missing toolchain — then one sheet: “Install Python to run this file? ~X MB” (you already have this gate; make it the *only* way they meet pkg).

### P0 — Colour that looks like code (VS Code, Acode Extra Syntax, Squircle)

**Change 4. Throw away the regex highlighter this week (research: TextMate).**  
If the first `.py` or `.html` looks like notepad, nothing else matters. Ship Sora `language-textmate` + VS Code grammars + Dark+ (see `OSS_REPLACEMENT_RESEARCH.md` §8). Expand colour to **every extension in `LanguageRegistry`**. This is the cheapest “this is a real IDE” signal on a 6″ screen.

### P0 — Suggestions that are worth tapping (Acode LSP / Snippets / Emmet + your Phase 27 UX)

**Change 5. Offline completeness before LSP.**  
`MAX_ITEMS = 8` and 10 snippets is why it “doesn’t give every suggestion.” I’d load **friendly-snippets** (MIT) + **Emmet** for HTML/CSS, keep ghost + chips, let the strip **scroll**, ⌄ for the rest. Phone law stays: Enter = newline.

**Change 6. IntelliSense as a Package card, not a promise.**  
Acode: tap “Acode LSP.” We: tap “Python IntelliSense” → `pkg install pylsp`. C: clangd after they already have clang. **Never** block typing on a missing server. Colour + snippets always work.

### P1 — Screen real estate (the real phone OS)

**Change 7. One chrome row while typing.**  
Spck/Pydroid: hamburger + filename + ▶. You already collapsed to `☰ tabs 🔍 RUN` — good. While Keys are up, **hide the 5-tab bar** (or shrink to a thin handle). IME + Keys + suggestion chips + nav bar + status bar currently stack into a postage stamp of code. Phase 28 Keys made this worse *and* better: better input, less canvas. Compensate by auto-hiding the bottom nav when the editor is focused (Instagram/Reddit pattern).

**Change 8. Suggestion chips must never cover the caret line.**  
You already demoted the floating panel. Guard with a device recipe: caret line always visible above Keys. If ghost + chips + Keys fight, **chips replace the Keys row** (Phase 28.3 was this idea). I’d do that next for phone ease — one row of meaning, not two.

**Change 9. Settings is a novel; fold it.**  
1283 lines of Settings is a desktop IDE. Phone: **Editor / Run / Appearance / Git** four lists. Developer options stay easter-egg. Default Keys ON was right; default word wrap ON for HTML/MD, OFF for C.

### P1 — “I just want to make a thing” (Spck / Pydroid examples)

**Change 10. Templates as the home of beginners, not a hidden screen.**  
`TemplatesScreen` exists off the main bar. I’d put **3 starter projects on the Projects empty state**: Hello C, Hello Python, Hello HTML (live preview). One tap clones into a project and RUNs. Coding C / Pydroid live on examples.

**Change 11. Errors are tappable or they don’t exist.**  
Squiggles + “line 12” in the output panel must **jump and pulse the line**. If clang prints `source_123.c` internally, the user still sees `main.c:12`. You already parse diagnostics — the phone ease is the *jump*, not the parser.

**Change 12. Git is a sheet, not a lifestyle.**  
Spck wins because clone URL → files. You have that `+` sheet. First-run should not mention tokens. Push failure already honest (Phase 17) — keep it. Hide Switch Branch until there is a `.git`.

### P2 — Power without homework (Acode store, our Packages)

**Change 13. Packages hub as “Add language power,” not a Linux distro.**  
Today the catalog reads as Termux (`nano`, `ripgrep`, `tmux`). Fine for power users. For ease: **two sections** — *Languages & IntelliSense* (python, node, clangd, pylsp) on top; *Unix tools* collapsed. Same `pkg` underneath.

**Change 14. Don’t build an Ace plugin runtime.**  
Acode’s store is JS because Ace is JS. Ours is Gradle + `pkg`. Replicate **tap to install**, not Cordova plugins.

### P2 — Identity, docs, store listing

**Change 15. Rewrite the first sentence everywhere.**  
README, Play/GitHub description, in-app About:  
*“CodeC — write and run C, Python, JavaScript, and HTML on your phone. C works offline with no setup.”*  
Not “A C programming IDE” and not “Mini-Termux.”

---

## 5. What I would *not* change (already phone-correct)

Stealing these would make it *harder*:

- **Sora core** (25.1 numbers). Never go back to Compose `BasicTextField` or Monaco/WebView.
- **Ghost + chips + Enter-is-Enter** (Phase 27). Desktop popup is the Acode/Sora default and it fails on thumbs.
- **CodeC Keys** (Phase 28) as default ON with IME escape. Unexpected-Keyboard density without leaving the app.
- **TCC in the APK** for `.c`. This is the Pydroid trick. Never gate Hello World on clang.
- **Autosave, last-file launch, RUN = HTML preview** (Phase 16).
- **Clean-room vs Termux GPL.** Keep our emulator.
- **No Copilot in the critical path.** Completeness ≠ cloud AI. Snippets + LSP beat a chatbot for phone typing.

---

## 6. Side-by-side: first session (what I’d optimize)

| Minute | Pydroid | Spck | Acode | **CodeC today** | **CodeC if I owned it** |
|---|---|---|---|---|---|
| 0:00 | Editor + empty `main` | Projects / last | File picker + Ace | Projects or last file; 5 tabs | Language tile → buffer ready |
| 0:20 | Type, colour OK | Colour premium, extra keys | Colour OK for 100 langs | Colour cheap; TS/Go plain | TextMate Dark+ |
| 0:40 | Completions for `pr` → print | TS complete / snippets | Need plugin for LSP | 8 snippets, miss stdlib | Snippets+Emmet in strip; scroll |
| 1:00 | Yellow ▶, output below | Preview HTML | Preview plugin / terminal | RUN ▶ works for C; Python asks install | C: same. Python: one sheet then RUN. Output peek. |
| 5:00 | pip, examples | Git clone, preview | Plugin store | Packages looks like Linux | “Add Python IntelliSense” card |

---

## 7. Effort vs ease (what I’d schedule)

| # | Change | Ease gain | Cost | Depends on owner saying |
|---|---|---|---|---|
| 4 | TextMate colour | Huge, instant visual | Medium (Sora module + assets) | Start Phase 29 |
| 5 | friendly-snippets + Emmet | Huge for “missing suggestions” | Small | Phase 30 |
| 7–8 | Hide nav / chips replace keys row | Huge canvas | Small–medium | After 28.x |
| 1–3 | First-run tiles + hide Packages | Huge for new users | Medium (product, not engine) | Explicit UX phase |
| 6 / 13 | LSP as Package cards | Huge for C/Python/JS *after* install | Medium–large | Phase 31 |
| 10 | Empty-state templates | Medium | Small | Anytime |
| 15 | Copy/identity | Medium, free | Docs only | Anytime |
| 11 | Tap diagnostic → line | Medium | Small | Bug-fix size |

I would **not** start rust-analyzer, a VS Code marketplace, X11, or Monaco. Those are ego features. Phone ease is colour, complete, canvas, first RUN.

---

## 8. Risks if we “just add Acode plugins”

- Ace/WebView would **regress** 25.1 (400 ms vs 15 ms keystrokes). Forbidden.
- Bundling clangd in the APK explodes size; Pydroid can do it for one language, we cannot for twelve.
- Showing every LSP diagnostic as a desktop lightbulb fight Keys. Merge into squiggles + chip “fix.”
- 5-tab Spck clone plus Keys plus chips **without** hiding chrome = less code visible than Acode.

---

## 9. Bottom line

If this were my project I would stop adding IDE surface area and spend the next phases on **making the phone loop boringly obvious**:

1. **Looks like VS Code** (TextMate, not regex).  
2. **Suggests like VS Code, accepts like a phone** (snippets/Emmet/LSP → ghost + chips).  
3. **Runs like Pydroid** (C already does; first-run must *show* that).  
4. **Installs like Acode** (Packages cards for brains, not a distro front page).  
5. **Uses the screen like Instagram** (hide the tab bar while typing).

The engines for 1–4 are listed in `docs/OSS_REPLACEMENT_RESEARCH.md`. This file is the **product** order: ease first, marketplace second.

Nothing here starts until you say **Start Phase N**. Specs: Phases **29–33** in `docs/chat-phase29/` … `docs/chat-phase33/` (planned 2026-09-05). Recommended first: **Start Phase 29**.
