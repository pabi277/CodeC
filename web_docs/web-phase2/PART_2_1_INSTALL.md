# CodeC Website Phase W2.1 — Install guide (`/install`)

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W1
· **Target file:** `website/install.html`

> Source: `README.md` — "Install the APK from GitHub", "Run C on the phone"
> (Termux engine setup), Troubleshooting (device limits).

---

## 1. Design — page structure (top to bottom)

1. **H1 + one line:** "Get the CodeC APK." (no store — GitHub only, D9).
2. **Three paths, README order, numbered cards:**
   1. **Latest build (GitHub Actions):** Actions tab → latest green
      `Build APK` run → Artifacts → `CodeC-IDE` → download the APK.
   2. **A release:** the Releases page → download the APK.
   3. **In the app (updates):** Settings → **Install APK from GitHub** —
      downloads the latest release APK and opens the installer.
3. **First install note:** Android will ask to allow *Install unknown apps*
   for the browser — allow it once.
4. **Device support table** (`.table`):

   | Device | Experience |
   |---|---|
   | arm64 phone/tablet | Best — built-in TCC + all engines |
   | x86_64 emulator | Good — built-in TCC covers it; bundled Clang module is arm64-only |
   | 32-bit device | Termux engine recommended (switch in Settings) |

5. **Optional: the Termux engine** (collapsible section) — when you'd want
   it (full C11/C17 via Clang, blocked-toolchain fallback), the exact setup
   (mirrors README): install Termux 0.109+ from F-Droid or GitHub (the
   README's two links are the **only** F-Droid/GitHub external references on
   the site — allowed by plan §5.2), then in Termux:
   `echo "allow-external-apps=true" >> ~/.termux/termux.properties`,
   `termux-reload-settings`, `pkg update && pkg install clang`; grant CodeC
   the **"Run commands in Termux environment"** permission; verify with
   Settings → Compiler Engine → **CHECK BRIDGE**.
6. **Footer links block:** Releases page · Actions tab · in-app updater
   recap.

### Meta: title "Install CodeC — APK from GitHub", description from §1.

## 2. Implementation steps

1. Build the page in the W1.1 chrome (active nav: Install).
2. Transcribe the three paths + Termux setup from the README; record the
   source line per section in `chat-web3/`.
3. Self-dependent sweep (plan §5.5); verify the two external README links
   resolve (200).

## 3. Exit condition

```text
1. Render at 360/1440; numbered paths, device table, Termux section all
   present in order.
2. Every command block matches README wording exactly (diff in chat-web3/).
3. A reader with a fresh arm64 phone can follow the page to a downloaded
   APK without opening any other site (manual trace in chat-web3/).
4. No store mention anywhere on the page; sweep PASS.
```
