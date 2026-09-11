import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.codeci.ide"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.codeci.ide"
    minSdk = 24
    // targetSdk 28 on purpose: Android 10+ (API 29+) SELinux policy denies
    // execve() of files in an app's own data directory for apps targeting
    // API 29+ ("Removed execute permission for app home directory", W^X).
    // That restriction is what made the downloaded Clang fail with
    // "Permission denied" on real Android 10+ phones. Termux uses the same
    // targetSdk 28 compatibility mode so that downloaded binaries keep
    // working. CodeC is distributed via GitHub (not Play), so this is safe.
    targetSdk = 28
    // Phase 42.1 — versionCode/versionName are release metadata now: a tag
    // `app-v<X.Y.Z>` must name this versionName, and the publish step refuses
    // a versionCode that is not strictly greater than every shipped one
    // (.github/workflows/build-apk.yml + docs/RELEASE_NOTES.md).
    versionCode = 21
    // The device round kept tripping over WHICH apk was installed (three
    // crash reports pasted from a stale build). The CI run number (or a
    // local timestamp) rides in versionName so Settings → About / app info
    // answers it at a glance: "1.3.17 (340xxxx)".
    versionName = "1.3.17" + (System.getenv("GITHUB_RUN_NUMBER")?.let { " ($it)" } ?: "")
    // Phase 42.3 — the About row's BUILD date + provenance: a tester asking
    // "which build is this?" answers it from Settings → About, next to the
    // version name. UTC date: stable per day, no clock lies.
    buildConfigField(
      "String", "BUILD_DATE",
      "\"${LocalDate.now(ZoneOffset.UTC)}\""
    )

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    externalNativeBuild {
      cmake {
        arguments += "-DANDROID_STL=none"
        cFlags += "-std=c11"
        cFlags += "-fvisibility=hidden"
      }
    }
    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }
  }

  ndkVersion = "27.2.12479018"
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      // Pinned shared debug key (committed at the repo root) so that every
      // CI-built and locally-built debug APK carries the SAME certificate.
      // Before this, each CI runner fell back to a per-runner ephemeral
      // ~/.android/debug.keystore, so sideloading the next build over the
      // previous one failed with INSTALL_FAILED_UPDATE_INCOMPATIBLE and the
      // required uninstall wiped the whole app sandbox (userland prefix,
      // dpkg DB, installed packages). Debug-only; release uses the upload
      // key above. The store is generated with OpenSSL as PKCS12 (30-year
      // validity, standard Android debug DN/AES-256 PBE), so declare it:
      val debugStore = file("${rootDir}/debug.keystore")
      if (debugStore.exists()) {
        storeFile = debugStore
        storeType = "PKCS12"
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
  }

  buildTypes {
    release {
      // Phase 42.2 — release gets R8 + shrinkResources + PNG crunch
      // (aapt2's crunch is lossless recompression); DEBUG is deliberately
      // untouched (fast iteration, readable traces, the CI debug artifact
      // is what developers install). The keep-rule file law: one section
      // per library, each -keep with a comment naming the crash it
      // prevents; mapping.txt is a CI artifact, never a public release
      // asset; SourceFile/LineNumberTable stays (a crash record the
      // owner cannot read is the worst third-party flake).
      isMinifyEnabled = true
      isShrinkResources = true
      isCrunchPngs = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val debugStore = file("${rootDir}/debug.keystore")
      if (debugStore.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }

  // Phase 42.2 — per-ABI artifacts, universal kept as the default. The
  // natural ABI set stays in ndk.abiFilters; splits only packages. A
  // tester on a slow link picks the per-ABI APK; "just install it" stays
  // the universal one. AbiPolicyTest pins the two lists in lockstep.
  // Owner-open decisions (numbers in docs/chat-phase42/PART_42_2): the
  // assets/tcc/<abi> trap (a packaging exclude or per-flavor assets) and
  // dropping x86 from abiFilters — both recorded as the owner's call.
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
      isUniversalApk = true
    }
  }
  compileOptions {
    // Phase 25.2 — Java 17: sora-editor (the edit core) requires consumers on
    // 17; :bench already builds at 17.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  sourceSets {
    // Part D: package only the public CodeC repository trust material. The
    // private primary/signing keys never enter the source tree or APK.
    getByName("main").assets.srcDir(rootProject.file("codec-packages/keys"))
  }
  packaging {
    jniLibs {
      // Extract the bundled TCC binary (libtcc.so) to nativeLibraryDir so it
      // can be exec()'d — works at any targetSdk, unlike app-data exec.
      useLegacyPackaging = true
    }
  }
  // isReturnDefaultValues: unit tests exercise error paths that log through
  // android.util.Log; on the JVM those stubs must return 0 instead of throwing
  // "not mocked".
  testOptions { unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true } }
  lint {
    // CodeC is distributed from GitHub (not Google Play), and deliberately
    // targets API 28 — Termux's compatibility mode — so that the downloaded
    // compiler stays executable on Android 10+. The Google Play targetSdk
    // policy check does not apply here.
    disable += "ExpiredTargetSdkVersion"
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }

}

// Phase 42.2 — APK file names follow the update-channel grammar the
// in-app updater already parses (UpdatePolicy): CodeC-IDE-<version>-
// <abi>.apk, the universal one named -universal.apk (the updater's
// default). Publishing AGP's raw app-<abi>-release.apk names would
// resurrect the "updater picks the first .apk" support ticket.
// versionName may carry " (GITHUB_RUN_NUMBER)" — strip before naming.
// AGP 9 note: the legacy android.applicationVariants API is REMOVED
// (run 34570360926: 'Unresolved reference: applicationVariants'); the
// onVariants + VariantOutputImpl route below is the AGP 9 surface — and
// the outputFileName property still lives on the impl class, so the hard
// error guards a future AGP bump from silently shipping
// app-<abi>-release.apk names under a "green" build again.
androidComponents {
  onVariants { variant ->
    variant.outputs.forEach { output ->
      val impl = output as? com.android.build.api.variant.impl.VariantOutputImpl
        ?: error("42.2 APK naming: output is not VariantOutputImpl — AGP surface changed?")
      val abi = impl.filters.firstOrNull { it.filterType.name == "ABI" }?.identifier ?: "universal"
      val version = (output.versionName.get() ?: "unknown").substringBefore(' ')
      val suffix = if (variant.buildType == "release") "" else "-${variant.buildType}"
      impl.outputFileName = "CodeC-IDE-$version-$abi$suffix.apk"
    }
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // Phase 25.2 — sora-editor, the edit core (LGPL-2.1, BINARY dependency
  // only — no source vendored; see docs/chat-phase25/PART_25_2_SORA_PATH.md).
  implementation(libs.sora.editor)
  // Phase 29.1 — TextMate: VS Code grammars + themes through sora's
  // language-textmate module (same BOM version; binary dependency only).
  // Exclusions (APK-budget trims, 2026-09-05): snakeyaml-engine is the
  // YAML theme/grammar parser — CodeC ships ONLY .json grammars/themes,
  // so it is dead weight (RawThemeReader only reaches it for YAML
  // content-type sources); org.eclipse.jdt.annotation is compile-time
  // @NonNull/@Nullable annotations. joni/jcodings/gson stay — they ARE
  // the grammar engine (oniguruma regexes + JSON parsing).
  implementation(libs.sora.language.textmate) {
    exclude(group = "org.yaml")
    exclude(group = "org.eclipse.jdt")
  }
  // Phase 31.1 — LSP completion client. sora editor-lsp is a self-contained
  // AAR that owns a stdio LanguageServerWrapper + an lsp4j implementation; we
  // never speak lsp4j types directly (see ui/editor/lsp/ — CodeC's pure
  // models stay Android-free and host-testable, the editor-lsp types are
  // sealed behind a callback). The module brings org.eclipse.lsp4j:1.0.0 +
  // kotlinx-coroutines-android 1.10.2 transitively; both are already on the
  // app classpath at the same versions.
  //
  // Phase 31.5 (2026-09-07, owner: "Now start wiring"): the gate-flip
  // experiment (commit ce4c41b) FAILED — the build reproduced the same
  // :app:processDebugMainManifest error from the very first 31.1 push.
  // The owner is pastes the failing log block in chat (rule.md §9); the
  // gate is re-CLOSED here so the UI-shell branch stays green-mergeable
  // while the wire question is being decided. The two paths forward,
  // documented in JOURNEY item 43 (31.4 follow-up paragraph):
  //   (A) bump CodeC's AGP 9.1.1 → 9.3.1 + Kotlin 2.2.10 → 2.4.10
  //       to match the sora BOM, then re-flip the gate;
  //   (B) ship a hand-rolled ~200-LOC LSP stdio client (no sora
  //       dep, no AGP delta) and skip the AAR entirely.
  if (project.findProperty("editorLsp")?.toString() == "true") {
    implementation(libs.sora.editor.lsp)
  }
  // Phase 37.1 — QR encoding for the LAN URL (ZXing `core`, Apache-2.0,
  // zero-dependency). Used only through `QrCode` (ui/services/QrCode.kt),
  // which returns a plain module grid, so no other file imports zxing and the
  // bitmap stays in Compose. Notice: assets/licenses/ZXING_APACHE2.txt.
  implementation(libs.zxing.core)
  // Phase 42.2 — REMOVED: com.squareup.okhttp3:okhttp + logging-interceptor.
  // Grep proof (2026-09-11): zero `okhttp3`/`okio` imports anywhere in
  // app/src — the app speaks HTTP through java.net HttpURLConnection
  // (UpdateManager, UserlandInstaller); :bench never referenced them
  // either. Both lines were dead weight on the release classpath.
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

// A hung host test (unclosed PipedInputStream.readBytes, etc.) used to
// pin the CI runner until the owner cancelled at 3+ hours. Fail the
// test task instead — the suite is normally well under this.
tasks.withType<Test>().configureEach {
  timeout.set(Duration.ofMinutes(5))
}
