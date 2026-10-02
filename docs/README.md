# CodeC — docs map

Everything documented about CodeC lives under this folder, sorted into seven
top-level areas. **If you are looking for one phase, use the
[phase tracker](phases/README.md).**

| Area | What is inside |
|---|---|
| [getting-started/](getting-started/) | How to work on this repo: the phase ceremony and the current next steps |
| [guides/](guides/) | Owner/user-facing guides: beta notes, privacy, troubleshooting, release notes, upload key |
| [journal/](journal/) | The story: full journey log, idea backlog, owner handoffs and reviews |
| [roadmaps/](roadmaps/) | Per-series roadmaps and plans, including the [staged AI integration roadmap](roadmaps/AI_INTEGRATION_ROADMAP.md) and its [level specs](roadmaps/ai-integration/) |
| [research/](research/) | Research dossiers behind the roadmaps, including the [AI integration repository map and OSS/runtime research](research/AI_INTEGRATION_RESEARCH_20260930.md), plus UX/OSS dossiers and mockups |
| [phases/](phases/) | **Phases through 82 / 82B**, grouped into 13 topics — see the [phase tracker](phases/README.md) |
| [reference/](reference/) | External reference material (Spck Editor screenshots) |
| [brand/](brand/) | Brand assets — the app icon masters (load-bearing for the icon pipeline) |

## Where do new docs go?

- **New phase** → `docs/phases/<category>/chat-phaseNN/` — the ceremony is in
  [getting-started/HOW_TO_CREATE_A_PHASE.md](getting-started/HOW_TO_CREATE_A_PHASE.md).
- **New roadmap** → `docs/roadmaps/`; **new research** → `docs/research/`.
- **Owner decision / journey entry** → `docs/journal/`.
- Keep every phase's docs inside its own `chat-phaseNN/` folder, and link
  between folders with relative paths.

## Conventions kept from before

- Phase folders are still named `chat-phaseNN`, so old links, habits and greps
  keep working — they simply live under a topic folder now
  (e.g. `docs/phases/03-editor/chat-phase27/`).
- `PART_NN_x_<SLUG>.md` = one spec per part, `README.md` = the phase record,
  `DEVICE_ROUND.md` = the owner's handset checklist.
