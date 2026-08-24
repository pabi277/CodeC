# CodeC repository signing operations

**Status:** Corrective signed Pages run `32642631785` and the real arm64
signed-client acceptance path passed against the exact v3 signing subkey.
Key-seeded build run `32643383952` passed both architectures and exact-keyring
validation; release run `32648783080` revalidated and published all four assets.
The rebuilt-bootstrap clean-device acceptance test then passed on a real
aarch64 device on 2026-08-24 (full uninstall/reinstall, exact keyring hash,
warning-free signed `pkg update`, independent `gpgv` tamper rejection, package
lifecycle, clean audit, compiler smoke — see
[`PHASE3_DEVICE_ACCEPTANCE.md`](PHASE3_DEVICE_ACCEPTANCE.md) §8). Signing is
fully device-verified end to end.

## Trust model

CodeC uses an offline OpenPGP primary key and a dedicated signing subkey:

- primary fingerprint: `3185B4D219C5EF30B263F5E50A458891ED0FB8D3`;
- repository signing subkey: `328500868CE9B0F74B62CEFC1D7D52F6F8135015`;
- versioned public keyring: `codec-archive-keyring-v1.gpg`;
- installed path: `$PREFIX/etc/apt/keyrings/codec-archive-keyring-v1.gpg`.

The offline primary key and revocation certificate must remain offline. Only the
protected signing-subkey export belongs in GitHub Actions. No private-key or
passphrase material belongs in Git, an APK, a bootstrap, an Actions artifact,
workflow output, or chat.

**Pre-publication replacement record.** The initial candidate
`46A371F74AF6D594CDD66C2893C9C5B5136C1ED4` / signing subkey
`787AFCCE525C45D4201E3AA3F896C6D8DF8BE456` was retired when its protected CI
export could not be recovered. A subsequent offline candidate was abandoned
before export after its passphrase handling was invalidated. Neither candidate
ever signed Pages or entered a released client/bootstrap. Therefore the current
public key safely replaced the unreleased v1 trust file before first use; normal
post-release overlap rules did not apply.

The APK and Phase 3 bootstrap distribute the exact committed public keyring.
CodeC's source uses `signed-by=` to restrict APT to that keyring. Before APT is
invoked, `pkg` independently downloads `InRelease`, verifies it with `gpgv`, and
checks the signed `Origin: CodeC` and `Suite: stable` fields. APT then verifies
the same signed metadata independently and follows the signed Release → index →
package SHA-256 chain.

## CI configuration

The publication workflow expects exactly these repository settings:

| Kind | Name | Required content |
|---|---|---|
| Actions secret | `CODEC_REPOSITORY_SIGNING_KEY` | protected secret-key export containing the CI signing subkey |
| Actions secret | `CODEC_REPOSITORY_SIGNING_PASSPHRASE` | passphrase for that export/subkey |
| Actions variable | `CODEC_REPOSITORY_SIGNING_FINGERPRINT` | `328500868CE9B0F74B62CEFC1D7D52F6F8135015` |

Publication fails closed if any value is absent, the fingerprint is malformed or
differs from the committed signing-subkey fingerprint, the secret key cannot be
imported/unlocked, signing fails, either signature is missing, or signed
validation fails. The passphrase is sent to GPG through stdin with loopback
pinentry; it is never a process argument.

## Safe rollout order

1. Merge/apply the reviewed publication workflow without dispatching it.
2. Publish the repository from an already successful immutable package-build
   run. This regenerates correct Release-relative index hashes, signs the merged
   repository, validates it against the committed keyring/fingerprint, and
   deploys the public key files. Retain `Release` and `Release.sha256` during
   this compatibility window for old clients.
3. Verify the deployed `InRelease`, `Release.gpg`, keyring, and fingerprints over
   HTTPS from a separate client.
4. Only then install/test the signed-only APK. Existing Phase 3 userlands already
   contain `gpgv`; the APK installs the pinned public key atomically.
5. Rebuild and republish the Phase 3 bootstrap with the seeded public key only
   after separate explicit approval. This is the expensive step.
6. Complete the signed-device acceptance section in
   `PHASE3_DEVICE_ACCEPTANCE.md` before calling Part D complete.

Never publish an unsigned repository after signed-only clients are released.
Never work around an incident with `[trusted=yes]` or a second ambient keyring.

### First signed publication evidence

Initial workflow run `32641097388` reused the successful immutable package
artifacts from run `32620704350`, skipped both expensive architecture builds,
and established both exact-key signature forms. The first CodeC-device APT run
then correctly rejected hash fields placed after blank Release-stanza
separators. Commit `0fa9823` fixed that Debian-control grammar and made the
validator reject regressions. Corrective run `32642631785` again skipped both
builds, signed, validated, and deployed. A separate Termux fetch verified both
live `VALIDSIG` records, public key/fingerprints, exact Release cleartext,
Origin/Suite, checksum sidecar, and tamper rejection. The CodeC device then
passed warning-free APT update, exact-key/tamper checks, nano lifecycle, clean
audit, and compiler smoke.

## Routine signing and validation

`sign-repository.sh` accepts a repository tree and the exact signing-subkey
fingerprint. It creates both signature forms. `validate-repository.py` must then
be called with both trust arguments:

```sh
codec-packages/scripts/sign-repository.sh packages/dev \
  328500868CE9B0F74B62CEFC1D7D52F6F8135015
python3 codec-packages/scripts/validate-repository.py packages/dev \
  --architectures aarch64 x86_64 \
  --keyring codec-packages/keys/codec-archive-keyring-v1.gpg \
  --signing-fingerprint 328500868CE9B0F74B62CEFC1D7D52F6F8135015
```

This example assumes an isolated `GNUPGHOME` already contains the protected CI
subkey and `CODEC_SIGNING_KEY_PASSPHRASE` is set without shell-history or argv
exposure. Normal publication should use Actions, not a developer workstation.

## Signing-subkey rotation

Rotation must preserve an overlap window; replacing a key before clients trust
it locks those clients out.

1. Offline, create a new dedicated signing subkey under the offline primary.
2. Export a new **public-only** versioned keyring containing both the currently
   trusted and new subkeys. Verify its fingerprints and verify that `gpg
   --show-keys --with-colons` contains no `sec`/`ssb` records.
3. Add the new keyring version to the APK/bootstrap code and keep publication
   signed by the old subkey.
4. Release and device-test clients/bootstrap that trust the overlap keyring.
5. Replace the two CI secrets and pinned fingerprint variable with the new
   protected signing subkey. Publish and validate with the new fingerprint.
6. After the supported-client migration window, a later keyring version may
   remove the retired subkey. Keep historical public keys and fingerprints for
   auditability.

Do not overwrite `codec-archive-keyring-v1.gpg` with incompatible trust content;
use a new versioned filename and bump the client/bootstrap reference.

## Revocation or compromise

1. Stop Pages publication immediately. Do not publish new metadata with the
   suspected key and do not fall back to unsigned metadata.
2. Determine whether the signing subkey alone or the offline primary is
   affected. Keep incident evidence and the last known-good signed Pages
   artifact immutable.
3. Revoke the affected key offline using the protected offline material. Create
   a replacement signing subkey only from a trusted offline primary; if the
   primary is compromised, establish a completely new offline primary.
4. Ship a new APK/keyring through the independently authenticated APK update
   path. A repository signed only by the replacement key must not appear until
   clients have received that trust update.
5. Replace Actions secrets/variable, publish a newly generated and signed tree,
   and rerun device acceptance. Never import the offline primary into CI.

A compromised old signing key cannot safely authorize its own replacement.
Client trust must then be recovered through the APK/bootstrap distribution path,
not by trusting a key file from the affected repository.

## Repository rollback

Rollback restores one complete, previously validated signed tree atomically:
packages, indexes, `Release`, `InRelease`, `Release.gpg`, manifest, and sidecars
must all come from the same retained publication artifact. Do not combine an old
Release signature with regenerated indexes or packages. Re-run signed validation
against the pinned keyring before deployment.

A repository rollback does not delete an installed prefix and does not silently
downgrade installed packages. If a package downgrade is required, publish a new
higher-version corrective package or perform a separately reviewed explicit
downgrade procedure. Keep the previous bootstrap release available until its
replacement passes acceptance.
