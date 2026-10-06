# CodeC AI — the plain guide

> Phase 96 (2026-10-06). This is the user's page for the assistant in CodeC:
> how to turn it on, what it can and cannot do, what it costs, and where the
> honest edges are. It describes **app-v1.3.18**, the first release that ships
> the whole AI line (Phases 76–96 / AI Levels 1–12).
>
> What *leaves the phone* and what is *kept* has its own page with the code
> behind every claim: [DATA_AND_PRIVACY.md](DATA_AND_PRIVACY.md), section
> *CodeC AI (Phases 76–96 / 82B)*. Read that one before sending anything
> sensitive.

## 1. What it is

An optional helper that answers questions about the code you have open and can
propose small, reviewable edits. It is **read-first**: it reads, it proposes,
and it asks — it never writes and never runs anything by itself.

- It lives in the **✨ rail slot** (`AiHome.kt`) and, once set up, behind the
  floating **✨ button** (`AiFloatingButton.kt` → `AiChatSheet.kt`).
- **Google Gemini** is the default provider; **NVIDIA Build** is a manual
  selection, marked *dev/test only* (`AiProviders.kt`).
- **There is no CodeC server, no shared key and no proxy.** Requests go from
  your phone to the provider you configured, with the key you saved
  (`GeminiClient.kt`, `NvidiaRequest.kt`).

## 2. Turning it on, and getting a key

1. Open the **✨** slot in the rail → **AI home**.
2. Choose the provider, then paste your key:
   - Gemini — *Get a key in Google AI Studio* (`AiCopy.GET_KEY_URL`:
     `https://aistudio.google.com/apikey`). Google's terms apply to your key,
     and in the EEA, Switzerland and the UK only paid keys are permitted
     (`AiCopy.REGION_NOTE`).
   - NVIDIA Build — `https://build.nvidia.com/`, internal testing/evaluation
     only.
3. Tick the 18+/terms line and **Save key**. The key is encrypted with Android
   Keystore (AES-256-GCM) under `no_backup/ai/` (`AiKeyStore.kt`) — it is never
   backed up and never sent anywhere except the provider you chose.
4. **Test connection** sends one fixed one-word request, with no code
   (`AiCopy`, `AiViewModel.testConnection`) — so you can check the key before
   trusting it with a question.
5. Choose whether the floating button shows (**Show AI button**,
   `ai_settings.properties`), and open the chat.

## 3. The agreement you accept once

Before the first question, the sheet shows a one-card welcome
(`AiCopy.WELCOME_TITLE` → **"Before you ask"**). Its four bullets, verbatim
(`AiCopy.WELCOME_AGREEMENT_BODY`):

1. **Small work only.** Short questions, explanations and small, reviewable
   edits — not whole apps, large refactors, or code you have not read.
2. **Your API key, your bill, your rate limits.** CodeC does not host a model
   and does not pay for requests; calls leave your phone with your key, under
   the provider's own terms.
3. **Always review before you apply.** Proposed edits are shown as a diff, and
   Apply is your tap — never automatic. Nothing runs without your tap either.
4. **The creator is not responsible** for what the model writes, for charges
   through your own key, or for code you run on your own device.

Then tap **I understand — start** (`AiCopy.WELCOME_AGREE`). It is a UI
agreement, not a permission: the model still sees nothing until you tap Send,
and it is asked once per `AiKeyStore.WELCOME_VERSION`. The composer is not
drawn before you accept.

## 4. A first question

1. Type your question in the composer, or use a chip: **Explain selection**,
   **Explain last error** (after a failed run), **Ask about the project**, or
   **Propose edits**.
2. A **preview** appears — *Check before sending* — naming the provider, the
   model and the exact text the request will carry (`AiViewModel.preview()` →
   `AiPrompt`). The two strings shown are the two strings sent
   (`GeminiRequest.body`).
3. Tap **Send** (`AiCopy.SEND`). The answer streams in; **Stop** cancels it
   (the request is disconnected, not hidden).
4. Answers are rendered as Markdown — headings, lists, tables and fenced code
   blocks with a copy-only **Copy** per block (`AiMarkdownView.kt`). Links are
   `https`-only and open behind a confirm dialog that shows the full URL
   (`AiLevel11Policies.kt`).
5. Follow-up questions carry the conversation, bounded and in memory
   (`AiChatSession.kt`, up to 8 turns). If an answer is cut off by a length
   cap, **Continue** builds another exact preview (`AiContinuation.kt`) — it
   never resends silently.

## 5. The tool timeline

For a project question the assistant can *read* — with limits it cannot
change:

| Tool | What it does | Cap |
|---|---|---|
| `list_files` | lists admitted project paths | up to 200 paths |
| `search_project` | searches with the same scoping keys as `list_files` (`max`, `path`, `ext`) — a scope can only narrow the walk's admitted list | 40 hits across 300 files |
| `read_file` | reads a line range of one file | 400 lines, 24 000 chars |
| `read_files` | reads several files in one call | 8 files per batch |
| `find_files` | globs the admitted paths | 80-char pattern |
| `outline_file` | definitions in one file, with line numbers | — |
| `read_run_output` | the last run's output the panel already held | — |
| `request_run` | *asks* to run the project — it runs nothing | 2 per task, each awaiting your **Run** tap |

Each step appears on the timeline as one line (collapsed by default), and
tapping it opens exactly the text the model saw — never a summary
(`AiResultRowPolicy`, `AiAgentLoop.kt`). Results are capped at 8 000 characters
before they travel into the next request (`AiToolLimits.MAX_RESULT_CHARS`).
Secret-like paths (`.env`, `.npmrc`, private keys), `.git/`, `.codec/`, build
output and symlink escapes are refused before a read ever happens
(`AiProjectFiles.kt`, `AiProjectReader.kt`, `AiToolRunner.kt`).

## 6. Approving a diff, and approving a run

- **Edits:** the model cannot write. It emits a `<<<CODEC_EDIT>>>` proposal,
  the app parses it into a per-file **diff review card**, and only your
  **Apply** writes — through `ui/projects/AiEditApplier.kt`. **Reject
  changes** discards it. After an applied task, one **Undo AI changes** card
  reverses that task from a bounded journal (`no_backup/ai/undo/<project>/`,
  ≤5 files / ≤256 KB), unless you have edited the same text since.
- **Runs:** `request_run` raises an approval card. **Run** hands it to CodeC's
  normal RUN ▶ pipeline; **Skip** runs nothing, and the model is told that.
  Only the bounded real output and exit code go back to the model
  (`AiToolRunner.kt`).

## 7. The history drawer

The **☰** in the chat header opens the drawer (`AiChatHistory.kt`):
**Pinned** and **Recents**, pin/unpin, a **+ New chat** pill with one confirm,
and a marked **current chat**. Rows are titles only — a clipped line, no
message text — and up to 20 conversations per project.

**They live in memory.** Nothing in the drawer is written to a file, a store,
a log or a backup: closing the app clears it (that is **D6**, deliberately not
amended — see [DATA_AND_PRIVACY.md](DATA_AND_PRIVACY.md)).

## 8. The nine agent controls

In **AI home** → *Agent options* (`AiOptionsPolicy.kt`, `AiCopy`): read
window, working set, task memory, answer detail, tool activity, request
inspection (**Always on** — it cannot be turned off), backup provider, budget
extension, read-only reviewer.

Every one of them **tunes inside a cap; none can widen what the agent may
read, write, run or reach** (S9). The shipped defaults are today's behaviour —
including the **read window at 400 lines**, which you can lower to 50, never
raise.

## 9. The caps

Hard limits, not settings (`AiAgentLoop.kt`, `AiToolLimits`):

| Cap | Value |
|---|---|
| Model turns per task | **12** |
| Tool calls per task | **24** |
| Runs per task (each approved) | **2** |
| Read size | 24 000 chars (`AiProjectFiles.MAX_READ_CHARS`) |
| Tool result size into the next request | 8 000 chars |
| Files per batched read | **8** |
| Identical repeats before it stops | **3** |
| Read window default (maximum the runner serves) | **400 lines** |

## 10. Honest limits

- **History is this session only** — D6, unchanged. Close the app and the
  drawer is empty.
- **The read window default is 400 lines** — the assistant may ask for ranges,
  but the runner serves at most 400 at a time.
- **There is no write tool and no exec tool**, by design; nothing is staged,
  committed, installed or typed for you.
- **On a small phone a code block's contrast is modest** — readable, not
  high-contrast.
- **No autonomy claim, no on-device model.** Levels 1–12 are what shipped;
  the local/on-device model (Level 13) is **postponed by the owner** — nothing
  is scaffolded for it — and Level 14 stays unauthorized
  (`docs/roadmaps/ai-integration/13_OPTIONAL_ON_DEVICE_MODEL.md`).
- **NVIDIA is internal-testing only**, under its API trial terms, and CodeC
  sends no Google-specific `store` flag to it.

## 11. When something looks wrong

- **The app judges itself:** the **Self-check** in the sheet runs five checks
  (file access, a remembered code word, whether a follow-up carries the
  conversation, whether an edit block parses, whether a run request reaches
  the card) and produces a redacted report you can paste into a bug report
  (`AiSelfCheck.kt`).
- **A failed run** offers *Explain last error* — that route is redacted
  exactly like `read_run_output` before anything is sent.
- **Rate limits:** a failed request may be retried **once**, visibly and
  cancelably (`AiRetry.kt`); you can also ask for the other provider when both
  keys exist — manually, with a fresh preview (`AiCopy.BACKUP_MODE_NOTE`).
- **Keys:** a key that can no longer be decrypted is deleted and the app asks
  for it again (`AiKeyStore.kt`); deleting a provider's key also clears that
  project's stored task memory and the last-task undo journal.

## 12. Where the claims live

| Claim | File |
|---|---|
| Key storage, welcome version, undo journal | `ui/ai/AiKeyStore.kt`, `ui/projects/AiEditApplier.kt` |
| Caps and stop reasons | `ui/ai/AiAgentLoop.kt`, `ui/ai/AiTools.kt` |
| Controls and their ranges | `ui/ai/AiOptionsPolicy.kt`, `ui/ai/AiCopy.kt` |
| Admitted files, reads, refusals | `ui/ai/AiProjectFiles.kt`, `ui/ai/AiProjectReader.kt` |
| Redaction before a request | `ui/ai/AiSecretScan.kt`, `ui/ai/AiContext.kt` |
| History drawer (titles only, in memory) | `ui/ai/AiChatHistory.kt` |
| What leaves, where, and what is kept | [DATA_AND_PRIVACY.md](DATA_AND_PRIVACY.md) |
