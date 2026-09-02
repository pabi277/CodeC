# DECISIONS.md — website decision log

Numbered, dated, never deleted. Reversing a decision = a new entry that
references the old one. "Owner" means the repo owner in chat; "Agent" means
proposed by the agent and adopted without objection.

| # | Date | Decision | By |
|---|---|---|---|
| D1 | 2026-09-02 | The website is a **separate workstream in the same repo**: own docs folder (`web_docs/`), own handoff file (`web_prompt.md`), own implementation folder (`website/` when it exists). Website work never modifies app code or `docs/`. The shared `rule.md` (branching, merge gate, invariants, docs policy) applies to both workstreams. | Owner + Agent |
| D2 | 2026-09-02 | **Planning phase (W0) = no code of any kind.** Owner's strict rule for this session: no HTML/CSS/JS, no scaffolding — only markdown planning in `web_docs/` + `web_prompt.md`. Website code is written only after the owner commands implementation. | Owner (strict rule) |
| D3 | 2026-09-02 | **Stack locked: plain static HTML + CSS on GitHub Pages** — no framework, no build step, no backend, no CMS. Alternatives rejected: Docusaurus/MkDocs/Hugo (overweight for 7 pages, adds a build the owner must maintain). Reopening requires the owner's explicit command. | Agent (proposed per Termux-structure goal; owner has not objected) |
| D4 | 2026-09-02 | **Page structure = 7 pages:** Home, Install, Getting Started, Engines, Packages, FAQ, About (detail in `WEBSITE_PLAN.md` §3). Modelled on the Termux site's public structure (landing + install + getting-started + FAQ), adjusted for CodeC's reality (GitHub-only distribution, 25+ packages, 4 compiler engines). | Agent (proposed) |
| D5 | 2026-09-02 | **Content source of truth = the repo itself** (`README.md` first, then `docs/TROUBLESHOOTING.md`, `docs/JOURNEY.md`). The site states nothing the repo files don't support; no rotting links (no artifact URLs, no run IDs); roadmap items labelled as roadmap. | Agent (proposed) |
| D6 | 2026-09-02 | **Clean-room boundary:** the Termux site is a structural reference only (page model, layout patterns from its public appearance). Its source is never copied; CodeC content is distilled from CodeC's own docs. | Agent (per the clean-room law in `rule.md` §6) |
| D7 | 2026-09-02 | **Site lives in a new top-level `website/` folder** (created in W1), served by GitHub Pages. The only cross-workstream touch allowed anywhere in W1–W5 is a one-line link in the root README (W5) and the Pages workflow file (W5). | Agent (proposed) |
| D8 | 2026-09-02 | **Merge gate applies unchanged:** no PR/merge for website work without the owner's literal command in chat; session-branch discipline per `rule.md` §2–3. | Owner (standing rule) |
| D9 | 2026-09-02 | **Distribution facts are law for copy:** GitHub-only distribution (Actions artifact, Releases, in-app updater); no Play Store/F-Droid implication; single CTA style ("Get the APK on GitHub"). | Agent (from README facts) |

## Open (pending owner)

- O1 — Screenshots on Home: wanted or not? (Who supplies the images.)
- O2 — Custom domain vs. GitHub Pages URL.
- O3 — Copy tone (technical-direct vs. friendly-casual).
- O4 — Phase order W1→W5 or re-prioritized.
- O5 — In-app link to the site (app workstream, separate command).

Open items map 1:1 to `WEBSITE_PLAN.md` §11.
