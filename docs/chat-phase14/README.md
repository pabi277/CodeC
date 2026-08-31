# CodeC Phase 14 Documentation — Mixed-Language & Server WebViews

Phase 14 connects local servers (Python Flask/FastAPI, Node.js, C microservers) directly to the in-app Web Preview screen and provides on-demand long-tail toolchain expansion.

## Status (2026-08-31)
**IMPLEMENTED (client-only) on `arena/01a05421-codec`** — background `ServerRunner`
+ port detector, `port`/`previewUrl` project schema, Flask/FastAPI/C-microservice
presets with runnable-out-of-the-box scaffold (stdlib fallback, page served from
`index.html` per request), Files-tab project wizard, RUN ▶ → stream → Ready →
auto-open Web Preview, live address bar + auto-reload. **No `[repo-build]`**
(Flask/FastAPI are pip packages; the C server uses the embedded TCC). Host tests
written; awaiting CI + device round. Long-tail toolchains (Node/Lua/Go/Rust)
remain deferred until the owner requests one.

## Contents & References
- **[Part 14.1 — Server Runner, Port Monitor & Web Preview Integration](PART_14_MIXED.md)** — the plan
- **[Implementation record + device recipe](PART_14_IMPLEMENTATION.md)**
