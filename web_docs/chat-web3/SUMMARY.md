# chat-web3 — W2 build session: Install + Getting Started (2026-09-02)

## What the owner asked

> "Start w2"

Executed strictly per `web_docs/web-phase2/` (README + PART_2_1 + PART_2_2)
on session branch `arena/01a062f7-codec`, inside the W1 chrome.

## What was created (2 files — the phase's exact scope)

| File | Content |
|---|---|
| `website/install.html` | H1 "Get the CodeC APK." → 3 numbered path cards (Actions artifact / Release / in-app updater, README order) → first-install note (Install unknown apps) → device-support table → optional Termux engine section → links block |
| `website/start.html` | H1 "Your first hour with CodeC." → 5 steps: first compile (RUN ▶) → terminal loop (`cc` → `./`, the `./` rule) → scanf-in-Term rule → 1-tap package install → web preview — then the bottom-bar map table + cross-links strip |

Both pages: chrome **byte-identical** to `index.html` (verified by diff,
modulo `aria-current="page"` moved to Install / Start), zero new CSS (W2
consumes W1.1 components only), zero JavaScript. Meta titles per PART specs.

## Traceability — every section → source line (README @ main, 2026-09-02)

### install.html

| Section | Claim | README source |
|---|---|---|
| Intro | distributed from GitHub only; no store | standing rule D9 + README has only GitHub paths |
| Path 1 | green `Build APK` run → Artifacts → `CodeC-IDE` | "Install the APK from GitHub" steps 1–2 |
| Path 2 | Releases page, APK attached to the release | same, step 3 + "Direct releases page: …" |
| Path 3 | Settings → Install APK from GitHub downloads latest release APK, opens installer | "In the app: **Settings → Install APK from GitHub**…" |
| First install | allow Install unknown apps for the browser, once | "On your phone: download the APK → allow **Install unknown apps** → install" |
| Device table | arm64 best; TCC arm64-v8a + x86_64; Clang module arm64-only (x86 emulators covered by TCC); 32-bit → Termux engine | "embedded in the APK for **arm64-v8a** and **x86_64**"; "must be **arm64**; an x86 emulator can't run it — but the built-in TCC covers x86_64 emulators automatically"; Troubleshooting "Exec format error" (32-bit → Termux) |
| Termux engine | optional; full C11/C17 via Clang + blocked-toolchain fallback; Termux 0.109+ from F-Droid or GitHub (Play Store outdated); exact 3-line block; "Run commands in Termux environment" permission path; CHECK BRIDGE | "To use the optional Termux engine…" block (command block diffed — **matches exactly**), permission line, "Settings → Compiler Engine → 'CHECK BRIDGE'" |

### start.html

| Section | Claim | README source |
|---|---|---|
| Step 1 | create hello.c, RUN ▶, output under editor; built-in TCC, static executable, instant, offline | "Install the APK, open the editor, tap **RUN**. That's it." + built-in compiler paragraph |
| Step 2 | Term tab or toolbar icon; one command per line; `cc hello.c -o a.out` then `./a.out`; `./` because cwd not on PATH; app-private storage makes `./a.out` executable | "In-app terminal & Package Manager" items 1–3 (commands verbatim) |
| Step 3 | scanf/getchar must run in Term, not RUN (no keyboard into the process); RUN on scanf hits 10 s cap (exit 124) = waiting for input | "Programs that use `scanf`/`getchar` must run in **Term**, not the editor RUN button (RUN has no keyboard into the process)" + Troubleshooting "Install or compile hangs" |
| Step 4 | Packages tab: 1-tap INSTALL/RUN, badges `INSTALLED ✓`/`AVAILABLE`; 25+ packages incl. git/python/clang/nano/make/ripgrep/tmux | "Package & Command Hub (Packages tab)" + "provides 25+ packages including…" |
| Step 5 | RUN on HTML **is** the preview; loopback server over project folder; relative CSS/JS, fetch, ES modules; live reload on save; console under page | "Web preview" bullet in "Editor, Projects & Web preview" |
| Bottom-bar map | Projects (hub + `+` sheet) · Editor · Terminal (middle) · Packages · Settings | "Bottom bar: Projects · Editor · **Terminal (middle)** · Packages · Settings" + Projects Hub / editor bullets |
| Cross-links | /learn, ch-01, /engines, /faq | final URLs from day one (phase README ground rule) |

## Verification evidence

- **Chrome byte-identity:** `diff` of header/footer blocks vs `index.html`
  (normalising only `aria-current`) — IDENTICAL on both pages. ✔
- **§5.5 self-dependent sweep (all pages): PASS** — zero external
  `src=`/`<link href=`/`@import`/`url(`; favicon `xmlns` remains the one
  declared non-fetched exception.
- **External href inventory (both pages):** only `github.com/…` (repo,
  releases, actions, issues, README, JOURNEY) + the **two README-mandated
  Termux links** (f-droid.org/packages/com.termux,
  github.com/termux/termux-app/releases) — exactly what plan §5.2 allows.
  curl: repo/actions/termux-GitHub = 200; **f-droid.org = 000 from the
  sandbox** (connection blocked, same as pabi277.github.io earlier) —
  sandbox network limitation, not a dead link; re-check in the W6 link
  sweep (CI/owner browser).
- **Command blocks:** Termux setup block diffed vs README — **exact
  match**; terminal commands (`cc hello.c -o a.out`, `./a.out`) verbatim.
- **HTML parse:** both pages clean (no mismatched/unclosed tags).
- **Served:** `/install.html` 200, `/start.html` 200.
- **Still-404 pages (expected):** engines, packages, faq, about, learn,
  ch-01 (built in W3/W4).

## Notes

1. scanf example is standard C (ANSI/C89-safe: `%63s` width, `return 0`)
   — course-level TCC-safe law applies from W4, applied here already.
2. The ch-14 "deep dive" link in PART_2_2's step 5 design landed in the
   cross-links strategy as `learn.html` + `ch-01.html` (both unbuilt →
   404 until W4, expected); ch-14.html gets its link from ch-14 itself
   (W6) — recorded here so the W6 sweep expects it.
3. No store mention anywhere on install.html (D9) — verified by grep
   ("Play Store" appears only inside the Termux paragraph as the README's
   own warning that the *Termux* Play Store build is outdated — quoted
   fact about Termux, not a CodeC store implication; flagged for the
   owner to veto if they'd rather drop it).

## Exit conditions

**W2.1:** 1. order + 360/1440 ✔ (structural; visual via preview) · 2.
command blocks match README (diff above) ✔ · 3. fresh-arm64 trace: page
alone gets a reader to a downloaded APK (Releases/Actions links + unknown-
apps note) ✔ · 4. no CodeC store mention; sweep PASS ✔.
**W2.2:** 1. five steps in continuous order; blocks verbatim ✔ · 2. `./`
rule + scanf-in-Term rule both explicit ✔ · 3. cross-links use final URLs
✔ · 4. sweep PASS; source lines recorded (this file) ✔.

**→ W2 COMPLETE.** Living docs updated in the same commit; push + CI +
report; stopped at the merge gate.

## Next step

Owner: **"Start W3"** → engines + packages + faq + about per
`web_docs/web-phase3/` (packages page pulls the authoritative list from
`codec-packages/` build config, sha recorded).
