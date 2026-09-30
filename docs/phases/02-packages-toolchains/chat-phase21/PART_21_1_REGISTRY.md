# CodeC Phase 21.1 — `LanguageRunProfile` Registry + Wire into EditorViewModel

**Status:** ✅ **IMPLEMENTED** (2026-09-03, `arena/01a064e0-codec` — see [`PART_21_IMPLEMENTATION.md`](PART_21_IMPLEMENTATION.md) §3.1–3.5; §2.4 `useLegacyTcc` intentionally not added, rationale in §3.7) · **Cost:** `[client-only]`
· **Depends on:** Phase 20.1 (gcc in the repo — needed for device acceptance in D.3,
  not for D.1 to compile)
· **Target files:** `ui/services/LanguageRegistry.kt` (new),
  `ui/viewmodels/EditorViewModel.kt`, `ui/services/ExecutionRunner.kt` (minor),
  `ui/utils/FileNameUtils.kt` (extension lookup)

---

## 1. Context & motivation

Today `EditorViewModel.runActiveFile` dispatches through a hard-coded `when`
block: TCC for `.c`, Python for `.py`, HTML for web preview, etc. Every new
language requires a new branch in that block — not future-proof. The owner's
directive: "extend it's scope with other languages as per need — make the plan
future proof."

The fix is a **`LanguageRunProfile` data class** + a **`LanguageRegistry`
singleton** that maps file extensions to profiles. `runActiveFile` becomes
a single generic dispatch: look up the profile, check the tool is installed,
build (if a build template exists), then run. Adding a new language = adding
one entry to the registry list. No new branches.

---

## 2. Architectural design

### 2.1 `LanguageRunProfile` (pure data class — host-testable)

```kotlin
/**
 * Describes how a single language is compiled + executed.
 * Android-free; host-unit-testable.
 * Token substitution: $SRC = abs source path, $OUT = abs output binary path.
 */
data class LanguageRunProfile(
    val displayName: String,
    val extensions: List<String>,
    /** Package to auto-install if the tool is missing (null = always present). */
    val requiredPackage: String?,
    /** Human-readable size hint shown in the install prompt ("~80 MB"). */
    val installSizeHint: String? = null,
    /** Build command template, null for interpreted languages. */
    val buildTemplate: String?,
    /** Run command template. Tokens: $SRC, $OUT. */
    val runTemplate: String,
    /** True when the program is likely interactive (PTY preferred). */
    val interactive: Boolean = false,
    /** Formatter command template, null if none. Token: $SRC. */
    val formatterTemplate: String? = null,
)
```

### 2.2 `LanguageRegistry` (singleton — host-testable)

```kotlin
object LanguageRegistry {
    val profiles: List<LanguageRunProfile> = listOf(
        LanguageRunProfile(
            displayName = "C",
            extensions  = listOf("c"),
            requiredPackage = "gcc",
            buildTemplate   = "gcc \$SRC -o \$OUT -lm",
            runTemplate     = "./\$OUT",
            interactive     = true,
            formatterTemplate = "clang-format -i \$SRC",
        ),
        LanguageRunProfile(
            displayName = "C++",
            extensions  = listOf("cpp", "cc", "cxx"),
            requiredPackage = "gcc",
            buildTemplate   = "g++ \$SRC -o \$OUT -lm",
            runTemplate     = "./\$OUT",
            interactive     = true,
            formatterTemplate = "clang-format -i \$SRC",
        ),
        LanguageRunProfile(
            displayName = "Python",
            extensions  = listOf("py"),
            requiredPackage = "python",
            buildTemplate   = null,
            runTemplate     = "python3 \$SRC",
            interactive     = true,
            formatterTemplate = "black \$SRC",
        ),
        LanguageRunProfile(
            displayName = "JavaScript",
            extensions  = listOf("js", "mjs"),
            requiredPackage = "nodejs",
            buildTemplate   = null,
            runTemplate     = "node \$SRC",
        ),
        LanguageRunProfile(
            displayName = "TypeScript",
            extensions  = listOf("ts"),
            requiredPackage = "nodejs",
            buildTemplate   = null,
            runTemplate     = "npx ts-node \$SRC",
        ),
        LanguageRunProfile(
            displayName = "Go",
            extensions  = listOf("go"),
            requiredPackage = "golang",
            installSizeHint = "~80 MB",
            buildTemplate   = null,
            runTemplate     = "go run \$SRC",
            formatterTemplate = "gofmt -w \$SRC",
        ),
        LanguageRunProfile(
            displayName = "Rust",
            extensions  = listOf("rs"),
            requiredPackage = "rust",
            installSizeHint = "~200 MB",
            buildTemplate   = "rustc \$SRC -o \$OUT",
            runTemplate     = "./\$OUT",
            formatterTemplate = "rustfmt \$SRC",
        ),
        LanguageRunProfile(
            displayName = "PHP",
            extensions  = listOf("php"),
            requiredPackage = "php",
            buildTemplate   = null,
            runTemplate     = "php \$SRC",
        ),
        LanguageRunProfile(
            displayName = "Ruby",
            extensions  = listOf("rb"),
            requiredPackage = "ruby",
            buildTemplate   = null,
            runTemplate     = "ruby \$SRC",
        ),
        LanguageRunProfile(
            displayName = "Lua",
            extensions  = listOf("lua"),
            requiredPackage = "lua54",
            buildTemplate   = null,
            runTemplate     = "lua \$SRC",
        ),
        LanguageRunProfile(
            displayName = "Shell",
            extensions  = listOf("sh", "bash"),
            requiredPackage = null,
            buildTemplate   = null,
            runTemplate     = "bash \$SRC",
            interactive     = true,
        ),
        LanguageRunProfile(
            displayName = "HTML",
            extensions  = listOf("html", "htm"),
            requiredPackage = null,
            buildTemplate   = null,
            runTemplate     = "__WEB_PREVIEW__",   // intercepted by EditorViewModel
        ),
    )

    fun forExtension(ext: String): LanguageRunProfile? =
        profiles.firstOrNull { ext.lowercase() in it.extensions }

    fun forFile(path: String): LanguageRunProfile? =
        forExtension(path.substringAfterLast('.', ""))

    /** Expand $SRC and $OUT tokens in a template string. */
    fun expandTemplate(template: String, src: String, out: String): String =
        template.replace("\$SRC", src).replace("\$OUT", out)
}
```

### 2.3 `EditorViewModel.runActiveFile` — after the registry

The new dispatch replaces the language `when` block with:

```kotlin
fun runActiveFile(context: Context) {
    // ... save, check busy, build-artifact-ignore (unchanged) ...
    val filePath = activeTabPath ?: return
    val ext = filePath.substringAfterLast('.', "")

    // Web and server projects bypass the registry (unchanged)
    if (info?.config?.type.equals("web", ignoreCase = true)) return
    if (info?.config?.isServerType() == true) { startServerRun(…); return }

    val profile = LanguageRegistry.forFile(filePath)
    if (profile == null) {
        _userMessage.value = "No run profile for this file type"
        return
    }

    // HTML: open Web Preview (intercepted sentinel)
    if (profile.runTemplate == "__WEB_PREVIEW__") { openWebPreview(…); return }

    // Auto-install gate (implemented in D.2)
    if (profile.requiredPackage != null && !isToolInstalled(profile.requiredPackage)) {
        promptInstall(profile); return
    }

    val srcFile   = File(filePath)
    val outFile   = File(srcFile.parent, srcFile.nameWithoutExtension)
    val buildCmd  = profile.buildTemplate?.let {
        LanguageRegistry.expandTemplate(it, filePath, outFile.absolutePath)
    }
    val runCmd    = LanguageRegistry.expandTemplate(profile.runTemplate, filePath, outFile.absolutePath)

    launchRun(
        workDir      = srcFile.parentFile ?: context.filesDir,
        buildCommand = buildCmd,
        runCommand   = runCmd,
        interactive  = profile.interactive,
    )
}
```

### 2.4 Feature-flag fallback for TCC (`useLegacyTcc`)

During D.1 and D.2, a `SettingsManager.useLegacyTcc: Boolean` flag
(default `false`) lets the owner switch back to TCC if the gcc path has a
problem. The TCC path in `runActiveFile` is gated behind this flag and removed
in D.4 once D.3 device acceptance is confirmed.

---

## 3. Implementation steps

1. **Create `ui/services/LanguageRegistry.kt`** with the data class and
   singleton above (pure Kotlin, no Android imports).
2. **Rewrite `EditorViewModel.runActiveFile`** to dispatch through the registry
   per §2.3. Gate the old TCC path behind `useLegacyTcc` (default `false`).
3. **Add `SettingsManager.useLegacyTcc`** (a `DataStore` boolean, default `false`).
   Wire a "Use legacy TCC (fallback)" toggle in Settings → Compiler (visible only
   while TCC assets are still present — removed in D.4).
4. **Write `LanguageRegistryTest`** (host JUnit4):
   - `forExtension` returns the correct profile for every registered extension.
   - `forFile` works end-to-end with a full path.
   - `expandTemplate` substitutes `$SRC` and `$OUT` correctly.
   - `forExtension` returns `null` for an unknown extension (no crash).
   - `profiles` has no two entries claiming the same extension.
   - `displayName` is non-blank for every entry.
5. **Commit and push**; watch CI (`Build APK`).

---

## 4. Exit condition & verification

**CI gate (no device needed for D.1):**
- `Build APK` green: assemble + `LanguageRegistryTest` (all tests pass) + lint.
- No TCC-path code paths are active by default (`useLegacyTcc = false`);
  the old TCC code compiles but is unreachable at runtime.

**Smoke test on device (lightweight — full acceptance is D.3):**
- Open a `.py` file → RUN ▶ → Python runs (same as before; registry now handles it).
- Open an `.html` file → RUN ▶ → Web Preview opens (same as before; `__WEB_PREVIEW__` sentinel).
- Open a `.c` file on a device WITH `gcc` installed → RUN ▶ → builds and runs via `gcc`.

---

## 5. Non-goals & invariants

- **Not in D.1:** the auto-install prompt (→ D.2); TCC removal (→ D.4).
- All existing run paths (Python, HTML/web preview, server presets, project-config
  override) keep working unchanged through the registry.
- `LanguageRegistry` is pure Kotlin — zero Android imports. If an Android import
  creeps in, it is a bug.

---

## 6. Design decisions

- **D1 — `__WEB_PREVIEW__` sentinel token:** avoids a sealed class hierarchy for
  now; the sentinel is intercepted before `ExecutionRunner` ever sees it. If more
  special-case actions are needed later (REPL, server-start), a sealed
  `RunAction` type can replace the sentinel — tracked in §4.15 of
  `RESEARCH_NEXT_PHASES.md`.
- **D2 — registry is a `val` list, not a `Map`:** linear scan over ≤15 profiles
  is negligible; a list preserves declaration order (useful for the Settings
  "Supported languages" display) and makes the code flat and readable.
- **D3 — `useLegacyTcc` defaults to `false`:** the gcc path is the new default;
  TCC is opt-in as a fallback. This matches the owner's intent ("remove tcc").
- **D4 — `installSizeHint` in the profile, not in the auto-install logic:**
  keeps the size warning co-located with the profile that needs it; D.2 reads it
  from the profile without needing a separate lookup.

---

## 7. Host unit tests plan

File: `app/src/test/java/com/codeci/ide/LanguageRegistryTest.kt`

| Test name | What it checks |
|---|---|
| `forExtension_c_returns_C_profile` | `forExtension("c")?.displayName == "C"` |
| `forExtension_cpp_cc_cxx_all_resolve` | all three C++ extensions → C++ profile |
| `forExtension_py_returns_python_profile` | |
| `forExtension_html_returns_web_preview_sentinel` | runTemplate == `__WEB_PREVIEW__` |
| `forExtension_unknown_returns_null` | `forExtension("xyz") == null` |
| `forFile_full_path_resolves` | `forFile("/home/user/main.c")?.displayName == "C"` |
| `expandTemplate_substitutes_src_and_out` | `$SRC` and `$OUT` replaced correctly |
| `expandTemplate_no_double_substitution` | `$SRC` not expanded into an `$OUT` that contains `$SRC` |
| `no_duplicate_extensions_in_registry` | no two profiles claim the same extension |
| `all_profiles_have_non_blank_display_name` | |
| `all_build_profiles_have_non_blank_build_template` | compiled languages have a build template |
| `all_interpreted_profiles_have_null_build_template` | Python/Node/etc. have `null` buildTemplate |

---

## 8. Research notes (fill in before implementing)

> **TODO for the implementer:**
> - Confirm the exact `runActiveFile` call sites that need updating — there may
>   be project-config overrides (`ProjectConfig.build` / `ProjectConfig.run`
>   strings) that should take priority over the registry (project-config always
>   wins; registry is the fallback for `auto` projects and single files).
> - Check if `TermuxCompiler` (the "Termux-Clang bridge") needs a registry entry
>   or is superseded by the `gcc` profile on devices with userland installed.
> - Confirm `FileNameUtils.languageForExtension` exists and whether it should
>   be unified with `LanguageRegistry.forExtension` (probably yes — one source
>   of truth for extension → language).
