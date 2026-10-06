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

**The AI assistant — the whole line (Phases 84–96 / AI Levels 7–12).** CodeC
now carries an optional, read-first AI assistant: open it from the ✨ rail
slot or the floating ✨ button. It runs on **your own provider key** (Google
Gemini by default; NVIDIA Build is a manual, internal-testing selection) —
there is no CodeC server and no shared key.

- **Your key, your bill, your provider.** The key is stored encrypted
  (Android Keystore AES-256-GCM) under `no_backup/ai/` and never rides a
  backup; nothing leaves the phone until you tap **Send** on a preview that
  shows exactly what will be sent.
- **Agent tools, and what they may see.** `list_files`, `search_project`,
  `read_file`, `read_files` (up to 8 files per batch), plus the
  readable-structure results `find_files`, `outline_file` and
  `read_run_output`. `search_project` takes `list_files`' own scoping keys
  (`max`, `path`, `ext`) and can only narrow the walk's admitted list.
  Secret-like paths (`.env`, `.npmrc`, private keys), symlink escapes and
  build output are refused before they can reach a request.
- **No write tool and no exec tool, by design.** Edits arrive as a
  `<<<CODEC_EDIT>>>` proposal rendered as a diff you review; only your
  **Apply** writes (through `AiEditApplier`). A run request reaches CodeC's
  normal **RUN ▶** pipeline only after you tap **Run** on the approval card —
  Skip runs nothing.
- **Markdown answers.** Headings, lists, tables and fenced code blocks, with
  a copy-only **Copy** per block; `https` links open only behind a confirm
  dialog that shows the full URL.
- **The nine agent controls** (AI panel): read window, working-set depth,
  task memory, answer detail, activity display, backup-provider offer, budget
  offer, reviewer — and request inspection, which is always on. Each control
  tunes *inside* a cap; none can grant a permission.
- **Caps that no control can raise:** 12 model turns, 24 tool calls, 2 runs,
  24 000-char reads, 8 000-char results, 8 batched reads, 3 identical
  repeats before the loop stops.
- **History drawer and first-run agreement.** Pinned/Recents rows, pin/unpin,
  a New-chat pill with one confirm, and a one-card welcome whose four bullets
  end in a pinned **I understand — start**.
- **Six bug fixes and three first-run shape fixes** from the device rounds —
  a settled conversation can no longer be filed under the wrong project or
  the wrong chat row, the drawer's New-chat pill keeps its confirm, ☰ can
  never go dead, the current row is marked, the *Explain last error* route
  redacts exactly like the `read_run_output` tool, and the agreement button,
  the first-run sheet height and the instant history row all behave on a
  small phone.

{{CHANGELOG}}

## Which APK should I install?

- **Everyone: `CodeC-IDE-{{VERSION}}-universal.apk` — the ONLY APK.** It
  runs on every device CodeC supports (64-bit ARM phones, x86_64
  emulators, and 32-bit ARM phones). Per-ABI variants were tried and
  reverted (measured < 2 % smaller because the offline C toolchain ships
  in every variant; Docs: Phase 42.2).
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

- **AI chat history lives in this session only (D6).** Conversations, titles
  and the drawer are held in memory: closing the app clears them. Raw chat
  text is never written to a file, a store, a log or a backup.
- **The read window default is 400 lines.** It can be lowered to 50 in the AI
  panel, but the default — and the maximum the runner will serve — is 400.
- **The AI has no write tool by design.** It can propose an edit (a diff you
  tap **Apply** on) and request a run (which waits for your **Run** tap); it
  cannot write a file, stage or commit, or type in your terminal.
- **On a small phone a code block's contrast is modest.** Fenced code in an
  answer is readable, but not high-contrast on narrow screens.
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
