# CodeC Phase 87.7 — Backup provider (manual offer only)

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** *"more options to use in the app for better optimization."*
> **Owner decision (2026-10-03):** **manual offer only.** CodeC offers; the user taps; a fresh
> preview discloses the new recipient before anything is sent. Automatic fallback is rejected.
> **Parent brief:** [Phase 87 README](README.md)

## First move: evidence, not code

Read 2026-10-03 against `main` @ `6838ea6`.

- Two providers exist: `AiProviders.kt:4-6` — `GEMINI("gemini", "Google Gemini")` and
  `NVIDIA("nvidia", "NVIDIA Build · dev/test only")`.
- Per-provider terms are already versioned and separately accepted: `AiProviders.kt:22`
  `NVIDIA_TERMS_VERSION = 1`; `:38-39` maps each provider to its own terms version;
  `AiKeyStore.kt:189-194` stores `PROP_TERMS`/`PROP_ACCEPTED_AT` and the NVIDIA equivalents
  **separately**.
- NVIDIA's own disclosure is already in the code at `AiProviders.kt:71`:
  *"Internal testing/evaluation only, not production. Shared rate limits; …"*.
- A provider switch already has a user-driven path: `AiViewModel.kt:1420` `selectProvider`.
- **There are exactly three `client.stream(` call sites** — `AiViewModel.kt:905`, `:1281`,
  `:1508` — and the standing law requires that number to stay at three.
- **S8:** *"Every recipient is disclosed per request; switching is never silent. A provider
  change needs a fresh tap and a fresh preview. Consent is never replayed across providers."*

Because consent is already stored per provider, a backup provider the user has never accepted
cannot be used until they accept it. That is existing behaviour, not something this part invents.

## Design

```kotlin
enum class AiBackupMode { OFF, MANUAL }

object AiBackupProviderPolicy {
    /** A backup is offered only on a provider-attributable failure or a rate-limit stop. */
    fun offer(
        mode: AiBackupMode,
        failedProvider: AiProviderId?,
        stopReason: AiAgentStopReason,
        backupConfigured: Boolean,
        backupTermsAccepted: Boolean,
    ): AiBackupOffer

    /** Never the provider that just failed; never a provider without accepted terms. */
    fun candidate(current: AiProviderId, configured: Set<AiProviderId>, termsAccepted: Set<AiProviderId>): AiProviderId?
}
```

The offer renders as a card that names the **other** provider, its label
(`AiProviderId.label`, which for NVIDIA already reads *"dev/test only"*), and the fact that a new
preview follows. Tapping it routes through the **existing** `selectProvider` path at
`AiViewModel.kt:1420` and then through the ordinary D4 preview. No request leaves the phone
without that preview.

**The three-site law is the design constraint.** The backup path must reuse one of the existing
`client.stream(` sites by changing the `session.provider` it is given — exactly as
`selectProvider` does today — rather than adding a fourth call site. A source pin counts the sites
and fails at four.

### What "manual only" rules out

- No automatic retry against a different provider.
- No provider chosen from a failure code without a tap.
- No replay of the Gemini acceptance for NVIDIA, or the reverse.
- No silent model change: `AiProviders.kt:26-27` keeps each provider's own default model, and
  the default model itself is unchanged (**no default-model change is authorized**).

## The Android edge

One row in `AiHome` (off / manual) and one card in the timeline at a provider failure or
rate-limit stop. `AiRateLimit.kt` already classifies the rate-limit case; this part reads it, it
does not extend it.

## Exit condition

- With `OFF`, no backup offer ever appears and behaviour matches `6838ea6`.
- With `MANUAL`, an offer appears only on a provider failure or rate-limit stop, and only for a
  provider that is configured **and** has accepted terms.
- Accepting produces a fresh preview naming the new provider before Send (**S8**, **D4**).
- The provider that just failed is never offered.
- Exactly three `client.stream(` call sites remain.
- No automatic switch occurs under any condition.

## Tests (plan)

- `AiBackupProviderPolicyTest` — **8**: `OFF` never offers; an offer only on the two stop
  reasons; the failed provider is excluded; an unconfigured provider is excluded; a provider
  without accepted terms is excluded; both-excluded yields no offer.
- `AiLevel10WiringTest` — **3 new**: source pins that the `client.stream(` count is exactly
  three; that no path assigns a provider without going through `selectProvider` + preview; that
  the default model constants are unchanged.

## Sources (record)

1. Repository: `AiProviders.kt:4-6,21-22,26-27,31-32,38-39,47-49,70-71,79-86`,
   `AiViewModel.kt:905,1281,1420,1508`, `AiKeyStore.kt:189-194`, `AiRateLimit.kt` — read
   2026-10-03 against `main` @ `6838ea6`.
2. **S8** in
   [`00_AGENTIC_MAP_AND_SECURITY_RULES.md`](../../../roadmaps/ai-integration/00_AGENTIC_MAP_AND_SECURITY_RULES.md);
   D4 in
   [`00_LEVEL0_DECISION_RECORD.md`](../../../roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md).

## Deferred / rejected with reasons

- **Automatic fallback** — rejected by the owner (2026-10-03) and by S8/D4: a provider switch is
  a new data recipient and needs fresh consent, not a replayed one.
- **Rotating between several keys at one provider** — rejected; the shared map's own verdict is
  that multiple keys at one provider usually share quota, so it does not solve rate limits.
- **Parallel requests to two providers** — rejected; S7, one brain writes, and it would double
  request count and spend.
- **Promoting NVIDIA beyond dev/test** — rejected; Phase 82's internal-testing-only limit stands.
