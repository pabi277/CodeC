# CodeC Phase 31.2 — clangd via Packages

**Status:** 📋 PLANNED · **Cost:** `[client-only]` + existing clang pkg
· **Effort:** M · **Depends on:** 31.1
· **Invariant:** do not overwrite `cc`; clangd is a sibling binary.

---

## 1. Design

C is still default/TCC. IntelliSense for C is **clangd** from the clang
toolchain package (or a `clangd` package if split). Packages hub card:

**IntelliSense: C / C++ (clangd)** → `pkg install -y clang` (if not
present) then attach.

Project root = current project dir. Flags: `compile_flags.txt` in the
project if present; else `-I` from settings / `$PREFIX/include`.

TCC-built files still RUN with `cc`; clangd is analysis only.

## 2. Exit condition

```text
(Device)
1. Fresh .c without clangd: snippets only; typing never blocked.
2. Install card → clangd attached → `#include` / struct member complete.
3. RUN ▶ still uses TCC `cc` for .c (Phase 21 invariant).
PASS = all three.
```
