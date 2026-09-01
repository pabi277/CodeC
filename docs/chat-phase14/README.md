# CodeC Phase 14 Documentation — Mixed-Language & Server WebViews

Phase 14 connects local servers (Python Flask/FastAPI, Node.js, C microservers) directly to the in-app Web Preview screen and provides on-demand long-tail toolchain expansion.

## Status (2026-09-01)
**MERGED to `main` — PR #32 @ `0b591e2` (2026-08-31).** Implemented on
`arena/01a05421-codec` — background `ServerRunner`
+ port detector, `port`/`previewUrl` project schema, Flask/FastAPI/C-microservice
presets with runnable-out-of-the-box scaffold (stdlib fallback, page served from
`index.html` per request), Files-tab project wizard, RUN ▶ → stream → Ready →
auto-open Web Preview, live address bar + auto-reload. **No `[repo-build]`**
(Flask/FastAPI are pip packages; the C server uses the embedded TCC).
`Build APK` green: `33352164172` (four CI-caught bugs fixed along the way —
see the implementation record); `33355693242` adds `ServerScaffoldE2ETest` —
the presets' real build/run commands + `ServerRunner` + loopback HTTP +
edit-index.html-hot-read, all verified on CI; **tip `33360571874` green with
Auto (detect) + bundled demo**. The app now
also **ships a bundled `demo_flask` project** in the Files tab (D9 — one-time
seed, never overwrites; `DemoProjectSeedTest`: 4) and the **New Project
wizard defaults to Auto (detect)** (D10, owner request 2026-08-31): no type
selection — RUN ▶ infers Flask/FastAPI/C-microservice/static-web/Python/C
from the project's files (`ProjectRunDetectorTest`: 13 + E2E auto→Flask).
Only the Compose WebView part of the recipe (§5) was never given a dedicated
owner device round before the merge — open item, not a blocker. (Header
updated 2026-09-01 — previously still read "implemented & CI-green".)
Long-tail toolchains (Node/Lua/Go/Rust) remain deferred until the owner
requests one.

## Contents & References
- **[Part 14.1 — Server Runner, Port Monitor & Web Preview Integration](PART_14_MIXED.md)** — the plan
- **[Implementation record + device recipe](PART_14_IMPLEMENTATION.md)**
