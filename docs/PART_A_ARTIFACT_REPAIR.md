# Part A — repair the published bootstrap *in place* (no ~100-minute rebuild)

**Status: HELD by owner decision (2026-08-22). Do NOT execute either path,
dispatch the workflow, or upload any release asset without the owner's
explicit go-ahead.** Prepared work only (PR #11). The on-device self-heal
(`ShellEnvironment.pkgScript`) keeps fresh devices unblocked meanwhile.
**Branch:** `arena/01a02962-codec`

## Why this exists (evidence, not memory)

- Release `userland-v2-dev` was published from build run `32546404876`
  (commit `9d7be9d`, 2026-08-22T02:29Z).
- `git diff 9d7be9d..main -- codec-packages/` is **exactly one change** (+17
  lines): the `dpkg-perl` recipe override that drops the bogus runtime
  dependency `Depends: clang`
  (`codec-packages/scripts/apply-recipe-overrides.sh`).
- That override changes dependency metadata only (payload files identical),
  so the *entire* content delta between the published bootstrap and a full
  `main` rebuild is one line in `var/lib/dpkg/status`:
  `Depends: perl, clang, make` → `Depends: perl, make` — the same line the
  app's on-device self-heal rewrites via `sed` (already verified on device,
  see `docs/PHASE3_PKG_DEBUGGING.md`).
- Therefore rerunning the ~104-minute "CodeC package repository" build for
  Part A is avoidable: patching the published artifact achieves the same
  exit condition with a strictly verifiable one-line diff.

## Mechanics already in the repo

| Piece | Path |
|---|---|
| Repair script (guarded, idempotent, evidence-printing) | `codec-packages/scripts/repair-bootstrap-status.sh` |
| Host tests (5) | `codec-packages/tests/test_bootstrap_repair.py` |
| Dispatch workflow (staged) | `codec-packages/ci/repair-bootstrap-release.yml` |

The script: verifies the published sidecar, extracts the archive, prints the
`dpkg-perl` stanza before/after, patches **exactly one** full line inside the
`Package: dpkg-perl` paragraph only, proves via before/after `sha256sum`
snapshots of every extracted file plus an archive member-list diff that
nothing else changed, repacks with the assembler's invocation
(`tar -C <stage> -czf <out> .`), regenerates the standard two-field sidecar,
and runs the repository's own `validate-bootstrap.py` gate. It *refuses*
(exit 4) on any unexpected evidence instead of guessing; exit 3 means
"already clean — nothing to do".

## Execute — choose one path

### Path 1 — CI workflow (fully audited in Actions logs)

The automation token may lack the `workflows` permission needed to push to
`.github/workflows/`. Activate the staged workflow (one-time, needs your own
credentials — e.g. in Termux):

```sh
git clone https://github.com/pabi277/CodeC codec && cd codec
git checkout arena/01a02962-codec
git mv codec-packages/ci/repair-bootstrap-release.yml .github/workflows/
git commit -m "Part A: activate bootstrap repair workflow"
git push origin arena/01a02962-codec
```

Then merge the branch's PR (`main ← arena/01a02962-codec`) and dispatch:

```sh
gh workflow run "Repair CodeC bootstrap release" --ref main
gh run watch
```

The workflow downloads the published assets, repairs each arch, re-uploads
with `--clobber` **only if something changed**, re-downloads and
re-validates the freshly published bytes (validator + no-`clang` assertion
on the published status DB), and appends repair provenance to the release
notes.

### Path 2 — direct repair in Termux (no CI at all)

```sh
pkg install -y gh git python coreutils tar gzip
git clone --depth 1 --branch arena/01a02962-codec \
  https://github.com/pabi277/CodeC codec && cd codec
mkdir -p work/downloaded work/patched
cd work/downloaded
gh release download userland-v2-dev --pattern 'bootstrap-phase3-*' --clobber
sha256sum -c bootstrap-phase3-aarch64.tar.gz.sha256
sha256sum -c bootstrap-phase3-x86_64.tar.gz.sha256
cd ~/codec
bash codec-packages/scripts/repair-bootstrap-status.sh \
  work/patched work/downloaded/bootstrap-phase3-aarch64.tar.gz
bash codec-packages/scripts/repair-bootstrap-status.sh \
  work/patched work/downloaded/bootstrap-phase3-x86_64.tar.gz
gh release upload userland-v2-dev work/patched/* --clobber
```

Then verify the published bytes and record the new hashes:

```sh
cd ~/codec && rm -rf work/verify && mkdir -p work/verify && cd work/verify
gh release download userland-v2-dev --pattern 'bootstrap-phase3-*' --clobber
sha256sum -c bootstrap-phase3-aarch64.tar.gz.sha256
sha256sum -c bootstrap-phase3-x86_64.tar.gz.sha256
python ~/codec/codec-packages/scripts/validate-bootstrap.py \
  bootstrap-phase3-aarch64.tar.gz bootstrap-phase3-x86_64.tar.gz
tar -xzOf bootstrap-phase3-aarch64.tar.gz ./var/lib/dpkg/status \
  | grep -A6 '^Package: dpkg-perl$'
cat *.sha256
```

Paste the final output back so the release notes can be updated with the new
SHA-256 values and the repair provenance.

## Exit condition (unchanged — still needs a clean device)

Uninstall CodeC → fresh APK install → Install userland → `pkg install nano`
with **zero manual fixes** (no `clang` error anywhere). That is a physical
device step; it is *not* done by this runbook.

## What this path deliberately does NOT do

- It does **not** touch the apt repository index on Pages (the `clang` bug
  lived in the bootstrap's seeded *status DB*, not in published `Packages`
  metadata for `dpkg-perl`; the repo channel was built from the same run
  and `dpkg-perl`'s `Depends` there is a separate question for Part B).
- It does **not** change the app, the TCC toolchain, `cc`, or `bash`.
- It does **not** replace the eventual need for a full rebuild for Part B
  (seed-closure / postinst / md5sums work), which edits
  `assemble-bootstrap.sh` and genuinely requires one ~100-minute run.
