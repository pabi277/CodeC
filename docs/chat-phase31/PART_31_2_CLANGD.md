# CodeC Phase 31.2 — clangd via Packages

**Status:** ✅ **CARD SHIPPED** (2026-09-07, owner: "Start phase 31").
The Packages hub now has an **IntelliSense: C / C++ (clangd)** card
that runs `pkg install -y clang`. The orchestrator picks the server
up automatically once `bin/clangd` lands in `$PREFIX/`. The real
wire (sora `LspEditor` ↔ clangd) is the 31.1 §3.1 follow-up.

**Invariant:** do not overwrite `cc`; clangd is a sibling binary.
Phase 20.1's `apply-recipe-overrides.sh` strips `bin/cc` from the
libllvm deb to keep the TCC frontend (the cc invariant); clangd
survives intact.

---

## 1. Design

C is still default/TCC. IntelliSense for C is **clangd** from the clang
toolchain package (or a `clangd` package if split). Packages hub card:

**IntelliSense: C / C++ (clangd)** → `pkg install -y clang` (if not
present) then attach.

Project root = current project dir. Flags: `compile_flags.txt` in the
project if present; else `-I` from settings / `$PREFIX/include`.

TCC-built files still RUN with `cc`; clangd is analysis only.

### 1.1 What was built (this commit)

| File | Role |
|---|---|
| `IntelliSenseCatalog.kt` | The C/C++ card. `id = "intellisense-c-cpp-clangd"`, `binary = "clangd"`, `installCommand = "pkg install -y clang"`. |
| `LspStatusResolver.kt` | Pure `(LanguageType, BinaryProbe) -> LspStatus`. Returns `Installed` when the probe binary is on disk, `AvailableCard` when the language has a card but the binary isn't on disk, `NoCard` otherwise. The orchestrator stays the source of truth. |
| `ModulesScreen` integration | `PackageCatalog.ALL_PACKAGES` is `originalPackages + IntelliSenseCatalog.cards`, so the existing UI surfaces the new card with no UI change. |
| `SystemBinaryProbe` | Now takes the application `filesDir`; `SystemBinaryProbe.NoOp` keeps the null-arg default for the host tests. |

## 2. Exit condition

```text
(Device)
1. Fresh .c without clangd: snippets only; typing never blocked.    ← host PASS (no-op provider)
2. Install card → clangd attached → `#include` / struct member complete.  ← install path ready; "attached" is the 31.1 wire (pending)
3. RUN ▶ still uses TCC `cc` for .c (Phase 21 invariant).            ← unchanged
PASS = (1) and (3) ship today; (2) is the device round + 31.1 wire.
```

### 2.1 Device recipe (owner)

1. Open a `.c` file. Type `printf` — expect snippet + identifier items
   in the strip; clangd is NOT on disk.
2. Open Packages tab → tap "IntelliSense: C / C++ (clangd)" → INSTALL
   runs `pkg install -y clang` in the live terminal.
3. Wait for the install to finish; come back to the editor. Re-type
   `printf` — the card flips to INSTALLED.
4. RUN ▶ the file — expect TCC `cc` to run (Phase 21 invariant);
   clangd is analysis only.
5. (When the 31.1 wire ships) type `stdin.` — expect `__stdin` from
   clangd's snippet memory in the strip BEFORE the engine's items.

## 3. Follow-ups

- The sora `LspEditor` wire (§3.1 of 31.1) is what flips "card
  INSTALLED + orchestrator probe TRUE" into "actual LSP completion in
  the strip". Until that ships, step 5 of the device recipe returns
  only the Phase 30 snippet world.
- 31.3 ships the same shape for Python + JS/TS (see PART_31_3).
