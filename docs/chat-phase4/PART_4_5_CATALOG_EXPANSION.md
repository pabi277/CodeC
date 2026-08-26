# Part 4.5 — Expand the curated package catalog (round 2)

**Status:** COMPLETE and CI verified in run [`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723) (1h 53m 36s) on commit [`fbf69db`](https://github.com/pabi277/CodeC/commit/fbf69db) (2026-08-25).
**Exit condition:** ✅ MET — the expanded closure built 100% green in CI
for both architectures (`aarch64` in 1h 53m 02s, `x86_64` in 1h 27m 09s) with a valid signed repository and an
**unchanged** bootstrap archive. Part 4.6 publishes and device-verifies it.

This is the record of the concrete technical decisions that
[`PHASE4_ROADMAP.md`](PHASE4_ROADMAP.md) Part 4.5 left open, plus the
evidence collected while making them. The pinned revision used for all
recipe inspection below is the one in `codec-packages/properties.codec.sh`:
`termux-packages @ 1bbe66903526df2e8af51e704316bc68ede72603`.

---

## D1 — Package list (15 new repository roots)

| Root | Version @ pinned rev | Why |
|---|---|---|
| `git` | 2.55.0 | the most-requested dev-environment tool for a C IDE |
| `wget` | (pinned) | second HTTPS client alongside seeded `curl` |
| `bat` | 0.26.1 | pretty `cat`; registers a low-priority `pager` alternative |
| `ripgrep` | (pinned) | fast recursive search |
| `fd` | (pinned) | fast `find` replacement |
| `htop` | (pinned) | process/system monitor |
| `tmux` | (pinned) | terminal session multiplexer |
| `tree` | (pinned) | directory tree |
| `patch` | (pinned) | apply/patch (zero runtime deps) |
| `diffutils` | (pinned) | `diff`/`cmp` |
| `zstd` | (pinned) | modern compressor complementing seeded `gzip` |
| `m4` | (pinned) | macro processor for the autotools chain |
| `autoconf` | (pinned) | `configure` generation |
| `automake` | (pinned) | `Makefile.in` generation |
| `libtool` | (pinned) | portable library build wrapper (also ships `libltdl`) |

**Deferred to round 3 (deliberately, with reasons):**

- `vim` — its `TERMUX_PKG_BUILD_DEPENDS` are `luajit, perl, python, ruby,
  tcl`; in the from-source docker build that means compiling CPython, Ruby,
  Tcl and LuaJIT just to generate syntax files: ~20+ min per arch for a
  single editor.
- `openssh` — runtime deps `krb5, ldns, libedit, termux-auth, openssl,
  zlib`; krb5/ldns are heavy, and `termux-auth` is a Termux-specific recipe
  that needs its own review before CodeC builds it.
- `python3` — the largest single value but a large closure; it is its own
  part.

## D2 — Repository-only scope; the bootstrap is untouched

New packages are available **only via `pkg install` from the repository**.
`CODEC_PACKAGE_MANAGER_BOOTSTRAP_PACKAGES` and `CODEC_BOOTSTRAP_SEED_PACKAGES`
in `properties.codec.sh` are unchanged, so the bootstrap build inputs are
identical to the run that produced the published `userland-v2-dev` assets
(JOURNEY §5e/§5f digests). With `SOURCE_DATE_EPOCH=0` the rebuilt bootstrap
archive is expected to be **byte-identical** to the published one — the
strongest possible proof that the catalog expansion changed nothing about
the already device-verified Part B/D bootstrap. The CI build job still runs
`validate-bootstrap.py` in every run, and the Part 4.6 publish step compares
the rebuilt digests against the published ones.

## D3 — Recipe overrides (narrow, fail-loud, established pattern)

Two new blocks in `apply-recipe-overrides.sh`, both verified against the
real pinned tree (this record's scratch clone) before coding:

1. **`bash` loses `termux-tools`.** The official recipe declares
   `TERMUX_PKG_DEPENDS="libandroid-support, libiconv, readline (>= 8.3),
   termux-tools"`; `termux-tools` pulls the `termux-am`/`termux-core`
   activity-manager chain, which CodeC never ships (the Phase 2/3 invariant).
   The bootstrap build already applied this removal in
   `build-bootstrap.sh`; round 2 needs `bash` in the *repository* closure
   (via `libtool`), so the override now lives in
   `apply-recipe-overrides.sh` where **both** builds share it.
   `build-bootstrap.sh`'s original block remains as a no-op double-safety
   assertion. Verified on the pinned tree: the line rewrites to
   `"libandroid-support, libiconv, readline (>= 8.3)"` and fails loudly on
   drift.
2. **`git` GUI/subversion subpackages excluded + tcl/tk disabled.**
   `git-gitk` and `git-gui` depend on `tk` (→ `tcl` → the whole X11 stack:
   `libx11/libxcb/…/xorgproto`), and `git-svn` depends on `subversion-perl`.
   None belong in the CodeC userland. Each subpackage file gets
   `TERMUX_SUBPKG_EXCLUDED_ARCHES="aarch64 x86_64"` — the upstream-native
   per-arch skip (`termux_create_debian_subpackages.sh`), so the main `git`
   package stays byte-identical to the pinned recipe. A missing subpackage
   file aborts the build (pinned-revision drift must be reviewed, not
   papered over). Separately, `--with-tcltk=$TERMUX_PREFIX/bin/wish` becomes
   `--with-tcltk=no`: with tcl/tk excluded, `wish` does not exist at
   configure time, so git is built without Tcl/Tk support (gitk/git-gui are
   unbuildable on Android anyway; the main `git` package is unaffected).

No override is needed for `util-linux`: `wget`'s `libuuid` dependency has no
standalone recipe — `libuuid` is a *subpackage* of `util-linux`, and
`buildorder.py` maps subpackage names to their parent package
(`pkgs_map[subpkg.name] = new_package` in non-fast mode), so the builder
builds the parent from source and all subpackages (`libblkid`, `libfdisk`,
`libmount`, `libsmartcols`, `libuuid`, `uuid-utils`, `blk-utils`, `fdisk`,
`mount-utils`) come out as debs. Verified by reading the pinned
`scripts/buildorder.py`.

## D4 — Maintainer-script allowlist extended by two reviewed entries

The repository preflight (`repository_lib.py`) refuses any .deb with
maintainer scripts except reviewed ones. In the round 2 closure exactly two
packages generate them (they register the `pager` alternatives group —
verified by scanning **every** recipe in the new closure for
`.alternatives` files and `termux_step_create_debscripts` overrides):

| Package | Group | Alternative | Priority | Effect on device |
|---|---|---|---|---|
| `bat` | `pager` | `bin/bat` | **10** | `less` (50) stays the default pager; `bat` is selectable |
| `util-linux` | `pager` | `bin/more` | **25** | `less` (50) stays the default pager |

Both entries were measured against the pinned `.alternatives` files
(`packages/bat/bat.alternatives`, `packages/util-linux/more.alternatives`)
and added to the exact-byte validator with the same shape as
`coreutils`/`less`/`nano` (shebang, conditions, one `--install` + one
`--slave`, `--remove` in prerm). Because both priorities are below less's
50, installing round 2 packages **cannot** change the device's default
pager. Any deviation (e.g. a tampered postinst claiming priority 999) is
rejected at publish time — covered by
`test_rejects_tampered_bat_alternatives_script`.

## D5 — Expected closure (evidence from the pinned tree)

Closure walked over the **overridden** pinned tree with `buildorder.py`
semantics (first `|` alternative, `(...)` constraints stripped, subpackage
names resolved to parent packages):

- Round 1 (26 debs) → Round 1+2: **32 new build packages**, plus the
  `util-linux` subpackage debs and auto-generated `*-static` debs, so the
  published repository is expected to list roughly **75–85 debs per
  architecture** (the CI build is the source of truth; the manifest is the
  promise).
- New packages: `autoconf, automake, bash, bat, diffutils, fd, git, htop,
  libandroid-posix-semaphore, libandroid-utimes, libcap-ng, libcurl,
  libevent, libexpat, libgit2, libidn2, liblzma, libnghttp2, libnghttp3,
  libngtcp2, libssh2, libtool, libunistring, m4, patch, perl, ripgrep,
  tmux, tree, util-linux, wget, zstd`.
- **Banned-set check: clean.** None of `termux-am(-socket)`, `termux-core`,
  `termux-exec`, `termux-tools`, `tk`, `tcl`, `libx11`/X11, `subversion-perl`,
  `python`, `ruby`, `luajit` appears in the closure.
- Heaviest new builds per arch: `openssl` (shared by `git`/`bat`/`wget`
  via `libcurl`/`libgit2`), `perl` (via autoconf/automake), `util-linux`,
  `libcurl` + nghttp family, `libgit2`, and the Rust roots `git`, `bat`,
  `ripgrep`, `fd` (the docker builder's `termux_setup_rust` works — the
  official farm builds these from the same image/revision). Estimated
  marginal cost ~40–70 min per arch on top of round 1 + bootstrap; the
  build job timeout is 360 min.

## Exit conditions

**Part 4.5 (this record):**

1. ✅ `CODEC_REPOSITORY_PACKAGES` lists the 15 new roots (round 1 unchanged).
2. ✅ Host suite green locally (81 tests, 4 skipped) — green in CI `host-tests` job on push (`32844160914`).
3. ✅ `workflow_dispatch` build [`32845127723`](https://github.com/pabi277/CodeC/actions/runs/32845127723) from `arena/01a03477-codec` (both arches) 100% green: repository
   artifacts (`codec-repository-aarch64`, `codec-repository-x86_64`) contain all 15 roots + closure, `validate-repository.py` and
   `validate-bootstrap.py` pass.
4. ✅ Rebuilt bootstrap archives are byte-identical to the published
   `userland-v2-dev` assets (D2): aarch64
   `49cef1ccf82831e870d2d94537c5b9091cc71fa17c4eb0c27dc913d4e79248bf`
   (23,928,215 bytes), x86_64
   `8e9fd6a973a4c56a957d952aa0ecc1d01ac4788f9cf61bd9162fa6d93e873b4a`
   (23,824,737 bytes).

**Part 4.6** (publish + device gate): ✅ **DONE** (see [`PART_4_6_CATALOG_ACCEPTANCE.md`](PART_4_6_CATALOG_ACCEPTANCE.md)). Published via run [`32858460740`](https://github.com/pabi277/CodeC/actions/runs/32858460740) and verified on real arm64 hardware.

## Continue here (next session)

1. Dispatch the publish workflow with `source_run_id=32845127723` and `release_tag=userland-v2-dev`:
   ```sh
   gh workflow run "CodeC package repository" --ref arena/01a03477-codec -f publish=true -f source_run_id=32845127723
   ```
2. Perform clean-device verification of expanded catalog (`pkg update`, `pkg install git wget bat ripgrep fd htop tmux tree patch diffutils zstd m4 autoconf automake libtool`).
