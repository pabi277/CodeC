# PART 58.3 — the preview's upper links become a hamburger

**Roadmap line:** *“58.3 The page’s upper links become a hamburger, after the research pass.”*
**Owner's row, verbatim:** *“The HTML page view's upper links want a hamburger treatment.”*

## 1. The research pass (both candidates, as the roadmap demands)

The roadmap named two possible readings and forbade inventing either: the page's own nav inside
`index.html`, or CodeC's own preview chrome. It also said: *“If a Play shot has arrived, use it.
If not, ask. Do not invent the preview chrome from the store Snake image.”*

**What the shots actually carry.** Re-read on 2026-09-22, all seven `docs/spck-ui/*.jpg`:

| Shot | What it shows | What it does **not** show |
|---|---|---|
| `122157` | editor, keyboard down, a pill | no preview |
| `122203` | the side panel's Files tree + an “Error opening file.” pill | no preview |
| `124049` | the side panel's NAVIGATION card + RECENT list | no preview |
| `124052` | *(this was the file expected to be the Play shot — see §1.1)* the same editor code area, phone status bar `12:40` | no browser chrome, no address bar, no Play screen |
| `124055` | the side panel's REPOSITORY card | no preview |
| `124058` | the side panel's ACCOUNT card | no preview |
| `124105` | the editor, keyboard up, and — in the **code on screen** — `<button class="nav-btn" …><span>More</span></button>` | no preview screen |

**Its own conclusion.** The Content / Home / More links live **inside the page's own markup**
(`124105` shows their source, including `More`), i.e. they belong to whatever page the user is
editing — in that shot, the user's file, not ours. A page's markup is the user's to write; CodeC
never rewrites a user's HTML. CodeC's *own* preview chrome is the other candidate, and there is no
shot of it at all.

**So the owner was asked** — the roadmap's own instruction, and *“if need say me”*. The two
candidates were put to him with the file:line evidence; on 2026-09-22 he chose **CodeC's preview
chrome**. That answer is what this part implements, and it is recorded here as the reason a
screen the shots do not contain was touched at all.

### 1.1 The one candidate that looked like a Play shot — and why it is not

`Screenshot_20260922_124052_Spck Editor.jpg` (midnight 12:40) has long been treated in this repo's
memory as an unopened image. Opened for this part, it is **not a Play or preview screenshot**: it
shows the same editor code area the other shots show (the `circle"`/`click` fragments of the
user's `index.html`, the `UTF-8` status strip, the ⏷ glyph), with the phone's status bar reading
`12:40`, `0 KB/s`. There is no address bar, no page, no browser controls. So it adds **no** evidence
either way: the roadmap's “if no Play shot, ask” therefore applied, and the owner was asked.

*(Recorded as a negative result on purpose: the next agent should not re-open this file hoping for
a Play screenshot. If a real one arrives later — a page rendered inside CodeC with the old panel
above it — the menu this part builds is the thing to measure it against.)*

## 2. What was built

| File | What changed |
|---|---|
| `app/src/main/java/com/codeci/ide/ui/services/PreviewChromePolicy.kt` | **new** — `PreviewLink`, `PreviewChromeFacts`, `links()`, `hasMenu()`, `panelVisible()` |
| `app/src/main/java/com/codeci/ide/ui/screens/WebPreviewScreen.kt` | the ☰ in the app bar's actions + the menu; the panel behind `panelVisible(showServerPanel)` |
| `app/src/main/java/com/codeci/ide/ui/components/ServerSharePanel.kt` | **one new optional parameter** `onClose` — a header row with the way back, rendered only for a surface that opened the panel from a menu |
| `app/src/main/res/values/strings.xml` | `preview_menu`, `preview_panel_title`, and four menu-item strings |
| `app/src/test/java/com/codeci/ide/PreviewChromePolicyTest.kt` | **new** — 7 cases (which item, when, in what order) |
| `app/src/test/java/com/codeci/ide/PreviewChromeWiringTest.kt` | **new** — 4 wiring pins |

**The chrome today, before → after:**

```text
before                                     after
┌─────────────────────────────┐            ┌─────────────────────────────┐
│ ←  index.html        ⟳      │            │ ←  index.html    ☰   ⟳     │  ← the ☰ is new
├─────────────────────────────┤            ├─────────────────────────────┤
│ ● live   http://127.0.0.1…  │            │ ● live   http://127.0.0.1…  │  ← readout stays
│ On this phone  127.0.0.1… ⧉↗│            │                             │
│ Other devices  not shared   │            │      the page itself        │  ← the page gets
│ [LAN switch]                │            │                             │     this height
│ 127.0.0.1:8100 … STOP ALL   │            │                             │     back
├─────────────────────────────┤            └─────────────────────────────┘
│      the page itself        │
└─────────────────────────────┘            ☰ menu: Copy page address ·
                                             Open in the phone's browser ·
                                             Open this LAN address … ·
                                             Share on LAN · Server options… ·
                                             Stop servers
```

Every item is an action the screen **already had** — nothing was re-implemented:
`OpenInBrowser.openOrCopy` (Phase 41's open-or-copy rule), `ShareActions.label` (the panel's own
wording for the two browser actions), `LanSharePolicy.shared.set`, `host.stopAll()`, and the panel
itself, reachable as its own menu item.

**Rules that survived the move, each pinned:**

* **The ☰ is drawn only when it has something behind it** (`PreviewChromePolicy.hasMenu`) — a menu
  icon over an empty menu is exactly the dead control this app refuses to draw.
* **A `file://` address is copyable but never handed to a browser** — the panel's own scheme rule
  (`ShareActions.isHttpUrl`), so a malformed address can never become a web search.
* **The LAN switch belongs to the server this screen owns** (`ownsStaticServer = !isLive`, the
  panel's `showSwitch` rule unchanged).
* **`Stop servers` keeps the panel's `> 1` rule.** The old chrome only ever offered STOP ALL with
  more than one server; a menu that could stop the very server serving the page would be a new way
  to break the screen it belongs to.
* **The address row stays** as a readout. The links moved; the fact did not.
* **The panel is unchanged inside** — the preview passes no `dense`, so both copy buttons, the QR,
  the notices and STOP ALL are all exactly as they were; only its *default visibility* moved, and
  its new `onClose` header is opt-in (`null` for the Output Panel, which is the panel of that
  surface).

## 3. Test log

| Run | Result |
|---|---|
| `PreviewChromePolicyTest` + `PreviewChromeWiringTest` (host harness) | **12 passed, 0 failed** |

One case was re-cut by its own run, and the reason is worth keeping: the first version asserted
“an unloaded preview offers nothing”, and the policy disagreed — a static preview owns its socket
from the first frame, so `Share on LAN` is real before the page resolves (the panel has always
shown it that way, because it renders from the endpoints and not from the page). The test now pins
both facts separately: a genuinely empty preview draws no ☰, and an address-less static preview
still offers the switch.

## 4. Device rows

Added to [`DEVICE_ROUND.md`](DEVICE_ROUND.md) as **S4-S6**: the ☰ opens and the page gains the
height; every item acts (and the two browser items behave per Phase 41 open-or-copy); the panel
opens from its item, closes from its header, and the ☰ is still there while it is open.
