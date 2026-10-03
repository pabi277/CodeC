# Level 11 — Agent phone presentation

**Status: ✅ MERGED 2026-10-03 to `main` via [PR #113](https://github.com/pabi277/CodeC/pull/113) on the owner's command (*"Ok merge to main"*). Implemented the same day as [Phase 88](../../phases/03-editor/chat-phase88/README.md) on the owner's command (*"Complete level 11"*); CI-green (Build APK `37132310296` on `88f0186`). (Authorized the same day for a brief first.) Depends on Level 7 (merged); independent of 8–10. Formal device acceptance remains POSTPONED to Level 12.**
**Owner decisions (2026-10-03, answered in chat):** the **full level in one phase**, in about 4–5 parts (Markdown answers, one truthful compact progress line, activity rows showing the full result on tap, request disclosure collapsed and **never removed**) · **links:** `https` links are tappable **only behind a confirm dialog that shows the full URL**; `javascript:`, `data:` and plain `http` stay inert · the Level 10 read-window default **stays 400**. Agent defaults the owner did not object to: a **Copy** button per fenced code block (copy only, never insert, **S6**) and **live, throttled** rendering while streaming.
**Corrections found while briefing** (details in the Phase 88 README): the defect citation `AiChatSheet.kt:584` below predates Phase 87.5, which already collapsed REQUEST rows (`:624`, `:638-669`) · `MarkdownPreview`'s URL allowlist is a file-preview policy (it admits `http:`, `file:`, `mailto:` and relative paths), so Level 11 gets its own stricter link policy instead of reusing it · there are **no** existing Roborazzi goldens to update (zero captures, zero reference images) — see the Phase 88 README's *Open decision 1* · the APK delta is recorded against both the Level 6 baseline and current `main`, because Levels 7–10 already grew the APK.
**Shared foundation:** [defect register, security rules S1–S12, sources](00_AGENTIC_MAP_AND_SECURITY_RULES.md).

## User value

Fixes the two complaints that made the agent unusable on a phone: **plain unformatted answers**, and an
activity card so full of system instructions and project maps that the actual answer was buried.

## The defect

- `AiParts.kt:55-58` — `Answer` is `SelectionContainer { Text(text, bodyMedium) }`. **No Markdown at all.**
- `AiChatSheet.kt:584` — `SentText(instruction + "\n\n" + step.sentUserText.orEmpty())` renders inside the
  timeline loop for **every** REQUEST row. At 12 turns that is 12 full copies of the system instruction
  plus a 6 000-char map, inline.

## Four separate surfaces

One card doing four jobs is the root problem. Split it:

1. **Answer** — the prose, formatted, selectable, scrollable.
2. **Compact progress** — one truthful line: what stage, how many reads, what is left.
3. **Tool activity** — expandable rows, collapsed by default, full result on tap.
4. **Exact request disclosure** — collapsed, **never removed** (**S8**, D4).

Truthful disclosure stays available. It just stops being the primary view.

## Markdown without a new dependency

CodeC **already owns the hard part**: `ui/utils/MarkdownPreview.kt` — a pure, host-tested converter whose
header states it escapes HTML **first** so output *"can never inject markup from the file"*, sanitises URLs
against `javascript:`, `vbscript:` and non-image `data:`, and carries **no JavaScript at all**.

That is exactly the posture required for untrusted model output. Reuse its parsing and escaping discipline,
retargeted to Compose `AnnotatedString` / inline content — **not** HTML in a WebView, which would mean a
WebView per chat bubble and a scrolling/memory problem on a phone.

**Evaluated and rejected:** `jeziellago/compose-markdown` (advertises HTML support and remote images/GIFs —
a *weaker* posture than the code already in the tree) · `mikepenz/multiplatform-markdown-renderer` ·
`halilozercan/compose-richtext`. All add APK weight to replace something already present.

## Beginner-friendly explanations

The owner's actual request was *"think I don't have much code knowledge."* Combined with Level 10's
**Answer detail** option, a thorough explanation must be allowed to be long and sectioned. Concise mobile
typography must not mean silently omitting most of the file — that was defect 1 all over again, at the
rendering layer.

## Touches

`AiParts.kt` · `AiChatSheet.kt` · a new pure Markdown-to-`AnnotatedString` renderer plus its host test.
**No new dependency. No permission change.**

## Security rules

**S8** — disclosure collapsed, never removed; provider and recipient still named per request. **S3** —
model output is untrusted: no HTML execution, no JavaScript, no remote image fetch, no automatic link or
network effect. **S1** — expanding or collapsing a row must not change what the model receives.

## Acceptance checks

- [ ] Headings, lists, inline code and fenced code blocks render; raw Markdown syntax is not shown.
- [ ] A hostile answer containing `<script>`, `<img onerror=…>`, and a `javascript:` URL renders inert.
- [ ] No network fetch is triggered by rendering an answer.
- [ ] The answer is selectable and scrollable without fighting the sheet.
- [ ] Request disclosure is one tap away and still shows the exact two strings sent.
- [ ] Collapsing or expanding any row does not alter the packed request (**S1**).
- [ ] A long beginner explanation renders in clear sections without truncation.
- [ ] Existing Roborazzi goldens updated deliberately, not incidentally.
- [ ] APK size delta recorded against the Level 6 baseline.

## Research references

- Anthropic — *think like your agent*; richer observations over more layers — https://www.anthropic.com/research/building-effective-agents
- Anthropic, *Effective Context Engineering* — system prompts at the right altitude, structured into navigable sections — https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents
- SWE-agent — *environment feedback should be informative but concise* — https://arxiv.org/abs/2405.15793

**Next:** [Level 12 — Evaluation and acceptance](12_AGENT_EVALUATION_AND_ACCEPTANCE.md)
