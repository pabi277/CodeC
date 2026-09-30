# Level 7 — Higher autonomy and evaluation

**Status: future-only. Do not begin until the guarded project agent has real usage and evaluation evidence.**

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
