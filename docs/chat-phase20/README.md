# CodeC Phase 20 — Package Toolchain Expansion (gcc/g++ + language packages in CI)

**Status:** 🚧 **C.1 IMPLEMENTED (2026-09-01, `arena/01a05cb9-codec`)** — host tests green
(93 total, +8 new); **`[repo-build]` CI dispatch + publish pending owner command**;
C.2 not started (depends on C.1's pipeline run). Started on the owner's
"Phase 20 start".
· **Cost:** `[repo-build]` — CI `package-repository.yml` only; **zero app Kotlin code changes**
· **Depends on:** nothing (CI/packages side only; can run in parallel with Phase 22)
· **Blocks:** Phase 21 (D needs `gcc` in the repo before wiring the app)

> **Research correction (2026-09-01):** at the pinned `TERMUX_PACKAGES_REF`
> there is **no `gcc` or `clang` recipe** — the compiler arrives as the
> **`libllvm`** root; its `clang` subpackage ships `bin/gcc`/`bin/g++`/
> `bin/c++`/`bin/cpp` driver symlinks (CodeC strips `bin/cc` to protect the
> app's own `cc` frontend until Phase 21.4). `npm` is a separate upstream
> recipe since nodejs 25.3.0-1 and is added as its own root. Six roots
> total: `libllvm nodejs npm php ruby lua54`. Details + full research notes:
> [PART_20_1_TOOLCHAINS.md](PART_20_1_TOOLCHAINS.md) §7.

> This phase is the **CI / package-repo side of the compiler redesign**. It adds
> the language toolchains that Phase 21's `LanguageRunProfile` registry will invoke —
> starting with `gcc`/`g++` (which resolve to Clang/LLVM wrappers in the Termux/CodeC
> userland) and expanding to `nodejs`, `php`, `ruby`, `lua54`, and optional heavy
> compilers (`golang`, `rust`) on demand. **No Kotlin code is written in this phase.**
>
> Full research & design rationale: [`docs/RESEARCH_NEXT_PHASES.md`](../RESEARCH_NEXT_PHASES.md)
> §Phase 20 and §D.2.1 (the "gcc = Clang wrapper" reality gate).

---

## Why this exists

Phase 21 retires TCC and wires C/C++ through a userland `gcc`/`g++` command —
but those commands must first exist in the CodeC package repository so
`pkg install gcc` works on-device. The existing Python path (Phase 12) proved the
model: add the package to `CODEC_REPOSITORY_PACKAGES`, build it in CI,
publish it, and the app's auto-install gate handles the rest. Phase 20 does
the same for C/C++ and every other language the `LanguageRunProfile` registry
will support.

**Hard constraint (must read):** In the Termux/CodeC userland `gcc` is NOT
real GNU GCC. It is a compatibility symlink pointing at Clang/LLVM — and at
the pinned upstream revision even the old shim recipe is gone: the `clang`
deb itself ships `bin/gcc`/`bin/g++`. **Phase 20 therefore adds the `libllvm`
(clang) root**, which gives users the `gcc foo.c -o foo` UX they expect while
actually running Clang. This is correct, battle-tested, and matches every
Termux user's experience. See `RESEARCH_NEXT_PHASES.md` §D.2.1 for the full
gate and PART_20_1 §2 for the as-built reality.

---

## The two parts

| Part | Title | What it delivers | Doc |
|---|---|---|---|
| **C.1** | Core toolchains + interpreted languages | `libllvm`(clang + gcc/g++ symlinks), `nodejs`, `npm`, `php`, `ruby`, `lua54` in the CodeC repo; CI green, device `pkg install` verified | [PART_20_1_TOOLCHAINS.md](PART_20_1_TOOLCHAINS.md) |
| **C.2** | Optional heavy compilers (on-demand) | `golang`, `rust` guarded by `[repo-build-heavy]` commit tag; size warnings wired into the auto-install prompt | [PART_20_2_HEAVY.md](PART_20_2_HEAVY.md) |

---

## ⚖️ Ground rules

- **GPL compliance:** all recipes are forked from termux-packages (GPL-3.0) with
  `TERMUX_PREFIX` repointed to `com.codeci.ide`. GPL kept; no source pasted into
  app code. Same policy as the Python round (Phase 12).
- **`build-package.sh -I` is FORBIDDEN** — never install official `com.termux`
  `.deb`s during a build.
- **Never trigger the package-repo build (`package-repository.yml`) without the
  owner's explicit confirmation** — it costs ~60–100+ min of CI time. Check
  `gh run list` first; never double-dispatch.
- No PR/merge without the owner's explicit command.

## 🔎 Research prompts

C.1's research is **done** — recorded in
[PART_20_1_TOOLCHAINS.md](PART_20_1_TOOLCHAINS.md) §7 (verified against the
live pinned tree 2026-09-01: no `gcc`/`clang` recipes exist; `libllvm` is the
compile root; `clang-format`/`gcc`/`g++` ship *inside* the clang subpackage;
npm is its own recipe; php needed a heavy-extension trim; lua54's
alternatives postinst became plain symlinks).

Before implementing C.2:
- Check the pinned `TERMUX_PACKAGES_REF` in `codec-packages/properties.codec.sh`
  for the recipe state of `golang`, `rust` at that exact commit.
- For `golang` and `rust`, check CI artifact sizes at the pinned ref and record
  them in `PART_20_2_HEAVY.md` §1.

## Standing rules (unchanged)

- **No PR/merge and no push to `main` without the owner's explicit command.**
- CI (`Build APK`) is the only test executor — the sandbox has no JDK.
- Verify state (`git status`, `gh run list`) before acting.
- A part is done only when its exit condition is met (CI green + device
  `pkg install <tool>` verified by the owner).
