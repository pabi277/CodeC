# CodeC Phase 31.4 — Language expansion (shell, HTML, CSS, JSON, YAML)

**Status:** ✅ **CARDS SHIPPED** (2026-09-07, owner: "If have a card in
sh file / In .css mar have many cards like margin 0 etc"). The
Packages hub now has 5 more IntelliSense install cards:

- **IntelliSense: Shell (bash)** — `npm install -g bash-language-server`
- **IntelliSense: HTML (vscode)** — `npm install -g @zed-industries/vscode-langservers-extracted`
- **IntelliSense: CSS (vscode)** — same install as HTML
- **IntelliSense: JSON (vscode)** — same install as HTML
- **IntelliSense: YAML (redhat)** — `npm install -g yaml-language-server`

The orchestrator picks each server up automatically once the probe
binary lands in `$PREFIX/bin/`. The real wire (sora `LspEditor` ↔
each server) is the 31.1 §3.1 follow-up.

**Why these 5?** Of the 13 languages that were NoCard after 31.3,
the user asked about shell + CSS specifically. CSS already has
80+ Emmet/CSS abbreviations in Phase 30 — the `margin 0` chips the
user saw ARE Phase 30's answer. But a real LSP server adds
schema-aware completions that snippets cannot (command-name for
shell; `$schema`-driven completion for JSON/YAML; HTML5 attribute
hints). For the other 8 NoCard languages (TEXT/MARKDOWN/GO/RUST/
PHP/RUBY/LUA/XML), the answer is unchanged — gopls/rust-analyzer
are heavy and stay deferred; PHP/Ruby/Lua need complex installs;
XML has no widely-deployed LSP; markdown/text have no good LSP.

---

## 1. Design

| Language | Card id | Probe binary | Install command | Notes |
|---|---|---|---|---|
| Shell | `intellisense-shell-bash` | `bash-language-server` | `npm install -g bash-language-server` | Phase 20.1 ships nodejs 26.4; bash-language-server 5.6.0 (MIT, 205k weekly) requires node 20+ |
| HTML | `intellisense-html-vscode` | `vscode-html-language-server` | `npm install -g @zed-industries/vscode-langservers-extracted` | Zed's maintained fork (3 months) of the original Microsoft package; binaries identical |
| CSS | `intellisense-css-vscode` | `vscode-css-language-server` | same | Same npm package; same install |
| JSON | `intellisense-json-vscode` | `vscode-json-language-server` | same | Same npm package; same install |
| YAML | `intellisense-yaml-redhat` | `yaml-language-server` | `npm install -g yaml-language-server` | redhat-developer; MIT; 2.4M weekly; requires node 18+ |

All 5 cards use `PackageCategory.LANGUAGES`. The 3 vscode cards
share the same install command; the user only has to install ONE
of them to land all 3 binaries (npm -g is idempotent — re-running
is a no-op). The probe binary is per-language so the orchestrator
picks the right server.

**Catalog totals after 31.4:**
- 10 server configs (C, CPP, PYTHON, JS, TS, SHELL, HTML, CSS, JSON, YAML)
- 8 install cards (3 from 31.2/31.3 + 5 from 31.4)
- 8 NoCard languages (TEXT, MARKDOWN, GO, RUST, PHP, RUBY, LUA, XML)

## 2. Exit condition

```text
(Device, per language)
1. Fresh .sh / .html / .css / .json / .yaml without the server: snippets only; typing never blocked.    ← host PASS (no-op provider)
2. Install card → server attached → `git`/`<div`/`margin:`/`"scripts"`/`apiVersion` complete.          ← install path ready; "attached" is the 31.1 wire (pending)
3. RUN ▶ still uses CodeC's built-in paths for any run profile (Phase 21 invariant).                   ← unchanged
PASS = (1) and (3) ship today; (2) is the device round + 31.1 wire.
```

### 2.1 Device recipe (owner)

1. **Shell**: Open a `.sh` file. Type `git` — expect Phase 30 shell
   pack items; bash-language-server is NOT on disk.
2. **Install**: Open Packages → LANGUAGES → tap "IntelliSense: Shell
   (bash)" → INSTALL runs `npm install -g bash-language-server`.
3. **Verify**: Terminal tab → `bash-language-server --version`
   (should print version 5.6.0). Card flips to INSTALLED.
4. (When the 31.1 wire ships) re-type `git` — expect command-name
   completion (git-merge, git-rebase, git-cherry-pick, etc.) in the
   strip BEFORE the engine's items.
5. **HTML / CSS / JSON**: Same recipe with the 3 vscode cards.
   Installing any ONE of them lands all 3 binaries (npm -g is
   idempotent).
6. **YAML**: Same recipe with the YAML card.

## 3. Follow-ups

- The sora `LspEditor` wire (§3.1 of 31.1) is what flips
  "card INSTALLED + orchestrator probe TRUE" into "actual LSP
  completion in the strip". Until that ships, step 4 of the
  device recipe returns only the Phase 30 snippet world.
- The "8 NoCard languages" stay as such until those compilers
  (gopls/rust-analyzer) are in the CodeC apt repo, OR a
  documented pip/npm install path is verified for the device.
- If `vscode-langservers-extracted` ever ships a
  `vscode-markdown-language-server` in the same package, the
  markdown card is a follow-up.
