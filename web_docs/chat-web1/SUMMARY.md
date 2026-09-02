# chat-web1 — W0 planning session (2026-09-02)

## What the owner asked

> "I want to create a website for this project like Termux have. And strict
> rule you will not write any code. You have to create a new folder
> `web_docs` and `web_prompt.md`. You will see there are already folders like
> `docs` where everything is stored as history or prompt, and `prompt.md` is a
> self-described file. You also have to create them — but for web."

## Constraints honoured

1. **Strict: no code of any kind** — no HTML/CSS/JS, no scaffolding, no
   website files. This session created **markdown only**.
2. Mirror the app project's documentation conventions:
   - `docs/` (history + planning) → **`web_docs/`**
   - `prompt.md` (self-distrust handoff) → **`web_prompt.md`**
   - `rule.md` → **shared, unchanged** (website inherits it)
3. Model the website on the Termux site's **public structure** (clean-room:
   structure yes, source never — `rule.md` §6).

## Research done

- `README.md` — app features, 4 compiler engines, 25+ signed packages,
  GitHub-only install paths, in-app updater, troubleshooting content.
- `rule.md` — operating manual (branching, merge gate, invariants, docs
  policy, CI policy) — applies to the website workstream too.
- `prompt.md` — the handoff format to mirror for the web workstream.
- `docs/` — living docs (`JOURNEY.md`, `NEXT_STEPS.md`, …) + `chat-phaseN/`
  session records — the pattern `web_docs/` mirrors.
- termux.dev — landing (one-sentence pitch, install CTAs, feature callout
  grid), docs-style subpages (install/getting-started/FAQ), plain and fast.
  Noted differences: Termux dual F-Droid/GitHub CTAs (CodeC = GitHub only),
  much larger package universe (CodeC = 25+, honest scope note).

## What was created

| File | Purpose |
|---|---|
| `web_prompt.md` (repo root) | Website handoff — paste as first message of a new chat |
| `web_docs/README.md` | Folder index + app↔web file map + ground rules |
| `web_docs/WEBSITE_PLAN.md` | Master spec: 7 pages with content outlines, design, stack, layout, deploy, phases W1–W5, acceptance criteria, open questions |
| `web_docs/DECISIONS.md` | D1–D9 + open items O1–O5 |
| `web_docs/NEXT_STEPS.md` | Head state (W0 complete, zero code) + owner command table |
| `web_docs/WEB_JOURNEY.md` | Narrative timeline (entry W0) |
| `web_docs/chat-web1/SUMMARY.md` | This record |

## Evidence

- All seven files are new markdown; `git status` shows nothing else
  modified; no file outside `web_docs/` + `web_prompt.md` was touched.
- No `website/` folder exists. No app code, `docs/` file, or workflow file
  was changed.

## Open owner questions (carried to `WEBSITE_PLAN.md` §11 / `DECISIONS.md`)

O1 screenshots · O2 domain · O3 copy tone · O4 phase order · O5 in-app link.

## Follow-up (2026-09-02, same session) — learning wing + self-dependent

**Owner added two commands (strict no-code rule unchanged):**

> "I want it to be learning page also see this website
> https://pabitra27706-oss.github.io/Termux-Mastery/ — Make it fully proper
> self dependent"

**What changed (plan v2, no code):**

1. **Learning wing added (D10):** the site now has a course —
   **"Master CodeC from Zero to Advanced"** — modelled on the owner's own
   Termux-Mastery (same owner → structure freely mirrored; content written
   fresh for CodeC from CodeC's docs): `/learn` course home + 17 proposed
   chapters `ch-01…ch-17`, fixed self-contained chapter template (goals →
   steps → try-it → common mistakes → prev/next), hands-on projects
   chapter, troubleshooting chapter. CodeC twists: chapter 04 (4 compiler
   engines), chapter 13 (CodeCApi device APIs — CodeC's Termux-API answer),
   chapter 14 (web projects + live preview).
2. **Fully self-dependent = law (D11, plan §5):** zero fetched external
   resources (no CDN, no external fonts/JS/CSS/images, no analytics; system
   font stack); outbound hyperlinks allowed; every page renders fully
   offline; the course is completable without ever opening the repo.
   Verified in W6: grep sweep (no external `src`/`<link>`/`@import`/`url()`),
   offline render check, full link sweep.
3. **Phases restructured (D12):** W1 scaffold+Home → W2 Install+Start →
   W3 Engines+Packages+FAQ+About → W4 course home+ch 01–06 → W5 ch 07–12 →
   W6 ch 13–17+polish+deploy+verification. 25 pages total at the end.
4. **Open items added:** O6 (chapter set confirmation, locked at W4) and
   O7 (course content license — repo default vs MIT).
5. **Living docs updated:** `WEBSITE_PLAN.md` → v2, `DECISIONS.md` D10–D12 +
   O6/O7, `NEXT_STEPS.md` head state v2 + phase queue, `WEB_JOURNEY.md`
   W0.1, `web_prompt.md` (facts + phases).

**Evidence:** still markdown-only; no `website/` folder; no app code,
`docs/` file, or workflow touched.

## Next step

**W0 (+W0.1) is closed.** The agent waits for the owner's implementation
command — "Build the website" / "start W1" — then begins W1 per
`WEBSITE_PLAN.md` v2 §9 (scaffold `website/` + Home with learning banner;
self-dependent rule in force from the first file).
