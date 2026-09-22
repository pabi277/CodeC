# Phase 58 — device round (owed, not run)

The phase's exit says *“Device pass required: Yes.”* These are the rows a handset answers; the
editor-chrome rows they sit on top of are in
[`../chat-phase57/DEVICE_ROUND.md`](../chat-phase57/DEVICE_ROUND.md) (P1-P10 / Q1-Q6 / R1-R11),
and none of either set has been run yet.

| # | Row | Why it needs a phone |
|---|---|---|
| **S1** | **A genuinely fresh install** (clear app data, launch): the editor opens on `snake/index.html`, the page is playable with a thumb (the pad) and with the phone's own way of typing, **no hub frame appears first**, and **no setup strip is anywhere** — not on the editor, not on the other tabs. | the seed path runs once per install and the strip's absence is a *deletion*; only a real first launch proves both, and the safe-mode branch (`SafeMode.active` ⇒ no seed) cannot be exercised in a host test. |
| **S2** | **The one warning**: still uninstalled (do not open Terminal), press RUN ▶ on a Python file. Exactly **one** pill appears, *“Finish installing the Linux tools first — open Terminal.”*, **nothing is downloaded on that tap**, and the editor stays usable. Then open Terminal, let the install run, come back, and press RUN ▶ again — the install sheet is offered now (the pill was the gate, not a wall). | the pill's timing (2.4 s), its size over the touch row, and the "one pill, not two" claim are visual; the reset of the gate after a real install is a device fact. |
| **S3** | **RUN ▶ on the page**: fresh install, snake open, RUN ▶ — the preview opens with **no download and no wait**, and the page is still playable inside it. | the in-process server is a device surface. |
| **S4** | *(after 58.3)* the hamburger row — to be written when the research pass decides whether the change is the page's own nav or CodeC's preview chrome. | — |

**Rules for this round** (same as 57's): one row at a time, on the CI artifact of the tip
commit, with the owner's own words recorded for whatever fails. A failed row is a fix plus a
re-cut or a new pin — never a silent retry.
