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
    versionCode = 19
    versionName = "1.3.15"

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
      isCrunchPngs = false
      isMinifyEnabled = false
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
  implementation(libs.logging.interceptor)
  implementation(libs.okhttp)
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
