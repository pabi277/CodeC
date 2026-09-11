# CodeC Phase 42.2 — Weight: what a tester actually has to download

> **Status:** 🔧 CODE-COMPLETE 2026-09-11 · awaiting CI measure + owner decisions · **Cost:** `[client-only]` + build config · **Effort:** M

### Implementation record (2026-09-11)

Landed (the spec's non-decision set; '🟡' marks what waits on the owner):

- **R8 release-only** — `isMinifyEnabled = true`, `isShrinkResources = true`,
  `isCrunchPngs = true` on the release buildType; **debug is untouched**
  (the CI debug artifact developers install stays a fast, unminified build).
  `proguard-rules.pro` is rewritten from the AGP template into the law-
  formatted file: one section per library, every keep with a comment naming
  the prevented failure, first attempt ships **zero** library keeps
  (Compose is R8-aware), and `-keepattributes SourceFile,LineNumberTable`
  is pinned non-negotiable (the spec's rule; pinned by test).
- **Per-ABI splits, universal kept** — `splits { abi }` enable + the same
  four ABIs as `ndk.abiFilters`, `isUniversalApk = true`.
- **Update-channel APK names** — release artifacts are now BORN named
  `CodeC-IDE-<version>-<abi>.apk` / `…-universal.apk` via
  `applicationVariants` output naming (filter `ABI`), which is the grammar
  `UpdatePolicy.pickAsset` already parses; publishing AGP's raw
  `app-arm64-v8a-release.apk` names would have resurrected the 42.1
  "first .apk asset" bug class. Debug: `CodeC-IDE-<version>-<abi>-debug.apk`.
- **okhttp + logging-interceptor REMOVED** (`app/build.gradle.kts:214-215`
  pre-change) — grep proof: zero `okhttp3`/`okio` imports in `app/src`
  (HTTP goes through `java.net.HttpURLConnection` — UpdateManager,
  UserlandInstaller), none in `:bench` either; the two lines were dead
  weight. Named here so a reviewer can object, per the spec's rule.
- **Workflow reconciled for the split set** — debug artifact now uploads
  from `outputs/apk/universal/debug/`, the release artifact from
  `outputs/apk/*/release/`; the notes build, the idempotent-attach cleaner
  (`clear_release_assets.py`, now recursive into `*/release/`), and both
  publish globs follow the same layout. New CI step
  `scripts/check_release_apk_set.sh` runs after `assembleRelease` (exit 1's
  machine): exactly one universal named per the update grammar, one APK per
  filtered ABI, native libs restricted to the split's own ABI, and the
  **assets/tcc-by-ABI trap annotated** (splits do NOT filter `assets/` —
  every split currently carries the full ~10.9 MB tcc set).
- **Tests (exit 2 set, host)** — `AbiPolicyTest` (splits.include ==
  abiFilters; universal lane present; **`armeabi-v7a` never dropped**),
  `BundledEngineAbiCoverageTest` (each filtered ABI either ships
  jniLibs+assets tcc or is in the declared no-bundled set ==
  `EmbeddedCompiler.ABI_DIRS`), `ProguardKeepsDocumentedTest` (no
  undocumented keeps; line-table keep present; release-only minify pinned).
  Local harness: 3 suites, **9/9 green**; CI `testDebugUnitTest` is the
  gate.
- **BETA.md gains the 32-bit ARM sentence** (exit 5): "the built-in C
  compiler is not available; install the C toolchain module or use
  Termux" (row B-4), so `armeabi-v7a` ships and the known limitation is a
  sentence, not a bug report.

🟡 **CI measure pending** — the byte delta (exit 3's recorded numbers) and
the ≥ 15 %-smaller-per-ABI verdict (exit 4) read off the "Report APK sizes"
annotations of the split-aware run *on this commit*; the record below fills
in when that run lands on green. Release numbers stay provisional until the
owner enters the 3 signing secrets (the release lane only assembles with
them); debug splits measure the same split machinery meanwhile.

🟡 **Owner decision card** (numbers, not a pick — spec law):

1. **assets/tcc trap.** Today every per-ABI APK duplicates the full tcc
   assets set (arm64 7.3 MB + x86_64 3.6 MB ≈ **10.9 MB** of the 24.8 MB
   universal base). Without a mechanism, per-ABI splits save only the
   jniLibs delta (~0.4–0.6 MB) and are likely to trip the exit-4 revert
   rule. Options: **(a)** strip the foreign `assets/tcc/<abi>` dir inside
   each split's package task (AGP has no split-aware assets API;
   `packaging.resources.excludes` is variant-wide, so this is a small task
   hook + `BundledEngineAbiCoverageTest` update); **(b)** ABI product
   flavors with per-flavor `assets.srcDirs` (louder Gradle change, same
   user effect, keeps `EmbeddedCompiler.abiDir()` agreement mechanical);
   **(c)** tcc-as-downloadable-module — delete `assets/tcc` from the APK,
   first C compile downloads it like clang does: **strongly against the
   offline-first promise** of **§deferred** (the bundled TCC is there
   because downloaded compilers fail on some devices) — listed because the
   spec says it needs YOUR explicit sign-off, not mine.
   **(d) keep both tcc dirs in every split** and let the 15 % rule decide
   whether splits survive at all (the revert would keep universal-only,
   which costs the slow-link tester the per-ABI lane entirely).
2. **Drop `x86` from `abiFilters`** — the emulator lane already has
   `x86_64`; `x86` (32-bit Intel) devices are effectively gone. Dropping it
   removes one split + one PTY build. `armeabi-v7a` is NOT droppable
   (32-bit ARM devices are real in the beta pool — exit 5).
3. **material-icons-extended** — the OkHttp pair died on grep proof; the
   icon set's verdict waits on the R8-measured release build (R8 tree-
   shakes unreferenced vector classes; measure before enumerating the
   `Icons.*` uses by hand). Same rule: removal commit must name it and
   show green CI.

### Pre-implementation analysis (spec), kept for the record

**24 847 906 B ≈ 23.7 MiB** for the debug universal APK (run `34399227052`);
the release build today would be about the same, because
`isMinifyEnabled = false` and `isShrinkResources` is unset.
**The noise floor, measured:** the docs-only run `34433912076` (tip `8d365c4`)
produced `CodeC-IDE` at 24 847 809 B — **97 B below** the build of its parent
tip `04f336f` (24 847 906 B) with **no `app/` file changed** and the same
3-digit CI run number in `versionName`. So zip assembly itself moves the size by
~10² bytes. Any 42.2 result smaller than ~0.1 % (≈24 KB) is not a finding, and a
before/after pair measured on *different* runs must be re-measured twice before
it is reported to the owner. What's inside it,
in order of weight, from the repo's own facts rather than guesses:

Measured in the working tree on 2026-09-10 (`du -sh`), not estimated:

| What | Size in the tree | Can it be smaller on the device? |
|---|---|---|
| `assets/tcc/arm64-v8a` + `assets/tcc/x86_64` — the bundled TCC runtime, extracted on first use by `EmbeddedCompiler` | **7.3 MB + 3.6 MB** (two ABI dirs) | **the biggest win here, and splits alone do NOT fix it** — see §assets |
| `jniLibs/*/libtcc.so` (the exec'able binary) | 581 600 B (arm64) + 405 352 B (x86_64) | yes — AGP's `splits.abi` filters `jniLibs` per ABI correctly |
| `libcodec-pty.so` — the only CMake target (`src/main/cpp/pty.c`), built for **all four** `abiFilters` | small, ×4 | yes, per split |
| `assets/textmate/` (grammars 2.3 MB + themes 52 KB), `assets/snippets` 332 KB, `assets/licenses` 76 KB | ≈ 2.7 MB | no — offline syntax highlighting and snippets are the product; themes could drop unused grammars only with a *measured* editor regression risk |
| Kotlin stdlib + **Compose BOM (runtime/foundation/material3)** + lifecycle + navigation + DataStore + coroutines, **unminified** | the bulk of the remaining ~15 MB of DEX | yes — R8 shrinks app+lib code ~30-50 % in comparable apps |
| `sora-editor` + `language-textmate` (already trimmed: `org.yaml` + `org.eclipse.jdt` **excluded** in `build.gradle.kts` with a comment naming the reason) | — | the precedent to follow: **exclusions with proof beat shrinking**; further `exclude()` candidates are checked the same way |
| `implementation(libs.okhttp)` + `implementation(libs.logging.interceptor)` (`build.gradle.kts:201-202`) | OkHttp 4.10 + okio, ~400 KB of DEX | **`grep -rn \"okhttp3\" app/src` finds no import in app code** — if `:bench` and the generated/bridge paths don't use it either, deleting the two lines is cheaper and safer than asking R8 to shrink them |
| `material-icons-extended` (`build.gradle.kts:146`) | a known multi-hundred-KB icon font/class set | trim to the icons actually referenced (the `Icons.*` set is enumerable), or rely on R8 in the release build |

## Design

**1. R8 on the release build only.** `isMinifyEnabled = true`,
`isShrinkResources = true` for `release`; **debug stays as-is** (fast iteration,
readable traces, and the CI's debug artifact remains the thing developers
install). Keep rules in `app/proguard-rules.pro` — which exists, is referenced
by `proguardFiles` at `build.gradle.kts:87`, and is **the untouched AGP
template**: its only content is commented-out sample text, including the
commented `#-keepattributes SourceFile,LineNumberTable` line that this phase
now un-comments deliberately. Every rule added there needs *one section per
library and a comment naming the failure it prevents*: Compose (
`-keep class androidx.compose.** { *; }` is the blunt version — start with the
documented minimum: R8 has Compose-aware defaults, so **the first attempt ships
with no Compose keeps at all** and each keep is added only if a device round
proves it), sora-editor + textmate + tree-sitter entry points (`-keep class
io.github.rosemoe.sora.** { *; }` only if the language-registry reflection
actually breaks), flexmark extension loader, `BuildConfig` (the
`GITHUB_RUN_NUMBER`-in-versionName read), DataStore serializer, and the
`CompilerResult`-style classes parsed from JSON (`org.json` reflection).
**Report the byte delta to the owner even if it's an increase** (Phase 37's
discipline: measure with `du -b`, state it as a percentage).
- `-keepattributes SourceFile,LineNumberTable` — non-negotiable here: CodeC's
  own crash record is the support channel (41/42.3), and an unobfuscated
  line-number table is what makes a minified-build crash readable. Renaming
  source-file attributes off (`-renamesourcefileattribute SourceFile`) is fine.
- `mapping.txt` is uploaded as a **CI artifact** (not into the APK), so a
  tester's stack trace can be read back by the owner; the file must never be
  attached to a public release.

**2. Per-ABI artifacts, universal kept as the default.** Add
```kotlin
splits { abi { isEnable = true; reset(); include("arm64-v8a","armeabi-v7a","x86_64","x86"); isUniversalApk = true } }
```
(NDK `abiFilters` stay — they define the set; `splits` then produces one APK
per ABI plus the universal one). The `ndk { abiFilters += "x86" }` entry is
also worth its own decision: **x86 (32-bit) phones are effectively extinct**,
and keeping it costs an unused `libtcc.so` in the universal build and a fourth
artifact. This part proposes dropping `x86` from `abiFilters` and keeping
`x86_64` (emulators) — with the explicit note that if the owner ever needs a
legacy x86 device, it's one line to restore; `minSdkVersion 24` era devices in
the owner's target group are arm64 or x86_64.

**3. Resource weight, cheaply.** `isCrunchPngs = false` was set for a reason
(the vendored IDE's PNG pipeline) — so **measure first**: try `isCrunchPngs =
true` on the release variant, record the delta and any build failure, and keep
it off if it breaks anything. Then Phase 38.1's icon set lands with real
WebP/PNG only (42 deleted nothing, 38.1 replaces the bitmaps), so the icon
densities are correct by construction.

**4. What we will not do.** No changes to `packaging { jniLibs {
useLegacyPackaging = true } }` (`build.gradle.kts:112-117`): its own comment
states why — the bundled TCC binary must be **extracted to
`nativeLibraryDir` so it can be exec'd, "works at any targetSdk, unlike
app-data exec"** — which is the same W^X story as `targetSdk = 28`, so touching
it is how a size win becomes a "C doesn't compile anywhere" release. No
dependency removals beyond the two proven candidates (OkHttp's
`logging-interceptor`/`okhttp` pair, `material-icons-extended`), each requiring
a usage grep + a green CI build in the same commit, and each *named in the
commit message* so a reviewer can object. `dependenciesInfo { includeInApk =
false }` is already set (`:130-133`) — recorded so nobody "discovers" it twice.
No `assets/` compression games (they aren't compressible further by AGP, and
xz-ing a directory the app extracts at runtime would need a decompressor in the
APK), no `vectorDrawables.useSupportLibrary` churn, and no `shrinkResources`
run without exit item 3's full language matrix on device — the vendored IDE
resources and the TextMate/theme assets are exactly where a "harmless" resource
shrink turns into a blank editor.

## Exit condition

```text
1. `:app:assembleRelease` produces: universal + 4 (or 3, after the x86
   decision) ABI APKs; `apkanalyzer files list` on an ABI APK shows exactly one
   `lib/<abi>/` directory, and `assets/tcc/` in it contains that ABI's directory
   only (option (a)/(b) from §assets, whichever the owner picks).
2. Byte budget, recorded as four numbers in this doc before the phase closes:
   universal debug (today: 24 847 906 B), universal release (R8 + shrink +
   crunch-or-not), per-ABI release before the assets mechanism, per-ABI release
   after. **Rule: if a per-ABI artifact is not ≥ 15 % smaller than the
   universal release build, the split machinery is reverted** — four artifacts
   that nobody can choose between are worse than one honest APK. No target is
   set for R8's share before the first measurement; the number comes from the
   build, not from this doc.
3. On-device with the R8'd release build: the full language matrix (C via TCC,
   C via clang module, C++ with its own compiler module, Python, Node), package
   install (the bootstrap path), editor with syntax colouring, preview,
   snippets, terminal, and a crash record generated + readable (not
   obfuscated-to-nothing). A silent "works on my debug build" is a fail. An
   armv7 or x86 device/emulator (if the owner can reach one) is included,
   because those are the ABIs where the bundled engine is absent by design.
4. mapping.txt present as a CI artifact; the release APK contains no
   `META-INF/*.version` bloat beyond what shrink can remove; no `R8: Missing
   class` warning left unresolved-by-design (each suppression justified).
5. The `assets/tcc` decision is recorded with its numbers and the owner's
   choice — including "keep shipping both ABIs in every artifact" if that is
   what the offline-first guarantee costs; and the armv7/x86 "no built-in C
   compiler" sentence lands in `docs/BETA.md` (42.3's release-notes work).
PASS = 1-5; 3 is where the phase is won or lost.
```

## Tests (plan)

R8 correctness cannot be unit-tested — that's what exit item 3 is for. What the
host does cover:

- `ApkContentsTest` (host, reads the CI artifacts): the release universal
  contains `lib/<abi>/libcodec-pty.so` for all four ABIs and
  `libtcc.so` for exactly two; each split contains exactly one `lib/<abi>/`
  directory; and — the point of §assets — **`assets/tcc/<other-abi>/` is still
  in every split unless the chosen mechanism removed it**, which this test
  asserts per the option the owner picks (a failing assertion here is the
  evidence that the trap was real, so the test is written first).
- `AbiPolicyTest`: `abiFilters` in `build.gradle.kts` matches the `splits.abi`
  include list — a mismatch between those two lists is the classic silent
  "we shipped an ABI nobody can install / forgot one" bug, and it costs one
  regex test to prevent.
- `BundledEngineAbiCoverageTest` (host, reads the real tree): for every entry
  in `abiFilters`, either `jniLibs/<abi>/libtcc.so` **and**
  `assets/tcc/<abi>/` exist, or the ABI is in the explicit
  `NO_BUNDLED_TCC = {armeabi-v7a, x86}` set — and the same two sets agree with
  `EmbeddedCompiler.ABI_DIRS`. This is the test that stops the two halves of
  the C engine (binary + assets, kept in sync by hand today from
  `assets/tcc/` to `abiFilters`) from drifting apart, which would silently
  break C on a device class; it also fails if someone adds a third ABI's
  directory without adding it to the filter list.
- Keep-rule audit: `ProguardKeepsDocumentedTest` — every `-keep` line in the
  rules file must be preceded by a comment line (the "name the crash you
  prevented" rule, enforced mechanically so the file can't turn into cargo
  cult).
- CI's `lintDebug` (no new finding) + `testDebugUnitTest` unchanged: the debug
  variant is deliberately untouched, so nothing here can destabilise the daily
  build path.

## Sources (record)

- R8 gains and cost: reported 30-50 % APK reductions for `minifyEnabled` +
  `shrinkResources` with measured build-time overhead (25 s → 35 s) in
  production write-ups [shafrazbuhary.medium.com "Reducing APK Size in
  Production Android Apps", codsod.medium.com "R8 vs ProGuard for Android in
  2025"]; the danger class is reflection/`Class.forName`-loaded libraries
  [dev.to Android app size guide].
- `splits { abi }` + `isUniversalApk` per the AGP documentation for ABI splits
  (the mechanism that keeps a universal artifact installable by anyone while
  offering smaller per-device ones) — and the same documentation's limit that
  motivates §assets: filtering applies to native libraries, not to `assets/`,
  which is why `assets/tcc/<abi>/` needs its own mechanism (a packaging
  exclude or per-flavor `assets.srcDirs`).
- CodeC code, measured/read 2026-09-10: `build.gradle.kts` — `:16`
  (`minSdk = 24`), `:24` (`targetSdk = 28`, the W^X comment above it), `:25`
  (`versionCode = 20`) and the `versionName`+`GITHUB_RUN_NUMBER` line, `:42`
  (`abiFilters` = 4 ABIs), `:34-41,44-50` (CMake `src/main/cpp/CMakeLists.txt`,
  whose only target is `libcodec-pty.so` from `pty.c`, `ndkVersion
  27.2.12479018`), `:85-87` (`isCrunchPngs=false`, `isMinifyEnabled=false`,
  `proguardFiles(… "proguard-rules.pro")` — and that file is the untouched
  template with every rule commented out), `:110` (the *only* extra assets
  srcDir: `codec-packages/keys`, the Part D trust material), `:112-117`
  (`useLegacyPackaging = true` "so it can be exec'd"), `:128`
  (`disable += "ExpiredTargetSdkVersion"`), `:130-133`
  (`dependenciesInfo.includeInApk = false`), `:146` (`material-icons-extended`),
  `:201-202` (`logging-interceptor` + `okhttp`, with no `okhttp3` import
  anywhere in `app/src`); `ui/services/EmbeddedCompiler.kt` (`TCC_LIB_NAME`,
  `ASSET_ROOT = "tcc"`, `ABI_DIRS = listOf("arm64-v8a", "x86_64")`,
  `abiDir()`, `tccBinary()` → null on an unsupported ABI, `BUNDLE_VERSION = "3"`);
  `du -sh` of `app/src/main/assets/` (tcc 7.3 M + 3.6 M, textmate 2.3 M + 52 K,
  snippets 332 K, licenses 76 K) and `jniLibs` (581 600 B / 405 352 B);
  `ui/terminal/UserlandInstaller.kt` (`DEFAULT_BASE_URL` = the GitHub
  `releases/download` route, `archiveName`/`tarballUrl`/`shaUrl`,
  `fetchSidecar` + `isValidSha` + `sha256`, `MIN_FREE_BYTES = 48 MB`,
  `STAGING_DIR` + `swapPrefix`, `archNameForAbis`), and the CI artifact sizes
  of run `34399227052` (app 24 847 906 B, `CodeC-Bench` 1 369 832 B) plus
  `README.md`'s install section (line 22-29) which is the "download the
  Actions artifact" flow this phase is about.

## Deferred / rejected with reasons

- **`androidx.compose` version catalog / BOM migration for size** — unrelated
  hygiene, own phase if ever.
- **Removing the bundled `libtcc.so` in favour of clang-only** — the TCC
  fallback exists precisely because downloaded clang cannot be exec'd on some
  devices (33.3's C path, 38.2's Termux fallback); deleting the offline
  compiler to save 1.2 MB would trade a real capability for a rounding error.
- **Dex shrink via `r8 -allowaccessmodification` tuning, `-classmemberranking`
  tricks** — measurable, brittle, unreadable crash risk; not for a beta whose
  support channel is a human reading a crash log.
- **Publishing `.aab` for direct install** — you can't install an AAB; and
  `bundletool build-apks --mode=universal` output is exactly 42.2's universal
  APK, without the signing ceremony. Recorded in case the Play decision ever
  changes.
- **Asset compression of `assets/`** — `assets/` is stored opaquely; the only
  lever there is the tarball policy above, which needs the owner's product
  decision (offline-first vs download-first), not a flag.
