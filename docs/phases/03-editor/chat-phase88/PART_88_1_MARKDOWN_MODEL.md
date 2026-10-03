# CodeC Phase 88.1 — Pure Markdown model for AI answers

> **Status:** ✅ IMPLEMENTED (2026-10-03, on the owner's *"Complete level 11"*; Build APK on CI pending — see the [README record](README.md#implementation-record-2026-10-03)) · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** *"think I don't have much code knowledge."*
> **Parent brief:** [Phase 88 README](README.md)

## First move: evidence, not code

Read 2026-10-03 on `arena/01a101db-codec` @ `8062f0c` (production code identical to `c3771c5`).

- `AiParts.kt:55-58` — `Answer` is `SelectionContainer { Text(text, bodyMedium) }`. No parser exists on
  the AI side.
- `ui/utils/MarkdownPreview.kt` — the tree's only Markdown code. It emits an **HTML string** (`toHtml`,
  `:29`) through `private` string-to-HTML helpers (`escape :203`, `inline :210`, list/table/rule
  recognizers `:266-313`). Its block subset (fences `:64-74`, ATX headings `:80`, rules `:95`, quotes
  `:102`, pipe tables `:115`, nested lists `:133`, paragraphs with GFM hard breaks `:40-45`) is the
  right **scope**. Its output
  format is the wrong **target**: AI answers render as Compose text, not as HTML.
- `AiHttpStream.kt:87` — the answer arrives as a growing prefix. Any parse must accept a half-written
  document: an open code fence, an unclosed `**`, a table missing its last row.

So 88.1 is a new pure parser that produces a **model** (blocks and inline spans), not markup. HTML is
never interpreted, so there is nothing to escape; a `<script>` tag is just eight characters of text.

## Design

```kotlin
// AiMarkdown.kt — pure Kotlin: no Android import, no Compose import, no regex with nested quantifiers.

sealed interface AiMdBlock {
    data class Heading(val level: Int, val inlines: List<AiMdInline>) : AiMdBlock      // 1..6
    data class Paragraph(val inlines: List<AiMdInline>) : AiMdBlock
    data class Bullets(val ordered: Boolean, val start: Int, val items: List<AiMdItem>) : AiMdBlock
    data class Quote(val blocks: List<AiMdBlock>) : AiMdBlock
    data class Code(val language: String, val text: String, val closed: Boolean) : AiMdBlock
    data class Table(val header: List<AiMdCell>, val rows: List<List<AiMdCell>>) : AiMdBlock
    data object Rule : AiMdBlock
}
data class AiMdItem(val checked: Boolean?, val blocks: List<AiMdBlock>)   // null = not a task item
data class AiMdCell(val inlines: List<AiMdInline>)

sealed interface AiMdInline {
    data class Text(val text: String) : AiMdInline
    data class Code(val text: String) : AiMdInline
    data class Strong(val children: List<AiMdInline>) : AiMdInline
    data class Emphasis(val children: List<AiMdInline>) : AiMdInline
    data class Strike(val children: List<AiMdInline>) : AiMdInline
    data class Link(val children: List<AiMdInline>, val target: String) : AiMdInline  // judged by 88.2
    data class ImageAlt(val alt: String) : AiMdInline                                 // never fetched
    data object LineBreak : AiMdInline
}

object AiMarkdown {
    const val MAX_NESTING = 8               // lists and quotes; deeper content flattens, never drops
    const val MAX_LINK_TEXT_CHARS = 1_000   // a longer "[…](…)" is plain text
    const val STREAM_REPARSE_MS = 150L      // 88.3's throttle; bounded here so a test asserts it

    fun parse(text: String): List<AiMdBlock>
    fun inlines(text: String): List<AiMdInline>
    /** Every character the user would see, markers removed. Test oracle: nothing is lost. */
    fun visibleText(blocks: List<AiMdBlock>): String
}
```

### Block rules

1. **Line endings** are normalised (`\r\n` and `\r` → `\n`), as `MarkdownPreview.toHtml` does.
2. **Fences** use ` ``` ` or `~~~` (three or more). The closing fence uses the same character and is
   at least as long. The first word of the info string is a display-only language label. Content is
   literal (never inline-parsed). **An unclosed fence is a code block to the end of the text**
   (`closed = false`), which is the CommonMark rule (source 1). Mid-stream code therefore renders as
   code immediately, never as raw backticks.
3. **ATX headings** `#`…`######` require a space after the hashes, so `#hashtag` stays text; trailing
   hashes are stripped. **Setext** underlines (`===` or `---` directly under a paragraph line) make
   levels 1 and 2, so a `---` under a sentence is not mistaken for a rule.
4. **Lists**: `-`, `*`, `+`, and `1.` or `1)` (up to 9 digits, as `MarkdownPreview`'s `ORDERED_ITEM`, `:277`).
   Nesting follows indentation up to `MAX_NESTING`. An ordered list keeps its start number. Task
   markers `[ ]` and `[x]` set `checked`.
5. **Quotes**: `>` prefixes, nested up to `MAX_NESTING`; anything deeper joins the deepest level.
6. **Tables**: a GFM pipe table needs a header row followed by a separator row (`|---|:--:|`). Cells
   are inline-parsed. Ragged rows are padded with empty cells and **extra cells are kept**: content is
   never dropped. Alignment markers are accepted and ignored (88.3 draws a monospace grid).
7. **Rules**: `---`, `***` or `___` (three or more, spaces allowed) alone on a line, when not a setext
   underline.
8. **Paragraphs**: consecutive non-blank lines. **A single newline is a `LineBreak`, not a space.**
   This is a deliberate departure from CommonMark's soft break: a model on a phone writes
   `Step 1: …` and `Step 2: …` on separate lines, and folding them into one run-on paragraph would
   misrepresent the answer. The `MarkdownPreview` hard-break forms (two trailing spaces, a trailing
   backslash) also give a `LineBreak`.
9. **Not supported, shown as text:** indented (four-space) code blocks, which collide with list
   continuation (models use fences), HTML blocks, reference-style links, footnotes and math.

### Inline rules (one hand-written left-to-right scanner)

- A backslash escapes ASCII punctuation.
- **Code spans** match backtick runs of equal length; their content is literal.
- `**…**` / `__…__` → `Strong`; `*…*` / `_…_` → `Emphasis`; `~~…~~` → `Strike`. **An intraword `_`
  is not emphasis**, so `snake_case_name` and `__init__`-style identifiers stay text.
- `[text](target)` → `Link`. An optional `"title"` is ignored; a target in `<…>` is accepted.
  `<https://…>` autolinks and bare `https://…` (GFM extension, source 2) also give `Link`. Trailing
  `.`, `,`, `;`, `:`, `!`, `?` and an unbalanced `)` end a bare URL. A bare `www.` without a scheme stays
  text.
- `![alt](target)` → `ImageAlt(alt)`. The target is discarded: nothing can fetch it.
- **Raw HTML is text.** `<script>alert(1)</script>` and `<img src=x onerror=…>` come out as `Text`
  holding exactly those characters. They are neither stripped (which would hide what the model wrote)
  nor interpreted.
- Unmatched delimiters are literal text (CommonMark), so `**bold te` mid-stream shows its asterisks
  until the closing pair arrives.

### Linear by construction

No regex with nested quantifiers; one pass per line for blocks and one per paragraph for inlines. The
delimiter stack is bounded by `MAX_NESTING`, and a `[` scans forward at most `MAX_LINK_TEXT_CHARS`. That
is what keeps pathological input (thousands of `*`, `[`, `>` or backticks) from turning a parse into
seconds of main-thread work.

## The Android edge

None. 88.1 lands no UI. `AiMarkdown.kt` sits in `ui/ai/` beside the other pure policies, and 88.3
consumes it. `MarkdownPreview.kt` is not modified.

## Exit condition

- [ ] Every construct above parses to the expected model; unsupported ones come out as text, never dropped.
- [ ] `visibleText(parse(x))` keeps every visible character of `x`; only markers disappear.
- [ ] An unclosed fence is `Code(closed = false)` holding everything after it; closing it later gives the same `text`.
- [ ] The hostile corpus (`<script>`, `<img onerror=…>`, `<iframe>`, `javascript:`/`data:` targets) parses to text and `Link` targets only. No HTML element exists in the model.
- [ ] Nesting beyond `MAX_NESTING` flattens without losing content.
- [ ] Pathological inputs (50 000 `*`, 20 000 `[`, 10 000 `>`, 10 000 backticks) each parse well under the test's generous time bound.
- [ ] `STREAM_REPARSE_MS` sits within 50..500 ms (asserted).
- [ ] No Android or Compose import in `AiMarkdown.kt` (source pin).

## Tests (plan)

`AiMarkdownTest`, about 26 cases: headings (ATX, setext, `#hashtag`); paragraphs and line breaks;
bullets, ordered start, nesting, task items; quotes and nesting cap; fences (backtick, tilde, longer
closing fence, info string, **unclosed while streaming**); tables (aligned, ragged, extra cells); rule
vs setext; inline code/strong/emphasis/strike; intraword `_`; links, autolinks, bare URLs with trailing
punctuation; images give alt only; raw HTML stays text; escapes; unmatched delimiters; the
`visibleText` oracle; four pathological inputs; the `STREAM_REPARSE_MS` bound; the no-Android-import
pin. Counts are a plan, not a result.

## Sources (record)

1. CommonMark spec, *Fenced code blocks* — an unclosed fence runs to the end of the document —
   https://spec.commonmark.org/ (read via search 2026-10-03).
2. GitHub Flavored Markdown spec — tables, task list items, strikethrough, autolinks (extension) —
   https://github.github.com/gfm/ (not fetched this session).
3. Repository: `MarkdownPreview.kt` and `AiParts.kt` lines above, read 2026-10-03 on `8062f0c`.

## Deferred / rejected with reasons

- **Reusing `MarkdownPreview`'s helpers** — rejected: they are `private`, emit HTML, and changing a
  Phase 68.1 surface to serve the AI would couple two different trust levels.
- **Full CommonMark conformance** (the complete delimiter-run algorithm, link reference definitions,
  HTML blocks) — deferred: model answers use a small subset; every unsupported form degrades to text.
- **Indented code blocks** — rejected: ambiguous with list continuation; models use fences.
- **Math, Mermaid, footnotes, emoji shortcodes** — deferred; shown as text.
