# CodeC Website Phase W4.7 — Chapter 05: Package Manager

**Status:** 📋 **PLANNED** · **Cost:** `[static]` · **Effort:** S
· **Depends on:** W4.2 gate (package list verified)
· **Target file:** `website/ch-05.html`

---

## 1. Content

- **Goal box:** install a package with one tap; run it from the same
  screen; use `pkg` from the terminal; read the status badges; know the
  repository's rules.
- **Need:** Chapters 03–04 done.

### Steps

1. **What `pkg` is** — a guarded, CodeC-only frontend over the signed
   repository `https://pabi277.github.io/CodeC/dev`; small and curated,
   not the Termux universe (honest scope, 3 lines — depth on `/packages`).
2. **The Packages tab tour** — the four things the tab does, each with the
   exact UI name (W4.2 facts): **1-tap INSTALL / RUN** on a package card;
   **live status badges** (`INSTALLED ✓` / `AVAILABLE`); **quick system
   actions** (`pkg update`, `pkg upgrade -y`, `codec-setup-storage`,
   `pkg status`, `pkg heal`, `pkg repair`); **interactive command runner**.
3. **Install nano** — find the `nano` card → **INSTALL** → watch the badge
   flip → **RUN** (or open Term and type `nano`).
4. **Use nano** — `nano first.txt`, type a line, the in-editor keys in
   plain terms (Ctrl+O write out, Ctrl+X exit — the extra-keys row makes
   Ctrl easy), quit, `cat first.txt` to confirm.
5. **From the terminal** — in Term: `pkg status` (what's here), `pkg
   update` (refresh the index), `pkg upgrade -y` (upgrade all) — each
   command, what it prints, when you'd actually run it.
6. **The rules of this repository** — signed metadata (`signed-by=`, never
   `trusted=yes`), SHA-256-verified bootstrap, atomic installs, never
   official `com.termux` packages — six lines, plainly (depth link
   `docs/chat-phase3/REPOSITORY_SIGNING.md` as "go deeper" footnote).

- **Try it:** (1) install `ripgrep`, run it on your project folder
  (`rg "int main" .` — wait for the output, read one hit); (2) run
  `pkg status` in Term and name two installed packages; (3) use a quick
  system action button and read what it did.
- **Mistakes:** badge looks stale (refresh the tab / `pkg status`);
  "package not found" — it's not in this curated repo (honest scope, link
  `/packages`); running `pkg` before the bootstrap exists (the app installs
  it automatically — if that fails, see `/faq`).

## 2. Implementation steps

1. Build `ch-05.html` (crumb "Chapter 5 of 17").
2. Package names/actions from W4.2 facts + W3.2's config list; source
   notes in `chat-web4/`.
3. Self-dependent sweep.

## 3. Exit condition

```text
1. Template complete; prev → ch-04, next → ch-06.
2. Every package name and quick-action label == verified list (diff
   noted); nano/rg walk-through commands valid in the userland.
3. 360/1440 clean; sweep PASS.
```
