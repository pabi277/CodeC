plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.codeci.bench"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        // Separate applicationId on purpose: the bench installs ALONGSIDE the
        // real IDE (com.codeci.ide) and can never affect it.
        applicationId = "com.codeci.bench"
        minSdk = 24
        // targetSdk 28 mirrors :app so the owner's phone runs the harness in
        // the same compatibility mode as the IDE (insets, dispatch behavior).
        targetSdk = 28
        versionCode = 1
        versionName = "28.1-codec-keys-spike"
    }

    // Phase 25.1 — the bench is sideloaded by the owner from the CI artifact
    // and there is no release upload key available in CI, so the (throwaway)
    // release build is signed with the REPO-PINNED shared debug key that
    // :app already uses for debug builds. Updates install in place.
    signingConfigs {
        create("benchSign") {
            storeFile = rootProject.file("debug.keystore")
            storeType = "PKCS12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // The 25.1 exit demands RELEASE numbers ("debug Compose is
            // misleadingly slow" — Phase 22 evidence), so the bench goes
            // through R8 even though the harness is throwaway.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("benchSign")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    // assembleRelease runs fatal-only lint (lintVitalRelease), which trips on
    // the same targetSdk-28 Google Play policy check :app's lint block
    // disables ("CodeC is distributed from GitHub"). The bench is a throwaway
    // harness — skip release lint entirely; the :app lintDebug gate in CI is
    // unaffected.
    lint { checkReleaseBuilds = false }
    // Pure-JVM unit tests; the flag keeps any accidental android.* stub call
    // returning a default instead of throwing "not mocked".
    testOptions { unitTests { isReturnDefaultValues = true } }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    // Candidate C-sora — LGPL-2.1, BINARY DEPENDENCY ONLY (rule.md clean-room
    // discipline; PART_25_2's checklist binds any real integration, and no
    // sora source is vendored here).
    implementation(libs.sora.editor)
    implementation(libs.sora.editor.language.java)
    testImplementation(libs.junit)
}
