# CodeC Phase 25.2 — Sora Editor Integration (research-recommended path)

**Status:** ⭐ **CHOSEN BY THE 25.1 GATE (2026-09-04)** — C-sora passed every
device budget on both corpora (decision table:
[`docs/EDITOR_MOBILE_RESEARCH.md`](../EDITOR_MOBILE_RESEARCH.md) §3.1; raw
numbers: [`PART_25_1_SPIKE_BENCH.md`](PART_25_1_SPIKE_BENCH.md) §4.5). Awaits
the owner's **"Start Phase 25.2"** — not started yet. **Cost:** `[client-only]` ·
**Effort:** L · **Depends on:** PART 25.1 decision = C-sora ✅
· **Target files:** `ui/screens/EditorScreen.kt`, `ui/viewmodels/EditorViewModel.kt`,
`ui/editor/*` (adapters), `app/build.gradle.kts` (dependency only),
About/licenses screen, host tests

> 25.1 notes for this part: the spike pinned `io.github.rosemoe:editor:0.24.6`
> + `language-java:0.24.6` (group moved since the 0.23.6 pin — research note
> in `gradle/libs.versions.toml`); prefer `editor-bom` at integration. The
> bench's `Typed=62` artifact (60 keys dispatched) already showed Sora's
> SymbolPairMatch working on the owner's device.

---

## 1. Design

Replace **only the text widget**; keep the whole Compose shell (tabs, drawer
file tree, status bar, output panel, git chrome). `CodeEditor` lives in an
`AndroidView` — the standard Compose↔View interop, used exactly this way by
Squircle-CE-style apps (behavior reference).

Mapping (CodeC feature → Sora surface):

| CodeC today | Sora mechanism |
|---|---|
| `SyntaxVisualTransformation` + `MultiLanguageSyntaxHighlighter` | `AsyncIncrementalAnalyzeManager` via a language module; **start with a small CodeC-side lexer adapter** reusing existing regex rules — TextMate/Tree-sitter grammars are a *later* upgrade, both supported by Sora out of the box |
| `CodeCompletionEngine` (keywords/snippets, 120 ms debounce) | Sora completion **provider adapter** calling the same engine; panel hidden — Phase 27 strips/ghost UI renders the results instead (provider pipe shared) |
| `EditorKeySet` row | drives insertion through Sora's text API; strip UI stays Compose, keys feed the editor |
| `EditorUndoManager` | delegate to Sora's `UndoManager` (cross-session persistence recorded as 26.2 stretch) |
| CodeC themes (`ui/theme/EditorThemes.kt`) | `EditorColorScheme` adapter — each Sora instance needs its **own** scheme object (Sora enforce single-owner schemes) |
| Pinch zoom (`FontSizeZoom`) | built-in text-scale gestures |
| Autosave (~2 s) | editor listener → existing `EditorViewModel` save pipeline, unchanged |
| Run/output, terminal | untouched |

Bridged surfaces that must not break: tab open/close/dirty, find/replace
(replace with Sora `EditorSearcher` — keep CodeC's dialog UI), line ops
(`EditorLineOps`: duplicate/comment/indent — keep VM methods operating on
Sora content), hardware shortcuts (Phase 24.3 keys reach the editor widget),
RunKeySet context swap (Phase 23.2 — strip swap logic is editor-agnostic).

### LGPL-2.1 obligation checklist (gate before merge)
- [ ] Sora used strictly as a Gradle dependency (`editor` + chosen language
      module from Maven, `editor-bom` for versions) — **no source copied, no
      shaded classes, no fork** (the Xed `soraX` fork path is rejected here).
- [ ] About/Settings screen lists "sora-editor © Rosemoe, LGPL-2.1" with the
      license text and the project link.
- [ ] Library stays user-replaceable (ordinary dependency substitutability;
      this is also realistically verified via a dependency-substitution build
      in CI).
- [ ] `rule.md` clean-room law still binds every *other* surveyed project
      (GPL/closed) — this part imports **nothing** from them.
- [ ] Owner has seen and accepted this checklist (explicit comment in chat).

### Risks called out now
1. **Compose↔View focus/IME juggling** — mitigated by confining the View to the
   editor surface only; strip/IME events already flow through the VM.
2. **APK size** (~1–2 MB) — measured in 25.1's budget table, capped at +2 MB.
3. **Autosave drive** — Sora buffers text internally; the VM pulls content on
   the debounced listener (never per keystroke).
4. **ProGuard/R8** keep rules for Sora widgets — ship in the same commit, CI
   release build must run the editor screen in CI smoke (screenshot test).

## 2. Implementation steps

1. Dependency + `AndroidView` host composable; load a file; caret works.
2. Highlighter adapter (CodeC rules → spans) on the incremental manager.
3. Input bridge: EditorKeySet/RunKeySet → Sora text API; IME options verified
   against Gboard + a code IME.
4. Completion provider adapter (panel suppressed; results to existing state —
   Phase 27 renders them).
5. Find/replace dialog → `EditorSearcher`; verify against existing host tests'
   cases (re-implemented against the bridge).
6. Undo/redo, autosave, tabs, dirty dots — full parity checklist with the
   current editor's Phase 9/22 devices recipes re-run.
7. LGPL checklist merged with this part; CI green incl. release-APK smoke.

## 3. Exit condition

```text
(Device, release APK)
1. Open 5k-line bench.c: type 60 chars burst → no visible jank (25.1 budgets).
2. Type "(" → paired ")" with caret inside; typing ")" types OVER (no doubling).
3. Caret drag → magnifier appears and tracks finger.
4. TAB/()/{} strip keys, HW Ctrl+S/Ctrl+R all work; RunKeySet swap on interactive
   run still works.
5. Autosave on ~2 s idle; close/reopen app → file intact; undo survives tab switch.
6. Settings → About lists sora-editor + LGPL text.
PASS = all six.
```
