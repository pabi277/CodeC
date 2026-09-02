# CodeC Phase 20.1 — Core toolchains + interpreted languages

**Status:** 🚧 **IMPLEMENTED (2026-09-01, `arena/01a05cb9-codec`) — host tests green (100 total);
round-4 repo build awaiting owner re-dispatch (3rd attempt = split into base/llvm/langs parallel legs)** · **Cost:** `[repo-build]`

> **Build-round log (CI dispatches):**
> 1. `33506104710` — **killed at the 360-min job ceiling** (6h01m, both arches
>    "cancelled"). → D9 fired; the D10 libllvm build-time trim (AArch64/X86
>    backends only, lldb/mlir/polly dropped) became the permanent recipe state.
> 2. `33544558167` — **aborted at ~3.5 min** by the trim's own fail-loud guard:
>    my synthetic test fixture had flattened two real upstream shapes (the host
>    `ninja` tblgen command is two continued lines; `termux_step_host_build`
>    has its own `-DLLVM_ENABLE_PROJECTS='clang;clang-tools-extra;lldb;mlir'`
>    line, which the broad `lldb;mlir` post-check then false-matched); a
>    subsequent edit also introduced CRLF line endings that broke bash parsing.
>    Fixed in `49d8d81` and verified by a rehearsal applying the override
>    script to the **real fetched recipe files** (16/16 checks OK) — the
>    committed fixtures now carry the exact upstream bytes.
> 3. `33547475854` — **killed at the 360-min ceiling again** (6h00m): even the
>    trimmed round doesn't fit one job. → **D11 split:** the build job now
>    fans out per arch into three parallel group legs — `base` (the round 1–3
>    catalog + the Phase 3 bootstrap), `llvm` (libllvm alone), `langs`
>    (nodejs/npm/php/ruby/lua54). Groups are resolved from
>    `CODEC_REPOSITORY_GROUP_*` in `properties.codec.sh` (single source of
>    truth; a host tripwire test asserts they're an exact partition of the
>    full list and that the workflow's matrix/artifact/publish references
>    agree). publish-dev pattern-merges the legs' artifacts
>    (`codec-repository-<arch>-*` + `merge-multiple: true`) — its existing
>    "harvest all debs + regenerate metadata" merge needed no change.
>    `publish-bootstrap-release.yml` now reads the `-base` artifacts (the
>    bootstrap builds only in that leg).
· **Depends on:** nothing
· **Blocks:** Phase 21 (D.2 needs `gcc` in the repo)
· **Target files:** `codec-packages/properties.codec.sh` (`CODEC_REPOSITORY_PACKAGES`)
· **CI workflow:** `.github/workflows/package-repository.yml`

> **Implementation corrections (found during §3 Step 1 research, 2026-09-01):**
> the pinned upstream ref has **no `packages/gcc` and no `packages/clang` at
> all**. The compile root is **`libllvm`** whose `clang` subpackage already
> ships `bin/gcc`, `bin/g++`, `bin/c++`, `bin/cpp` driver symlinks (see §7) —
> so users install **`clang`** (`pkg install clang`) and get the `gcc` UX for
> free. Also: `npm` was split out of `nodejs` upstream (since 25.3.0-1), so
> `npm` is its own root. Six **roots** added: `libllvm`, `nodejs`, `npm`,
> `php`, `ruby`, `lua54`.

---

## 1. Context & motivation

The CodeC package repository today ships Python and a handful of CLI tools.
Phase 21's `LanguageRunProfile` registry will invoke `gcc`, `node`, `php`,
`ruby`, and `lua` for their respective languages — but `pkg install gcc` must
work first. This part adds those toolchain packages to `CODEC_REPOSITORY_PACKAGES`
and verifies they build and install correctly.

**Packages to add** (Termux recipe names at the pinned `TERMUX_PACKAGES_REF`
— **as verified against the real tree**, see §7):

| Root recipe | What ships | Notes |
|---|---|---|
| `libllvm` | LLVM 21.1.8 toolchain: subpackages `clang` (incl. `bin/clang*`, `bin/clang-format` **and** the `gcc`/`g++`/`c++`/`cpp` driver symlinks), `lld`, `llvm`, `llvm-tools`, `libcompiler-rt` (+`lldb`/`mlir`/`libpolly` excluded — build-time trim, D10, after the 360-min timeout of run `33506104710`) | The actual compiler; there is **no** `gcc` or `clang` recipe at the pinned ref. CodeC strips `bin/cc` from the clang subpackage (cc invariant — see D5). |
| `nodejs` | `node` 26.4.0-1 | No X11 deps; upstream preinst notice neutralized (D6) |
| `npm` | npm/npx 11.19.0 | Split out of nodejs upstream at 25.3.0-1; own root, postinst neutralized (D6) |
| `php` | PHP 8.5.1 CLI + `php-fpm`, `php-sodium` | **Trimmed:** apache/ldap/pgsql/gd extensions and their build/runtime closures removed (D7); `php-apache*`, `php-ldap`, `php-pgsql`, `php-gd` subpackages excluded |
| `ruby` | Ruby 3.4.1-2 + `gem` | Clean closure, no override needed |
| `lua54` | Lua 5.4.8-10 as `lua`/`luac` | `.alternatives` postinst replaced by plain symlinks (D8) |

> **Note on `clang`:** "Bundled Clang" today is downloaded separately as a module
> (`ModuleCatalog`). After Phase 20.1, `clang` is a proper `pkg` package from the
> CodeC repo, installed via `pkg install gcc` (since `gcc` declares `Depends: clang`).
> The old "Bundled Clang" module path may be deprecated in Phase 21.

---

## 2. The "gcc = Clang" hard reality (read before implementing)

> **In the CodeC/Termux userland, `gcc` is NOT real GNU GCC — and at the
> pinned revision it is not even a package.** Upstream Termux removed the old
> `packages/gcc` shim recipe entirely (verified: no `packages/gcc`, no
> `packages/gcc-defaults`, no `packages/clang`, no `packages/llvm` at
> `1bbe66903526df2e8af51e704316bc68ede72603` — full-tree API listing). The
> compiler is the `libllvm` recipe; its `clang` subpackage creates the
> compatibility symlinks in `termux_step_post_make_install`:
> `for tool in clang clang++ cc c++ cpp gcc g++; ln -f -s clang-<major> $tool`.
> So `bin/gcc` is a symlink into Clang — same behavior the plan anticipated,
> just delivered by the `clang` deb itself, not by a separate `gcc` deb.
>
> **⇒ Users run `pkg install clang`** and then `gcc foo.c -o foo` works.
>
> Why there is no real GNU GCC: the Android NDK dropped GCC in r18 (2018)
> because GCC cannot properly target Android/Bionic (crtbegin/crtend,
> in-libc threading, lld linker incompatibilities). Termux followed, and
> eventually deleted even the shim recipe — Clang's own driver-mode symlinks
> (`gcc`, `g++`, `c++`, `cpp` → `clang-<major>`) made it redundant.
>
> **Consequence for this phase:** adding the `libllvm` root gives users
> `gcc foo.c -o foo` at the shell — it compiles with Clang. The UX is
> identical to every Linux machine where `gcc` is installed; the underlying
> compiler is Clang. This is the correct, battle-tested choice and what every
> Termux user already experiences.
>
> A genuine GNU GCC for Android/Bionic exists only in the `tur-repo`
> third-party overlay and is fragile. It is **out of scope** for CodeC.
>
> See `RESEARCH_NEXT_PHASES.md` §D.2.1 for the full rationale.

---

## 3. Implementation steps

### Step 1 — Verify the pinned ref ✅ DONE (2026-09-01)

Findings recorded in §7 below (the package table had to be corrected from
the research — the `gcc`/`clang` recipes do not exist at the pinned ref).

### Step 2 — Add packages to `CODEC_REPOSITORY_PACKAGES` ✅ DONE

Six roots appended (round-4 comment block explains each):
`libllvm`, `nodejs`, `npm`, `php`, `ruby`, `lua54`.

> **Do not add `golang` or `rust` here** — they are C.2 (heavy, opt-in).

### Step 3 — Recipe overrides ✅ DONE (all in `apply-recipe-overrides.sh`)

- **libllvm/clang:** strip exactly `bin/cc` from the clang subpackage include
  list (cc invariant; D5); keep `gcc`/`g++`/`c++`/`cpp`.
- **nodejs:** neutralize the recipe's own `termux_step_create_debscripts`
  (upstream `preinst` npm-split notice) — last-defined no-op, python-pip
  pattern.
- **npm:** same neutralization (upstream `postinst` notice).
- **php:** trim apache/ldap/pgsql/gd configure flags + `postgresql`
  build-dep, replace `termux_step_post_make_install` (no apache assembly,
  conf.d for sodium only), exclude `php-apache{,-ldap,-pgsql,-sodium}`,
  `php-ldap`, `php-pgsql`, `php-gd` subpackages. `php-fpm`/`php-sodium` stay.
- **ruby:** no override needed (clean closure, no maintainer scripts).
- **lua54:** remove `lua54.alternatives` (postinst not allowlisted), append
  `termux_step_post_massage` creating relative `bin/lua`/`bin/luac` (+man)
  symlinks.
- **ruby/nodejs/php X11 scan:** none of the five recipes has X11/GUI
  build-depends at the pinned ref (checked each `TERMUX_PKG_DEPENDS` /
  `TERMUX_PKG_BUILD_DEPENDS` line).

Every new override follows the existing conventions: exact-line drift checks
that **fail loudly** on pinned-revision changes, marker-guarded appends.
Hermetic tests: +10 cases in `codec-packages/tests/test_recipe_overrides.py`
(fixture trees run through the real override script: cc strip + drift,
nodejs/npm debscripts, php trim + drift, lua54 alternatives + drift).

### Step 4 — Commit and request CI build

Commit message: `[repo-build] Phase 20.1: add libllvm/clang nodejs npm php ruby lua54 toolchains to CODEC_REPOSITORY_PACKAGES`

(The `[repo-build]` tag is informational: `package-repository.yml`'s build job
runs only on manual `workflow_dispatch` — pushes never trigger it, so
committing is always safe; dispatching waits for explicit owner confirmation.)

> **⚠ WARNING:** Do NOT trigger `package-repository.yml` without explicit owner
> confirmation. Committing is fine; running the build workflow is not. After
> committing, report to the owner: "Ready for `[repo-build]` — trigger when
> convenient. Expected CI time: ~90–120 min (clang is the long-pole build)."

### Step 5 — After CI completes (owner-triggered)

Once the owner triggers and CI finishes:
1. Verify all six packages appear in the published repo metadata.
2. If any package fails to build: isolate it (remove from the list, re-trigger),
   fix the recipe override, re-add. Never block all six on one failure.

---

## 4. Exit condition & device recipe

The part is **done** when all of the following pass:

```sh
# On the owner's device, with the new APK installed:

# 1. C compiler
pkg update
pkg install clang        # There is NO gcc package at the pinned ref — the
                         # clang deb ships the gcc/g++ driver symlinks
gcc --version            # EXPECT: ... clang version 21.x ... (Clang-based — correct)
echo '#include<stdio.h>
int main(){printf("Hello gcc\n");}' > /tmp/t.c
gcc /tmp/t.c -o /tmp/t && /tmp/t
# EXPECT: Hello gcc

# 1b. cc invariant — the app's own frontend MUST survive the clang install:
which cc && head -c 60 "$(command -v cc)"
# EXPECT: $PREFIX/bin/cc is still the CodeC/TCC frontend script (NOT a link to clang)

# 2. C++
g++ --version            # Should also print Clang-based version
echo '#include<iostream>
int main(){std::cout<<"Hello g++\n";}' > /tmp/t.cpp
g++ /tmp/t.cpp -o /tmp/t && /tmp/t
# EXPECT: Hello g++

# 3. Node
pkg install nodejs npm   # npm was split out of nodejs upstream at 25.3.0-1
node --version           # Should print: v26.x.x
node -e "console.log('Hello Node')"
# EXPECT: Hello Node
npm --version            # EXPECT: 11.x.x

# 4. PHP
pkg install php
php --version
php -r "echo 'Hello PHP\n';"
# EXPECT: Hello PHP

# 5. Ruby
pkg install ruby
ruby --version
ruby -e "puts 'Hello Ruby'"
# EXPECT: Hello Ruby

# 6. Lua
pkg install lua54
lua --version
lua -e "print('Hello Lua')"
# EXPECT: Hello Lua
```

**PASS** = all six steps produce the expected output. No `com.termux` packages
involved (verify with `dpkg -l | grep termux`; only `com.codeci.ide` packages
should appear).

---

## 5. Non-goals & invariants

- **Not in C.1:** `golang`, `rust` (→ C.2); app code changes (→ D).
- **`build-package.sh -I` is FORBIDDEN.**
- **Never add `python-tkinter` or anything pulling the X11 closure.**
- The bootstrap seed and manager roots (`CODEC_BOOTSTRAP_PACKAGES`,
  `CODEC_PACKAGE_MANAGER_BOOTSTRAP_PACKAGES`) are **not changed** —
  gcc/clang install on demand, not at first boot.
- Published bootstrap archives stay byte-identical.

---

## 6. Design decisions

- **D1 — gcc as a Clang shim:** Deliberate. See §2. The behavior is identical
  to every Termux device; users get the `gcc` UX; the compiler is Clang.
  No genuine GNU GCC attempt.
- **D2 — clang as a `pkg` package, not a module:** The "Bundled Clang" module
  (`ModuleCatalog`) predates the package repo. After Phase 20.1, `clang` lives
  in the package repo (installed as a `gcc` dependency). The module path is
  superseded but not actively removed yet (Phase 21.4 will clean it up).
- **D3 — one `[repo-build]` trigger for all six:** Build all six in one CI run
  to minimize CI-time consumption. If one fails, remove it from the list and
  re-trigger for the remainder; do not hold the others hostage.
- **D4 — no bootstrap change:** Tools install on demand. A fresh device without
  userland still works (Phase 22/B only need the app code; no userland required
  for the editor smoothness or IME keys fix).
- **D5 — `bin/cc` stripped from the clang subpackage (cc invariant):** the
  upstream clang deb ships `bin/cc`→`clang-21` along with the other driver
  symlinks. In CodeC, `$PREFIX/bin/cc` is the app's own TCC frontend written
  by `ShellEnvironment`; letting a deb overwrite it would silently rewire
  every existing compile path the moment a user installs clang, and the
  states could flip-flop with app starts. The override removes exactly the
  `bin/cc` include line (fail-loud on drift); `gcc`/`g++`/`c++`/`cpp` are
  kept — nothing in the userland owns those names. Phase 21.4 revisits `cc`
  when TCC is retired.
- **D6 — nodejs/npm maintainer scripts neutralized:** both recipes define
  their own `termux_step_create_debscripts` (preinst/postinst notices).
  Maintainer scripts are forbidden for every package except the five reviewed
  alternatives packages; per-recipe definitions override the shared stub, so
  each recipe gets the appended last-definition-wins no-op (the exact
  python/python-pip precedent from CI repo-build 33308884424).
- **D7 — php trimmed to the CLI reality of a phone IDE:** upstream php's
  default configure flags pull apache2 (apxs in the MAIN configure +
  patchelf assembly), openldap, postgresql (configure + build-dep) and
  libgd (png/freetype/jpeg/webp/avif) into the source-build closure. None of
  that serves `php file.php` / `php -S`. Removed flags: `--with-ldap{,-sasl}`,
  `--with-pgsql=shared` / `--with-pdo-pgsql=shared`, `--with-apxs2`,
  `--enable-gd=shared` / `--with-external-gd`, and
  `TERMUX_PKG_BUILD_DEPENDS="postgresql"`; excluded subpackages
  `php-apache{,-ldap,-pgsql,-sodium}`, `php-ldap`, `php-pgsql`, `php-gd`;
  replaced `termux_step_post_make_install` with a trimmed twin (fpm conf,
  php.ini templates, sodium-only conf.d, phpize fix). Kept: mysqli/pdo-mysql
  (mysqlnd — zero closure cost), fpm, sodium, intl, curl, sqlite, mbstring…
- **D8 — lua54 `lua`/`luac` as plain symlinks, not alternatives:** the
  upstream `lua54.alternatives` would generate an update-alternatives
  postinst — and the repository validator approves maintainer scripts only
  for coreutils/less/nano/bat/util-linux. Rather than extending the review
  surface for a phone that will never carry two Lua versions, the override
  removes the `.alternatives` file and ships relative
  `bin/lua`→`lua5.4`, `bin/luac`→`luac5.4` symlinks (plus the renamed man
  pages) from `termux_step_post_massage`. Bonus: `lua` works even if the
  device's alternatives DB is ever damaged.
- **D9 — timeout risk accepted and recorded:** round 4 adds LLVM 21 (with
  hostbuild), nodejs, php, ruby to the same dispatch that rebuilds the
  existing 33 roots on both arches; round 2 alone took 1h53m. The
  `package-repository.yml` `build` job allows 360 min/arch — this round may
  approach or exceed it. If a timeout red happens, the recovery is D10's
  LLVM trim. (Raising `timeout-minutes` is pointless: 360 min is GitHub's
  per-job ceiling for hosted runners.)
- **D10 — LLVM build-time trim (PERMANENT since the 2026-09-01 timeout):**
  round-4 build `33506104710` was killed at the 360-minute per-job ceiling
  after 6h01m inside the monolithic build step (GitHub-hosted runners cannot
  raise that ceiling — splitting lists across dispatches would publish an
  incomplete repo, since `publish-dev` merges artifacts from a single run).
  `apply-recipe-overrides.sh` therefore permanently trims libllvm: only the
  two device backends are built (`AArch64;X86` instead of `all` + the
  experimental ARC/CSKY/M68k/VE), `lldb`/`mlir`/`polly` leave
  `LLVM_ENABLE_PROJECTS` and are excluded as subpackages (CodeC run profiles
  need clang/clang-format, lld, llvm, compiler-rt — never lldb, mlir or
  polly), the host tblgen build shrinks to the still-needed tools, and the
  target-coupled subpackage include lines that no longer exist after the
  backend trim are removed (`bin/wasm-ld`, `bin/amdgpu-arch`,
  `bin/nvptx-arch`, `bin/offload-arch`) so subpackage creation cannot fail
  on missing files. Everything fails loudly on pinned-recipe drift, same as
  every other override.
- **D11 — build job split into parallel group legs (after the second 360-min
  kill, run `33547475854`):** a 6h-capped job cannot compile the whole round
  even trimmed, and the ceiling cannot be raised. The build job fans out per
  arch into **base / llvm / langs** legs; each leg resolves its roots via
  `build-package-repository.sh <arch> <group>` from `CODEC_REPOSITORY_GROUP_*`
  in `properties.codec.sh` (single source of truth; `CODEC_REPO_DRY_RUN=1`
  makes resolution host-testable — the only dry path, before any mkdir or
  clone). The Phase 3 bootstrap builds/validates/uploads only in `base`.
  Artifacts are group-suffixed and publish-dev pattern-merges them
  (`codec-repository-<arch>-*`, `merge-multiple: true`) — its raw-deb
  harvest + regenerate step already performed exactly this merge, so the
  published repo shape is unchanged. Consistency is tripwired in
  `test_ci_guardrails.py`: exact partition (no missed/duplicated root),
  workflow matrix/artifact/publish references agree, and
  `publish-bootstrap-release.yml` reads the `-base` artifacts. Calling the
  script without a group keeps the legacy one-shot behavior for local runs.

---

## 7. Research notes (measured 2026-09-01 against the live pinned tree)

> Sources: GitHub API full-tree listing of `termux/termux-packages` at
> `1bbe66903526df2e8af51e704316bc68ede72603` (10,197 blobs) + raw recipe
> files fetched via `api.github.com` contents API. `TERMUX_PACKAGES_REF` =
> `1bbe66903526df2e8af51e704316bc68ede72603`
> (`codec-packages/properties.codec.sh`, pinned for Phase 3 on 2026-08-20).

- **`packages/gcc/build.sh` exists?** **NO.** Neither does `packages/clang`,
  `packages/llvm`, or `packages/gcc-defaults`. The only matches for "gcc" in
  the whole tree are unrelated patches and `mingw-w64-gcc-libs`. The old gcc
  shim recipe was deleted upstream; `libllvm/build.sh` even carries
  `TERMUX_PKG_CONFLICTS="gcc, …"` + `TERMUX_PKG_REPLACES="gcc, …"` noting
  "Replace gcc since gcc is deprecated by google on android".
- **Where do `gcc`/`g++` come from then?** `packages/libllvm/build.sh`
  `termux_step_post_make_install`: `for tool in clang clang++ cc c++ cpp gcc
  g++; ln -f -s "clang-${TERMUX_PKG_VERSION%%.*}" "$tool"`. The
  `clang.subpackage.sh` include list contains these names — including
  *triplet-prefixed* `*-linux-android*-gcc` wrappers. Version: LLVM 21.1.8-3.
- **`packages/clang/clang-format.subpackage.sh` exists?** **NO.** There is
  no `clang-format` subpackage anywhere in the tree; `clang-format` ships
  inside the `clang` subpackage via the `bin/clang*` include glob
  (clang-tools-extra is in `LLVM_ENABLE_PROJECTS`).
- **clang subpackage runtime deps:** `libcompiler-rt, lld, llvm, ndk-sysroot`;
  libllvm parent deps: `libc++, libffi, libxml2, ncurses, zlib, zstd`. All
  source-built in-closure by the existing pipeline. **No maintainer scripts**
  in libllvm or any of its 8 subpackages (audited every `*.subpackage.sh`).
- **nodejs/build.sh X11/GUI build-depends?** **None.** `TERMUX_PKG_DEPENDS=
  "libc++, openssl, c-ares, libicu, libsqlite, zlib, libffi"`. It *does*
  define its own `termux_step_create_debscripts` → `preinst` (npm-unbundled
  notice) → neutralized (D6). Version 26.4.0-1.
- **npm is a separate recipe** (`packages/npm`, 11.19.0,
  `TERMUX_PKG_PLATFORM_INDEPENDENT=true`, `TERMUX_PKG_DEPENDS="nodejs |
  nodejs-lts"`) since nodejs 25.3.0-1 — npm is NOT bundled with nodejs at
  the pinned ref, so `npm` was added as its own root. It also has its own
  `postinst` notice → neutralized (D6).
- **php 8.5.1:** runtime deps include `libcurl, libxml2, libxslt, libzip,
  oniguruma, openssl, pcre2, tidy, capstone, libicu` — no X11. BUT the
  configure flags + `TERMUX_PKG_BUILD_DEPENDS="postgresql"` would pull
  **apache2, openldap, postgresql, libgd** into the build just for extension
  modules (ldap, pgsql, gd, apache SAPI) and `termux_step_post_make_install`
  assembles php-apache extension copies with patchelf → trimmed (D7). All 9
  php subpackage files audited: scriptless.
- **ruby 3.4.1-2:** deps `libandroid-execinfo, libandroid-support, libffi,
  libgmp, readline, openssl, libyaml, zlib` — no X11, no debscripts, no
  override.
- **lua54 5.4.8-10:** build-deps `readline` only; BUT ships
  `lua54.alternatives` (`lua → lua5.4`, `luac → luac5.4`, priority 140, with
  man-page dependents) — a maintainer script outside the reviewed allowlist
  → replaced by plain symlinks (D8). Without either mechanism, the upstream
  deb would ship only `lua5.4` and the device recipe's `lua --version` would
  fail.
- **New `apply-recipe-overrides.sh` entries:** clang `bin/cc` strip, nodejs
  debscripts no-op, npm debscripts no-op, php trim, lua54 alternatives
  removal + symlink step. (+10 hermetic tests; full suite 95 green locally.)
- **Sizes:** measured post-build (the repo metadata gets the exact numbers);
  rough expectations: clang/lld/llvm ~80–120 MB/arch combined, nodejs ~30 MB,
  php ~15 MB, ruby ~20 MB, lua54 ~3 MB.
- **CI timeout:** recorded as D9, fired twice (build-round log above);
  D10 trims the recipe, D11 split the job — group legs are the shipped
  mechanism (a per-dispatch libllvm split would publish an incomplete repo,
  since publish-dev merges artifacts from a single run).
