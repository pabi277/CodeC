# Package-safe website publishing

The CodeC Pages site is also the signed APT endpoint. **Never deploy only website/**
and never replace `dev/` or `keys/` with an empty tree. Owner authorization D37
covers this publication setup; it does not authorize unrelated app/package changes.

## Flow

1. `website-pages.yml` runs safety/static tests on selected branch pushes, without
   a Pages environment or publication permissions. It fetches the existing public
   package snapshot using HTTPS with the normal certificate checks.
2. Live public trust files must exactly equal `codec-packages/keys/`. Verify both
   InRelease and detached Release signatures and cleartext equality with the pinned
   signing fingerprint. There is no private-key access or re-signing.
3. Read only the six expected signed APT indexes. Check their SHA-256 values,
   safe package paths, bounds and every downloaded package hash/size. Require JSON
   manifest equality with signed indexes; run the existing full repository validator.
   Snapshot the complete generator output: marker, JSON/sidecar, Release/sidecar,
   signatures, six indexes, all indexed debs and three public trust files.
4. Check live metadata again after download; reject concurrent changes. Compose
   the static website into that verified tree. Reserved prefixes (case-insensitive),
   dotfiles, symlinks and destination file collisions fail before copying. Assert
   protected file manifests remain identical. Add `.nojekyll` to the artifact only.
5. Only main uploads the combined Pages artifact and enters `github-pages` for
   deployment. Recheck the current live metadata/key hashes immediately before
   publishing. Pages settings already allow main; no environment bypass is needed.
6. After publication, check every public website file and every protected package
   file against the prepared hash/size, plus directory entry URLs, MIME types and
   noindex headers. A short bounded homepage propagation wait is included.
   Publish results as Actions artifacts, not as an invented local/live pass.

The shared `codec-pages` concurrency group serializes this workflow with the
existing package publisher's publishing job; `cancel-in-progress: false` protects
an active publish. The package publisher adds the website only after its own full
signing/validation. Never publish from an old branch predating this integration,
which lacks the shared lock/composition step. Do not run a competing legacy Pages
workflow while publishing. New package artifacts/suites/keys need explicit review;
this recovery tool deliberately fails on an unexpected layout or key change.

## Failure and recovery

- Missing file, bad key/signature/hash, changed live snapshot or oversized tree:
  fail preparation; no Pages upload/deployment occurs. Existing live files stay put.
- A failed post-publication check is an incident, not a successful handoff. Inspect
  preserved/public report artifacts and HTTP/CDN propagation before re-running.
- Do not trigger a package rebuild or generate a new signing key to fix a website
  job. The current package tree can be recovered without either action.
- For a website rollback, revert website source on the session branch, review and
  merge through the normal gate. A new combined deployment preserves the *current*
  signed repository. Never blindly redeploy an old full Pages artifact, which might
  roll the package repository back too.
- If the existing public repository is unavailable, stop. Restore it from a known
  complete, validated backup under separate owner authorization; do not deploy a
  website-only fallback. These scripts do not promise recovery of unindexed files
  outside the verified generator layout; stop for an operator layout change.

## Maintenance and boundaries

Main pushes touching website/deployment paths publish automatically after successful
preparation; a main workflow_dispatch can retry the same safe flow. Other branch
runs never publish. The agent still needs owner authority for new work/merges;
this workflow is not permission to auto-start phases. No Search Console/Bing
verification, indexing/ranking promise, root-host robots edit or app release.

The static site still needs no browser build/runtime, CDN, analytics or JavaScript.
The downloadable website-only ZIP remains the offline course artifact, not an APT
mirror. Licensing is narrowly documented at `website/learn/licenses/SCOPE.txt`.

```sh
python3 -m unittest discover -s web_docs/deploy -p 'test_*.py' -v
python3 web_docs/chat-web9/checks/check_site.py
# CI/public-host access required; output must not already exist:
python3 web_docs/deploy/preserve_packages.py /path/to/new/artifact \
  --report /path/to/package-preservation.json
python3 web_docs/deploy/compose_site.py website /path/to/new/artifact
# Only after authorized publication:
python3 web_docs/deploy/verify_live.py /path/to/package-preservation.json \
  /path/to/live-verification.json
```

Required host tools: Python 3, `gpgv`, `dpkg-deb` and the existing package validator's
standard-library tooling. No credentials in script parameters. Large recovered debs,
Pages artifacts and browser tooling belong in runner/scratch storage, never Git.
