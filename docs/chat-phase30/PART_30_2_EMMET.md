# CodeC Phase 30.2 — Emmet

**Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
· **Depends on:** 30.1 or 27 pipeline
· **Target:** HTML/CSS completion path

---

## 1. Design

Acode’s Emmet plugin is the phone-web expectation: `ul>li*3` expands.
Use [emmetio/emmet](https://github.com/emmetio/emmet) (MIT) **behavior**:
abbreviation → expansion. Implementation must be **clean-room / library
depend**, not Ace. Prefer a small JVM/Kotlin expansion or a trimmed JS
interpreter only if size stays in budget; otherwise a **host-tested
abbreviation subset** (`!`, tags, `>`, `+`, `*n`, `.class`, `#id`) is
enough for phone HTML.

Trigger: current token looks like an Emmet abbr in HTML/CSS; item kind
SNIPPET detail `emmet`.

## 2. Exit condition

```text
(Device, HTML file)
1. Type `ul>li*3` → chip/ghost offers expansion; tap inserts a list.
2. Type `!` → HTML5 skeleton (or keep the 22.6 DOCTYPE snippet).
3. C file: Emmet does not fire on `ul>li`.
PASS = all three.
```
