# CodeC Phase 88.3 — The answer surface: formatted, selectable, inert

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** L
> **Owner row (verbatim):** *"think I don't have much code knowledge."*
> **Parent brief:** [Phase 88 README](README.md) · **depends on:** [88.1](PART_88_1_MARKDOWN_MODEL.md), [88.2](PART_88_2_LINK_POLICY.md)

## First move: evidence, not code

Read 2026-10-03 on `arena/01a101db-codec` @ `8062f0c`.

- `AiParts.kt:54-59` — `Answer(text)` = `SelectionContainer { Text(text, bodyMedium) }`, called at
  `AiChatSheet.kt:373, 393, 407, 412, 784, 787` (table in the README). `:787` is
  `MarkupShownAsText`, *"Shown exactly as it arrived"*.
- `AiParts.kt:33-52` — `SentText`: a bordered box with `heightIn(max = HUGE × 5)`, its own vertical
  scroll, monospace and selectable. That is the D4 disclosure box, and 88.5 reuses it.
- `AiParts.kt:91-96` — `copyAnswer`, documented as *"The one way out of the AI surface (D1): Copy. There
  is no apply/insert/run."* Code-block Copy goes through it, so there is still one way out.
- `AiChatSheet.kt:280-284` — the conversation is a `Column.verticalScroll`; `:276-279` follows the
  stream while `STREAMING`. `:975` — the DONE **Copy** button copies the raw `state.answer`.
- `AiHttpStream.kt:87` + `AiViewModel.kt:1113-1114` — every stream event republishes the whole answer.
- Compose BOM `2024.12.01` → 1.7.6: `LinkAnnotation`, `TextLinkStyles`, `withLink`, `DisableSelection`
  are available (README sources 1–4). `ui/ai/` has no `AlertDialog` yet.

## Design

### Two composables, chosen explicitly

```kotlin
@Composable internal fun Answer(text: String, streaming: Boolean = false)  // Markdown (this part)
@Composable internal fun VerbatimText(text: String)                         // today's Answer body
```

`Answer` keeps its name, so the five Markdown call sites stay put. Only `:373` gains
`streaming = true`. `:787` switches to `VerbatimText`, which keeps reviewer markup exactly as it arrived.

### Parsing: throttled while streaming, exact at DONE

- **While streaming:** parse **off the main thread** (`Dispatchers.Default`), at most once per
  `AiMarkdown.STREAM_REPARSE_MS`, always on the newest text (`snapshotFlow` + `conflate`, then a
  delay). Chunks that arrive in between are folded into the next parse. A long answer therefore costs
  a bounded number of parses, not one per chunk.
- **At DONE:** the final text is parsed **exactly once** (`remember(text)`), and that result is drawn.
  A throttled parse from the streaming phase is never shown as the finished answer.
- Because an unclosed fence is already `Code(closed = false)` (88.1), a half-streamed code block is
  drawn as code, never as raw backticks.

### Drawing the blocks

Everything sits in **one** `SelectionContainer`, so a selection can span paragraphs, as it can today.

| Block | Drawn as |
|---|---|
| `Heading` | `titleLarge` / `titleMedium` / `titleSmall` for levels 1 / 2 / 3–6, with `semantics { heading() }` |
| `Paragraph` | `bodyMedium`, inline spans as styles |
| `Bullets` | a marker column (`•` `◦` `▪` by depth, `N.` for ordered from `start`, `☐`/`☑` for tasks) and an indented block column |
| `Quote` | a thin start bar in `outlineVariant`, text in `onSurfaceVariant` |
| `Code` | a `surfaceVariant` box: a header row with the language label and a **Copy** button inside `DisableSelection`, then monospace text with `softWrap = false` in a `horizontalScroll` |
| `Table` | a monospace grid (cells padded to visible width, header bold) in a `horizontalScroll` |
| `Rule` | `HorizontalDivider` |

**Nothing in the answer is truncated.** There is no `maxLines`, no `heightIn` cap and no nested vertical
scroll in the renderer: the sheet's own scroll (`:280-284`) carries the answer. Code and tables scroll
sideways only. Colours come from `MaterialTheme.colorScheme` and `CodecTokens`; there are no raw
`Color(0x…)` literals, so the Phase 40.5 contrast law keeps holding.

Inline spans: `Code` → code font on a code background; `Strong` → bold; `Emphasis` → italic;
`Strike` → line-through; `ImageAlt` → muted text naming the image (nothing is fetched); `LineBreak` →
newline; `Link` → `AiLinkPolicy.classify(target)` (88.2):

- **`Openable`** → `withLink(LinkAnnotation.Url(link.url, styles) { pending = link }) { … }`. The
  listener is **always** passed, so a tap only raises the dialog. A two-argument `LinkAnnotation.Url`
  would open the browser directly.
- **`Inert`** → the link text plus ` (target)` as plain muted text. It is not a link.

### The confirm dialog

```
Open this link?
  developer.android.com                    ← host, prominent
  https://developer.android.com/x?y=1#z    ← full URL, monospace, selectable, wrapped
  Opens in your browser. CodeC does not check links.
                     [Copy link]  [Cancel]  [Open]
```

- **Open** → `runCatching { uriHandler.openUri(pending.url) }`, then close. This is the **only** new
  `openUri(` in `ui/ai/`, giving exactly two with `Links()`.
- **Copy link** → `copyAnswer(context, pending.url)`. **Cancel** or dismiss → close; nothing happens.
- The host and URL shown are the fields of the same `Openable` that **Open** passes on.
- Exact wording lives in `AiCopy`, decided at implementation. Tests pin the code that builds it, never
  the sentence.

### What Copy copies

- A code block's **Copy** copies `Code.text` verbatim (no fence lines, no language label).
- The DONE bar's **Copy** (`:975`) keeps copying the **raw Markdown** the model wrote, so pasting into a
  `.md` file or a chat keeps the formatting. This is unchanged on purpose.

## The Android edge

- **New:** `AiMarkdownView.kt` — block and inline drawing, the `AnnotatedString` builder, the dialog.
- **`AiParts.kt`:** `Answer` delegates to the renderer; `VerbatimText` is added.
- **`AiChatSheet.kt`:** `:373` passes `streaming = true`; `:787` calls `VerbatimText`. No other site
  changes.
- **Accessibility:** headings are announced as headings; `LinkAnnotation` gives links link semantics;
  the code **Copy** button has a content description; text follows the system font scale.

## Exit condition

- [ ] Five model-prose sites render Markdown; `:787` renders verbatim.
- [ ] A hostile answer (`<script>`, `<img onerror=…>`, `javascript:`/`data:`/`http:` links) is drawn as inert text.
- [ ] An `https` link tap opens the dialog and nothing else; only **Open** calls `openUri`.
- [ ] No image request, `WebView`, `fromHtml` or `ClickableText` anywhere in `ui/ai/`.
- [ ] Streaming parses off the main thread at most once per `STREAM_REPARSE_MS`; DONE parses the final text once.
- [ ] Code blocks scroll sideways and copy through `copyAnswer`; there is no insert, apply or run path.
- [ ] No `maxLines`, height cap or raw colour literal in `AiMarkdownView.kt`.

## Tests (plan)

**Wiring pins** in `AiLevel11WiringTest` (on `RepoFiles.codeOnly` source, so comments and strings never
satisfy a pin):
- `Answer` calls `AiMarkdown.parse`.
- No `fromHtml`, `WebView`, `ClickableText`, `AsyncImage`, `rememberAsyncImagePainter` or `ImageRequest`
  in `ui/ai/`.
- Exactly two `openUri(` in `ui/ai/`, the new one inside the dialog's confirm lambda.
- Every `LinkAnnotation.Url(` in `ui/ai/` is built with a listener.
- `AiLinkPolicy.classify(` is called by the `AnnotatedString` builder.
- No `maxLines`, `heightIn` or `Color(0x` in `AiMarkdownView.kt`.
- Code Copy calls `copyAnswer(`.
- The `MarkupShownAsText` branch calls `VerbatimText(`; the streaming site passes `streaming = true`.
- The streaming parse runs under `Dispatchers.Default`.

**Robolectric Compose** `AiAnswerLinkDialogTest` (CI only; the host harness cannot run Compose), with a
fake `LocalUriHandler` that records calls:
1. Tapping an `https` link shows the dialog with the full URL and host, and records **zero** opens.
2. **Open** records exactly **one** open, with the same URL.
3. Tapping `javascript:` link text shows no dialog and records zero opens.
4. Tapping `http:` link text shows no dialog and records zero opens.

Counts are a plan, not a result.

## Sources (record)

1. Android Developers, *Enable user interactions* — `SelectionContainer`, `DisableSelection`,
   `LinkAnnotation` with a listener — https://developer.android.com/develop/ui/compose/text/user-interactions
   (fetched 2026-10-03).
2. Compose Foundation 1.7.0 release notes — `ClickableText` deprecated in favour of `LinkAnnotation` —
   https://developer.android.com/jetpack/androidx/releases/compose-foundation (read via search 2026-10-03).
3. Google Maven `compose-bom-2024.12.01.pom` — foundation/ui/ui-text 1.7.6 —
   https://dl.google.com/android/maven2/androidx/compose/compose-bom/2024.12.01/compose-bom-2024.12.01.pom
   (fetched 2026-10-03).
4. Repository lines above, read 2026-10-03 on `8062f0c`.

## Deferred / rejected with reasons

- **Syntax highlighting** — deferred: needs a tokenizer; not required by the acceptance checks.
- **A lazy list for very long answers** — deferred: `Column` keeps whole-answer selection working;
  measure on the device in Level 12 before changing it.
- **Hiding Copy on an unclosed block while streaming** — rejected: Copy copies what is visible, which is
  honest.
- **A "never ask" link setting** — rejected: the owner chose a confirm on every link.
