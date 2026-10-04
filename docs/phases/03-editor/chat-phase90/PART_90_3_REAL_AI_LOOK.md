# Phase 90.3 — The real-assistant look

> **Status: ✅ IMPLEMENTED (2026-10-04).** Touches `AiMarkdownView.kt`, `AiMarkdown.kt` (model only if a
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
| **Long blocks** | ✅ As built: a fence over **200 lines** draws its first 200 with `AiCopy.CODE_BLOCK_TRIMMED`; **Copy always copies the whole block** |

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
| **Long-answer jump** | none | **Not built in this pass.** The sheet already follows the stream while `STREAMING` (it scrolls to the end on every chunk), so a jump affordance would need a scroll-position signal and a "user scrolled away" state — a real change to the sheet's scroll policy, best decided after the owner sees the polish on the phone (C13) |
| **Empty states** | hints exist | a first-run line in an empty chat: "Ask about a selection, or type a question about this project." (copy only) |

## What is explicitly **not** here

- No syntax highlighting (a dependency, a language registry, and a performance question — not this phase).
- No avatars, timestamps, "typing…" shimmer, or sounds.
- No change to the link rules: `https`-only behind the dialog (V3 passed) and `javascript:`/`data:` stay inert.
- No change to `AiMarkdown.kt`'s parsing contract: raw HTML stays characters, an unclosed fence stays code.

## As built (2026-10-04)

- `AiMdColors` gained `frame` (`outlineVariant`), and the code block is now: a **1 dp frame**, a header strip with
  the language and **Copy**, a divider under the header, the code on its own surface with padding, and the trimmed
  note when the fence is over 200 lines. `Copy` is unchanged (whole block, `copyAnswer`) and remains the only
  action a block offers (**S6**).
- `AiMdColors`' `remember` keys grew, so an unchanged parse still skips the blocks while streaming.
- **Not done, on purpose:** syntax highlighting (a dependency and a performance question), avatars/timestamps/
  shimmer, and any change to link handling (V3 passed; `javascript:`/`data:` stay inert).

## Planned checks

- **Host/CI:** `AiMarkdownView` is Compose — compile-checked by CI only, as today. Pure-model cases that exist are
  re-run; if a block-length cap is added, `AiMarkdown` gains the case and it is host-runnable.
- **Device (part 90.4):** V1–V8 are re-run after this part lands, plus **C11** (a code-heavy answer on the dark
  theme, scrolled) and **C12** (an over-wide line inside a block).
- **Pins that must not move:** exactly two `openUri(` in `ui/ai/`; Copy is the only action a block offers (§S6 —
  never insert, apply or run); no new dependency in `gradle/libs.versions.toml`.
