# Part 76.2 — key storage, acceptance and redaction

Phase: [76](README.md) · Decisions D3 and O1 of the [Level 0 record](../../../roadmaps/ai-integration/00_LEVEL0_DECISION_RECORD.md)

## First move: evidence, not code

- `androidx.security:security-crypto` (EncryptedSharedPreferences) is deprecated upstream (research dossier, Addendum A) → not used.
- The GitHub token is plaintext in DataStore today (`datastore/settings.preferences_pb`); the AI key deliberately does **not** follow that pattern.
- `res/xml/backup_rules.xml` includes only `CodeC/projects`; `noBackupFilesDir` is Android's never-backed-up directory — both keep the key out of backups.
- minSdk 24 ≥ API 23 needed for `KeyGenParameterSpec` AES-GCM.

## Design

`AiKeyStore(context)` (`ui/ai/AiKeyStore.kt`) and `AiKeyBlob` (`ui/ai/AiKeyBlob.kt`, pure):

- Files: `noBackupFilesDir/ai/gemini_key.bin` (blob) and `ai_settings.properties` (`model`, `terms_version`, `terms_accepted_at` — non-secret).
- Keystore alias `codec_ai_gemini_key_v1`, AES-256, `AES/GCM/NoPadding`, 128-bit tag, non-exportable.
- Blob format `[version=1][ivLen][iv][ciphertext]`; anything else ⇒ rejected.
- `saveKey` writes tmp → rename, then the acceptance; if the acceptance write fails the key is deleted at once.
- `loadKey` ⇒ the plaintext for **one request**; any failure (Keystore reset, corrupt file, bad version) ⇒ `deleteKey()` and null ⇒ UI says *"Your saved key can't be read anymore. Please enter it again."* — **never a plaintext fallback**.
- `isReady` requires a current `TERMS_VERSION` acceptance; a stale acceptance deletes the key (a new terms version means a fresh confirmation).
- `deleteKey` removes blob, Keystore entry and acceptance; the model choice stays.
- Nothing in `ui/ai` logs (pinned).

**Redaction (Level 0 record §4.2 — both halves):**
- *Shape:* `FeedbackDraft` gains `AIza[0-9A-Za-z_\-]{20,}` (20 floor like the PAT rule — a truncated paste is still a secret).
- *Literal:* `FeedbackInput.extraSecrets` / `redact(…, extraSecrets)`; `FeedbackSectionCard` loads the stored key once (IO, `runCatching`) purely to scrub its exact text from log/crash lines — so a future key format no shape knows is still caught. Literals shorter than 8 chars are ignored so they cannot shred a report. The key is never rendered or kept beyond the screen.
- The helper itself never logs the key (pinned), so both rules are a second line of defence against a user paste.

## Exit condition

- No code path stores the key unencrypted; decrypt failure removes it.
- Deleting the key clears the 18+/terms acceptance.
- A feedback report never contains an `AIza…` key.

## Tests (as built)

- `AiAnswerTest` — 2 blob cases (round-trip; rejects empty/other version/zero IV/short/plaintext).
- `AiHelperWiringTest` — `noBackupFilesDir`, `"AndroidKeyStore"`, `"AES/GCM/NoPadding"`; no SharedPreferences/DataStore/`filesDir`/external storage; key not in `AiUiState`; nothing logs.
- `FeedbackDraftTest` — +2 cases: the key shape is redacted in any position (a bare `AIza` word stays); the stored literal is scrubbed whatever its format (short literals ignored).
- The Keystore round-trip itself is **device-only** (Robolectric has no AndroidKeyStore provider) → DEVICE_ROUND rows A3–A6.

## Deferred / rejected with reasons

- **No DataStore key** (standing law): the non-secret settings live in a small properties file beside the blob, so they share its no-backup location and are deleted with it.
- **No new permission, no new dependency.**
- **User authentication–bound key (biometric)** — deferred: one more prompt per request contradicts the single-click law; can be offered later as an option.
- **Migrating the GitHub token to Keystore** — out of scope for Level 1; noted for a separate phase.
