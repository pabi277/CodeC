# PART 59.2 — the project's own mark

**Roadmap line:** *“59 … a name-derived project mark (pure policy: initials + one of the app's
palette seats chosen from the name hash) replacing the kind-only glyph in `ProjectIconView`.”*
**Owner's answer:** the not-in-shots items are approved to be built as specified (2026-09-22).

## 1. What the square was, and why it changed

`ProjectIconView` drew a **kind** glyph: every C project the same orange `C` (or the Python/HTML
vector), driven by `ProjectHubEntry.icon` via the `HubIconToken` table. Two C projects were
indistinguishable at a glance — which is exactly what the spec's §1 asks to fix (*“auto-generated
distinct logo per project”*).

It is now the **project's own mark**: the name's initials on one of the app's five project-tile
colours, chosen from a hash of the name. A project keeps the same mark forever, and two projects
of the same kind are told apart by initials and, where initials collide, by colour.

## 2. The policy (`ui/projects/ProjectMark.kt`, pure)

```kotlin
enum class MarkSeat { ORANGE, BLUE, VIOLET, GREEN, GRAY }
data class ProjectMark(val initials: String, val seat: MarkSeat)
object ProjectMarks { fun mark(name), initials(name), seat(name) }
```

* **Initials.** The name is split on every run of characters that is neither a letter nor a digit
  (Unicode-aware), and empty words dropped. Two or more words → the first character of the first
  two (`todo-app` → `TA`); one word → its first two characters (`snake` → `SN`); nothing at all →
  `""`, which `mark()` turns into `?` so the view is never handed an empty tile. Upper case, at
  most two characters. **No camel-case splitting** — a name's own separators are the rule, so
  `myApp` is `MY` and `my-app` is `MA`: one rule a user can predict.
* **Seat.** FNV-1a 32-bit over the trimmed, lower-cased name, folded into the five seats with
  `Math.floorMod`. The hash is **written out in the policy** rather than taken from
  `String.hashCode()` for the app's usual offline reason: the mark a user sees must be the same in
  every build, on every device, after every update — and the test pins real values
  (`snake` → BLUE, `api-server` → GRAY, `web-scraper` → GREEN) so a swap to the JVM hash fails
  loudly instead of quietly recolouring everyone's projects.
* **The five seats are the palette's existing project tiles** (`CodecPalette.TILE_ORANGE/BLUE/
  VIOLET/GREEN/GRAY`) — the colours Phase 50.1 measured and darkened for white-on-tile contrast
  (all ≥ 4.83:1). The policy names a seat; `ProjectIconView` maps it to a colour, and the wiring
  pin checks all five seats reach five **distinct** constants.

**Where the kind went.** `ProjectHubEntry.icon` / `iconLabel` and the `HubIconToken` table lost
their last reader the moment the mark stopped being kind-based, so they were **deleted** rather
than left as dead code — and the kind became a word on the card's own information line:
`ProjectsHub.kindLabel(kind)` (`C` / `C server` / `Python` / `Python server` / `Web` / `Project`),
leading `subtitleSegments` (`C · main · 3 files · 2 days ago`). That one-line move is why the phase
can still say *nothing that worked before is gone*: the kind is where the user reads the rest of
the card's facts, and it is now checkable in the same glance as the file count.

## 3. What compiling locally caught (recorded, because it cost a round)

The first draft declared `data class ProjectMark` **and** `object ProjectMark` in the same package —
a redeclaration the sandbox caught on the first compile (`redeclaration` at two lines of the new
file). The fix follows the pattern the file sits beside (`ProjectsHub` holds `ProjectHubEntry`):
the **data** is `ProjectMark`, the **decider** is `ProjectMarks`. Three call sites moved with it
(the view, the policy test, the wiring test).

Two more local lessons worth keeping:

* **Three of my own test expectations were wrong, not the policy** — `mark("snake").initials` is
  `SN` (one word keeps two characters), `initials("日本")` is `日本`, and the seat of `snake` is
  BLUE. They were corrected to the implementation's documented rule, with the real values then
  pinned; the rule was not bent to the guesses.
* **The sandbox harness can now compile `ProjectsHubTest`.** It never could before, because
  `ProjectsHub.kt` reaches `AutoRunPlan` → `ProjectRunTarget` → `LanguageRegistry`/`WebFileSupport`
  → `LanguageType`, and `LanguageType` is declared inside the sora-coupled
  `MultiLanguageSyntaxHighlighter.kt`. The enum is data-only, so the harness lifts it **verbatim**
  into a `/tmp`-only shim (never part of the repo) and compiles the real `LanguageRegistry` and
  `WebFileSupport` against it — which is why this phase's hub cases ran on a host JVM at all.

## 4. Tests

`ProjectMarkTest` (10 cases) holds the two promises the spec's wording really makes: **the same
project always wears the same mark** (stability, case/space folding, pinned hash values) and
**different projects are told apart** (three same-kind names → three marks; a shared
initials pair separated by the seat; every seat reachable). It also fixes the edges: two words vs
one, camel case, non-Latin names, digits, and a name with no letters or digits at all.

`ProjectMarkWiringTest` (7 pins) proves the mechanism: the view asks `ProjectMarks.mark(entry.name)`
and draws `mark.initials`; all five seats map to five distinct `CodecPalette.TILE_*` constants; no
`FileIcon`/`entry.icon` remains in the mark; the kind is on the subtitle; the dead kind→glyph table
is gone; and the recency the *Recent* filter uses comes off disk through the ViewModel.
