# CodeC Phase 21.2 — Auto-install Gate (prompt + install before first RUN)

**Status:** ✅ **IMPLEMENTED** (2026-09-03, `arena/01a064e0-codec` — see [`PART_21_IMPLEMENTATION.md`](PART_21_IMPLEMENTATION.md) §3.3/§3.4/§3.6; an `AlertDialog` is used instead of `ModalBottomSheet`, rationale in §2 note 5) · **Cost:** `[client-only]`
· **Depends on:** Phase 21.1 (`LanguageRegistry` and `requiredPackage` field exist)
· **Target files:** `ui/viewmodels/EditorViewModel.kt`,
  `ui/screens/EditorScreen.kt` (install prompt dialog/sheet),
  `ui/services/ModuleInstaller.kt` (reuse `pkg install` flow)

---

## 1. Context & motivation

When a user opens a `.c` file on a device that has never installed `gcc` and
presses RUN ▶, the run should not silently fail. Instead, CodeC shows a
bottom sheet:

```
┌──────────────────────────────────────────┐
│  Install gcc to run C files?             │
│                                          │
│  gcc is a C/C++ compiler (~2 MB).        │
│  Downloads once; works offline after.   │
│                                          │
│  [ Cancel ]           [ Install ]        │
└──────────────────────────────────────────┘
```

Tapping **Install** runs `pkg install -y <package>`, streams the output to the
Output Panel, and on success automatically continues with the run. Tapping
**Cancel** aborts without installing.

This is the exact model Phase 12 used for Python — reuse it, don't reinvent it.

---

## 2. Architectural design

### 2.1 Tool-present check

```kotlin
// In EditorViewModel — Android-free helper
private fun isToolInstalled(packageName: String): Boolean {
    val prefix = ShellEnvironment.prefix  // /data/data/com.codeci.ide/files/usr
    // Check the primary binary for the package (heuristic; good enough)
    val binName = when (packageName) {
        "gcc"    -> "gcc"
        "python" -> "python3"
        "nodejs" -> "node"
        "php"    -> "php"
        "ruby"   -> "ruby"
        "lua54"  -> "lua"
        "golang" -> "go"
        "rust"   -> "rustc"
        else     -> packageName
    }
    return File("$prefix/bin/$binName").exists()
}
```

> This is a file-exists check, not a `pkg` query — fast, synchronous,
> no process needed. The same pattern is used in Phase 12 for Python.

### 2.2 Install prompt state in `EditorViewModel`

```kotlin
data class InstallPromptState(
    val packageName: String,
    val displayName: String,       // "gcc", "Python", "Node.js", …
    val sizeHint: String?,         // "~80 MB" or null
    val pendingRunOnSuccess: Boolean = true,
)

// StateFlow exposed to EditorScreen
val installPrompt: StateFlow<InstallPromptState?> = _installPrompt.asStateFlow()

fun dismissInstall() { _installPrompt.value = null }

fun confirmInstall(context: Context) {
    val prompt = _installPrompt.value ?: return
    _installPrompt.value = null
    viewModelScope.launch {
        runInstall(context, prompt.packageName, continueRun = prompt.pendingRunOnSuccess)
    }
}

private suspend fun runInstall(context: Context, pkg: String, continueRun: Boolean) {
    // Stream "pkg install -y <pkg>" to the Output Panel (same as Phase 12)
    // On success (exit code 0): if continueRun, call runActiveFile(context) again
    // On failure: show error in Output Panel; do not retry automatically
}
```

### 2.3 `EditorScreen` — install prompt bottom sheet

A `ModalBottomSheet` shown when `installPrompt != null`:

```
┌──────────────────────────────────────────┐
│  Install <displayName>?                  │
│                                          │
│  Required to run <language> files.       │
│  <sizeHint line — only if non-null>      │
│  Downloads once; works offline after.   │
│                                          │
│  [ Cancel ]           [ Install ]        │
└──────────────────────────────────────────┘
```

- **Cancel** → `viewModel.dismissInstall()`.
- **Install** → `viewModel.confirmInstall(context)` → Output Panel opens,
  streams `pkg install -y <pkg>` output, run continues on success.

### 2.4 `runActiveFile` gate (from D.1 §2.3, now filled in)

```kotlin
val profile = LanguageRegistry.forFile(filePath) ?: return

if (profile.requiredPackage != null && !isToolInstalled(profile.requiredPackage)) {
    _installPrompt.value = InstallPromptState(
        packageName  = profile.requiredPackage,
        displayName  = profile.displayName,
        sizeHint     = profile.installSizeHint,
    )
    return   // runActiveFile exits; will be re-called after successful install
}
// ... proceed to build + run ...
```

---

## 3. Implementation steps

1. **Add `isToolInstalled(packageName)`** helper to `EditorViewModel` (or a
   companion utility — must be host-testable; inject the prefix as a parameter).
2. **Add `_installPrompt: MutableStateFlow<InstallPromptState?>`** and the
   `confirmInstall` / `dismissInstall` / `runInstall` functions.
3. **Wire the gate in `runActiveFile`** (D.1 §2.3 already shows the call site).
4. **Add the `ModalBottomSheet` in `EditorScreen`** observing `installPrompt`.
5. **Reuse `ModuleInstaller` or `ExecutionRunner`** for the `pkg install -y`
   call — whichever is cleaner; avoid duplicating process launch logic.
6. **Write host unit tests** in `LanguageRegistryTest` and a new
   `EditorInstallGateTest`:
   - `isToolInstalled` returns `true` when the binary exists at the prefix path.
   - `isToolInstalled` returns `false` when it does not.
   - `runActiveFile` sets `installPrompt` when the tool is missing.
   - `runActiveFile` proceeds (no prompt) when the tool is present.

---

## 4. Exit condition & device recipe

```sh
# On a fresh device (or after `pkg remove gcc`):
# 1. Open a .c file in the editor.
# 2. Tap RUN ▶.
#    EXPECT: bottom sheet appears — "Install gcc to run C files? (~2 MB)"
# 3. Tap Install.
#    EXPECT: Output Panel shows pkg install progress; no crash.
# 4. Install completes (exit 0).
#    EXPECT: build runs automatically; program output appears in Output Panel.
# 5. Tap RUN ▶ again (gcc is now installed).
#    EXPECT: no install prompt; compiles and runs directly.
# 6. Tap Cancel on a fresh file type (e.g., .rb with ruby not installed).
#    EXPECT: prompt dismisses; no run; no crash.
# PASS = all six steps behave as described.
```

---

## 5. Non-goals & invariants

- **Not in D.2:** TCC removal (→ D.4); formatter trigger (→ E.2).
- The install prompt is shown at most once per RUN tap — if `confirmInstall`
  itself fails (pkg error), the Output Panel shows the error and the user must
  tap RUN again manually. No retry loop in the prompt.
- Heavy packages (`golang`, `rust`) show the `sizeHint` ("~80 MB / ~200 MB")
  in the sheet. Lighter packages show no size hint (the line is omitted when
  `sizeHint == null`).

---

## 6. Design decisions

- **D1 — file-exists check, not `pkg query`:** avoids spawning a process per RUN
  tap. The binary check is a good enough heuristic (a partial install that left
  the binary would also allow RUN — acceptable; `pkg repair` handles broken installs).
- **D2 — auto-continue after install:** tapping Install should not require a
  second RUN tap. The run resumes automatically on success — same UX as VS Code's
  "Install extension then retry" flow. On failure, the user must tap RUN again
  (they can see the error in the Output Panel).
- **D3 — reuse Phase 12's pkg install flow:** do not add a new process-launch
  path. The same `ExecutionRunner` / `InteractiveRunSession` that Phase 12 uses
  for `python3 file.py` can run `pkg install -y gcc`. Consistency reduces surface area.

---

## 7. Research notes (fill in before implementing)

> **TODO for the implementer:**
> - Find the exact Phase 12 code path that runs `pkg install -y python` and
>   streams output to the Output Panel. Re-use it verbatim (or via a shared
>   helper) for D.2's install flow.
> - Confirm `ShellEnvironment.prefix` (or equivalent) is accessible from
>   `EditorViewModel` without an `Activity` reference.
> - Check if `ModuleInstaller.install` already does `pkg install -y` (it might —
>   Phase 10 added the Package Hub install flow; this may be a one-liner reuse).
> - Verify the `ModalBottomSheet` API (Compose M3) — in the resolved BOM version
>   check whether it needs a `SheetState` parameter and whether `dismissOnClickOutside`
>   is available.
