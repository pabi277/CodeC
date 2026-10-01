# Part 78.1 — the AI's own file policy (`AiProjectFiles`)

**Status: ✅ IMPLEMENTED 2026-10-01 on `arena/01a0f5d2-codec`.**
File: [`app/src/main/java/com/codeci/ide/ui/ai/AiProjectFiles.kt`](../../../../app/src/main/java/com/codeci/ide/ui/ai/AiProjectFiles.kt) — 398 lines, pure Kotlin, no `java.io`, no Android.
Test: [`AiProjectFilesTest.kt`](../../../../app/src/test/java/com/codeci/ide/AiProjectFilesTest.kt) — **28 cases**.

---

## 1. Owner decisions this part implements

The owner said *"Complete the lavel 2"* and did not answer the six OPEN
questions in the brief. Each was therefore taken at its **most conservative**
option and is recorded here as **the agent's decision, not the owner's** (the
`PHASE_73_1_GIT.md` precedent). Any of them can be changed by the owner
without re-litigating the rest.

| # | Question | Decision taken | Why this is the conservative one |
|---|---|---|---|
| Q1 | Multi-turn? | **No** — one question at a time, Level 1 behaviour unchanged | The Phase 77 Q1 answer was already *"No i just making the ui future pruff"*; the Level 2 spec never asks for turns. |
| Q2 | The context budget | **`MAX_CONTEXT_CHARS` stays 12 000**; answer with relevance + ranges | Does not touch a value the Phase 76 device round validated (`AiPolicy.kt:23`). |
| Q3 | Secret-like files | **Never offered, no opt-in** | A false positive costs one file of context; a false negative sends a credential to Google. |
| Q4 | `.gitignore` | **Out of scope** — the fixed `excludedDirectories` set only | No matcher exists (§2.6 of the README); writing one is a separate sub-project. |
| Q5 | Entry point | **A third chip in the existing chat sheet** | No new surface, no new rail slot, no new Settings control. |
| Q6 | Persistence | **Nothing new saved**; context rebuilt per request | D6. No seventh key in `ai_settings.properties`. |

---

## 2. Evidence the filter had to be a new one

Read on `main` @ `5abe768`, 2026-10-01:

| Read | Fact |
|---|---|
| `ui/editor/ProjectFilesPolicy.kt:9-12` | `configNames` contains **`.env`** and **`.npmrc`**. |
| `ui/editor/ProjectFilesPolicy.kt:13` | `usefulConfig(name)` is also true for **anything starting `.env.`**. |
| `ui/editor/ProjectFilesPolicy.kt:16` | `visible(name, false)` lets a dotfile through when `usefulConfig(name)`. |
| `ui/editor/ProjectSearch.kt:159-166` | `isSearchable` = `visible` && (`usefulConfig` \|\| extension ∈ `TEXT_EXTENSIONS`). |
| `ui/editor/ProjectSearch.kt:172` | `TEXT_EXTENSIONS` itself contains **`"env"`**. |

Three independent routes to a credential file. So `isSearchable` is **not**
reused: `AiLevel2WiringTest` fails the build if any file under `ui/ai`
mentions `isSearchable` or `ProjectSearch.` at all.

---

## 3. What the policy decides

### 3.1 Exclusion, in a fixed order

```
exclusionFor(name, binary, chars) =
    isSecretLike(name)  -> SECRET      // first, always
    !isTextFile(name)   -> NOT_TEXT
    binary == true      -> BINARY
    chars <= 0          -> NOT_TEXT
    else                -> null (include)
```

The order is load-bearing and is pinned by `AiLevel2WiringTest`
(*"the secret check is the first branch of the exclusion decision"*): a future
loosening of the text list must not be able to re-admit `.npmrc`.

`isSecretLike` matches on the **file name only**, lower-cased — never on the
path — so nesting cannot dodge it (`config/prod/.env` is still `.env`).
Name set, prefixes (`.env.`, `id_rsa`, `id_ed25519`, `service-account`,
`secret`) and suffixes (`.pem`, `.p12`, `.pfx`, `.key`, `.keystore`, `.jks`,
`.asc`, `.gpg`, `.ppk`). Deliberately over-inclusive.

`isTextFile` is **narrower** than the search's list on purpose: no `env`, no
`lock`, no `csv`/`tsv`, and no `properties` (Android `local.properties`
carries SDK paths and, in some projects, signing passwords). `.gitignore` is
still readable — it is not a credential.

### 3.2 Relevance without embeddings

`keywords(question)` keeps lower-case `[a-z0-9_]{3,}` tokens minus a stop-word
set, deduped and **sorted** — so the same question always ranks the same way,
which is what makes the preview honest (D4).

`relevance(path, text, words, isOpenFile)`:

- **+200** the file the user is looking at (they asked about something they can see);
- **+60** per question word in the path — a path match is about what the file *is*;
- **+10 … +26** per question word in the body, capped at 8 hits;
- **+15** a small body (1–4 000 chars), **+5** a large one;
- **+25** an entry-point name (`main.c`, `index.ts`, `Makefile`, `package.json`, …),
  because "where does this start" is the most common project question.

`pathScore` is the cheap path-only version used for the shortlist, with
**−3 per directory level** so a top-level `main.c` beats `vendor/old/main.c`.

No index, no cache, no persisted state — recomputed per request (D6).

### 3.3 The budget

| Constant | Value | Meaning |
|---|---|---|
| `MAX_FILES` | 5 | files per request |
| `MAX_FILE_CHARS` | 3 000 | characters of any one file |
| `MAX_READ_CHARS` | 24 000 | characters read from a shortlisted file |
| `READ_SHORTLIST` | 12 | files read before the final pack |
| `MAX_ENTRIES` | 4 000 | directory entries the walk may visit |
| `MIN_USEFUL_CHARS` | 240 | below this room a file is left out whole |

`plan()` sorts by relevance (ties broken by path, so ordering is stable), then
packs against **`AiLimits.MAX_CONTEXT_CHARS` measured on the rendered body** —
headers and footers included. That is the important detail: the ceiling is
enforced on exactly the string that will be sent, so
`AiPromptText.projectBody(plan.included).length <= 12_000` is an invariant, not
a hope. `AiProjectFilesTest` asserts it on a 12-file project.

A file is sliced on **whole lines only**. If the room left is under
`MIN_USEFUL_CHARS`, the file is **left out whole** rather than sent as a stub —
the same reasoning `fromSelection` uses when it refuses an over-long selection
instead of cutting it (`AiContext.kt:73-75`).

Whatever did not fit goes into `Plan.leftOut` with reason `BUDGET`, and
`Plan.truncated` becomes true. **Nothing is dropped silently**
(`02_WHOLE_PROJECT_CONTEXT.md`: *"report when the project is too large rather
than silently dropping important information"*).

---

## 4. Tests

`AiProjectFilesTest` — 28 cases: secret-like names (18 positive, 7 negative),
the exclusion order, the narrower text filter, directory pruning, keyword
extraction/dedup, the three relevance rules, the shortlist, the ceiling
invariant, the per-file slice, whole-line cutting, left-out reporting, empty
plan, the too-small-to-send case, the open-file-first rule, path
normalisation, and the rendered prompt.

Pre-validated in-sandbox before CI: **102/102** assertions against the real
compiled classes on the local kotlinc/JRE harness (`jdk4py` + npm
`kotlin-compiler`). One harness failure was the **test's** fault, not the
code's — it asserted a slice must not end with `"padding padding"`, but a
correct whole-line slice legitimately ends with the line's last word; the
assertion was rewritten to require every sent line to be complete.

## 5. Deferred / rejected with reasons

- **Reusing `ProjectSearch.isSearchable`** — rejected with evidence (§2).
- **An opt-in for secret-like files** — rejected (Q3). No `includeSecret`,
  `allowSecret` or `forceInclude` exists; `AiLevel2WiringTest` pins their absence.
- **Embeddings / vector storage** — rejected (Level 2 spec defers them; D6
  forbids a persisted index).
- **A `.gitignore` matcher** — deferred (Q4). The fixed
  `ProjectFilesPolicy.excludedDirectories` set is used instead, and the user
  can see what was skipped.
- **Raising `MAX_CONTEXT_CHARS`** — deferred (Q2). Revisit only if the device
  round shows five files are not enough to answer a real question.
