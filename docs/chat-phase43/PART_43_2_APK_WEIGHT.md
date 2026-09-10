# CodeC Phase 43.2 — Weight: what a tester actually has to download

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` + build config · **Effort:** M

## The number, and what it is made of

**24 847 906 B ≈ 23.7 MiB** for the debug universal APK (run `34399227052`);
the release build today would be about the same, because
`isMinifyEnabled = false` and `isShrinkResources` is unset. What's inside it,
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
| `implementation(libs.okhttp)` + `implementation(libs.logging.interceptor)` (`build.gradle.kts:201-202`) | OkHttp 4.10 + okio, ~400 KB of DEX | **`grep -rn "okhttp3" app/src` finds no import in app code** — if `:bench` and the generated/bridge paths don't use it either, deleting the two lines is cheaper and safer than asking R8 to shrink them |
| `material-icons-extended` (`build.gradle.kts:146`) | a known multi-hundred-KB icon font/class set | trim to the icons actually referenced (the `Icons.*` set is enumerable), or rely on R8 in the release build |
| resources: 268 `strings.xml` entries, layouts, themes, vendored-Termux resources, `res/font` (Material Symbols Rounded) | — | `shrinkResources` removes unreferenced ones; `isCrunchPngs = false` currently keeps PNGs fat |

**There is no packaged userland, and that fact changes the whole size
argument.** The Termux-style rootfs is **downloaded** on first run by
`ui/terminal/UserlandInstaller.kt` from
`https://github.com/pabi277/CodeC/releases/download/<tag>/userland-<arch>.tar.gz`
with a `.sha256` sidecar, `MIN_FREE_BYTES = 48 MB` of headroom, staging +
atomic `swapPrefix` — the ~24 MiB in the APK is therefore *app* code, not a
toolchain payload, and the "make the APK small by dropping the bootstrap" idea
is already done. What the APK **does** carry per-ABI is the C engine:

**§assets — the one trap in this part.** `assets/tcc/<abi>/` (7.3 MB for
arm64, 3.6 MB for x86_64) is *not* filtered by `splits.abi`: AGP filters
`jniLibs` and the CMake output per ABI, but `assets/` is copied into every
artifact as-is. So the naive version of this phase yields four APKs that are
still ~4 MB overweight each, and an arm64 user still downloads the x86_64 TCC
runtime. Three ways out, all measurable:
(a) **`packaging.resources.excludes`** (AGP 8+/9) driven by the ABI being
built — needs a per-split hook, i.e. a small custom task or four
product flavors (`abi` dimension) instead of `splits`;
(b) **product flavors per ABI**, each with `sourceSets.main.assets.srcDirs`
pointing at one `assets/tcc/<abi>` — more build config, exact control, and it
is what `EmbeddedCompiler.abiDir()` would then have to agree with (a mismatch
means *no C compiler at all*, so the pairing needs the test below, not hope);
(c) **ship the TCC runtime as a module download** like everything else
(there is already a `tcc` entry in `ModuleCatalog.kt:53` and a `ModuleInstaller`
with a host test) — smallest APK by far, costs first-run network for C users,
and 43.2 explicitly *does not* decide this alone: it is the owner's
offline-first call, recorded as an open question with its numbers.
**Also found while measuring:** `abiFilters` lists four ABIs but
`EmbeddedCompiler.ABI_DIRS = listOf("arm64-v8a", "x86_64")` and `jniLibs` holds
only those two — so **on an `armeabi-v7a` or `x86` device `tccBinary()` returns
`null` by design**, the bundled engine is absent, and C compilation depends on
the userland/Termux path. Consequences for this part: dropping **x86** from
`abiFilters` costs nothing except `libcodec-pty.so` for a platform nobody
ships phones on (emulators are x86_64); dropping **armeabi-v7a** would break the
terminal on 32-bit ARM phones, so it **stays**. And armv7 users are precisely
the ones for whom a per-ABI artifact matters — today they download 7.3 MB of
`libtcc`/TCC runtime their device cannot even load. The release notes must say
which file to pick (`docs/BETA.md`'s known-issues list gains: *"32-bit ARM
devices: the built-in C compiler is not available; install the C toolchain
module or use Termux"*).

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
  own crash record is the support channel (41/43.3), and an unobfuscated
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
it off if it breaks anything. Then Phase 42.1's icon set lands with real
WebP/PNG only (42 deleted nothing, 42.1 replaces the bitmaps), so the icon
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
   compiler" sentence lands in `docs/BETA.md` (43.3's release-notes work).
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
  devices (33.3's C path, 42.2's Termux fallback); deleting the offline
  compiler to save 1.2 MB would trade a real capability for a rounding error.
- **Dex shrink via `r8 -allowaccessmodification` tuning, `-classmemberranking`
  tricks** — measurable, brittle, unreadable crash risk; not for a beta whose
  support channel is a human reading a crash log.
- **Publishing `.aab` for direct install** — you can't install an AAB; and
  `bundletool build-apks --mode=universal` output is exactly 43.2's universal
  APK, without the signing ceremony. Recorded in case the Play decision ever
  changes.
- **Asset compression of `assets/`** — `assets/` is stored opaquely; the only
  lever there is the tarball policy above, which needs the owner's product
  decision (offline-first vs download-first), not a flag.
