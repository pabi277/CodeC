# CodeC IDE v{{VERSION}}

<!--
    Phase 42.1 — the RELEASE NOTES template the publish workflow fills in.
    Every `##` heading below is asserted by scripts/check_release_notes.sh
    (run in CI on every build), so the notes can never silently shrink to
    "bug fixes". Placeholders the publish step substitutes:

      {{VERSION}}       — e.g. 1.3.17, taken from the tag app-v<X.Y.Z>
      {{VERSION_CODE}}  — the versionCode the tag was built with
      {{DATE}}          — UTC date of the publish run
      {{SHA256_LINES}}  — one "sha256: <64hex>  <asset>" per attached APK
      {{CHANGELOG}}     — git log --oneline since the previous app-v* tag

    Update the prose (What's new / What still bites) per release; keep the
    headings. The notes are ALSO what the in-app updater reads its
    "sha256:" lines from (UpdatePolicy.digestsFromNotes): no checksum line,
    no auto-install — that is why the publish step, not a human, writes them.
-->
versionCode: {{VERSION_CODE}}
tag: app-v{{VERSION}}
published: {{DATE}}

## What's new

_CodeC IDE v{{VERSION}} — write and run C, Python, JavaScript and HTML on
your phone; C works offline with no setup._

{{CHANGELOG}}

## Which APK should I install?

- **Almost everyone: `CodeC-IDE-{{VERSION}}-universal.apk`.** It runs on
  every device CodeC supports (64-bit ARM phones, x86_64 emulators, and
  32-bit ARM phones).
- The per-ABI files (`-arm64-v8a`, `-armeabi-v7a`, `-x86_64`) are the same
  app minus the parts your device cannot use. Pick them when you know your
  device's ABI (Settings → About in the app shows it); when in doubt,
  take universal.
- **32-bit ARM (armeabi-v7a / older x86) devices:** the built-in C compiler
  is not bundled for your ABI — C works through the downloadable Clang
  module (Packages tab) or the Termux fallback. Everything else in the app
  is the same.

## Upgrading / downgrading

- Install the APK over the existing app — Android updates it in place and
  keeps your projects **as long as the signing key is the same**.
- The first release-signed build does **not** share a key with the old
  debug-signed CI builds: switching from a debug build to this release is a
  fresh install. **Export your projects first** (Files → project → Export
  ZIP, or Files → ⋮ → Export all projects) and import them back afterwards.
- The app's own **Check for updates** (Settings → About) never installs an
  older release over a newer one and says why.

## Checksums

Verify before sideloading (`sha256sum <file>`), or let the in-app updater
check for you — it refuses to install anything whose checksum does not
match these lines, and refuses to install at all when none are published:

{{SHA256_LINES}}

## What still bites (known issues)

- Opening a huge folder from the file picker can be slow on low-RAM phones;
  if it hangs, back out and open a smaller subfolder.
- Device-to-device restore and cloud backup carry **your projects only** —
  the toolchain, packages, and your GitHub sign-in do not travel; a fresh
  install downloads the userland again and asks you to sign in again (that
  is deliberate: the backup never carries your token).
- Everything user-visible that has bitten someone and what to do about it:
  [TROUBLESHOOTING.md](TROUBLESHOOTING.md); beta notes: [BETA.md](BETA.md).

## Feedback

In the app: **Settings → Feedback & Support** (WhatsApp chat with the
developer, email, or a GitHub issue — nothing is sent unless you send it).
Rating and exit-survey answers ride the same report.

## Privacy

CodeC runs entirely on your device. No analytics, no ads, no account, no
crash upload. The only network requests it makes are the ones you start:
the bootstrap/package downloads you approve, git against the repository you
configured, and this update check when you tap it. Details:
[DATA_AND_PRIVACY.md](DATA_AND_PRIVACY.md).
