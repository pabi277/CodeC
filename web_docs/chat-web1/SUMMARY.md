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

## Next step

**W0 is closed.** The agent waits for the owner's implementation command —
"Build the website" / "start W1" — then begins W1 per
`WEBSITE_PLAN.md` §9 (scaffold `website/` + Home page).
