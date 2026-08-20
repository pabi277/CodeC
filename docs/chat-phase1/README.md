# Phase 1 terminal — this chat’s record

Start here in a **new chat**. Do not re-debug issues that are already closed below.

| | |
|---|---|
| **App version** | **1.3.13** (`versionCode` 17) |
| **Branch** | `arena/01a01d83-codec` (PR #5 exists — do not open another unless asked) |
| **Phase 1** | Working on device: `cc file.c -o a.out` then `./a.out` |
| **Phase 2–3** | **Not started.** New chat. Do not begin bootstrap/apt here. |

```
docs/chat-phase1/
  README.md       ← you are here
  PROBLEMS.md     sorted bugs from this session (symptom → cause)
  SOLUTIONS.md    what landed, files, version bumps
  HANDOFF.md      rules for the next agent
```

Also read:

- [docs/TERMINAL_PLAN.md](../TERMINAL_PLAN.md) — Mini-Termux roadmap
- [docs/TROUBLESHOOTING.md](../TROUBLESHOOTING.md) — compiler / W^X / Termux engine

## Device smoke test (1.3.13)

Uninstall the old APK, install **CodeC-IDE** from Actions, Term → refresh.

```
cc input.c -o input.out
```

```
./input.out
```

Prompt `Enter a number:` must appear **before** the user types. Then a number + Enter.

Editor **RUN** is still non-interactive (`scanf` times out). That is known, not a regression.
