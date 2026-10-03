# Level 10 — Agent controls and options

**Status: ✅ AUTHORIZED 2026-10-03 and 📋 PLANNED as [Phase 87](../../phases/03-editor/chat-phase87/README.md). No production or test source written yet. Depends on merged Level 9 (PR #111, `main` @ `6838ea6`).**
**Owner decisions (2026-10-03, answered in chat):** **all nine** controls ship in Phase 87 · the controls live in the **`AiHome` AI panel**, not the app Settings screen (the spec's *Touches* line names `SettingsManager.kt`; this repository keeps non-secret AI options in `AiKeyStore`'s property bag and renders them in `AiHome`) · the **backup provider is manual offer only** · **answer detail defaults to `normal`**, i.e. today's wording, so an untouched install does not change behaviour on upgrade.
**Flagged, not silently decided:** the read-window default. This spec proposes 150; today's effective default is **400** (`AiTools.kt:321,348`). Phase 87 ships 400 to stay behaviour-preserving, consistent with the `normal` answer-detail default, and records 150 as a one-constant change the owner can ask for. See *Open decision 1* in the Phase 87 README.
**Shared foundation:** [defect register, security rules S1–S12, sources](00_AGENTIC_MAP_AND_SECURITY_RULES.md).

## User value

The owner's *"more options to use in the app for better optimization."* Nine bounded controls, each
defaulting to the safe value, each stored beside the existing non-secret AI settings.

## The one rule that governs this whole level

**S9 — options tune within caps; they never raise a permission.**

Every range below has a hard ceiling declared in pure policy and asserted by a host test. No control can
widen D1/D5 tool, write, run, or path permissions. An option that could grant access is not an option; it
is a permission change, and it does not belong here.

## The option set

| Option | Range | Default | Notes |
|---|---|---|---|
| Read window (lines per read) | 50–400 | 150 | SWE-agent's optimum is 100; 400 is the existing `MAX_READ_LINES` ceiling. |
| Working-set depth (files held) | 2–8 | 4 | `KEEP_LAST_RESULTS = 4` is already near SWE-agent's best (last-5 18.0 % vs full history 15.0 %). |
| Task memory | on / off + **Clear now** | on | **S4/S5**. Off falls back to today's in-memory behaviour. |
| Answer detail | brief / normal / thorough | normal | Replaces the blanket *"Keep answers short"* (defect 11). |
| Tool activity | collapsed / expanded | collapsed | Presentation only — disclosure stays reachable (**S8**). |
| Request inspection | always on | on | **Not user-removable.** D4. |
| Backup provider | configured / manual switch | off | Manual tap only (**S8**). Never automatic. |
| Read-only reviewer | off / on | off | Read-only, separately triggered. |
| Budget extension | offer at cap | offer | Read-only extension; run count and approvals never increase. |

## Two options that need an explicit owner decision

**Backup provider — manual or automatic?** Automatic breaks the standing *"no silent provider switching"*
rule and D4's frozen-recipient guarantee. Recommendation: **CodeC offers, the owner taps.** A provider
switch is a new data recipient and needs fresh consent, not a replayed one.

**Answer detail.** The current `AGENT_ASK_SYSTEM_INSTRUCTION` ends *"Keep answers short: they are read on
a phone."* — unconditional, and it overrode the owner's own *"explain full main.js line by line."* This
option makes brevity a choice instead of a hardcoded assumption.

## Why the defaults are conservative

The owner's instinct was that bigger is better. SWE-agent measured the opposite: full-file reads and
full history both **lose** to bounded settings. So the defaults sit at the measured optimum, and the
ranges let the owner experiment without being able to fall off the edge.

## Touches

`AiPolicy.kt` (bounds, pure) · `SettingsManager.kt` (non-secret keys only) · `AiContext.kt` (system
instruction selection) · `AiViewModel.kt` · Settings UI. **No new dependency.**

## Security rules

**S9** is the whole level. **S4/S5** via the memory toggle and Clear-now. **S8** via the backup-provider
and request-inspection rows. **S12** via budget extension, which must still produce a readable answer.

## Acceptance checks

- [ ] Every ceiling is asserted by a host test at its declared bound.
- [ ] No option alters D1/D5 tool, write, run, or path permissions.
- [ ] The request-inspection row cannot be turned off.
- [ ] The backup provider never switches without a fresh tap and a fresh preview.
- [ ] *Clear now* deletes memory for the project and is reflected immediately.
- [ ] Answer detail visibly changes the system instruction shown in the disclosed request.
- [ ] Budget extension never increases the run count or grants an extra approval.
- [ ] Settings export / feedback scrubbing still redacts every credential shape.

## Research references

- SWE-agent ablation Table 3 — bounded beats unbounded — https://arxiv.org/abs/2405.15793
- Anthropic — *find the simplest solution possible, and only increase complexity when needed* — https://www.anthropic.com/research/building-effective-agents
- Manus — *mask, don't remove*; keep the action space stable — https://medium.com/@peakji/context-engineering-for-ai-agents-lessons-from-building-manus-71883f0a67f2

**Next:** [Level 11 — Agent phone presentation](11_AGENT_PHONE_PRESENTATION.md)
