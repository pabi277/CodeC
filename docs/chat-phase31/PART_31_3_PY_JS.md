# CodeC Phase 31.3 — Python / JS-TS install cards

**Status:** ✅ **CARDS SHIPPED** (2026-09-07, owner: "Start phase 31").
The Packages hub now has:

- **IntelliSense: Python (pylsp)** — installs `python-lsp-server`
  via `pip install --user python-lsp-server` (Python 3 itself is the
  existing Phase 12 card).
- **IntelliSense: JavaScript / TypeScript (tsserver)** — installs
  `typescript` + `typescript-language-server` via
  `npm install -g typescript typescript-language-server` (Node.js
  itself is the existing Phase 20.1 card).

The orchestrator picks the server up automatically once the binary
(`pylsp` or `typescript-language-server`) lands in `$PREFIX/bin/`.
The real wire (sora `LspEditor` ↔ pylsp / tsserver) is the 31.1 §3.1
follow-up.

**Pip/npm rule:** if a pip/npm name is not in the CodeC apt repo
(neither is — Phase 20.1 only published `nodejs` as a deb), use the
documented `pip install --user` / `npm i -g` inside PREFIX after
`python` / `nodejs` exist. **Never invent a `com.termux` package**.

---

## 1. Design

| Language | Card id | Probe binary | Install command | Notes |
|---|---|---|---|---|
| C / C++ | `intellisense-c-cpp-clangd` | `clangd` | `pkg install -y clang` | Phase 20.1 ships clang |
| Python | `intellisense-python-pylsp` | `pylsp` | `pkg install -y python-pip && pip install --user python-lsp-server` | Phase 12 ships python3 + python-pip; chained so the card is self-sufficient on a fresh userland |
| JS / TS | `intellisense-js-tsserver` | `typescript-language-server` | `npm install -g typescript typescript-language-server` | Phase 20.1 ships nodejs (npm is a sibling of node) |

All three cards are `PackageCategory.LANGUAGES`, so they show up in
the LANGUAGES filter and in the ALL list with no ModulesScreen edit.

The orchestrator probe is `$PREFIX/bin/<binary>` (matched via the
application's `filesDir/usr/bin/<binary>` symlink, which is the same
path `ModulesScreen.checkIsInstalled` and the Phase 21 D.2 install
gate use). One install flips both the card and the orchestrator.

## 2. Exit condition

```text
(Device)
1. Fresh .py / .ts without pylsp / tsserver: snippets only; typing never blocked.    ← host PASS (no-op provider)
2. Install card → server attached → `import json` / `console.` complete.            ← install path ready; "attached" is the 31.1 wire (pending)
3. RUN ▶ still uses CodeC's `python` / `node` for the file.                        ← unchanged
PASS = (1) and (3) ship today; (2) is the device round + 31.1 wire.
```

### 2.1 Device recipe (owner)

**Python**
1. Open a `.py` file. Type `import ` — expect Phase 30 snippet
   `import ... as` plus identifier items; pylsp is NOT on disk.
2. Open Packages tab → tap "IntelliSense: Python (pylsp)" → INSTALL
   runs the **chained** install in the live terminal:
   `pkg install -y python-pip && pip install --user python-lsp-server`.
   The first half lands `pip` on PATH (Phase 12 publishes `python-pip`
   as its own deb in the CodeC apt repo; idempotent if already
   installed — device round 2026-09-07 caught a fresh userland
   failing with `pip: command not found` on the bare `pip install`
   form). The second half installs the LSP server into `$PREFIX/`.
3. Wait for the install to finish; come back. Re-type `import ` — the
   card flips to INSTALLED.
4. RUN ▶ the file — expect CodeC's `python3` to run (Phase 21 path);
   pylsp is analysis only.
5. (When the 31.1 wire ships) type `import ` — expect a `json` /
   `os` / `re` list from pylsp's Jedi memory in the strip BEFORE
   the engine's items.

**JavaScript / TypeScript**
1. Open a `.ts` file. Type `cons` — expect Phase 30 snippet + identifier
   items; tsserver is NOT on disk.
2. Open Packages tab → tap "IntelliSense: JavaScript / TypeScript
   (tsserver)" → INSTALL runs `npm install -g typescript
   typescript-language-server` in the live terminal.
3. Wait; come back. Re-type `cons` — the card flips to INSTALLED.
4. RUN ▶ the file — expect CodeC's `node` to run; tsserver is
   analysis only.
5. (When the 31.1 wire ships) type `Window.` — expect a list from
   tsserver's DOM types in the strip BEFORE the engine's items.

## 3. Follow-ups

- The sora `LspEditor` wire (§3.1 of 31.1) is what flips
  "card INSTALLED + orchestrator probe TRUE" into "actual LSP
  completion in the strip". Until that ships, step 5 of either
  device recipe returns only the Phase 30 snippet world.
- gopls (Go) and rust-analyzer (Rust) stay disabled until those
  compilers are `inRepository`. The catalog does not list them.
