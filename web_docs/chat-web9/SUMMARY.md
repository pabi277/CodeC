# Course licensing and package-safe publication — 2026-10-07

## Authority and current status

D36: owner delegated the course license choice and requested completed docs and
merge to main. D37: owner explicitly added website deployment. Baseline branch
commit: `24a7dfec871cfdb0a3baec47d0e4b8745908d056`; main: `d4231f0`.
Work stays on `arena/8b8f8edc-codec`.

**Local preparation complete; CI/merge/publication results belong to the delivery
handoff and GitHub Actions/PR records.** D38 explicitly permits the two test-only
fixture corrections and one PR for this session while old PR42 and unrelated
PR83 remain untouched. No application/package runtime or signing edits.

## Licensing — O7 closed

- Original course educational prose, exercises and instructional diagrams:
  **CC BY 4.0**. Credit, license link and change notices are required; commercial
  sharing/adaptation is permitted under its terms.
- Original copyable lesson source code/shell commands: **MIT**. Keep copyright
  and permission notices with copies or substantial portions of the software.
- Full texts and precise scope are in `website/learn/licenses/`. Copyright notice:
  2026 pabi277 and CodeC course contributors. No warranty; compliant recipients'
  permissions cannot simply be revoked later.
- Excludes app/package binaries, third-party content, site implementation/shared
  navigation, non-course material, README artwork and marks. No root LICENSE or
  global relicensing. Removed unsupported broad app open-source claims from the
  website; source availability is stated instead.
- Course home, all 30 footers and root README expose the license. Original teaching
  code is unchanged. Existing Android16/aarch64 owner device evidence still stands.

## Publication design

[Operations/security notes](../deploy/README.md).

Pages is configured for Actions at https://pabi277.github.io/CodeC/ and main is
already allowed by the `github-pages` environment. Last successful deployment was
package run33669069048; old artifacts have expired. New preparation recovers the
public package tree, verifies pinned-key signatures and every signed-index package
hash, then adds the website without changing package/key bytes. No private key,
package build, install, metadata generation or re-signing is used by the website job.

New `.github/workflows/website-pages.yml`: read-only verification on selected branch
pushes; publication only from main, followed by all public site/package byte checks.
The existing package publisher gets only a shared non-cancelling lock and website
composition step. Its builds, signing and validation remain intact. This minimal
integration is required so a future package publication does not erase the website.
No Build APK, bootstrap workflow, application or package-runtime changes.

## Local evidence

- 30 pages, 38 files; 1,139 internal links/anchors; no missing links or orphans.
- Unique metadata, 30 sitemap URLs and JSON-LD documents; original preformatted
  lessons byte-identical to accepted course baseline.
- HTML validation: all 30 pages pass.
- 120 responsive/axe cases (320/360/768/1440), zero violations/overflow in tested
  states; 30 extracted offline/no-JS pages; three local web-example fixtures pass.
- 38 local deployment-prefix HTTP routes/assets/license texts match source bytes.
- 14 new deployment-safety/license tests pass; path/collision/symlink guards,
  wrong-key/invalid-signature/hash failures and workflow publication boundaries.
- Corrected package suite: 94 tests, zero failures, four skips (`gpg` signer
  unavailable locally; `gpgv` verification is available). The initial run exposed
  two baseline fixture problems; D38 explicitly authorized their test-only fixes.
  The abort mock rejected the automatic first-use index refresh before reaching
  confirmation; allow that refresh while retaining the install-mutation guard.
  The error fixture now fails the actual download and checks exact exit100,
  original streamed error/current guidance, no installation and marker cleanup.
  The initial TTY diagnosis was incorrect; piped confirmation input is supported.
  No failing assertion is suppressed; application/package-runtime bytes unchanged.
- Sandbox direct HTTPS to Pages fails TLS; live recovery/public checks must run on
  GitHub-hosted CI. No live-download or publication result is claimed yet.

Machine evidence: STATIC_REPORT.json, BROWSER_REPORT.json, HTTP_REPORT.json,
ARCHIVE.json. Screenshots/tooling are scratch-only, not website dependencies.
Historical chat-web7/8 reports remain historical rather than being overwritten.

## Full website ZIP

`web_docs/chat-web9/CodeC-website-licensed.zip`: **209,200 bytes**, SHA-256
`c8761230c75acbcc1342ed2784ee1728d6f7c34f40905b1149d0f3b505311cbb`.

All 38 source files, CRC, safe paths and exact extracted bytes pass; offline tests
used this extraction. This ZIP excludes packages/keys, app binaries and deployment
tooling; it contains the complete product site/course and license texts.
After push, verify GitHub uploaded bytes and deliver a commit-pinned direct URL.
Import into a new CodeC project, preserve folders, open root index.html and RUN.

## Remaining execution

D38 scope resolution is complete. Commit/push only this branch. Watch normal
Build APK, package host checks and read-only Pages preparation. Require green
checks for the one authorized session PR, then merge via GitHub (never push main
or switch branch). Watch main's automatic deployment and Build APK; retain actual
run/commit/deployment/public-byte results in the PR delivery comment and final
handoff. No old PR merge, search-console submission, guaranteed indexing or
host-root robots change. This preparation record does not invent future results.

## Primary license references

- https://creativecommons.org/licenses/by/4.0/ and its linked legal code.
- https://opensource.org/license/mit
- Verbatim CC text retrieved through GitHub from SPDX license-list-data v3.27.0,
  `text/CC-BY-4.0.txt`; MIT text uses the standard terms with the course copyright.
