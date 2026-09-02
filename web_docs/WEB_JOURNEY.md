# WEB_JOURNEY.md — narrative timeline of the CodeC website

Numbered entries, newest last. Append-only — never rewrite an entry, add a
correction entry instead. This is the website's `docs/JOURNEY.md`.

---

## W0 — Planning (2026-09-02)

**1. Owner asks for a website (2026-09-02).** The owner wants a website for
CodeC "like Termux have" (the termux.dev model: landing + install +
getting-started + FAQ). With a **strict rule: no code of any kind** — the
project must start the same way the app did, with its history & planning
structure first: a `web_docs/` folder (the website's `docs/`) and
`web_prompt.md` (the website's `prompt.md`, the self-distrust handoff file).

**2. Research (2026-09-02).** The agent read `README.md` (features, engines,
packages, install paths, troubleshooting), `rule.md` (operating manual:
branching, merge gate, invariants, docs policy), `prompt.md` (handoff format
to mirror), the `docs/` structure (living docs + `chat-phaseN/` session
records), and the public structure of termux.dev (landing page with install
CTAs + feature callouts; docs-style subpages).

**3. Plan written (2026-09-02).** `web_docs/WEBSITE_PLAN.md` created: 7-page
structure (Home, Install, Start, Engines, Packages, FAQ, About), per-page
content outlines with repo sources, design direction (mobile-first, dark,
terminal accents), locked static stack (HTML/CSS, GitHub Pages, no
framework/build/backend), `website/` folder layout, deployment approach,
implementation phases W1–W5 with exit conditions, acceptance criteria, and
open owner questions O1–O5.

**4. Living docs + handoff created (2026-09-02).** `web_docs/README.md`
(index & file map), `web_docs/DECISIONS.md` (D1–D9 + open O1–O5),
`web_docs/NEXT_STEPS.md` (head state + owner command table),
`web_docs/WEB_JOURNEY.md` (this file), `web_docs/chat-web1/SUMMARY.md`
(session record), and root `web_prompt.md` (paste-this-into-the-next-chat
handoff).

**5. W0 closed (2026-09-02).** Planning complete. **No code written** —
owner's strict rule honoured: the only new files are markdown. The website
now waits for the owner's implementation command ("Build the website" /
"start W1").

## W0.1 — Learning wing + self-dependent requirement (2026-09-02)

**6. Owner expands the scope (2026-09-02).** Same session, same strict no-code
rule. Two new owner commands:
1. **"I want it to be learning page also"** — the site must include a
   learning course, with the owner's own Termux-Mastery site
   (`pabitra27706-oss.github.io/Termux-Mastery`) as the structural
   reference: "Master Termux from zero to advanced", 15 numbered chapters,
   read-like-a-book, hands-on projects, course structure table, disclaimer,
   MIT license.
2. **"Make it fully proper self dependent"** — the site must stand alone:
   no external dependencies of any kind.

**7. Reference study (2026-09-02).** The agent fetched and studied the
public structure of Termux-Mastery: course home (about / why learn / how to
use / 15-chapter table / getting started / contributions / roadmap /
disclaimer / license) + numbered chapter folders. Since it is the owner's
own project, mirroring its structure is clean-room-safe; its content will
not be reused (the chapters are written fresh for CodeC from CodeC's own
docs).

**8. Plan v2 written (2026-09-02).** `WEBSITE_PLAN.md` rewritten to v2:
- **Two wings:** product (7 pages, unchanged in intent) + learning wing —
  `/learn` course home + **17 proposed chapters** (`ch-01` Getting Started
  → `ch-17` Troubleshooting), each on a fixed self-contained template
  (goals → steps → try-it → common mistakes → prev/next). CodeC-specific
  twists: chapter 04 (4 compiler engines), chapter 13 (CodeCApi = the
  CodeC answer to Termux API), chapter 14 (web projects + live preview).
  Chapters teach only what CodeC ships today; final set locked at W4.
- **Self-dependent = law (plan §5):** zero fetched external resources (no
  CDN/fonts/JS/images from outside, system font stack), outbound links
  fine, offline-complete rendering, course completable without the repo;
  W6 verifies via grep sweep + offline render + link sweep.
- **Phases restructured W1→W6** (D12): product wing first (W1–W3), course
  second (W4–W6 content), deploy + verification last (W6).
- Decision log: **D10** (learning wing), **D11** (self-dependent), **D12**
  (phases); open items **O6** (chapter set) and **O7** (course license)
  added.
- `web_prompt.md` updated (facts + phase list), `NEXT_STEPS.md` head state
  v2 + new phase queue.

**9. W0.1 closed (2026-09-02).** Planning v2 complete. Still **zero code** —
the strict rule stands. Waiting for the owner's implementation command.

---

*Next entry: W1 (scaffold + Home) — after the owner's implementation command.*
