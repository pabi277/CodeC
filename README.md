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

1. Install the APK, open the editor, tap **RUN**. That's it.

CodeC ships with a **built-in C compiler** (TCC, Tiny C Compiler — the same approach as
apps like Coding C / C4droid): a static musl toolchain is embedded in the APK for
**arm64-v8a** and **x86_64** devices, so compiling works **offline, instantly, with no
downloads, no Termux and no setup**. Programs are compiled to fully static executables.

### Compiler engines (Settings → Compiler Engine)

| Engine | What it does |
|---|---|
| **Auto** (default) | Built-in TCC first (offline, instant). If it's unavailable, the Clang module; if Android blocks that (Android 10+ W^X policy, noexec storage, CPU mismatch, broken toolchain), automatically compiles and runs through **Termux's Clang**. |
| **Built-in (TCC)** | Only the compiler embedded in the APK. Covers ANSI C and most of C99; perfect for learning and everyday code. |
| **Bundled Clang** | Only the Clang downloaded in **Modules** (full C11/C17, stricter warnings — for advanced code). |
| **Termux** | Always compiles with Termux's Clang. |

The bundled Clang module (optional) must be **arm64**; an x86 emulator can't run it — but
the built-in TCC covers x86_64 emulators automatically.

To rebuild the embedded TCC bundles (e.g. to add more ABIs), run `scripts/build-tcc.sh`
with a musl cross toolchain — the script is self-contained and CI-ready.

To use the optional Termux engine, install **Termux 0.109+** from
[F-Droid](https://f-droid.org/packages/com.termux/) or
[GitHub](https://github.com/termux/termux-app/releases) (the Play Store version is outdated
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

> **Roadmap:** we're building a built-in terminal + package manager (Termux-style, no
> Termux dependency) — see [docs/TERMINAL_PLAN.md](docs/TERMINAL_PLAN.md).

### "The built-in compiler could not start"

Only possible when the APK's TCC binary doesn't match the device CPU (an exotic ABI, or a
corrupted install). Reinstall the app; the Auto engine falls back to the Clang module /
Termux in the meantime.

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

1. **Update CodeC** to the latest APK (Settings → Install APK from GitHub). The new
   builds don't use the downloaded Clang at all by default: **Auto** engine compiles with
   the **built-in TCC compiler** that ships inside the APK and runs from the native
   library directory, which Android allows at any targetSdk — no module, no Termux, no
   network. If the error persists after updating, **uninstall and reinstall the app
   once** — Android labels the sandbox at install time and an in-place update may keep the
   old restriction.
2. **Switch the engine to Termux** (Settings → Compiler Engine → Termux, setup above).
   Termux's own storage is exempt, so this works even when the bundled compiler is
   blocked.
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
