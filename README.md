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

The bundled Clang must be **arm64**. An x86 emulator cannot run it directly — but you can
still use emulators and any other device by switching the compiler engine (below).

### Compiler engines (Settings → Compiler Engine)

| Engine | What it does |
|---|---|
| **Auto** (default) | Uses the bundled Clang; if Android blocks it (Android 10+ W^X policy, noexec storage, CPU mismatch or broken toolchain), automatically compiles and runs through **Termux's Clang**. |
| **Bundled** | Only the Clang downloaded in Modules. |
| **Termux** | Always compiles with Termux's Clang. Works on any real phone and on x86_64/32-bit emulators, because Termux targets API 28 where downloaded binaries still run. |

To use the Termux engine, install **Termux 0.109+** from [F-Droid](https://f-droid.org/packages/com.termux/)
or [GitHub](https://github.com/termux/termux-app/releases) (the Play Store version is outdated
and does not support this), then in Termux run:

```bash
echo "allow-external-apps=true" >> ~/.termux/termux.properties
termux-reload-settings
pkg update && pkg install clang
```

and grant CodeC the **"Run commands in Termux environment"** permission
(Android Settings → Apps → CodeC IDE → Permissions → Additional permissions).
Settings → Compiler Engine → "CHECK BRIDGE" verifies the whole chain.

## Troubleshooting

### "Permission denied" when compiling — Android blocks the downloaded compiler

This error has two real causes:

1. **Android 10+ (API 29+) W^X policy.** Since Android 10, the system refuses `exec()`
   of downloaded binaries stored in an app's own data directory **when the app targets
   API 29 or higher** (see the [Android 10 behavior change](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)).
   This affects *real phones*, not only emulators — the old build of CodeC targeted
   API 34, so on any Android 10+ phone the downloaded Clang was blocked with
   "Permission denied". The new builds use the same **targetSdk 28 compatibility mode
   that Termux uses**, which keeps downloaded binaries executable.
2. **noexec app storage.** Some emulators, cloud phones and managed devices mount app
   storage with the `noexec` flag. No app-side change can fix that — nothing can execute
   there, Termux included.

**Fixes, in order:**

1. **Update CodeC** to the latest APK (Settings → Install APK from GitHub). If the error
   persists after updating, **uninstall and reinstall the app once** — Android labels the
   sandbox at install time and an in-place update may keep the old restriction.
2. **Switch the engine to Termux** (Settings → Compiler Engine → Termux, setup above).
   Termux's own storage is exempt, so this works even when the bundled compiler is
   blocked — and it is the only option on x86_64 emulators.
3. **Use Termux directly** — see [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) for a
   complete step-by-step C workflow in Termux.
4. On a truly `noexec` device (cloud phones, some enterprise ROMs) no local compiler can
   run; use a real phone or an online compiler.

If you still see this error, open **Logs** (Settings → Developer Options) and include the
"Device: …" line (ABI + app storage mount flags) in a bug report.

### "Exec format error" when compiling

CPU mismatch. CodeC ships an ARM64 compiler, so **x86/x86_64 emulators and 32-bit devices
can't run it directly**. Use a real ARM64 phone, or switch **Settings → Compiler Engine →
Termux** — Termux ships a native Clang for x86_64 and 32-bit ARM too. On an ARM64 phone,
reinstall the module (Uninstall → Download) to rule out a corrupted download.

### "Runtime libraries missing" when compiling

The toolchain is incomplete or corrupted. Open **Modules** → **Uninstall** → **Download**
again (the download is checksum-verified, so this usually means the install was
interrupted), or switch to the Termux engine.

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
