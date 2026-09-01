# CodeC Phase D.4 — Remove TCC Entirely

**Status:** 📋 **PLANNED** — not yet started · **Cost:** `[client-only]`
· **Depends on:** Phase D.3 device acceptance (CONFIRMED by owner) — **irreversible, do not start early**
· **Target files:** `app/src/main/assets/tcc/` (delete), `app/src/main/jniLibs/` (remove libtcc.so),
  `app/src/main/java/com/codeci/ide/ui/services/EmbeddedCompiler.kt` (delete),
  `app/build.gradle.kts` (remove TCC abiFilters/jniLibs if present),
  `ui/viewmodels/EditorViewModel.kt` (remove `useLegacyTcc` branch),
  `ui/settings/SettingsManager.kt` (remove `useLegacyTcc`),
  `scripts/build-tcc.sh` (delete)

---

## 1. Context & motivation

After D.3 confirms gcc compiles C/C++ correctly on device, TCC is no longer
needed. Removing it:
- Shrinks the APK by ~3–5 MB (TCC binary + musl headers + libc.a + crt objects).
- Removes dead code (`EmbeddedCompiler`, `TermuxCompiler` TCC path, `useLegacyTcc` flag).
- Deletes `scripts/build-tcc.sh` (CI no longer needs to build TCC).
- Closes the last reference to the musl static toolchain.

**This step is irreversible.** The TCC assets are binary blobs; once removed from
git history (or even just the working tree), restoring them requires re-running the
TCC build. Do not start D.4 without D.3 acceptance.

---

## 2. Deletion checklist

### 2.1 Assets

```sh
rm -rf app/src/main/assets/tcc/
# Removes: arm64-v8a/ and x86_64/ subdirs containing:
#   libtcc1.a, libc.a, crt1.o, crti.o, crtn.o,
#   include-tcc/*, codec_stdio.c, COPYING.tcc
```

### 2.2 Native library

```sh
# Remove libtcc.so from jniLibs if present:
find app/src/main/jniLibs -name "libtcc.so" -delete
# If jniLibs/ becomes empty, remove it too.
```

### 2.3 Kotlin source

- **Delete** `app/src/main/java/com/codeci/ide/ui/services/EmbeddedCompiler.kt`
- **Remove** all `EmbeddedCompiler.*` references from `EditorViewModel.kt`,
  `CompilerService.kt`, and any other caller (grep first).
- **Remove** the `useLegacyTcc` branch from `EditorViewModel.runActiveFile`.
- **Remove** `SettingsManager.useLegacyTcc` and the Settings UI toggle
  ("Use legacy TCC (fallback)") added in D.1.

### 2.4 Build scripts

```sh
rm scripts/build-tcc.sh
# Also remove any CI step / workflow reference to build-tcc.sh if present.
```

### 2.5 `app/build.gradle.kts`

- Remove any `abiFilters` or `jniLibs.srcDirs` entries that were added
  specifically for TCC (verify first — these may be shared with `libcodec-pty.so`).
- Remove any `assets { srcDirs += "src/main/assets/tcc" }` override if present.

---

## 3. Implementation steps

1. **Grep for all TCC references** before deleting anything:
   ```sh
   grep -r "EmbeddedCompiler\|libtcc\|build-tcc\|useLegacyTcc\|BUNDLE_VERSION\|TCC_LIB_NAME" \
     app/src/ scripts/ --include="*.kt" --include="*.sh" --include="*.kts" -l
   ```
   Record every file. Delete / edit in order.

2. **Delete the assets and native lib** (§2.1, §2.2).

3. **Edit Kotlin files** (§2.3) — remove all callers before deleting the class
   to avoid dangling references that would fail CI.

4. **Delete `EmbeddedCompiler.kt`** after all callers are removed.

5. **Delete `build-tcc.sh`** (§2.4); remove CI references.

6. **Edit `build.gradle.kts`** (§2.5) — verify the PTY library entry is untouched.

7. **Run CI** — `Build APK` must be green (assemble + tests + lint). The tests
   for TCC (if any exist in `EmbeddedCompilerTest` or similar) must be deleted
   in the same commit.

---

## 4. Exit condition

**D.4 is DONE when:**
- `Build APK` CI is green after the deletion commit.
- `git diff --stat HEAD~1` confirms the expected files are gone.
- APK size in the CI artifact is measurably smaller than before D.4.
  (Record: before ___ MB, after ___ MB — note in the commit message.)
- No `EmbeddedCompiler`, `libtcc`, or `useLegacyTcc` symbol remains in the
  codebase (`grep` returns empty).

---

## 5. Non-goals & invariants

- **Phase D.4 does NOT remove `TermuxCompiler.kt`** (the Termux-Clang bridge)
  unless the owner explicitly requests it — it is a separate optional engine
  that some users with Termux installed may rely on.
- The PTY library (`libcodec-pty.so`) is **not touched** — it is unrelated to TCC.
- All Phase 6/7 terminal invariants (no `.` on `PATH`, PTY/JNI contract,
  multi-session routing) are unaffected.
- The TCC link-order invariant (`-o` last) **no longer applies** after D.4 —
  it is removed from `rule.md` §6 and `prompt.md` STANDING RULES in the same
  commit.

---

## 6. Design decisions

- **D1 — delete from the working tree only, not from git history:** git history
  is not rewritten (no `filter-branch` or `git rm --cached` history purge).
  The blobs remain in git history (they are already there). This is intentional
  — the project has no LFS migration and the repo is already shallow; a history
  rewrite would break the Arena branch lineage.
- **D2 — remove `TermuxCompiler` TCC path, keep the Termux-Clang bridge method:**
  `TermuxCompiler` has two roles — calling TCC via Termux (removed) and calling
  Clang via Termux (kept as an optional engine). Separate the two in D.4 rather
  than deleting the whole file.
- **D3 — commit message records APK size delta:** this gives the owner a concrete
  "benefit realised" confirmation (e.g. "APK shrinks from 24 MB → 21 MB").

---

## 7. Post-D.4 updates required

After D.4 is merged:
- **`rule.md` §6 invariants:** remove "TCC link order with `-o` last" (no longer
  applicable). Record that D.4 is complete.
- **`prompt.md` STANDING RULES:** remove the TCC link-order bullet.
- **`docs/JOURNEY.md`:** append item "Phase D — TCC retired, LanguageRunProfile
  registry, gcc/g++ via userland, Phase C packages."
- **`docs/NEXT_STEPS.md`:** update head state line.
