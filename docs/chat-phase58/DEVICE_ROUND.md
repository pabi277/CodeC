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
| **S4** | **The ☰, opened and closed**: open the preview (RUN ▶ on a page), then tap ☰ — the menu appears; the page keeps the height the old panel used to take. Tap every item once: the two browser items open (or copy, if no browser takes the URL — Phase 41's rule), *Share on LAN* rebinds and the peer address appears in the menu, *Server options…* opens the panel with its own ✕ header, and the ☰ is **still visible while the panel is open** (the way back can never be hidden by the thing it opened). | the menu's size over a live page and the panel's own ✕ row are visual; a rebind is a socket fact. |
| **S5** | **Nothing was lost**: with the panel open, every control the old chrome had still works — both copy buttons, the browser buttons, the QR toggle, the LAN switch, STOP ALL (which only appears with more than one server, exactly as before). | the old behaviour is the reference; the round proves the move, not a rewrite. |
| **S6** | *(guarded)* a `file://` address never offers the browser item, and an unloaded preview shows no ☰ at all. | the two honesty rules in `PreviewChromePolicy`, visible only on a device. |

**Rules for this round** (same as 57's): one row at a time, on the CI artifact of the tip
commit, with the owner's own words recorded for whatever fails. A failed row is a fix plus a
re-cut or a new pin — never a silent retry.
