# Phase 102 — install-path hardening + the Play Protect heads-up

**Status:** implemented on `arena/dcabbb28-codec` (2026-10-08), owner's pick
**"1. A+B"** from the discussion of his Play Protect screenshot
(`CodeC-IDE-1.3.18-universal.apk` → *Unsafe app blocked — built for an older
version of Android*). No edit was made before the owner chose the path.

## The diagnosis (verified in-repo)

- The dialog is Play Protect's low-`targetSdk` warning; `targetSdk = 28`
  (`app/build.gradle.kts:26`) is the trigger and is load-bearing: Android 10+
  SELinux denies `execve()` in app data for targets 29+, which is where the
  downloaded Clang/userland live. Same mode as Termux, same dialog as Termux.
- There is no middle value: 29+ breaks exec AND still warns on 2026 devices.
- So: **A** explain the wall everywhere, **B** close the real gaps in the
  install path. **C** (re-architect exec under a modern target) recorded as
  the only way to remove the dialog, deferred by owner.

## A — the wall is now expected, not scary

- `docs/guides/TROUBLESHOOTING.md` §62: why the dialog appears, that it is a
  warning not a block, Install-anyway + verify recipe.
- `README.md` download section: the same notice beside the install steps.
- `docs/guides/RELEASE_NOTES.md` "What still bites": standing line.
- In-app: the updater no longer auto-installs on one tap. A consent dialog
  names the version, the checksum contract and the Play Protect notice;
  INSTALL runs download → size+SHA-256 gate → **signature gate** → handoff.

## B — the install path, hardened

1. **Session-first handoff.** `ApkUpdateManager.installViaSession` writes the
   verified APK into a `PackageInstaller` session (private pipe, no URI grant
   leaves the app); `InstallStatusReceiver` (exported=false) handles the
   status callback incl. `STATUS_PENDING_USER_ACTION`.
2. **Scoped fallback.** When a session cannot be created, the legacy
   FileProvider intent is `setPackage(...)`-scoped via pure
   `InstallHandoffPolicy` (known system installers → any system app → null +
   logged last resort). The pre-102 implicit intent let any app with an APK
   intent filter claim the `FLAG_GRANT_READ_URI_PERMISSION` grant.
3. **Authenticity, not just integrity.** Pure `ApkSignaturePolicy` compares
   the archive's signing certificate (SHA-256 over DER) to the pin published
   in `docs/guides/UPLOAD_KEY_SETUP.md`
   (`d58fed4b…4638`) or to the running install's own certificate
   (self-update). `Unverifiable`/`WrongKey` → browser, never install. The
   pre-102 digest check rode the same channel as the APK (integrity only).
4. **Host pin.** `UpdatePolicy.isTrustedDownloadUrl` (https + `github.com` /
   `*.githubusercontent.com`) enforced in `preflight`; a tampered release
   JSON can no longer point the downloader at a foreign host.

## Tests (CI runs them; the sandbox has no JVM and the release-asset CDN is
unreachable from it, so nothing below ran locally — stated, not hidden)

- `UpdatePolicyTest` + host-pin cases; helper URL moved onto a trusted host.
- `InstallHandoffPolicyTest` (5), `ApkSignaturePolicyTest` (6, incl. the
  pin↔docs spelling pin and the SHA-256 "abc" vector),
  `UpdateHardeningWiringTest` (5 source pins: session-first, scoped
  fallback, gate-before-install, consent dialog + notice strings, receiver
  exported=false).

## Not done (open, named)

- C remains the only route to remove the dialog itself (bundle the toolchain
  as native libs or a ptrace interpreter; dual flavors). Owner deferred it.
- The pin is checked against the docs spelling by test; a device round should
  confirm the session path on the owner's phone (Play Protect still shows
  for sessions — expected, the dialog now explains it).
