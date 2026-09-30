# Level 0 — Product boundaries and shared foundations

**Status: discussion / planning only.** This page defines decisions that should be settled before AI code is started.

## User's product direction

- The agent works on a user-selected whole project.
- Users bring API credentials and can select among providers and models.
- An on-device model is optional; CodeC first checks whether a supported model looks practical on that device.
- The agent's file edits can be undone.
- Capabilities grow from a simple read-only helper toward a more autonomous agent, rather than arriving all at once.

## Product boundary: selected project, not whole phone

The default workspace should be the selected CodeC project. Project-wide access does not imply access to unrelated projects, the rest of shared storage, settings, credentials, GitHub tokens, or other apps. If CodeC's existing external-folder support is in scope, the selected folder must be explicitly disclosed and the permission revocable.

## One tool contract, several model backends

Keep the agent's capabilities behind CodeC-owned tools and permission rules. The model proposes a tool call; CodeC validates the arguments, scope, state, and approval requirement before the operation happens. Cloud and local models should use the same project boundary and the same action rules.

A provider adapter is not itself a safety layer. Different APIs vary in tool-call format, streaming, context size, rate limits, model discovery, and structured output. Treat capability support as explicit metadata rather than assuming every model can perform every action.

## Session and change ownership

An agent session should be tied to one project and have a visible activity history: user request, context shared, model/provider, plan, tool request, approval/denial, result, changed files, and rollback state. Do not log raw API keys or silently include project contents in diagnostic/crash reports.

Synchronize editor buffers and disk before the agent reads. If the user edits a file during agent work, detect the conflict and stop/ask rather than overwrite either version.

## Security reality

Android's app sandbox prevents ordinary apps from freely reading CodeC's private data. It does not sandbox an agent tool against CodeC: a shell or process that runs with CodeC's access may read or change the project and other CodeC-accessible files. Project-root checks and command approvals reduce risk but do not make arbitrary shell execution safe. A stronger isolated workspace (for example, a disposable project copy with controlled import/export) is a later design question, not something the word "sandbox" guarantees today.

Treat source files, README instructions, terminal output, and downloaded dependencies as untrusted content. They can contain prompt-injection text. Such content must never grant itself extra permissions or redefine the user's approvals.

## Decisions needed before implementation

1. Is the first release read-only (recommended), or should it include edits from day one?
2. Does "user provides API" mean direct provider keys stored on-device, and are OpenAI-compatible custom endpoints also desired?
3. Is the workspace exactly one CodeC project, or can the user choose a folder imported/opened through Android's picker?
4. Should cloud prompts require a per-request context preview, or is a persistent, clearly scoped consent acceptable?
5. Should rollback cover only agent file edits (recommended), with commands/package/Git side effects separately approved?
6. Which minimum Android version/device families should on-device inference target?

## Not a commitment

These decisions need owner agreement. This plan does not authorize changing CodeC's privacy statements, adding permissions/dependencies, storing keys, running commands, distributing model weights, or implementing any level.
