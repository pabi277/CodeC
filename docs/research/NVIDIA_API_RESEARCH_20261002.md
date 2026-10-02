# NVIDIA NIM / Build free API — provider research

**Date:** 2026-10-02 (owner request in chat: *"Api limit can be solved by Nvidia
api key for free tier research it"*, after reporting two problems: rate limits
and answers stopped short).
**Repository baseline:** `main` @ `df2c5d4`; Phase 80 branch
`arena/01a0f9a5-codec` @ `e01a439` where noted.
**Status:** **research only — nothing here authorizes code, a dependency, a
permission, or a keystore change.** Level 5 of
[`AI_INTEGRATION_ROADMAP.md`](../roadmaps/AI_INTEGRATION_ROADMAP.md)
([`05_PROVIDERS_AND_MODEL_COLLABORATION.md`](../roadmaps/ai-integration/05_PROVIDERS_AND_MODEL_COLLABORATION.md))
already plans provider selection; this dossier is the input for an owner
decision, not a phase.

Every claim about CodeC is a `file:line` read on the revision named; every
external claim names its source and says whether it was fetched this session.

## 1. The two problems, separated

The owner reported one symptom pair; they are different problems with different
fixes. CodeC evidence:

| Problem | What actually happens in CodeC today |
|---|---|
| **429 / rate limit** | `ui/ai/AiErrors.kt:40` maps HTTP 429 → `AiFailureKind.RATE_LIMIT` with one fixed sentence ("Gemini's rate or quota limit was reached. Wait a moment and try again."). `ui/ai/GeminiClient.kt:47` reads only `responseCode` — **no `Retry-After`, no automatic retry, no backoff**. A 429 is a dead end the user retries by hand. |
| **"limited context window stopping ai from full answer"** | The binding limits are **CodeC's own**, not the model's: `ui/ai/AiPolicy.kt:40` asks for `MAX_OUTPUT_TOKENS = 8_192`; `ui/ai/AiPolicy.kt:32` caps the rendered reply at `MAX_REPLY_CHARS = 24_000`; `ui/ai/AiAnswer.kt:37,43` marks the answer `cutShort` on either the local cap or `finishReason = MAX_TOKENS`, and the sheet shows `AiErrors.CUT_SHORT` (`AiChatSheet.kt:384`). On the input side, Phase 80 bounds what is sent by design: map ≤ 6 000 chars (`AiRepoMap.MAX_MAP_CHARS`) and ≤ 24 000 chars per agent request (`AiAgentLimits.MAX_REQUEST_CHARS`); the classic path still packs under `AiLimits.MAX_CONTEXT_CHARS = 12_000` (`AiPolicy.kt:23`). |

The model the app is configured for, `gemini-3-flash-preview`, is documented by
Google at **1,048,576 input tokens and 65,536 output tokens** (Vertex AI model
card, fetched 2026-10-02). CodeC asks for **one eighth** of the output budget and
then caps the text at ~24 000 characters. So a "context window" complaint against
Gemini is, in this app, mostly **our own cap** — and sometimes the free tier's
per-minute/per-day limits rejecting a long task, which reads as a cut or failed
answer too. Both need fixing before any provider swap is worth discussing.

## 2. NVIDIA Build / NIM free tier — the facts

**What it is.** NVIDIA hosts an OpenAI-compatible inference API at
`https://integrate.api.nvidia.com/v1`. `POST /v1/chat/completions` with an
`Authorization: Bearer <key>` header; streaming is `stream: true` with SSE
terminated by `data: [DONE]`; `max_tokens` 1–32 768 on the model excerpt checked
(`docs.api.nvidia.com/nim/reference/llm-apis` and
`…/nvidia-nemotron-3-super-120b-a12b-infer`, fetched 2026-10-02).

**Multiple models — the catalog is the point, not one model.** The fetched
model list (`docs.api.nvidia.com/nim/reference/llm-apis`, page footer
"Updated 10 days ago" at fetch time) names families including `deepseek-ai`,
`google`, `meta`, `microsoft`, `minimaxai`, `mistralai`, `moonshotai`, `nvidia`,
`openai`, `poolside`, `qwen`, `sarvamai`, `stepfun-ai`, `stockmark`,
`thinking machines`, `upstage` and `z-ai`. Coding-relevant ids seen in that
list:

| Model id | Notes (from the same page / model reports) |
|---|---|
| `nvidia/nemotron-3-super-120b-a12b` | 120B MoE (12B active), up to 1M context, tool use, `reasoning_effort` none/low/high, `max_tokens` ≤ 32 768. RULER @1M: 91.75 (Nemotron 3 Super report, fetched). |
| `nvidia/nemotron-3-ultra-550b-a55b` | NVIDIA's flagship "Ultra", agentic reasoning/coding (catalog). |
| `nvidia/nemotron-3.5-lightning-30b-a3b` | Newer small/fast tier (catalog). |
| `nvidia/nemotron-3-nano-30b-a3b` | Smallest Nemotron 3 tier (catalog). |
| `deepseek-ai/deepseek-v4-pro` | ~1M context MoE, coding/reasoning. |
| `deepseek-ai/deepseek-v4-flash` | Faster/cheaper sibling (trackers). |
| `moonshotai/kimi-k3`, `moonshotai/kimi-k2-thinking`, `moonshotai/kimi-k2-instruct` | Kimi family, long context; kimi-k3 free designation reported as *limited-time*. |
| `z-ai/glm-5.1`, `z-ai/glm-5.2`, `z-ai/glm-5.3`, `z-ai/glm-5.3-flash`, `z-ai/glm4.7` | GLM family (agentic coding). |
| `qwen/qwen3-next-80b-a3b-instruct`, `qwen/qwen3-next-80b-a3b-thinking`, `qwen/qwen2.5-coder-32b-instruct` | Qwen family incl. a dedicated coder. |
| `openai/gpt-oss-120b`, `openai/gpt-oss-20b` | OpenAI open-weight pair. |
| `minimaxai/minimax-m2.7`, `minimaxai/minimax-m2.5` | MiniMax family. |
| `stepfun-ai/step-3.5-flash` | Fast speculative model. |
| `google/gemma-4-31b-it` (family `google/gemma-*`, `google/codegemma-7b`) | Gemma incl. a code variant. |
| `mistralai/mistral-nemotron`, `meta/llama-3.3-70b-instruct`, `microsoft/phi-4-mini-instruct` | The general open-weight bench. |
| `nvidia/usdcode`, `poolside/laguna-xs-2-1`, `thinking machines/inkling` | Specialists; checked only as catalog entries. |

**Also seen on the NVIDIA catalog landing page** (`build.nvidia.com`, fetched):
Nemotron 3 Ultra, Kimi K3, DeepSeek V4 Pro and Nemotron 3.5 Lightning are the
headline models — i.e. the free catalog is **actively refreshed**, which is
exactly why CodeC must not hard-code assumptions about which ids work (below).

**Free tier, per third-party trackers read this session** (NVIDIA does not publish
one universal table; the account's own console is authoritative):

| Source | Claim |
|---|---|
| [freellm.net](https://freellm.net/providers/nvidia-nim) | Free with a developer account, no credit card; **up to ~40 RPM**, **shared across all models**; "no daily token cap"; 78 free models; some catalog entries are not callable with a standard key. |
| [apis.io plan](https://apis.io/plans/nvidia-nim/nvidia-nim-plans-pricing/) | "Developer (Free): 1,000 inference credits on signup, **40 requests per minute soft rate limit**, 100+ models." |
| [NVIDIA forums](https://forums.developer.nvidia.com/t/api-rate-limit/377627) (July 2026) | A moderator: no official way to raise free-tier RPM; increases require a paid deployment. Users report 429s on agent-style multi-call workflows. |
| [decodethefuture](https://decodethefuture.org/en/nvidia-nim-api-pricing-limits-guide/) (Sept 2026) | The credit system was replaced by **model-specific rate limits that are not published**; hosted access is for prototyping, not production. |

**So, compared with the Gemini free tier** (~10–15 RPM, ~250 K TPM, ~1 000–1 500
requests/day for Flash-class models, per [pecollective](https://pecollective.com/tools/gemini-free-tier-guide/),
[tinkerllm](https://tinkerllm.com/blog/gemini-api-free-tier-limits-rate-quotas/);
Google's own page says limits vary by tier/model and AI Studio is the source of
truth — `ai.google.dev/gemini-api/docs/rate-limits`, fetched 2026-10-02), NVIDIA's
free tier is a **larger allowance in requests per minute** and reports no daily
request cap. It is **not unlimited** — it is ~40 RPM shared, and agent tasks are
bursty by nature.

### 2.1 Future updates — what changes, and what CodeC must not assume

The catalog changes under the app's feet, and the trackers this session read
give concrete evidence of each drift:

| What changes | Evidence | CodeC consequence |
|---|---|---|
| Model ids appear and disappear | Catalog footer "Updated 10 days ago"; `z-ai/glm-5.3`, `glm-5.3-flash`, `nvidia/nemotron-3.5-lightning-30b-a3b` exist now; ID `z-ai/glm-5.2` even renders as an unresolved `ref:` on the page itself. | Never hard-code a "default model" list as truth. Ship a **pre-filled** id, keep the field editable (already the O3 pattern, `AiModel.DEFAULT`), and let **Test connection** decide. |
| Free names are temporary | Reddit / OpenCode users (Aug 2026): "Kimi K3 … FREE on NVIDIA NIM (60 req/min)" with comments about capacity degradation; a tutorial (Jul 2026) notes `moonshotai/kimi-k3-free` labelled "free for a limited time". | Treat "free right now" as a cached fact with a date, never a promise in UI copy. |
| Rate limits are model-specific and unpublished | NVIDIA forum moderator (fetched): limits depend on model, use-case and overall traffic; **no official way to raise free-tier RPM**; the credit system was replaced by model-specific limits ([decodethefuture](https://decodethefuture.org/en/nvidia-nim-api-pricing-limits-guide/)). | A provider must expose a **per-model capability row** (context, output cap, tool use, ~RPM if known) and CodeC's retry/backoff must be provider-agnostic (see §5.1). |
| Reality deviates from the catalogue | A user report: "Most of their models are dead… all allocations in nemotron ultra"; another: a mid-conversation switch to a different model after a few requests. | The D4 preview and the timeline must name the **actual model id used** each turn; CodeC should surface a provider's error rather than switching silently (Level 5 acceptance). |
| The terms can change | Trial ToS is "as updated from time to time"; §1.3 lets NVIDIA terminate a pre-release service "at any time without liability". | Keep NVIDIA behind the provider seam so it can be removed without touching the agent loop, tools, or permissions. |

**Practical update check for the owner (no code needed):** the NVIDIA catalogue
page (`docs.api.nvidia.com/nim/reference/llm-apis`), the individual model page
(`…/<model>-infer` shows `max_tokens`, streaming and reasoning knobs), and
`build.nvidia.com`'s landing page are the three places that move; the account's
own console is the only authoritative source for the account's RPM.

## 3. The license trap — read this before adding a key

NVIDIA's own terms say the free hosted catalog is **not for production**:

- [NVIDIA API Trial Terms of Service](https://assets.ngc.nvidia.com/products/api-catalog/legal/NVIDIA%20API%20Trial%20Terms%20of%20Service.pdf)
  §1.2 (fetched): access is "for limited trial purposes only and **without use of
  the API Service or Generated Content in production**"; §1.4: "Unless you
  purchase a Subscription … you may only use the API Service for **internal
  testing and evaluation** purposes, not in production."
- [NIM FAQ](https://docs.api.nvidia.com/nim/docs/product) (fetched): "Production
  use involves any use of NIM for purposes other than development, testing,
  research or evaluation … including **activity serving real end-users**."
- Data: the Trial ToS §2.3 says NVIDIA will not store User/Generated Content at
  the end of a session unless expressly disclosed (fine-tuning is the stated
  exception) — but the endpoint is still a **third-party boundary** that must be
  disclosed per request exactly like Gemini's is today (D4 preview).

What this means for CodeC, stated plainly:

1. A CodeC user bringing **their own** NVIDIA key is doing what the NVIDIA
   Developer Program offers that key for (prototyping/testing). CodeC is not
   allowed to become the service that routes real end-users through a free
   NVIDIA endpoint.
2. Therefore NVIDIA would have to be offered as a **development/testing
   provider**, gated by an explicit terms checkbox at key setup — the same
   pattern the app already has for Google (`AiKeySetup.canSave`, O1, read in
   `ui/ai/AiPolicy.kt`), with copy that says NVIDIA's free catalog is for
   testing/evaluation and not for production use.
3. No CodeC marketing or UI text may imply NVIDIA free access is production-grade
   or unlimited.

### 3.1 The owner's question: "I will use the NVIDIA API for my own research — is that a breach?"

**No. That is what the free trial is for.** Straight from the documents read
this session: the Trial ToS §1.4 restricts unpaid access to "internal testing and
evaluation purposes", and the NIM FAQ says Developer Program members get free API
access "for prototyping, research, development and testing". A solo developer
using his own key, on his own projects, in his own app, is squarely inside that.
What would fall outside, in the same words:

- **"Production use … any non-testing activity including activity serving real
  end-users"** (NIM FAQ) — e.g. shipping CodeC with *your* key baked in so
  others use it, or running a service on it.
- **§4.2**: you may not "copy, sell, rent, sublicense, transfer or distribute or
  make available to others" the API or its Generated Content — so the key stays
  yours, and each CodeC user brings their own.
- **§2.6(a)**: do not submit confidential data you have no right to send —
  CodeC's D4 preview already shows exactly what would leave the phone.
- **The free tier's own limits** (no RPM increases, models can be withdrawn) —
  not a rule breach, but a reason not to depend on it.

So: **personal research = fine; a product that routes other people's use through
your free key = not fine.** The design that keeps you on the right side is BYOK
per user plus the explicit terms checkbox described above — never a shared or
bundled key, never a CodeC-hosted proxy.

This is the agent's conservative reading of the terms, and it is the part the
owner must confirm before any implementation.

## 4. What NVIDIA does **not** fix

- **It does not remove rate limits**; it gives a second allowance (~40 RPM,
  shared) that a bursty agent task can still exhaust. It is mitigation plus a
  useful fallback, not a cure.
- **It does not lengthen CodeC's answers by itself.** The output caps above are
  ours. NVIDIA's longer-context models only help if CodeC raises its own caps —
  and raising caps raises token cost, latency, and 429 pressure.
- **It is not Gemini quality.** Coding quality of the listed open models is
  strong but different; the Level 5 acceptance check ("compare model quality and
  tool-call correctness on a fixed CodeC task set, not marketing benchmarks")
  applies.
- **It requires phone-verified signup and a new vendor dependency** on the user's
  side: another key to store (encrypted, D3), another provider boundary to
  disclose, another model field in the UI.

## 5. Recommended fix order (cheapest first, provider last)

1. **Gemini resilience + full answers (small, no provider change).**
   Read `Retry-After`/`RESOURCE_EXHAUSTED` and do **one** visible retry after the
   server's own delay ("the limit was hit; retrying in Ns"), instead of today's
   dead-end 429 (`AiErrors.kt:40`); raise `MAX_OUTPUT_TOKENS` (8 192 → 24 576 or
   32 768, inside the model's 65 536 ceiling) and `MAX_REPLY_CHARS`; add a
   **Continue** affordance for a `cutShort` answer that re-asks with the tail of
   the previous answer. This alone answers most of "stopped from full answer".
2. **Provider seam (Level 5, 5A).** Separate the provider interface from
   `GeminiRequest`/`GeminiClient`, add a capability table (streaming, tools,
   context, output cap), and let the user pick a provider/model per task with
   **no silent switching** (Level 5 acceptance). NVIDIA fits this seam naturally:
   OpenAI-compatible `POST /v1/chat/completions`, SSE, key in a header.
3. **NVIDIA as the first second provider (BYOK, dev/test disclosure).** New
   encrypted key slot (or a provider-indexed slot), a model field pre-filled with
   one tested id, a Test connection, the terms checkbox from §3, and the D4
   preview naming NVIDIA and the exact payload. Tool loop unchanged: the agent
   protocol is CodeC's own text protocol, so a provider swap does not touch
   permissions.
4. **Only then** consider longer-context defaults per provider (§1 numbers say
   this is a cap decision, not a model purchase).

## 6. Decisions the owner must make (not taken here)

1. **Start a phase** for (1) — Gemini retry/backoff + larger answer budget +
   Continue? (Recommended first; it is small and fixes the visible symptom.)
2. **Do you want NVIDIA as a second provider in CodeC** (Level 5 work), given the
   trial-terms framing in §3 and the signup requirements?
3. **Key policy:** always available, or offered only when Gemini says 429 / when
   the user switches manually? (CodeC's law: never switch silently.)
4. **Cap policy:** raise the app's output caps globally, or per provider/model?
5. **Model policy for NVIDIA:** keep today's pattern (one pre-filled id in an
   editable field, exactly like `AiModel.DEFAULT`), or offer a short list of
   known-good ids (e.g. Nemotron 3 Super, DeepSeek V4 Pro, Kimi K3, GLM-5.3) with
   a "type any id" escape hatch? The catalogue's churn (§2.1) argues for the
   editable field plus **Test connection**, with the list as a convenience only.
6. Nothing here is authorized; Phase 80's device round is still owed and Levels
   5+ stay unauthorized until you start them (`rule.md` §3, roadmap status).

## 7. Multiple providers and models — Level 5 candidates (tracker numbers, verify at source)

Level 5 ([`05_PROVIDERS_AND_MODEL_COLLABORATION.md`](../roadmaps/ai-integration/05_PROVIDERS_AND_MODEL_COLLABORATION.md)) wants "a small number of well-tested providers". These are the free-tier numbers third-party trackers report for the obvious OpenAI-compatible candidates — **all are tracker values, not vendor SLAs, and they change**:

| Provider | Free tier as reported | Models relevant to coding | OpenAI-compatible |
|---|---|---|---|
| **NVIDIA Build/NIM** | ~40 RPM shared, no published daily cap (see §2) | Nemotron 3 (Nano/Super/Ultra/Lightning), DeepSeek V4, Kimi K2/K3, GLM-5.x, Qwen3-Next, gpt-oss, Gemma | Yes |
| **Google Gemini** (today's provider) | ~10–15 RPM, ~250 K TPM, ~1 000–1 500 RPD for Flash-class | `gemini-3-flash-preview` (1M in / 65 536 out) | Native (CodeC's existing client) |
| **Groq** | ~30 RPM, ~1 000–14 400 RPD, ~6 K TPM | Llama 3.3 70B, DeepSeek R1, Mixtral, Gemma | Yes |
| **Cerebras** | ~30 RPM, ~1 M tokens/day | gpt-oss-120b, Llama 3.1 8B, Qwen3 235B | Yes |
| **Mistral "La Plateforme"** | ~2 RPM, ~1 B tokens/month (reported), Codestral 30 RPM / 2 000 req/day | Codestral (code), Mistral Small/Large | Yes |
| **OpenRouter** | ~20 RPM/model, **50 RPD** free (1 000/day with a one-time $10 top-up) | ~30 free models (DeepSeek, Llama, Qwen, Gemma) | Yes |
| **GitHub Models** | Daily limits, 100+ models (tracker) | Mixed | Yes |

Reading for CodeC: **NVIDIA is a strong first second-provider** (breadth, long
context, OpenAI-compatible, no card) and **Groq/Cerebras are the better fallback
if latency matters**, while **OpenRouter is the wrong first pick** for an agent
that makes 20+ calls per task (50 requests/day). The provider seam should carry
per-provider capability rows and per-provider retry policy — never a silent
switch (Level 5 acceptance).

## Sources (all read this session, 2026-10-02)

- Fetched: `docs.api.nvidia.com/nim/reference/llm-apis`;
  `docs.api.nvidia.com/nim/reference/nvidia-nemotron-3-super-120b-a12b-infer`
  (`max_tokens` 1–32768, `stream` SSE `[DONE]`, `reasoning_effort`);
  `docs.api.nvidia.com/nim/docs/product` (free NIM access = prototyping;
  "production" definition);
  `assets.ngc.nvidia.com/.../NVIDIA API Trial Terms of Service.pdf` (§1.2, §1.4,
  §2.3); `ai.google.dev/gemini-api/docs/rate-limits` (RPM/TPM/RPD, tiers, AI
  Studio authoritative).
- Fetched also: `build.nvidia.com` landing page (headline models: Nemotron 3
  Ultra, Kimi K3, DeepSeek V4 Pro, Nemotron 3.5 Lightning).
- Search results (third-party summaries, not primary): freellm.net, apis.io,
  yangmao.ai, decodethefuture.org, jaesolshin.com (NVIDIA 40 RPM / credits /
  model list / reasoning-field note); Reddit r/opencode + volanea.com (Kimi K3
  free "limited time", capacity/queue reports, 60 req/min claim); OpenRouter's
  own comparison blog, agentdeals.dev, clawhosters.com, hypereal.cloud (Groq /
  Cerebras / Mistral / OpenRouter / GitHub Models free-tier tables);
  pecollective.com, tinkerllm.com, aifreeapi.com (Gemini free-tier numbers);
  Vertex AI model card for `gemini-3-flash-preview` (1 048 576 in / 65 536 out);
  NVIDIA Developer Forums threads July 2026 (free-tier RPM increases refused).

## Owner follow-up — research, deferred device acceptance and merge authority

[Agent-core optimization research](AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md) records the subsequent GLM-5.3/manual-selection discussion, informal Nemotron screenshot issues, source audit, full-file/batch context and multi-API lead/backup/reviewer options. No default-model or optimization source change. Owner (2026-10-02) postpones formal device acceptance until after optimization and explicitly authorizes merging the current Phase 82/82B work; this is **not** a device pass or a start command for 5B/6+. The phase ledger records actual CI/PR/merge facts separately.

## Merge / post-merge CI — PR #107

[Phase 82/82B](../phases/03-editor/chat-phase82/README.md): **PR **#107 MERGED** @ **4cb4151**; main Build APK **37006780729 GREEN**; release **7,117,440 B**, debug **26,869,596 B**; formal device acceptance **POSTPONED until after optimization, NOT PASSED****. The [optimization note](AI_AGENT_CORE_OPTIMIZATION_RESEARCH_20261002.md) is research only; known informal screenshot issues remain open. No model-default/optimization/native-tool/routing change or device acceptance is inferred from this authorized merge. Post-merge verification docs are carried on the current session branch.
