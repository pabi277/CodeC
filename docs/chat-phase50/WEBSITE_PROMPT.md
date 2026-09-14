# Phase 50.2 — the matrix filler: one paste-ready brief for a website-building AI

> **What this file is, and how to use it.** Paste **this whole file** and
> `docs/chat-phase50/DEVICE_MATRIX.md` into a fresh chat with any capable website
> builder, and say *"build exactly this"*. It returns **one self-contained HTML
> file**. Open that file on the phone you are testing on, answer 100 pre-made
> questions with big buttons, and **download a `.md`** at the end. That `.md` is
> shaped so a coding agent can paste it into the repo and so
> `app/src/test/java/com/codeci/ide/DeviceMatrixTest.kt` accepts it on the next
> CI run — which is the whole reason this brief exists instead of a blank form.
>
> **This is not app code.** Nothing here is compiled, packaged, or shipped in the
> APK; no dependency is added; no `app/src/main` file changes. It is an intake
> tool for a human running a device round. If the builder is asked to "just wire it
> into the app", the answer is no.
>
> **Why not a spreadsheet?** Because a spreadsheet cannot hold the three rules that
> make a device round trustworthy: every row of a part must appear exactly once,
> a ✅ may not exist without a device identity + class + build, and the sentence a
> row expects must stay verbatim. A form that enforces them is worth more than a
> grid that doesn't.

---

## 0. The deal — read before building anything

1. **One `.html` file.** No build step, no `npm`, no framework, no CDN, no web
   font, no icon set, no analytics, no `fetch()` at all after load. Inline CSS and
   inline JS. It must work when opened from the phone's Files app, in airplane
   mode, with no server behind it. If a feature needs a library, drop the feature.
2. **The data is baked in.** The builder parses `DEVICE_MATRIX.md` **at build
   time** and writes the parsed rows into the HTML as a JS array. The tester's
   phone never sees that file and must never be asked to paste it again. Keep the
   raw matrix text in the page (in a `<script type="text/plain">` block) so the
   page can re-verify itself against its own source (§7).
3. **Nothing leaves the device.** Results live in `localStorage`. Export is a file
   the *owner* moves by hand. No upload, no account, no sync, no screenshot
   transfer (only a screenshot's *name*, typed), no "share to cloud" affordance.
   CodeC's law is no telemetry, and this tool touches the same testing workflow.
4. **Verbatim is law.** Every piece of expectation text shown to the tester is the
   matrix's own cell string, character for character (markdown `**` markers may be
   stripped for rendering; nothing else may change). No summarising, no
   re-phrasing, no "clarifying" the app's wording, no translations. The PASS column
   is CI-pinned to the app's real strings precisely so a tester reads what the app
   says; a paraphrase in the tool turns a working app into a reported failure.
5. **The site never edits the runbook.** No row may be added, deleted, renamed,
   renumbered, reordered or "fixed" by the tool. If a row looks wrong to the
   builder, that is a repo bug — say so in the delivery message, don't patch it.
6. **Phone-first, thumb-first.** 360 dp wide is the design target, not the minimum.
   Tap targets ≥ 44 dp; the two answer buttons sit in the bottom third; one-hand
   scrolling only; the page must survive a reload mid-test and a browser killed
   after 10 minutes in the background (that gap is literally what rows A11 and E
   measure). Support the system font scale up to 200 % and both light and dark.
7. **No invented states.** The answer set is fixed: matched / different / could-not-run /
   waiting / unanswered. Anything else is a bug in the form, not a new option.

---

## 1. Inputs the builder gets

| Input | Use |
|---|---|
| `docs/chat-phase50/DEVICE_MATRIX.md` | the only data source: rounds, rows, device classes, the 20-minute pass, §0's preparation steps, §3's record format |
| this file | the contract: parse rules, UI behaviour, export shape, self-check |

Nothing else. Do not ask the owner for the app, a schema, an API, or a design file.
Do not read the internet for "matrix testing best practices".

---

## 2. The parse contract

The matrix is markdown with a strict grain. Parse it literally; if a line does not
fit, **stop and report the line number** rather than guessing — a silently skipped
row is exactly the failure this phase was created to kill.

| Find | Where | Notes |
|---|---|---|
| Rounds | every `### Round <letter> — <title> (<part>)` heading, in document order | ten of them, letters A…J; keep the title and the part in parentheses for the round header |
| Rows | the table under each round heading, `^\|\s*([A-J]\d+)(\s*★)?\s*\|` | 5 columns: `#` `Part` `Run on` `What to do` `PASS looks like`. One row = one line, so a row that wraps in the source is a parse error, not a multi-line row |
| Row id | column 1, `★` stripped from the id, remembered as a flag | `★` means "run it first on a new device / the answer differs by class" — show it as a badge, never as part of the id |
| Part | column 2, e.g. `44.1` | drives which `## Test log` table the answer ends up in (§6b) — show it on the card so the tester knows where the result lands |
| Run on | column 3, `ALL` or one/many of `D1 D2 D3 D4` | drives per-device filtering (§4). A row not in the current device's class renders **dimmed, not hidden**, with a one-tap `could not run (no such hardware)` |
| Classes | §1's table (`| **D1** | Small gesture-nav phone …`) | use the `Shape` column verbatim as the text of each class chip |
| The 20-minute pass | §3, the fenced block of ids | exactly 24 ids; render as the `SHORT` pass filter. If the count differs from 24, report it — the doc says 24 |
| Preparation | §0's numbered steps | render, verbatim, as a dismissible "before you start" panel shown once per device, including the `COPY REPORT` instruction and the nav-mode step |
| Record format | §3's fenced "per-device sheet" block and `### The results, as they arrive` table header | §6b/§6c must reproduce these shapes byte-for-byte in the export |

Also keep, per row, the **whole cell text** (including any inline code spans and
arrows) — do not split on `|` inside a cell without checking that the cell has no
escaped pipes; the doc uses none, so a naive split is fine, but assert the column
count is 5 and fail loudly otherwise.

Expected totals after parsing: **100 rows**, contiguous ids per round
(A1…A14, B1…B7, C1…C6, D1…D19, E1…E7, F1…F7, G1…G9, H1…H9, I1…I15, J1…J7),
11 distinct parts (44.1 ×22, 44.2 ×8, 45.1 ×7, 45.2 ×20, 46.1 ×3, 46.2 ×5, 47.1 ×6,
47.2 ×4, 48.1 ×9, 49.1 ×8, 49.2 ×8). Print these numbers in the self-check (§7);
if the site cannot produce them, the site is wrong, not the doc.

---

## 3. Session model — the device comes first

A **session** = one device × one pass. State machine:

1. **New device.** Ask, in this order, all on one screen:
   - **Identity line** — one textarea, empty is not accepted. The panel says, in
     the doc's own words: *Settings → Feedback & Support → COPY REPORT → paste
     anywhere → line 1* is `CodeC <version> · Android <release> (API n) · <model> ·
     <abis>`. Do not validate its format beyond "not blank"; do not auto-fill it
     from `navigator.userAgent` — that is a browser, not a handset, and the pin
     trusts only the app's own line.
   - **Nav mode** — two chips: `3-button` / `gesture`. Required.
   - **Android release + API level** — two numeric-ish fields; also accept a paste
     of the whole identity line and pre-fill from it if the pattern matches.
   - **Screen size and RAM** — free text; used only to *suggest* a class.
   - **Class** — four chips `D1 D2 D3 D4`, pre-selecting the suggestion, editable.
     The tester's word beats the heuristic; never auto-lock it.
   - **Build** — CI run id and/or commit, plus a link to the artifact if he has it.
     Required before an export can contain a ✅ (§6c). Offer "same as the last
     device" so he doesn't retype it.
   - **Date** — defaults to today, editable.
   - **Pass** — `FULL` (all 100) or `20-minute` (the 24). Choosing `20-minute`
     filters the list; it does not delete the other rows.
2. **Device switcher** in the header: every session is independent; a row answered
   on device 1 is *not* answered on device 2. Label each tab by `model · nav mode`.
3. **Storage key**: one `localStorage` entry per session
   (`codec-p50::<device-slug>::<pass>`), plus `codec-p50::last`. Never store
   anything else; no cookies.

---

## 4. The question card

One card per row, in a single scrollable list grouped by round, with a sticky
progress strip. Card contents, in order:

- `A13 ★` · `part 44.1` · `Run on: ALL` · a `copy row id` button.
- **What to do** — the cell, verbatim, in reading size. Long rows (they run past
  five lines) stay uncollapsed: a truncated instruction produces a wrong test.
- **PASS looks like** — the cell, verbatim, in a visually distinct block (code-ish
  styling for anything inside backticks), because this is the sentence he must
  compare the screen against.
- Answer buttons, big and side by side: `✅ matched` · `❌ different` ·
  `n/a — couldn't run` · `⏳ not yet`. Tapping the current answer clears it.
- **Evidence**, always available but *required* only for `❌`: a textarea with
  `paste from clipboard` and a small `screenshot filename` field. For `✅`, one
  optional line, prompted with the doc's own standard — quote what the screen said
  if it is the interesting kind of pass (mid-percentage, mid-lock, post-kill).
- For `n/a`, one chip that records *why*: `no such hardware` / `no second device` /
  `superseded by a newer build` — `n/a` must never be recorded as ✅.
- For `❌`, the card cannot be left like that: after the evidence, it asks which
  one it is — **`fixed in <commit/PR>`** or **`deferred to YYYY-MM-DD because …`** —
  because the phase's exit condition 3 is "every failure has a fix or a dated
  deferral". The answer saves without this, but the export marks the row
  `❌ (no disposition)` and the self-check flags it.
- **`waiting` state**: some rows say *start a download, put the app in the
  background 10 minutes, come back* (A11, the E-round lock rows, B1/B2's kills).
  A card can be set to `waiting` so it stays visually open at the top of the list
  with a note of what he is waiting for, and it does not count as answered.

A row answered on a device whose class the row does not name gets a small
non-blocking notice: *"this row is a D2 row and you're on D1 — record it, it still
counts as extra evidence"*. Never forbid it; the doc's `Run on` column states the
minimum, not the ceiling.

---

## 5. Progress, resume, and the short pass

- Per-round counters in the sticky strip: `A 9/14 · B 4/7 · …`; total answered;
  `❌ 2 (1 with no disposition)`; `n/a 3`.
- Filter chips: `all` · `unanswered` · `★ first` (the starred rows) · `❌ and unknown`
  · the 20-minute pass · by round · by part.
- Autosave on every change (debounced ~300 ms) and *before* unload; a quiet
  "saved" tick. If there are unsynced answers and he presses the browser's back,
  warn once — the whole point of the tool is that a lost afternoon is not an option.
- **Resume by import**: "load a previously exported `.md`" re-parses the export's
  own tables (§6b) and restores answers. `localStorage` is per-app-data and a
  browser cleanup can eat it mid-round; the export doubles as the backup. Also
  offer `download JSON backup` (same data, no parsing needed) — but `.md` is the
  primary export and must never need the JSON to be valid.

---

## 6. The export — the part that has to be exactly right

`Download report (.md)` produces **one file**, filename
`codec-device-<model-slug>-<YYYYMMDD>.md`, containing four blocks in this order.
Every line of it is pasted straight into the repo by a human or an agent, so the
shapes below are not style preferences — they are what the CI pin reads.

**a) The per-device sheet**, exactly §3's block:

```text
Device:   <the identity line he pasted>
Class:    D2        Nav mode: 3-button     RAM: 6 GB     Date: 2026-09-14
Build:    CI run 34744496605  /  commit 62cfe7b
Pass:     FULL

A1 ✅   A2 ❌ "the bar's number went back to 0 after I came back to Terminal"   …
```

**b) The part-file tables** — one fenced block per part that the device ran rows
for, headed by the part file's real path so the paste-back is mechanical:

~~~markdown
### docs/chat-phase44/PART_44_1_VISIBLE_SETUP.md

| Row | Result | Evidence (device · OS · nav mode · what was seen) |
|---|---|---|
| A1 | ✅ | Pixel 4a · Android 13 · gesture · bar read `Setting up CodeC · C works right now` |
| A2 | ❌ | Pixel 4a · Android 13 · gesture · "the bar's number went back to 0" |
| A3 | ⏳ | not run |
~~~

Rules for b), because `DeviceMatrixTest` enforces them:
- the heading line is the literal path of that part file, and the section it feeds
  in that file is `## Test log (Phase 50 — the cross-device matrix)`;
- **every row the matrix assigns to that part must appear, exactly once, in matrix
  order** — including the untouched ones as `⏳`. A missing row or a row listed
  under the wrong part fails CI; so does a duplicate;
- first column is the bare id (`A1`, never `A1 ★`, never `Round A row 1`);
- one line per row; no nested pipes in cells (replace `|` in pasted text with
  `\|` or, better, with `/`).

**c) The results row** for §3's `### The results, as they arrive` table — exactly
8 columns, header included so the paste-back can be checked by eye:

```markdown
| Device | Class | Nav | Build | Rows run | ✅ | ❌ | Report |
|---|---|---|---|---|---|---|---|
| Pixel 4a · Android 13 | D2 | 3-button | 34744496605 / 62cfe7b | 100 | 94 | 2 | codec-device-pixel4a-20260914.md |
```

The pin's rule, which the site must enforce *before* it lets a ✅ exist: a row
containing ✅ or ❌ must carry a real device name, a class that is not `—`, and a
build that is not `—`. If the session is missing identity or build, the export
still works but every answer is written as `⏳` with a first line
`!! NOT SUBMITTABLE: device identity or build missing` — a red banner in the app
says the same thing while he fills it in.

**d) Failures.** After the tables:

```markdown
## Failures
- **A2** (part 44.1) — "the bar's number went back to 0 after I came back to
  Terminal" — **deferred to 2026-09-21**: needs the log line, owner asked for a
  repro on the second device first.
```

One bullet per `❌`, with the verbatim text, the owning part, and the fix commit or
the dated deferral. This block is what exit condition 3 is read from.

**Also in the file, at the very top:** a `> Generated by … from DEVICE_MATRIX.md
(<sha or date of the copy baked in>) · device · pass` line. A report that cannot say
which build of the *document* it was filled against is the exact staleness this
phase exists to remove.

---

## 7. The built-in self-check

A `Self-check` button (and an automatic run on load, quiet unless it fails) that
re-derives everything from the baked matrix text and prints pass/fail lines:

1. rows parsed = 100, ids contiguous per round, no duplicate id;
2. every part's row count equals the export's row count for that part, sets equal;
3. every cell rendered in the UI equals the source cell (strip markdown, compare
   raw) — this catches any place where the builder "improved" the wording;
4. the three pin rules from §6b/§6c hold for the export as it currently stands;
5. `❌` count in the sheet = number of `❌` in b) = `❌` column in c) = bullets in d);
6. the shortlist parsed = the 24 ids in §3, and every one of them exists as a row.

Anything red means do not send the export; the message must name the row ids at
fault, not "some rows differ".

---

## 8. What the owner does with the download (put this on the page as a footer)

1. Open the file, read the red banner if there is one.
2. Hand it to the repo's coding agent with this sentence, and no more:
   *"Phase 50 device round: fill each part file's `## Test log` table from block (b)
   — replace only the rows whose result changed, keep the row ids — add block (c)'s
   row to `docs/chat-phase50/DEVICE_MATRIX.md` §3, add block (d) to the owning part
   file's notes, and never change a `PASS looks like` cell. Then push and let
   `DeviceMatrixTest` in CI judge it."*
3. The agent may not re-word any expectation, may not tick a row the file doesn't
   show as run, and may not "fix" a red `DeviceMatrixTest` by relaxing a floor.

---

## 9. Out of scope — do not build, even if it seems easy

Server, database, auth, cloud sync, sharing a "report link", screenshot upload, PDF
generation, dashboards, charts beyond the progress strip, an AI summary of results,
a mobile app, a browser extension, service-worker install prompts, analytics,
i18n beyond English, editing `DEVICE_MATRIX.md`, a second format to fill in,
keyboard-driven flows, dark-mode-only. If a nice idea needs one of those, the idea
loses.

## 10. Nice-to-haves, only when §0–§8 all work

- a per-row timer (some rows measure what the phone does over minutes);
- `⏳ → waiting → answered` keyboard shortcuts on a desktop, since a round is
  sometimes run beside a laptop;
- `@media print` CSS so a device sheet can be printed and ticked with a pen;
- a `plain text` export variant of block (a) only, for pasting into a chat;
- deep links to a row (`#A13`) so a bug report can point at a card.

---

## Sources for this brief (what the export must satisfy)

- `app/src/test/java/com/codeci/ide/DeviceMatrixTest.kt` — the eleven host cases CI
  runs: contiguous unique ids; `Part` must name a real part file; every back-ticked
  span in a `PASS looks like` cell must exist in the app; `Run on` must be a real
  class and prompt rows must ask for both nav modes; **each matrix row is listed in
  exactly one part file's `## Test log`** (two-way set equality, `^\|\s*([A-J]\d+)\s*\|`
  is the reader); the destructive pair is present; **a results row may not claim a
  result without a device, a class and a build**; the runbook must point at a branch
  that ships all of 44–49; the matrix must be reachable from this phase's README and
  the roadmap.
- `docs/chat-phase50/DEVICE_MATRIX.md` — the data, §0's preparation, §1's classes,
  §3's record format and results table, §4's re-sync law, §5's honest limits.
- `docs/TROUBLESHOOTING.md` §46 — how a red `DeviceMatrixTest` is read, and the rule
  that a pin is never lowered to get green.
- `docs/BETA.md` — the standard a device round is held to: a record per device, not
  a verdict.
