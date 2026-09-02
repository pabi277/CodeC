# CodeC Website Phase W4.2 — Verification gate (locks the chapter set)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` (docs-only deliverable) ·
**Effort:** S
· **Depends on:** nothing (README + repo config) — **but runs first in W4**
· **Deliverable:** a **Verified Facts Table** committed in
  `web_docs/chat-web4/` + the locked 17-chapter set (closes **O6**)

---

## 1. Design

Before the course publishes a single chapter, the repo is re-read and every
claim the course may make is written down with a source. This gate exists
because the course (unlike the product pages) teaches *steps*, and a step
that doesn't exist on a fresh install breaks the whole promise of
self-sufficiency.

### What gets verified (minimum rows)

1. **Install paths** — the three APK paths, exactly as README states them
   (W2 built the page from the same source; confirm nothing drifted).
2. **Package list** — re-extract from `codec-packages/` build config (sha
   recorded); diff against W3.2's table; **note which tools exist for
   chapters 09, 10, 12, 15** (bash, python/python3, any networking tools,
   make/clang/ripgrep/tmux/nano).
3. **Engines & coverage** — 4 engines, TCC coverage line, arm64-only
   Clang module, CHECK BRIDGE name, Termux permission string (verbatim).
4. **Terminal facts** — `$PREFIX` path, `cc` = TCC, one-command-per-line,
   `./` rule, app-private project storage path, input-programs-in-Term rule.
5. **CodeCApi facts** — the five script names, output paths, markers
   (`NEED_PERMISSION:`, `CAPTURING:`), TTS cap, camera output-name rule
   (chapter 13 depends on these).
6. **Editor facts** — autosave delay, extra-keys row keys, Format behavior,
   squiggle quick fix (chapter 07).
7. **Web preview facts** — loopback server behavior, live reload, console
   location, `fetch`/modules support (chapter 14).
8. **Git UI facts** — Settings → GitHub Account, Clone from GitHub, Source
   Control pane, COMMIT & PUSH, unpushed badge, conflict flow (chapter 11).

### Gate outcomes

- **Green** → chapter set locked at the 17 in plan §3.2; O6 closed;
  W4.1–W4.8 proceed.
- **Amber** (a chapter's premise is thin — e.g. no networking tools for
  ch-12) → the affected part doc already contains the fallback outline
  (see `PART_5_6_CH12.md` variant B); chapter count stays 17 unless the
  owner re-scopes.
- **Red** (a chapter's premise is false) → chapter cut/re-scoped, `DECISIONS.md`
  entry, plan §3.2 updated, O6 closed with the new set.

## 2. Implementation steps

1. Read `README.md` fully; re-extract the package config list.
2. Write the Verified Facts Table (fact · value · source file+section ·
   sha/date) into `web_docs/chat-web4/VERIFIED_FACTS.md`.
3. Diff against W3.2's package table; resolve any drift (drift = repo
   changed since W3 — the repo wins; update W3.2's page in the same commit
   if needed — same workstream, allowed).
4. Record the locked chapter set; close O6 in `DECISIONS.md`; update
   `NEXT_STEPS.md` head state.

## 3. Exit condition

```text
1. VERIFIED_FACTS.md committed with all 8 row-groups complete; every row
   has a source.
2. Package list diff against W3.2 recorded (clean or drift handled).
3. Locked chapter set (expected 17) recorded; O6 closed in DECISIONS.md.
4. Any plan §3.2 change made in the same commit.
```
