# CodeC — Phases 34–37 · open-source-first research dossier

> **Status:** 📋 **RESEARCH ONLY — NO APP CODE.** Owner brief (2026-09-09):
> *"I want you to research everything on open source 1st for reference and
> update if anything useful found."* Scope: the four UX/UI phases (34 file
> icons, 35 editor typing feel, 36 terminal speed & UX, 37 device-as-server).
> This dossier is the **open-source-first** reference for those phases; the
> phase docs are updated where the research changed the spec. Clean-room law
> still applies — replicate **features**, never paste GPL/copyleft code;
> depend on (or vendor with attribution) only MIT / Apache-2.0 / BSD.

---

## 0. What "open source first" means here

For each phase we ask: *is there already a free, correctly-licensed library,
asset set, or reference implementation that does this — before we write any
custom Kotlin?* The answer is recorded per phase, with the licence deciding
what is **vendorable/dependable** (MIT · Apache-2.0 · BSD) vs **behavior
reference only** (GPL/copyleft, trademarks, closed source — read the visible
behavior, re-implement).

## 1. Phase 34 — official file icons

| Source | Licence | Usable? | Note |
|---|---|---|---|
| **VS Code "Seti" file icon theme** (built-in) | **MIT** (Microsoft) | ✅ vendor w/ attribution | The *official* VS Code file icons; monochrome; matches our single-tint `SpckIcons` convention. |
| **Material Icon Theme** (PKief) | **MIT** | ✅ alternative | Material-flavoured; also 2-tone + folder themes. |
| **vscode-icons** (vscode-icons-team) | source MIT, **icons CC BY-SA** + branded icons under their own licences | ❌ avoid | ShareAlike is a copyleft clause on the assets; branded logos carry their own terms. |
| Python / JS / TS / Java logos | trademarked (PSF, Oracle, etc.) | ❌ don't vendor logos | Keep the **already-drawn** two-tone marks in `SpckIcons` (Python/HTML/globe); use Seti's monochrome glyphs for the rest. |

**Key conversion finding (changes the "how"):** the repo builds `ImageVector`
from `List<PathNode>`, and Jetpack Compose ships
**`androidx.compose.ui.graphics.vector.PathParser`** —
`PathParser().parsePathString(d).toNodes()` turns an SVG `d="…"` string
straight into `PathNode`s (`ui-graphics`, Apache-2.0, already a dependency).
So the pipeline is **Seti SVG → extract `d` → `PathParser` → `ImageVector`** —
no hand-porting, exactly the `SpckIcons` shape. Tooling to mass-produce it
off-device: `svgo` (MIT, minify) → Android Studio's `Svg2Vector`
(`vd-tool`, AOSP) or `svg2vectordrawable` (MIT, npm) to eyeball/validate the
paths before the Kotlin step.

**Verdict:** vendor **Seti (MIT)** for ~40 extension glyphs + filename
specials; keep the drawn brand marks; generate the `ImageVector`s via
`PathParser`, with a `scripts/` converter + `licenses/` attribution (same
precedent as the Phase 30 MIT snippet packs).

## 2. Phase 35 — editor typing feel

| Source | Licence | Usable? | Note |
|---|---|---|---|
| **sora-editor** 0.24.6 (Rosemoe) | **LGPL-2.1** (binary dependency, already in-tree) | ✅ study public API, never paste | Cursor/selection/scroll events (`SelectionChangeEvent`, `ScrollEvent`, `ContentListener`, `InlayHintClickEvent`) are already wired in `SoraEditorHost`; the cursor-blink/solid-caret knob lives on `CodeEditor`/its cursor — **verify the exact 0.24.6 method at implementation** (the AAR's public surface, not a guessed name). |
| **jackpal Android-Terminal-Emulator** | Apache-2.0 | ✅ reference | Its terminal uses solid-caret + dirty-line redraw thinking; the same "don't re-animate per keystroke" law applies to the editor caret. |
| Gboard / Samsung / SwiftKey | closed | ⚠️ behavior only | "Smooth like a normal keyboard" = solid caret while typing + no per-key relayout; measure, don't guess. |

**Verdict:** no new dependency; this phase is about the **per-keystroke path
already in the repo** (35.2's measurement card) and sora's cursor config
(35.3/35.4). The open-source reference confirms the *behavior target*, not a
library to add.

## 3. Phase 36 — terminal speed & UX

| Source | Licence | Usable? | Note |
|---|---|---|---|
| **jackpal Android-Terminal-Emulator** (archived) | **Apache-2.0** | ✅ study + reuse w/ attribution | The canonical open Android VT-100 terminal: native PTY (`libtermexec`), a separate **emulator** + **view**, multi-window, UTF-8. Confirms CodeC's own `libcodec-pty.so` + `TerminalEmulator` + `TerminalEmulatorView` split is the right architecture; any emulator/scrollback/render detail can be cross-checked here (Apache-2.0 is reusable, but we keep our own emulator). |
| **Termux** (app + terminal-view/terminal-emulator libs) | **GPL-3.0** | ⚠️ behavior only, never paste | The "Termux-fast" target: **one-time bootstrap, then every open is just `exec`**. That is exactly 36.1's `PreparedShell` cache + "starting shell…" state. |
| sora / Compose | (in-tree) | — | The terminal already renders through `RenderPump` (frame-paced) — the fast-path is the *startup*, not the render loop. |

**Verdict:** the architecture is already right; jackpal is the Apache-2.0
reference and Termux the GPL-3.0 behavior target. 36.1's cache/measure work
is the whole fix — no library swap.

## 4. Phase 37 — device as server (LAN)

| Source | Licence | Usable? | Note |
|---|---|---|---|
| **NanoHTTPD** | **BSD-3-Clause** | ✅ depend or reference | One-file embedded HTTP server; its `nanohttpd-webserver` adds range/ETag/dir-listing. CodeC's `WebPreviewServer` is leaner and path-confined, so **likely keep ours and borrow the technique list** — but NanoHTTPD is the documented fallback if we ever want range/streaming. |
| **ZXing `core`** | **Apache-2.0** | ✅ depend (zero-dep `core`) | `MultiFormatWriter().encode(url, QR_CODE, w, h)` → `BitMatrix` → bitmap — the QR code for the LAN URL, exactly the Spck-style "scan to open". |
| **Android NSD** (Network Service Discovery) | framework (Apache-2.0 AOSP) | ✅ no dependency | Registers the server as a `_http._tcp` service so peers discover it; optional but cheap. |
| **JmDNS** | Apache-2.0 (current) / LGPL (old) | ⚠️ verify version licence | Pure-Java mDNS fallback if framework NSD is insufficient; **verify the exact version's licence before depending** (do not pull an LGPL-era jar). |
| **jackpal / Termux** | Apache-2.0 / GPL-3.0 | ⚠️ behavior only (Termux) | Behavior reference for "phone as a dev server" (bind 0.0.0.0, show the LAN URL). |

**Verdict:** keep the hand-rolled `WebPreviewServer` (BSD NanoHTTPD as
reference); add **ZXing `core` (Apache-2.0)** for the QR; use **framework
NSD** for discovery (JmDNS only if needed and licence-verified); reuse the
in-tree `RunForegroundService` for keep-alive. `ServerPortDetector`/`LanAddress`
stay pure + host-tested.

---

## 5. Source list

1. VS Code Seti theme (MIT) — open-vsx `vscode/vscode-theme-seti`; VS Code "Original VS Code sources Copyright (c) 2015–present Microsoft Corporation … MIT".
2. vscode-icons (CC BY-SA icons) — github.com/vscode-icons/vscode-icons README (rejected).
3. Material Icon Theme (MIT) — github.com/PKief/vscode-material-icon-theme.
4. Compose `PathParser` — developer.android.com/reference/kotlin/androidx/compose/ui/graphics/vector/PathParser (`parsePathString`, `toNodes`).
5. `svgo` (MIT) · `vd-tool`/`Svg2Vector` (AOSP) · `svg2vectordrawable` (MIT) — SVG→VectorDrawable tooling.
6. sora-editor (LGPL-2.1) — github.com/Rosemoe/sora-editor (binary dependency; public API only).
7. jackpal Android-Terminal-Emulator (Apache-2.0, archived) — github.com/jackpal/android-terminal-emulator.
8. Termux (GPL-3.0, behavior reference only) — github.com/termux.
9. NanoHTTPD (BSD-3-Clause) — github.com/NanoHttpd/nanohttpd.
10. ZXing `core` (Apache-2.0) — github.com/zxing/zxing (`MultiFormatWriter` → `BitMatrix`).
11. Android NSD — developer.android.com/training/connect-devices-wirelessly/nsd; JmDNS (licence to verify) — github.com/jmdns/jmdns.

## 6. One-line verdict

**Everything this phase-family needs already exists as correctly-licensed
OSS:** Seti icons (MIT) + Compose `PathParser` for 34, sora's public cursor
config + the in-tree typing path for 35, jackpal (Apache-2.0) as the terminal
reference for 36, and NanoHTTPD (BSD) / ZXing `core` (Apache-2.0) / framework
NSD for 37. **No GPL paste; no trademarked logos; at most two small new
dependencies (ZXing `core`, optional JmDNS-with-verified-licence).**
