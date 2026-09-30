# Level 2 — Whole-project context

**Status: proposed; depends on the provider/request foundation.**

## User value

The agent can answer project-level questions and reason across files in the selected project—for example, trace which file starts a program or find where a setting is read—without the user manually pasting each file.

## How to make whole-project support practical

"Whole project" is the allowed scope, not a requirement to upload every byte on each turn. Build context in steps:

1. Show the project tree and identify likely relevant files using filenames, language, symbol/search results, and current editor/run context.
2. Retrieve targeted file ranges as the task needs them.
3. Include relevant build/run configuration and diagnostics when useful.
4. Let users inspect included paths and exclude files/folders.
5. Keep context within the selected provider model's known limits; report when the project is too large rather than silently dropping important information.

A compact project map (as seen in Aider's repo-map approach) is a useful research reference. Start with deterministic file listing/search and language-aware structure where available; semantic embeddings and a vector database are not prerequisites for the first version.

## Exclusion and secret hygiene

- Respect `.gitignore` and CodeC-generated/build-output exclusions where appropriate, but let the user inspect the effective list.
- Exclude obvious secret/config files by default (for example, private keys, local environment files, credentials, and token stores). Do not claim secret detection is perfect.
- Do not crawl outside the selected project root, follow symlinks outside it, or include other CodeC projects without an explicit user action.
- Preview paths and the provider receiving them before a cloud request; support path exclusion and cancel.
- Do not index in the background without an explicit, documented policy.

## Editor consistency

The editor's live buffer may differ from disk. Before context assembly, either use the current editor buffer for that file or require an explicit save; never send stale disk contents while implying they are the visible unsaved version. If the buffer is dirty, label the source in the context preview.

## Acceptance checks

- A multi-file question uses relevant files and can name the paths used.
- A large project does not freeze the UI or create an unbounded request.
- Ignored, excluded, secret-like, and out-of-root files are not silently included.
- Cloud context transfer happens only after user initiation and is visible.
- The same project can be used in offline mode later without changing the scope policy.

## Deliberately deferred

Applying edits, shell access, unattended indexing, embeddings/vector storage, and parallel agents.
