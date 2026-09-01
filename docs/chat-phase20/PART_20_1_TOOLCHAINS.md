# CodeC Phase 20.1 — Core toolchains + interpreted languages

**Status:** 📋 **PLANNED** — not yet started · **Cost:** `[repo-build]`
· **Depends on:** nothing
· **Blocks:** Phase 21 (D.2 needs `gcc` in the repo)
· **Target files:** `codec-packages/properties.codec.sh` (`CODEC_REPOSITORY_PACKAGES`)
· **CI workflow:** `.github/workflows/package-repository.yml`

---

## 1. Context & motivation

The CodeC package repository today ships Python and a handful of CLI tools.
Phase 21's `LanguageRunProfile` registry will invoke `gcc`, `node`, `php`,
`ruby`, and `lua` for their respective languages — but `pkg install gcc` must
work first. This part adds those toolchain packages to `CODEC_REPOSITORY_PACKAGES`
and verifies they build and install correctly.

**Packages to add** (Termux recipe names at the pinned `TERMUX_PACKAGES_REF`):

| Package | What ships | Approx size | Notes |
|---|---|---|---|
| `gcc` | `gcc`/`g++` Clang wrappers | ~0.5 MB | Termux recipe: symlinks to clang; NOT real GNU GCC (see §2) |
| `clang` | LLVM/Clang toolchain, `clang-format` sub-package | ~80 MB | The actual compiler; `gcc` depends on it |
| `nodejs` | `node`, `npm` | ~30 MB | JavaScript / TypeScript runtime |
| `php` | PHP CLI | ~15 MB | |
| `ruby` | Ruby runtime, `gem` | ~20 MB | |
| `lua54` | Lua 5.4 interpreter | ~3 MB | |

> **Note on `clang`:** "Bundled Clang" today is downloaded separately as a module
> (`ModuleCatalog`). After Phase 20.1, `clang` is a proper `pkg` package from the
> CodeC repo, installed via `pkg install gcc` (since `gcc` declares `Depends: clang`).
> The old "Bundled Clang" module path may be deprecated in Phase 21.

---

## 2. The "gcc = Clang" hard reality (read before implementing)

> **In the CodeC/Termux userland, `gcc` is NOT real GNU GCC.**
>
> The Termux `gcc` package provides a set of compatibility shim scripts:
> `gcc` → `clang`, `g++` → `clang++`, `cc` → `clang`, `c++` → `clang++`.
> This is correct behavior — the Android NDK dropped real GCC in r18 (2018)
> because GCC cannot properly target Android/Bionic (crtbegin/crtend,
> in-libc threading, lld linker incompatibilities). Termux followed.
>
> **Consequence for this phase:** adding the `gcc` Termux recipe gives users
> `gcc foo.c -o foo` at the shell — it compiles with Clang. The UX is identical
> to every Linux machine where `gcc` is installed; the underlying compiler is
> Clang. This is the correct, battle-tested choice and what every Termux user
> already experiences.
>
> A genuine GNU GCC for Android/Bionic exists only in the `tur-repo`
> third-party overlay and is fragile. It is **out of scope** for CodeC.
>
> See `RESEARCH_NEXT_PHASES.md` §D.2.1 for the full rationale.

---

## 3. Implementation steps

### Step 1 — Verify the pinned ref

Before touching any file, run:
```sh
# In the codec-packages/ subdir; read TERMUX_PACKAGES_REF from properties.codec.sh
grep TERMUX_PACKAGES_REF codec-packages/properties.codec.sh
# Then check: does packages/gcc/build.sh exist at that ref? Is it the shim?
# Does clang have a clang-format.subpackage.sh at that ref?
```
Record the answers as **Research notes** in §7 of this doc before proceeding.

### Step 2 — Add packages to `CODEC_REPOSITORY_PACKAGES`

In `codec-packages/properties.codec.sh`, append to the `CODEC_REPOSITORY_PACKAGES`
block (maintaining the existing alphabetical order within logical groups):

```sh
# C/C++ toolchain (gcc is the Clang-shim wrapper; clang is the actual compiler)
gcc
clang
# Interpreted language runtimes
nodejs
php
ruby
lua54
```

> **Do not add `golang` or `rust` here** — they are C.2 (heavy, opt-in).

### Step 3 — Check for recipe overrides needed

Review each recipe at the pinned ref for known Android/CodeC patching needs:
- `gcc`: expect no patch needed (it is just a `postinst` that creates symlinks).
- `clang`: the largest and most-patched; check if the existing CodeC
  `apply-recipe-overrides.sh` already handles it (Phase 3 used Clang for the
  manager bootstrap — if so, no new override needed).
- `nodejs`: check for any `TERMUX_PKG_EXTRA_CONFIGURE_ARGS` that reference
  `TERMUX_PREFIX`; these should resolve automatically via the overlay.
- `php`, `ruby`, `lua54`: typically lightweight; scan for X11/Tk/GUI build-depends
  (if any, exclude the sub-package via the same `apply-recipe-overrides.sh`
  pattern used for `python-tkinter`).

Record any new overrides added in §7.

### Step 4 — Commit and request CI build

Commit message: `[repo-build] Phase 20.1: add gcc/clang/nodejs/php/ruby/lua54 to CODEC_REPOSITORY_PACKAGES`

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
pkg install gcc          # Should install gcc + clang from the CodeC repo
gcc --version            # Should print: ... clang version ... (Clang-based — expected)
echo '#include<stdio.h>
int main(){printf("Hello gcc\n");}' > /tmp/t.c
gcc /tmp/t.c -o /tmp/t && /tmp/t
# EXPECT: Hello gcc

# 2. C++
g++ --version            # Should also print Clang-based version
echo '#include<iostream>
int main(){std::cout<<"Hello g++\n";}' > /tmp/t.cpp
g++ /tmp/t.cpp -o /tmp/t && /tmp/t
# EXPECT: Hello g++

# 3. Node
pkg install nodejs
node --version           # Should print: v2x.x.x
node -e "console.log('Hello Node')"
# EXPECT: Hello Node

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

---

## 7. Research notes (fill in before implementing)

> **TODO for the implementer:** Run the verifications in §3 Step 1 before touching
> any file. Record findings here:
>
> - `TERMUX_PACKAGES_REF` = ___________________
> - Does `packages/gcc/build.sh` exist at that ref? Is it the Clang-shim? ___
> - Does `packages/clang/clang-format.subpackage.sh` exist? ___
> - Does `packages/nodejs/build.sh` have any X11/GUI build-depends? ___
> - Any new `apply-recipe-overrides.sh` entries needed? ___
> - Estimated sizes at the pinned ref: gcc ___ MB, clang ___ MB, nodejs ___ MB
