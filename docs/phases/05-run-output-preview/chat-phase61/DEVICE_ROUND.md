# Phase 61 phone round — NOT RUN

> **Owner update — 2026-09-27:** Top-level appearance looks good; further device
> testing for this delivery is declined, **not passed**. The owner explicitly
> authorised merging via [PR #86](https://github.com/pabi277/CodeC/pull/86),
> subject to final CI. Future detail-by-detail polishing phases will be defined
> in new chats. [Full owner feedback and handoff](../../../journal/OWNER_HANDOFF_20260927.md).

**Checklist retained for reference only; the owner is not being asked to run it for this merge.**

Install the APK from CI **36300428167**, code **5707e38**:
https://github.com/pabi277/CodeC/actions/runs/36300428167

Optional fixture: copy the two files in [device-fixture/](device-fixture) into
a CodeC web project and preview `index.html`. It is our own small test page,
not an SPCK sample. It starts without console messages or fetches.

| ID | Action | Expected |
|---|---|---|
| P1 | ☰ → Show console / Network before emitting any logs | Empty console is reachable; page retains space |
| P2 | Tap Emit all console levels | Five messages; debug shares Log, info/warn/error have their own filters |
| P3 | Turn each filter off/on, then all off, then on | Only selected levels show; all-off empty state; originals return |
| P4 | Drag handle up/down to limits | Console never consumes more than 65% of remaining content; page still visible |
| P5 | Open Network; Fetch JSON once | Page receives JSON once; GET resource row without the query; no fabricated status/duration |
| P6 | Clear Network, then Console | Only selected tab clears; empty state visible; fresh events still appear |
| P7 | Close panel and reopen it | Page regains space; captured entries and chosen height retained within session |
| P8 | Type into input, select Zoom 150%, then 100% | Visible zoom/reset; text survives and Page loads count does not increase |
| P9 | At 100%, choose 360×640, 768×1024, 1280×720 | Page reports corresponding logical viewport (rounding permitted); wide mode blue; entire simulated rectangle fits page area |
| P10 | Tap page controls in each fitted viewport; choose Device | Hit targets track visuals; Device fills available area |
| P11 | Open console; rotate or raise IME | Both surfaces remain bounded. Rotation recreates view/capture session; no old messages promised |
| P12 | Refresh, then edit/save page from editor | Refresh/live reload still load page; no duplicate polling loops |
| P13 | Existing ☰ Open in browser, LAN/server options | Existing actions still work, normal errors/copy fallback unchanged |
| P14 | Back to editor; switch project, preview again | Editor code stays visible; old native callbacks cannot populate new diagnostic session |

Network caveats: HTTP(S) request observations only; redirects' later hops,
blob/javascript/packaged assets and some other traffic are not reported by this
WebView hook. No response timing/status, request bodies, headers or cookies.
Zoom/viewport caveats: logical dp and fit-to-screen, not device/DPR emulation;
page viewport metadata and native zoom limits still apply.

Please report failing ID + screenshot if any. No rows are marked passed until
the owner reports them; green CI does not imply this handset round passed.
