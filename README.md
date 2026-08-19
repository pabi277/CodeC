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

## Troubleshooting

### "Permission denied" when compiling

The device is blocking execution of the downloaded compiler. This happens on
**emulators and cloud Android devices** that mount app storage without execute
permission — the app extracts the compiler into its private storage, but the
system refuses to run binaries from there. Install and run CodeC on a **real
ARM64 phone** (Android 7+).

If you see this on a real phone: open **Modules** → **Uninstall** → **Download**
again so the toolchain is re-extracted with correct permissions.

### "Exec format error" when compiling

CPU mismatch. CodeC ships an ARM64 compiler, so **x86/x86_64 emulators and
32-bit devices can't run it**. Use an ARM64 device. On an ARM64 phone, reinstall
the module (Uninstall → Download) to rule out a corrupted download.

### "Runtime libraries missing" when compiling

The toolchain is incomplete or corrupted. Open **Modules** → **Uninstall** →
**Download** again (the download is checksum-verified, so this usually means the
install was interrupted).

### Install or compile hangs

Compilation is capped at 30s and program execution at 10s; both are killed
automatically. A compile that "hangs" for exactly 30s usually means the
toolchain can't start — check the error text above.

## Build locally

Android Studio: open this folder and run the `app` debug configuration.

Command line (needs JDK 17; the checked-in Gradle wrapper downloads the AGP-compatible Gradle 9.3.1):

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.
