# Phase 90.3 — The real-assistant look

> **Status: 📋 BRIEFED (2026-10-04). No code.** Touches `AiMarkdownView.kt`, `AiMarkdown.kt` (model only if a
> block shape is genuinely missing), `AiChatSheet.kt` (bubble chrome), `AiParts.kt`, `AiCopy.kt`,
> `CodecTokens`/theme usage. **No new dependency, no HTML, no WebView** — the answer stays the hand-written
> Markdown model drawn with Compose primitives.

The owner, after the V1–V8 pass: ***"still i think it should be better like code with a visible block, etc other
stuff like a real ai"***. Two candidate defects and one upgrade list come out of that sentence.

## A. Code must read as *a block* (the named ask)

Today a code block is a background + a small language label + a Copy button + horizontally scrolling monospace
(`AiMarkdownView.kt:200-230`). On a phone, against a `surfaceVariant` bubble, that can read as "slightly different
text". The upgrade:

| Change | Detail |
|---|---|
| **Frame** | A real border (`outlineVariant`, 1 dp) around the block, plus the existing background — so it survives the bubble's own surface colour, light **and** dark |
| **Header strip** | One row: language label (or `Code`) · **Copy** on the right, with a divider under it — the header is part of the block, not floating text |
| **Code surface** | A hair darker/lighter than the bubble (a token, not a hardcoded colour), monospace, `softWrap = false`, horizontal scroll, `Space.S` padding, and a visible start-of-scroll affordance when the line is wider than the screen |
| **Unlabelled fence** | Keeps `Code` as the label instead of an empty row |
| **Long blocks** | No vertical cap by default (the answer scrolls); if a fence is pathological (> 200 lines), show the first 200 with a one-line `[block continues]` note — honest, and cheap to draw |

## B. "Other stuff like a real ai" (the general ask)

| Area | Today | Upgrade |
|---|---|---|
| **Inline code** | drawn as monospace text | a chip: subtle background + corner radius, still selectable |
| **Lists** | `•` / `1.` lines | keep the marker column aligned (hanging indent), tighter leading, nested markers by depth (model already carries `depth`) |
| **Quotes** | a `>` line | an inset block with a left rule |
| **Headings** | three sizes | a clear step per level with space above, and a rule under `#` level-1 when it starts a section |
| **Rules / tables** | present | tables keep their grid; add row separators that read in dark theme |
| **Bubbles** | `YOU` / `AI` label rows (`AiChatSheet.kt:914`, `:931`) | per-turn label moves into the header row: **You** right-aligned, **AI · provider · model** left-aligned; spacing between turns (not just inside them) |
| **Streaming** | re-parses ≤ every 150 ms | unchanged; the new chrome must not flicker — the block frame is drawn from the parsed model, not per token |
| **Long-answer jump** | none | a small "jump to the newest" affordance while a long answer streams (appears only when scrolled up) — helps the T1-style answers the owner runs |
| **Empty states** | hints exist | a first-run line in an empty chat: "Ask about a selection, or type a question about this project." (copy only) |

## What is explicitly **not** here

- No syntax highlighting (a dependency, a language registry, and a performance question — not this phase).
- No avatars, timestamps, "typing…" shimmer, or sounds.
- No change to the link rules: `https`-only behind the dialog (V3 passed) and `javascript:`/`data:` stay inert.
- No change to `AiMarkdown.kt`'s parsing contract: raw HTML stays characters, an unclosed fence stays code.

## Planned checks

- **Host/CI:** `AiMarkdownView` is Compose — compile-checked by CI only, as today. Pure-model cases that exist are
  re-run; if a block-length cap is added, `AiMarkdown` gains the case and it is host-runnable.
- **Device (part 90.4):** V1–V8 are re-run after this part lands, plus **C11** (a code-heavy answer on the dark
  theme, scrolled) and **C12** (an over-wide line inside a block).
- **Pins that must not move:** exactly two `openUri(` in `ui/ai/`; Copy is the only action a block offers (§S6 —
  never insert, apply or run); no new dependency in `gradle/libs.versions.toml`.
