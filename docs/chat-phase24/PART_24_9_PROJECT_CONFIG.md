# CodeC Phase 24.9 — Per-project `.codec.json` Run-Config Override

**Status:** 📋 **PLANNED** · **Cost:** `[client-only]` · **Effort:** S
· **Depends on:** Phase 21.1 (`LanguageRunProfile` registry exists)
· **Target files:** `ui/projects/ProjectConfig.kt`, `ui/viewmodels/EditorViewModel.kt`

---

## 1. Design

`ProjectConfig` (`.codec/project.json`) already stores `type`, `build`, `run`.
Phase 21's `LanguageRunProfile` registry provides defaults based on file extension.

The owner may want a **per-project override** — e.g. a C project that needs
`gcc main.c utils.c -o app -lm` instead of the single-file `gcc $SRC -o $OUT`.
This part allows the project root to contain a `.codec.json` file that overrides
the registry's `buildTemplate` and `runTemplate` for that specific project.

### `.codec.json` schema

```json
{
    "build": "gcc main.c utils.c -o app -lm",
    "run": "./app",
    "formatter": "clang-format -i main.c utils.c"
}
```

Fields map directly to `LanguageRunProfile` fields. Missing fields fall back
to the registry defaults for the file's extension.

### Resolution order (highest priority first)

1. `.codec.json` in the project root (if exists and field is non-null)
2. `ProjectConfig` (`project.json`) `build` / `run` fields (existing Phase 14 behavior)
3. `LanguageRunProfile` registry default for the file extension
4. No run (unsupported file type)

### Implementation

```kotlin
// In EditorViewModel.runActiveFile, after loading the profile:
val codecJson = File(projectRoot, ".codec.json")
val override  = if (codecJson.exists()) CodecJsonParser.parse(codecJson) else null

val buildCmd = override?.build
    ?: info?.config?.build?.takeIf { it.isNotBlank() }
    ?: profile.buildTemplate?.let { LanguageRegistry.expandTemplate(it, src, out) }

val runCmd   = override?.run
    ?: info?.config?.run?.takeIf { it.isNotBlank() }
    ?: LanguageRegistry.expandTemplate(profile.runTemplate, src, out)
```

`CodecJsonParser` is a pure Kotlin/JSON parser (use the existing JSON library
already in the project — check `build.gradle.kts` for `org.json` or `kotlinx.serialization`).

---

## 2. Implementation steps

1. Add `CodecJsonParser.parse(file: File): CodecOverride?` (pure Kotlin, host-testable).
2. Apply the resolution order in `EditorViewModel.runActiveFile`.
3. Add **"Edit run config"** to the project `⋮` overflow — opens a simple
   dialog where the user can type `build` and `run` commands; saves to `.codec.json`.
4. Add `.codec.json` to `BuildArtifactIgnore` (should NOT be gitignored — it's
   project config; but confirm the owner's preference).
5. Write host unit tests:
   - `CodecJsonParser.parse` correctly extracts all three fields.
   - Missing fields return `null` (fall through to registry).
   - Malformed JSON returns `null` (no crash).
   - Resolution order test: override beats registry; registry beats nothing.

---

## 3. Exit condition

```text
1. Create a C project with two source files (main.c, utils.c).
2. Tap ⋮ → "Edit run config".
   EXPECT: dialog opens with build/run command fields.
3. Enter: build = "gcc main.c utils.c -o app", run = "./app". Tap Save.
   EXPECT: .codec.json is created in the project root.
4. Tap RUN ▶.
   EXPECT: both files are compiled together; app runs.
5. Delete .codec.json.
   EXPECT: RUN ▶ falls back to the single-file registry default.
PASS = steps 1–5 behave as described.
```
