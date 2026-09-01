# CodeC Phase C — Package Toolchain Expansion (gcc/g++ + language packages in CI)

**Status:** 📋 **PLANNED** — not yet started. Awaiting owner's explicit "Start Phase C" command.
· **Cost:** `[repo-build]` — CI `package-repository.yml` only; **zero app Kotlin code changes**
· **Depends on:** nothing (CI/packages side only; can run in parallel with Phase A)
· **Blocks:** Phase D (D needs `gcc` in the repo before wiring the app)

> This phase is the **CI / package-repo side of the compiler redesign**. It adds
> the language toolchains that Phase D's `LanguageRunProfile` registry will invoke —
> starting with `gcc`/`g++` (which resolve to Clang/LLVM wrappers in the Termux/CodeC
> userland) and expanding to `nodejs`, `php`, `ruby`, `lua54`, and optional heavy
> compilers (`golang`, `rust`) on demand. **No Kotlin code is written in this phase.**
>
> Full research & design rationale: [`docs/RESEARCH_NEXT_PHASES.md`](../RESEARCH_NEXT_PHASES.md)
> §Phase C and §D.2.1 (the "gcc = Clang wrapper" reality gate).

---

## Why this exists

Phase D retires TCC and wires C/C++ through a userland `gcc`/`g++` command —
but those commands must first exist in the CodeC package repository so
`pkg install gcc` works on-device. The existing Python path (Phase 12) proved the
model: add the package to `CODEC_REPOSITORY_PACKAGES`, build it in CI,
publish it, and the app's auto-install gate handles the rest. Phase C does
the same for C/C++ and every other language the `LanguageRunProfile` registry
will support.

**Hard constraint (must read):** In the Termux/CodeC userland `gcc` is NOT
real GNU GCC. It is a compatibility wrapper / symlink pointing at
Clang/LLVM. This is intentional — NDK dropped GCC in r18 (2018) and Termux
followed. A genuine GNU GCC for Android/Bionic exists only in fragile
third-party overlays. **Phase C adds the Termux `gcc` package**, which gives
users the `gcc foo.c -o foo` UX they expect while actually running Clang.
This is correct, battle-tested, and matches every Termux user's experience.
See `RESEARCH_NEXT_PHASES.md` §D.2.1 for the full gate.

---

## The two parts

| Part | Title | What it delivers | Doc |
|---|---|---|---|
| **C.1** | Core toolchains + interpreted languages | `gcc`, `clang`, `nodejs`, `php`, `ruby`, `lua54` in the CodeC repo; CI green, device `pkg install` verified | [PART_C1_TOOLCHAINS.md](PART_C1_TOOLCHAINS.md) |
| **C.2** | Optional heavy compilers (on-demand) | `golang`, `rust` guarded by `[repo-build-heavy]` commit tag; size warnings wired into the auto-install prompt | [PART_C2_HEAVY.md](PART_C2_HEAVY.md) |

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

Before implementing each part:
- Check the pinned `TERMUX_PACKAGES_REF` in `codec-packages/properties.codec.sh`
  for the recipe state of `gcc`, `clang`, `nodejs` at that exact commit.
- Look up the `gcc` Termux recipe to confirm it is indeed a Clang wrapper at the
  pinned ref (it is — but verify before writing docs).
- Confirm `clang-format` is a sub-package of `clang` at the pinned ref.
- For `golang` and `rust`, check CI artifact sizes at the pinned ref and record
  them in `PART_C2_HEAVY.md` §1.

## Standing rules (unchanged)

- **No PR/merge and no push to `main` without the owner's explicit command.**
- CI (`Build APK`) is the only test executor — the sandbox has no JDK.
- Verify state (`git status`, `gh run list`) before acting.
- A part is done only when its exit condition is met (CI green + device
  `pkg install <tool>` verified by the owner).
