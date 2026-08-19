# CodeC IDE

A C programming IDE for Android. Download Clang from the in-app Module Store, then tap **RUN**.

## Install the APK from GitHub

1. Push this branch (or merge to `main`). GitHub Actions builds `app-debug.apk`.
2. Open **Actions** → latest **Build APK** run → **Artifacts** → `CodeC-IDE`.
3. Or create a GitHub **Release** (any tag). The workflow attaches the APK to that release.
4. On your phone: download the APK → allow **Install unknown apps** → install.

In the app: **Settings → Install APK from GitHub** downloads the latest release APK and opens the installer.

Direct releases page: https://github.com/pabi277/CodeC/releases

## Run C on the phone

1. Open **Modules** and download **Clang/LLVM Compiler** (ARM64).
2. Wait until status is **Installed** (extracts into app-private storage so Android can execute it).
3. Open the editor and tap **RUN**.

Clang must be **arm64**. An x86 emulator cannot run this module.

## Build locally

Android Studio: open this folder and run the `app` debug configuration.

Command line (needs JDK 17; the checked-in Gradle wrapper downloads the AGP-compatible Gradle 9.3.1):

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.
