# CodeC — Phases 34–37 · UX & UI roadmap

> **Owner (2026-09-09, verbatim):** *"Device test pass … before you do any
> merge i want you to add some new phrases … From now on i will work on the
> user experience improvement and the ui so research throughly and create the
> phases properly after all that i will go for various devices use test."*
>
> The four row ideas the owner handed over were researched below and turned
into four phases (34–37). Phase 34 is merged; Phase 35 is device-passed by
owner report on its session branch. Phase 36 is now STARTED on that branch;
Phase 37 remains planned. Each phase lands on `main` only through the §3
merge gate and finishes with the owner's cross-device test round.

## The owner's four row ideas → phases

| # | Owner's words (verbatim) | Phase | Title | Effort |
|---|---|---|---|---|
| 1 | "The file icons i want the official icons" | **34** | Official file icons | S |
| 2 | "always keyboard stay open … typing is not smoth … cursor is very bouci … open a file initialization the corsure to the top … fully remove [until] user clicks anywhere" | **35** | Editor typing feel | M |
| 3 | "Termux is very fast and my Terminal sometimes stays exited for a while then start working … behavior not user friendly" | **36** | Terminal speed & feel | M |
| 4 | "like spck or Termux we can use a device as a server and run our files at localhost" | **37** | Device as server (LAN) | M/L |

## Recommended order

**35 → 34 → 36 → 37.** The editor is where the owner spends the first
minute of every session, and its four pain points are one shared surface
(`EditorScreen` + `SoraEditorHost` + `EditorViewModel`), so they are one
phase with four parts. Icons (34) are pure visuals and can land any time.
Terminal speed (36) is measurement-led and must not ship on a guess. The
LAN server (37) depends on the Phase 14 server pipeline (already solid) and
is the largest surface, so it goes last. The owner may reorder by saying
"Start Phase N" for any of them.

## Cross-device test round (the owner's stated end-goal)

The owner will test on **various devices** after the phases land. Every
exit condition below is written to hold across that matrix, and each phase
records the matrix-specific risks to watch:

- **Android version** — minSdk 24 through current (IME insets, `WifiManager`
  vs `ConnectivityManager` IP discovery, foreground-service rules differ).
- **OEM keyboards** — Gboard / Samsung / SwiftKey (composing spans, key
  repeat rates; CodeC Keys must feel at least as smooth as the system IME).
- **Screen size / density** — small phone to tablet (icon sizing, keys-row
  height, terminal columns).
- **SoC speed** — low-end vs flagship (typing p95 and terminal start-to-prompt
  must hold on a slow device, not just a fast one).
- **Network** — home Wi-Fi, phone hotspot, no Wi-Fi (the LAN server must
  degrade gracefully with a clear message when there is no usable address).

## Law this roadmap must obey (from `rule.md`)

- Clean-room: replicate **features**, never copy code. Icon **assets** follow
  the repo's existing MIT-vendoring precedent (friendly-snippets) — see
  Phase 34's licensing notes; no CC BY-SA (ShareAlike) assets, no
  `com.termux` code, no GPL paste, no trademarked logos.
- **Open-source first** — the owner's 2026-09-09 directive: research the
  free-OSS option before writing custom code. The per-phase findings, licences
  and verdicts are in **`docs/PHASE34_37_OSS_RESEARCH.md`** (Seti MIT +
  Compose `PathParser` for 34; sora public API + in-tree typing path for 35;
  jackpal Apache-2.0 / Termux GPL-3.0 reference for 36; NanoHTTPD BSD + ZXing
  Apache-2.0 + framework NSD for 37).
- Pure, host-testable engines first (`LanguageRegistry`-style), Compose only
  at the edges — the codebase pattern since Phase 3.
- CI (`Build APK`) is the only executor of record; local pre-validation with
  shimmed JVM classes is allowed but never replaces CI.
- Evidence before hypothesis: Phase 36 in particular ships a **measurement
  card first** — no fix without the number it fixes.

---

## Phase 34 — Official file icons (`docs/chat-phase34/`)

Replace the generic `Code` / `InsertDriveFile` Material glyphs (and the
handful of drawn marks) with a **real file-icon theme**: an
extension→icon map covering `LanguageRegistry`'s types plus common assets,
applied in all four places files are shown — Files tab tree, editor drawer
tree, editor tab bar, and (where a single mark is shown) the hub/drawer
file glyph. Spec: [`chat-phase34/`](chat-phase34/README.md).

## Phase 35 — Editor typing feel (`docs/chat-phase35/`)

Four parts, one surface: **35.1** "always keyboard stay open" (a Settings
toggle + no gesture silently dismisses CodeC Keys while editing); **35.2**
smooth typing (measure keystroke p95 on a slow device, then cut the
per-keystroke main-thread work); **35.3** bouncy cursor (solid, non-blinky
caret while typing — kill the re-animation the VM→sora caret replay causes);
**35.4** no caret on open (a freshly opened file shows **no cursor** and no
auto scroll-to-top until the user taps; the first tap places the caret
exactly where it landed). Spec: [`chat-phase35/`](chat-phase35/README.md).

## Phase 36 — Terminal speed & feel (`docs/chat-phase36/`)

Two parts: **36.1** cold-start latency (measure tap→prompt, cache the
`PreparedShell`, show a "starting shell…" state instead of dead air, remove
the startup `delay` quirks); **36.2** behavior polish (session restore,
clear restart/new-session affordances, honest exit notices, no silent
restart surprises). Spec: [`chat-phase36/`](chat-phase36/README.md).

## Phase 37 — Device as server / localhost (`docs/chat-phase37/`)

Run CodeC files as a **LAN-reachable** server like Spck/Termux: bind
`0.0.0.0` (opt-in, loopback stays the default), discover the Wi-Fi IP,
surface `http://<ip>:<port>` + a QR code, and keep the server alive through
a foreground service. Two parts: **37.1** LAN server + URL/QR UX; **37.2**
foreground keep-alive + port lifecycle. Spec:
[`chat-phase37/`](chat-phase37/README.md).

---

## State of record

- Phase 33 (first-hour UX) is **✅ DEVICE-PASSED** and **MERGED** to `main`.
- Phase 34 (Official file icons) is **✅ DEVICE-PASSED** and **MERGED** to `main`.
- Phase 35 (Editor typing feel) is **✅ DEVICE-PASSED** by the owner report;
  implementation tip `317b89a`, docs follow-up `88839cd`, and Build APK CI
  `34367008019`/`34367770583` are green. Exact device evidence was not
  supplied, so the record does not invent it.
- Phase 36 (Terminal speed & feel) is **🚧 STARTED** on the session branch;
  implementation and pure host tests are in progress from `docs/chat-phase36/`.
- Phase 37 remains **📋 PLANNED** (researched + specced, not implemented).
- The owner starts each phase with "Start Phase N" and runs the cross-device
  round at the end.
