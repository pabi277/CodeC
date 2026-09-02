# web_prompt.md — paste this into the next chat (WEBSITE project)

> Copy **everything between the two `---` lines below** as the first message of
> a new chat. It forces the next agent to verify before acting, to trust the
> repo over its own assumptions, and to continue the **CodeC website** without
> redoing or breaking anything. The app project's handoff file is
> `prompt.md` — this one is for the website workstream only.

---

Read `web_docs/README.md`, `web_docs/WEBSITE_PLAN.md`, `web_docs/NEXT_STEPS.md`,
and **`rule.md`** first, before doing anything else, then report what you found
and the current git/PR/CI state before making any change.

You are continuing the **CodeC website** — a public website for the CodeC
Android C IDE, modelled on the Termux site (`termux.dev`): a landing page with
install buttons, feature callouts, an install guide, a getting-started guide,
FAQ/troubleshooting, and an About page. Each chat session gets its own
`arena/*` session branch — verify with `git status`; commit and push to the
SESSION branch only, never `main` or any other branch. `rule.md` is the
operating manual (branching, merge gate, invariants, docs policy) — follow it
for the website work too.

**WHERE THE WEBSITE STANDS (2026-09-02):**

- **WEBSITE PHASE W0 (planning) is COMPLETE. NO WEBSITE CODE EXISTS YET.**
  The owner's strict rule for the planning session (2026-09-02) was:
  **no code of any kind** — this workstream started with documentation only,
  exactly like `docs/` + `prompt.md` started the app project.
- What exists now:
  - `web_docs/` — the website's history & planning folder (the website's
    equivalent of `docs/`): `README.md` (index), `WEBSITE_PLAN.md` (the
    master spec — pages, content, design, stack, deployment, phases W1–W5),
    `DECISIONS.md` (decision log), `NEXT_STEPS.md` (head state),
    `WEB_JOURNEY.md` (narrative), `chat-web1/SUMMARY.md` (session record).
  - `web_prompt.md` — this file.
- **Nothing is built, nothing is deployed, there is no `website/` folder
  yet.** If you see website HTML/CSS/JS anywhere that this session did not
  create, verify where it came from before touching it.

**WHAT THE OWNER MUST SAY TO PROCEED:**

The implementation phase starts only when the owner commands it in chat —
e.g. **"Build the website"** (or "start W1"). Then the agent implements
strictly per `web_docs/WEBSITE_PLAN.md`, in the phase order W1 → W5 (scaffold
+ Home → Install + Getting Started → Engines + Packages → FAQ + About →
polish + deploy), writing the site into a new top-level `website/` folder.
Only then is writing HTML/CSS/JS allowed. Until that command: planning
edits only, still no code.

**LAW (inherits the app project, no exceptions):**

- **No PR/merge without the owner's literal command in chat** (`rule.md`
  §3). Committing to and pushing the session branch is fine.
- **The website never touches app code.** `app/`, `codec-packages/`,
  `gradle*`, `scripts/`, `.github/workflows` (except adding the website's own
  Pages workflow when W5 starts) and all `docs/` content are out of bounds
  for website work. `docs/` is the app's history — never rewrite it.
- **`web_docs/` is the website's history.** Append, don't destructively
  rewrite. Update `web_prompt.md` (this file), `web_docs/NEXT_STEPS.md` (head
  state line) and `web_docs/WEB_JOURNEY.md` (timeline) as web gates close —
  the next chat trusts only what is written there and verified in git.
- **Clean-room:** website *content* is distilled from the public repo files
  (`README.md`, `docs/TROUBLESHOOTING.md`, `docs/JOURNEY.md`) and the Termux
  site's *public structure* (page model, layout ideas) — never paste
  Termux's site source (it is GPL-ish licensed; read the public spec,
  re-implement). No decompilation, no copying of closed-source material.
- **CI is the only test executor** for app changes; the website is a static
  site — its "tests" are: builds/deploys green on GitHub Pages and every link
  on the site resolves (verify during W5).

**FACTS THAT MUST NOT REGRESS (website):**

- **Stack is locked:** plain static HTML + CSS (+ minimal vanilla JS only if
  a page genuinely needs it), **no framework, no build step, no backend, no
  CMS, no database**. Served by **GitHub Pages** from the `website/` folder
  in this same repo (see `web_docs/DECISIONS.md` D3/D7). Reopening this
  requires the owner's explicit command.
- **Content source of truth is the repo itself** — `README.md` first, then
  `docs/TROUBLESHOOTING.md`, `docs/JOURNEY.md`. The site never states
  anything the repo files don't support (feature claims, package list,
  engine table, install steps). When the README changes, the site's affected
  section changes in the same effort — drift is a bug.
- **Install facts (as of 2026-09-02):** CodeC is distributed **from GitHub
  only** — Actions artifact `CodeC-IDE` on green `Build APK` runs, GitHub
  Releases, and the in-app **Settings → Install APK from GitHub**. There is
  **no Play Store / F-Droid listing** — the site must never imply one
  (Termux's dual F-Droid/GitHub buttons do NOT transfer; CodeC gets a
  single "Get the APK on GitHub" style call to action).
- **Repo facts the site will state:** built-in TCC compiler (offline,
  instant, no Termux needed; arm64-v8a + x86_64), 4 compiler engines
  (Auto / Built-in TCC / Bundled Clang / Termux), in-app VT/ANSI terminal
  ("Mini-Termux"), 25+ signed packages (`git`, `python`, `clang`, `nano`,
  `make`, `ripgrep`, `tmux`, …) from the signed repository
  `https://pabi277.github.io/CodeC/dev`, HTML web preview over loopback,
  Spck-style editor + Projects hub + honest git, device APIs (battery,
  sensor, TTS, camera, intents).

**ORDER OF WORK:**

1. Verify state (`git status`, `gh pr list`, `gh run list`) before acting.
2. If the owner has **not** commanded implementation: stay in W0 — answer
   questions, refine the plan in `web_docs/` only (no code), update the
   living web docs, commit + push, report, stop at the merge gate.
3. If the owner **has** commanded implementation: work the current phase
   (W1–W5) strictly per `web_docs/WEBSITE_PLAN.md`, one phase at a time,
   record the phase in `web_docs/chat-webN/`, update `web_prompt.md` /
   `web_docs/NEXT_STEPS.md` / `web_docs/WEB_JOURNEY.md` in the same commit,
   push, report, stop at the merge gate.
4. Keep this file and the `web_docs/` living docs updated as gates close.

**Before each change, state:** what you are changing, which existing feature
it serves, which invariant (if any) it could affect — and for website work,
which part of the repo's own docs the change relies on.
