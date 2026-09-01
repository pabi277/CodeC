# CodeC Phase D.3 — Device Acceptance: gcc compiles C/C++ end-to-end

**Status:** 📋 **PLANNED** — not yet started · **Cost:** `[client-only]` (device-gated)
· **Depends on:** Phase D.1 (registry wired), Phase D.2 (auto-install gate), Phase C.1 (gcc in repo)
· **Target files:** none new — this is the device acceptance pass for D.1 + D.2

---

## 1. Purpose

Phase D.3 is the **device acceptance gate** for the entire compiler redesign.
D.4 (TCC removal — irreversible) must not run until D.3 passes. The owner
runs the recipe below on their aarch64 device, confirms each step, and signals
acceptance. Only then does D.4 begin.

---

## 2. Pre-conditions

Before the owner runs this recipe:
1. Phase C.1 CI must be green and the package repo must be published (i.e.
   `pkg install gcc` resolves to a real package).
2. Phase D.1 + D.2 `Build APK` CI must be green.
3. The APK from the latest green CI run must be installed on the device.
4. The owner's device must have the CodeC userland installed (bootstrap present).

---

## 3. Device recipe

```sh
# ── STEP 1: verify gcc is not yet installed (clean state) ──────────────
which gcc || echo "gcc not found — good"

# ── STEP 2: open a .c file and tap RUN ▶ ──────────────────────────────
# In the editor, create a new file "hello.c" with content:
#
#   #include <stdio.h>
#   int main() {
#       printf("Hello from gcc!\n");
#       return 0;
#   }
#
# Tap RUN ▶.
# EXPECT: "Install gcc to run C files?" bottom sheet appears.

# ── STEP 3: tap Install ───────────────────────────────────────────────
# EXPECT: Output Panel shows pkg install progress for gcc (and clang as dep).
# EXPECT: After install completes (exit 0), build starts automatically.
# EXPECT: Output Panel shows:
#   Compiling…
#   Build OK (Xms)
#   Hello from gcc!
#   Exit 0

# ── STEP 4: verify gcc version ───────────────────────────────────────
# Open Terminal tab.
gcc --version
# EXPECT: output contains "clang version" (this is expected — gcc is the Clang wrapper)

# ── STEP 5: C++ compilation ──────────────────────────────────────────
# In the editor, create "hello.cpp":
#
#   #include <iostream>
#   int main() {
#       std::cout << "Hello from g++!" << std::endl;
#       return 0;
#   }
#
# Tap RUN ▶.
# EXPECT: no install prompt (gcc already installed); compiles and runs directly.
# EXPECT Output Panel:
#   Compiling…
#   Build OK (Xms)
#   Hello from g++!
#   Exit 0

# ── STEP 6: interactive C program (scanf) ────────────────────────────
# In the editor, create "greet.c":
#
#   #include <stdio.h>
#   int main() {
#       char name[64];
#       printf("Enter your name: ");
#       scanf("%s", name);
#       printf("Hello, %s!\n", name);
#       return 0;
#   }
#
# Tap RUN ▶.
# EXPECT: "Enter your name: " appears in Output Panel.
# Type a name and press Enter.
# EXPECT: "Hello, <name>!" appears.

# ── STEP 7: Python still works (regression) ──────────────────────────
# Open a .py file and tap RUN ▶.
# EXPECT: Python runs without a prompt (already installed from Phase 12).

# ── STEP 8: HTML still works (regression) ────────────────────────────
# Open an .html file and tap RUN ▶.
# EXPECT: Web Preview opens (no install prompt).

# ── STEP 9: legacy TCC fallback (optional) ───────────────────────────
# Settings → Compiler → enable "Use legacy TCC (fallback)".
# Open hello.c → RUN ▶.
# EXPECT: compiles with TCC (no install prompt; static musl binary).
# Disable the toggle again.

# PASS = steps 1–8 all produce the expected output.
# Step 9 is optional but confirms the fallback before D.4 removes TCC.
```

---

## 4. Exit condition

**D.3 is DONE when:** the owner reports "PASS" or equivalent confirmation
(a transcript of the output, or explicit "All working") for steps 1–8. Only
then does the agent begin Phase D.4.

**If any step fails:** report the exact failure output; the agent diagnoses
and fixes before re-running D.3. Do not skip to D.4 on a partial pass.

---

## 5. Non-goals & invariants

- **D.3 adds no code** — it is a verification pass only.
- If the owner cannot run C.1 (the package repo build was never triggered),
  D.3 must wait. The agent will note "D.3 is blocked on C.1 package publish"
  in the report.
- **Do NOT run D.4 until D.3 is explicitly accepted by the owner.**
