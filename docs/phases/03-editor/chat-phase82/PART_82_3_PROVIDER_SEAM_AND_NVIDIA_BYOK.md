# CodeC Phase 82B / 82.3 — Level 5A provider seam + NVIDIA development/testing BYOK

> **Status:** 🚧 IMPLEMENTED · CI/device pending · **Cost:** `[client-only / per-user BYOK]` · **Effort:** M
> **Owner authorization:** ask_user option **authorize_82b**, selected 2026-10-02 before code.
> This authorizes Level **5A only**. 5B second-model review and Levels 6+ are not started.

## First move: evidence, not code

Baseline `e089880`, read 2026-10-02: AiViewModel.kt:114 instantiates GeminiClient; GeminiRequest/Response implement native Google JSON/SSE; AiHome.kt:43–66 is the setup-only rail surface; AiKeyStore.kt:32–36 holds one encrypted slot + non-secret settings in noBackupFilesDir; FeedbackSectionCard.kt:105,120,149 scrubs only the one loaded AI key. The Level 5 roadmap §5A names provider separation, capabilities, manual model selection, Test connection and no silent fallback.

## Design

A small provider interface separates request building, SSE decoding and provider selection from the agent/tools. Two fixed HTTPS endpoints only: Gemini native API and NVIDIA Build `https://integrate.api.nvidia.com/v1/chat/completions`. No configurable endpoint, proxy or remote catalogue. NVIDIA uses Bearer header, stream=true, messages=[system,user] carrying **exactly** the preview strings; parse choices[0].delta.content, finish_reason, error and [DONE]. Skip reasoning-only deltas; HTTP **202 pending** is a fixed failure with **no automatic polling**, and empty/pending tests never report “NVIDIA answered”; native function calling is not enabled (CodeC's bounded text tool protocol remains the only tool surface).

Capability rows indexed by provider/model: streaming, native tools, structured output, input/context and output ceilings, constraints/source date. Known rows are facts with dated sources; unknown model ids retain unknown values, do not inherit a named model's limits. Pre-fill **nvidia/nemotron-3-super-120b-a12b** (reference re-fetched this chat), editable; no promise that a particular user's key can call it. Connection and fixed-task phone checks decide.

Provider/model are captured with the prompt and then immutable for the task/Continue/retry. Every preview names the recipient/model; each agent request discloses its actual two strings in the memory-only timeline. A failure **never** contacts another provider. Switching selection is a deliberate user tap and requires a new task preview; selection is disabled while request/gather/apply/test is active.

## The Android edge

AI home adds a manual provider selector and capability row; does not add chatting or a network caller. Existing model/test/delete/layout controls remain. NVIDIA key setup:

- Prominent **Development/testing only — not for production** notice, own key only; no bundled/shared key.
- Link API Trial ToS; required checkbox accepting NVIDIA terms and internal testing/evaluation-only restriction (legal adult).
- Save disabled until confirmation, sane model/key; VM/store also enforce consent. No request possible without current acceptance.
- Two independent AES-GCM Keystore aliases and ciphertext slots in `noBackupFilesDir/ai/`; existing Gemini filename/alias/property names retained, no plaintext migration/fallback. Delete/decrypt failure clears only the affected key and its acceptance, plus the existing AI undo-journal cleanup. Shared-store operations are serialized across VM/support instances; metadata is atomically replaced with minSdk-safe File.renameTo, so layout/model updates cannot lose either consent slot.
- NVIDIA credentials/config are the explicit new persistence exception needed for authorized BYOK: three non-secret properties for model, terms version and accepted-at. **No prompt, answer, timeline, countdown, provider session, history or index is persisted.** No new DataStore key, dependency or permission.
- Support redaction loads both saved key literals plus NVIDIA nvapi- shape; no key appears in AiUiState, logs or project files.
- Test connection sends only the fixed one-word test prompt after its disclosed tap, with cancelable retry/Stop; no project text.

## Exit condition

Request/SSE/error fixtures, independent credential-slot/persistence pins and no-fallback tests green; original D1/D4/D5/D6 pins remain intact. Phone verifies both providers manually on the same safe task set (explanation, admitted file read, refused secret, diff review, separately approved run) — no coding-quality claim until that transcript. Terms gating and key backup/deletion checks are core device rows.

## Tests (implemented)

AiProvidersTest **12**, AiProviderClientTest **4**, NvidiaRequestTest **7**, NvidiaResponseTest **8** (real Robolectric in CI), AiProviderWiringTest **9**, NvidiaRedactionTest **3**, AiKeyStoreSlotsTest **8**, plus shared transport fixtures. Local **461/461** green with host shims; existing AI and FeedbackDraft tests retained unchanged. Successful Keystore encryption/slot survival, consent UI, real SSE/model access and fixed-task comparison remain owner device rows, not claimed by source pins.

## Sources (record)

All fetched this chat 2026-10-02 unless noted:

1. [NVIDIA request reference](https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3-super-120b-a12b-infer): model id, messages, max_tokens 1–32,768, SSE/[DONE].
2. [NVIDIA API Trial Terms](https://assets.ngc.nvidia.com/products/api-catalog/legal/NVIDIA%20API%20Trial%20Terms%20of%20Service.pdf), §§1.2/1.4: internal testing/evaluation only, not production; §2.3 session retention caveat, §2.6 no confidential/sensitive input. Do not market trial use as production or unlimited.
3. [NIM FAQ](https://docs.api.nvidia.com/nim/docs/product): Developer Program prototyping/research/development/testing, production includes serving real end-users.
4. Repo NVIDIA_API_RESEARCH_20261002.md (prior-session allowance research): ~40 RPM shared is a tracker estimate, no published daily cap is **not** unlimited access.
5. Repo Level 5 roadmap §5A / Level 0 decision record, read this chat.

## Deferred / rejected with reasons

No second-model reviewer (5B), native provider tools, dynamic catalogue, custom OpenAI endpoint, shared key, server proxy, automatic fallback, offline model or multi-agent concurrency. No new orchestration/HTTP/JSON dependency: existing coroutines + HttpURLConnection + platform org.json suffice. Do not send an unsupported store field to NVIDIA or claim it overrides NVIDIA retention; stateless messages and the trial terms disclosure replace Google's provider-specific store:false setting.
