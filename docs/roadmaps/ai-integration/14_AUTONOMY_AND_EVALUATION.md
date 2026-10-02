# Level 14 — Higher autonomy and evaluation

**Status: future-only. Do not begin until the guarded project agent has real usage and evaluation evidence.**

> **Renumbered 2026-10-02** from Level 7 → **Level 14**. The owner directed that the proposed agentic
> optimization ([Levels 6–12](00_AGENTIC_MAP_AND_SECURITY_RULES.md)) be placed immediately after the
> completed levels, with the not-yet-started levels moved after them. Content is unchanged; only the
> number moved. Its *"Evaluation before expansion"* section is now partly superseded by
> [Level 12 — Evaluation and acceptance](12_AGENT_EVALUATION_AND_ACCEPTANCE.md), which builds the
> fixture-and-metric harness this level assumed would exist. **Read-only specialist review roles** listed
> below are scoped in Level 12's *"Deferred beyond this series"*: read-only only, never parallel writes
> (Cognition Principles 1 & 2; Anthropic reports multi-agent at ~15× more tokens).

## Possible later capabilities

- User-selected autonomous mode for a bounded task, with a clear scope, time/turn/tool limits, stop control, and rollback checkpoint.
- Read-only specialist review roles (security/reviewer/test planner) using configured models.
- Isolated workspaces for parallel implementation, with explicit patch reconciliation.
- Optional user-defined skills or trusted tool integrations, after a separate permission and supply-chain review.

These are possibilities, not requirements. “More agents” is not automatically better: several models can repeat mistakes, disagree, increase spend, or expose project context to more providers.

## Conditions before any autonomy increase

- Users understand the current plan/edit/run/undo flow and can stop it.
- File rollback works in ordinary, interrupted, and conflicting-edit cases.
- Tool events and model/provider attribution are inspectable without leaking secrets.
- The model/tool combination passes repeatable task evaluations across C, Python, JavaScript, and HTML workflows CodeC actually supports.
- Security testing covers path escape, prompt injection in project data, destructive shell proposals, stale editor buffers, provider failure, and process interruption.
- A stronger execution isolation design has been reviewed before untrusted commands are allowed to run unattended.

## Evaluation before expansion

Create a small, versioned task set drawn from realistic CodeC projects: explain a compiler error, find a definition across files, propose a bounded fix, preserve behavior, run an appropriate check, and report truthfully. Score correctness, edit quality, unnecessary changes, tool-call safety, latency, cost, and rollback. Compare provider models and local models on identical tasks. Preserve failure cases as regression examples.

Do not use GitHub stars, model size, or one successful demo as evidence that a model is safe or reliable for unattended file and terminal access.

## Stop conditions

If autonomy increases destructive mistakes, stale-buffer conflicts, privacy confusion, or unbounded cost, keep or return to approval-per-action mode. User control is a feature, not a temporary obstacle to remove.
