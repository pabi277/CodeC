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

---

*Next entry: W1 (scaffold + Home) — after the owner's implementation command.*
