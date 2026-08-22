# Pending CI changes (GitHub App lacks `workflows` permission)

The Arena GitHub App that pushes `arena/01a0248f-codec` does **not** have the
`workflows` permission, so GitHub rejects any push that creates or updates
files under `.github/workflows/` (verified twice this session). Everything
else — app code, `codec-packages` build scripts, validators, tests, and docs —
is pushed normally.

Two small CI changes are staged here, ready to apply:

## 1. New workflow: publish the Phase 3 bootstrap release

`publish-bootstrap-release.yml` (in this folder) is complete and YAML-validated.

- Copy it to `.github/workflows/publish-bootstrap-release.yml`, or
- Grant the Arena GitHub App the `workflows` permission
  (GitHub → Settings → Applications → Arena → Permissions → Repository
  permissions → `workflows: read and write`), then ask the agent to push the
  branch again.

It publishes the validated bootstrap artifacts of a successful `CodeC package
repository` build run to the stable development release `userland-v2-dev`
(pre-release so it does not shadow `userland-v1` as "Latest"; `userland-*`
tags skip APK attachment). Dispatch with:

```text
source_run_id = <successful CodeC package repository run ID>
release_tag   = userland-v2-dev   (default)
```

## 2. Validator step in the existing build workflow

`package-repository.validator-step.patch` adds one step to
`.github/workflows/package-repository.yml` that runs
`validate-bootstrap.py` on each built bootstrap archive (defense in depth —
the publish workflow also validates before release).

```sh
git apply docs/ci-pending/package-repository.validator-step.patch
```

## Rollback (works regardless of the above)

- **Bootstrap/app:** the app automatically falls back to `userland-v1` when
  `userland-v2-dev` is removed or unreachable. To roll back a published
  release: `gh release delete userland-v2-dev --yes` (or replace its assets
  from the previous successful run via the publish workflow).
- **APT repository (Pages):** re-publish the previous manifest by dispatching
  "CodeC package repository" with `publish=true` and `source_run_id` set to
  the previous successful run (e.g. `32484160427` published the current tree
  from build `32469769089`).
- The app never deletes an installed prefix on update failure; staged
  extraction rolls back to the previous userland.
