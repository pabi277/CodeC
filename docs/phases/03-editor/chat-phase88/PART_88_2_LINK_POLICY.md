# CodeC Phase 88.2 — Link policy: https only, behind a confirm

> **Status:** ✅ IMPLEMENTED (2026-10-03, on the owner's *"Complete level 11"*; CI-GREEN: Build APK `37132310296` on `88f0186` — see the [README record](README.md#implementation-record-2026-10-03)) · **Cost:** `[client-only]` · **Effort:** S
> **Owner decision (2026-10-03, verbatim choice):** `https` links tappable behind a confirm dialog that
> shows the full URL; `javascript:`, `data:` and plain `http` stay inert.
> **Parent brief:** [Phase 88 README](README.md)

## First move: evidence, not code

Read 2026-10-03 on `arena/01a101db-codec` @ `8062f0c`.

- `AiParts.kt:62-72` — `Links()` is the only link code in `ui/ai/`. Its one `openUri(` (`:67`, wrapped
  in `runCatching`) opens **fixed, trusted** provider-key pages written into the app. An AI answer's
  links are the opposite: chosen by the model, so untrusted (**S3**).
- `MarkdownPreview.safeUrl` (`ui/utils/MarkdownPreview.kt:249-262`) is a **file-preview** allowlist:
  `http:`, `https:`, `mailto:`, `file:`, `data:image/`, `#` and scheme-less relative paths. Each makes
  sense for a local README and is wrong for model output. **It must never be reused as the AI link
  filter**, for the same reason `ProjectSearch.isSearchable` is never reused as the AI file filter.
- Compose 1.7.6 `LinkAnnotation.Url(url, styles, listener)`: with a listener attached, a tap calls
  the listener, not the browser (source 1). The policy decides **which** text becomes a link at all;
  88.3's listener only raises the dialog.

## Design

```kotlin
// In AiLevel11Policies.kt — pure Kotlin, java.net.URI only (never android.net.Uri).

sealed class AiLink {
    /** May be offered behind the confirm dialog. [url] is exactly what Open passes to openUri. */
    data class Openable(val url: String, val host: String) : AiLink()
    data class Inert(val reason: AiLinkRefusal) : AiLink()
}

enum class AiLinkRefusal { NOT_HTTPS, NO_HOST, NON_ASCII_HOST, USERINFO, HIDDEN_CHARACTERS, TOO_LONG, MALFORMED }

object AiLinkPolicy {
    const val MAX_URL_CHARS = 2_048
    fun classify(target: String): AiLink
}
```

The checks run in this order. The first failure decides.

1. Trim ASCII whitespace at both ends. Nothing else is normalised.
2. Longer than `MAX_URL_CHARS` → `TOO_LONG`.
3. Any **hidden character** → `HIDDEN_CHARACTERS`: whitespace or control characters inside the URL
   (U+0000–U+0020, U+007F–U+009F), zero-width and invisible characters (U+200B–U+200F, U+2060–U+2064,
   U+FEFF) and bidirectional overrides (U+202A–U+202E, U+2066–U+2069). Browsers silently strip some of
   these, and bidi overrides make a URL *read* differently from what it is. Refusing them outright
   avoids reasoning about either.
4. The scheme (the text before the first `:`) is compared **case-insensitively** (RFC 3986 §3.1,
   source 2). Only `https` passes; anything else → `NOT_HTTPS`. `JavaScript:`, `HTTP:`, `Data:` are the
   schemes `javascript`, `http`, `data`. A scheme-relative `//host/x`, a relative path and a bare
   `#anchor` have no `https` scheme and are refused too.
5. `java.net.URI(target)` fails → `MALFORMED`.
6. Any user-info → `USERINFO`. `https://bank.example@evil.example/` really goes to `evil.example`; the
   part before `@` exists to deceive.
7. No host → `NO_HOST` (`https://`, `https:///path`).
8. A host that is not ASCII letters, digits, `-` and `.` (or a bracketed IP literal) → `NON_ASCII_HOST`.
   Punycode (`xn--…`) passes and is **shown as punycode**. Decoding it for display would invite
   look-alike hosts.
9. Otherwise `Openable(url, host)`. `url` is the target with **only the scheme lowercased**; every
   other character is kept as written. `host` is lowercased for display.

What the user sees for each outcome (drawn by 88.3):

| Outcome | In the answer | On tap |
|---|---|---|
| `Openable` | link-styled text | a dialog naming the **host** and showing the **full URL** verbatim (selectable, wrapped, never ellipsised); **Open** calls `openUri(url)`; **Copy link**; **Cancel** |
| `Inert` | the link text, then its target as plain muted text in parentheses | nothing |

Showing an inert target as text is deliberate. `http://localhost:8080` is often exactly what the user
needs to copy, and plain text is inert. The hostile forms (`javascript:…`) become visible instead of
hidden, which is the honest outcome.

## The Android edge

None in this part. The policy is pure, so every case is host-testable. 88.3 calls `classify` once per
`Link` span while building the `AnnotatedString`, and the dialog shows the `Openable` it got back.

## Exit condition

- [ ] Only `Openable` results ever become `LinkAnnotation`s (88.3 pin).
- [ ] The allow and deny tables below pass exactly.
- [ ] The URL shown in the dialog and the URL passed to `openUri` are the same `Openable.url` (88.3 pin).
- [ ] No `android.net.Uri` and no network call in the policy; it stays pure (source pin).

## Tests (plan)

`AiLinkPolicyTest`, about 20 cases.

**Allowed:** `https://developer.android.com/x?y=1#z` · `HTTPS://Example.COM/a` (url
`https://Example.COM/a`, host `example.com`) · `https://example.com:8443/p` ·
`https://xn--exmple-cua.example/` · `https://192.168.1.10/` (still https, still confirmed).

**Inert:** `http://example.com` · `javascript:alert(1)`, `JavaScript:alert(1)`, ` JAVASCRIPT:x` ·
`vbscript:x` · `data:text/html;base64,…` and `data:image/png;base64,…` · `file:///sdcard/x` ·
`content://x/y` · `intent://x#Intent;end` · `mailto:a@b.example` · `tel:123` · `ftp://x.example` ·
`//evil.example/x` · `relative/path.md` · `#anchor` · `https://` · `https://good.example@evil.example/`
· `https://user:pw@host.example/` · `https://exa\u200Bmple.com` · `https://example.com/\u202Egnp.exe`
· `java\tscript:x` · `https://ex ample.com` · `https://exämple.com` · a 2 049-character URL.

Counts are a plan, not a result.

## Sources (record)

1. Android Developers, *Enable user interactions* — `LinkAnnotation.Url` with a custom listener —
   https://developer.android.com/develop/ui/compose/text/user-interactions (fetched 2026-10-03).
2. RFC 3986 §3.1 — *"schemes are case-insensitive"* — https://www.rfc-editor.org/rfc/rfc3986#section-3.1
   (not re-fetched this session).
3. Repository: `AiParts.kt:62-72` and `MarkdownPreview.kt:249-262`, read 2026-10-03 on `8062f0c`.

## Deferred / rejected with reasons

- **Opening `http:` links** — rejected by the owner (2026-10-03).
- **`mailto:` / `tel:`** — not requested; they hand data to another app. They stay inert and visible.
- **Decoding punycode for display** — rejected: invites look-alike hosts.
- **A reputation or Safe Browsing lookup** — rejected: a network call (**S3**), and Send stays the only
  network road the app itself drives.
- **Link previews or unfurling** — rejected: a network fetch triggered by rendering.
- **A "don't ask again" switch for the dialog** — rejected: the owner chose a confirm on every link.
