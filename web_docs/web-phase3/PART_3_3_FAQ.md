# CodeC Website Phase W3.3 — FAQ & troubleshooting (`/faq`)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/faq.html`

> Source: `README.md` §Troubleshooting + `docs/TROUBLESHOOTING.md` (depth
> links). Website-length answers; the repo docs stay authoritative.

---

## 1. Design — page structure

H1: "Problems, and the honest answers." + intro line (each answer below is
distilled from the repo docs — "go deeper" links go to the exact doc).
`.faq-item` blocks (heading + anchor), in this order:

1. **"The built-in compiler could not start"** — ABI mismatch or corrupted
   install; reinstall; Auto falls back in the meantime.
2. **"Permission denied" when compiling** — the two real causes (Android
   10+ W^X policy → fixed by the targetSdk-28 compatibility mode the new
   builds use; noexec storage → nothing can execute there, Termux included);
   fixes in order: update (Settings → Install APK from GitHub), uninstall +
   reinstall once, Termux engine, real phone.
3. **"Exec format error" when compiling** — CPU mismatch; ARM64 TCC in the
   APK; x86/x86_64 emulators → TCC covers x86_64 automatically, 32-bit or
   stuck emulator → Termux engine; reinstall the module to rule out
   corruption.
4. **"Runtime libraries missing" when compiling** — interrupted/corrupt
   toolchain; Modules → Uninstall → Download (checksum-verified); or Termux.
5. **Install or compile hangs** — compile capped at 30 s, execution at 10 s,
   both killed automatically; a 30 s "hang" usually means the toolchain
   can't start (check above).
6. **"Do I need Termux?"** — No. Auto + built-in TCC work with no Termux,
   no downloads, offline; Termux is an optional engine (→ `/install`).
7. **Keyboard input on a phone** — the extra-keys row (ESC, TAB, CTRL, ALT,
   arrows) + custom macros in Settings; hardware keyboards supported.
8. **Where do my projects live? Can I get them out?** — app-private project
   folders; SAF folder/file/ZIP import & export; ZIP share (→ `/about`).
9. **Where do I report a bug?** — GitHub Issues; include the "Device: …"
   line from Settings → Developer Options → Logs (ABI + storage mount
   flags).

Every block ends with a **go deeper →** link (README anchor or
`docs/TROUBLESHOOTING.md` section).

### Meta: title "FAQ & troubleshooting — CodeC".

## 2. Implementation steps

1. Build the page (active nav: FAQ) with the 9 blocks.
2. For each answer record: source doc + section (traceability table in
   `chat-web3/`).
3. Self-dependent sweep (plan §5.5).

## 3. Exit condition

```text
1. All 9 blocks present, in order, 360/1440 clean; anchors work.
2. Traceability table complete in chat-web3/ (9/9 rows).
3. No answer contradicts README/TROUBLESHOOTING.md (spot-diff recorded).
4. Sweep PASS.
```
