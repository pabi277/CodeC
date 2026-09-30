# CodeC first-run experience + game arena

> **Current shipped behavior (2026-09-30):** a fresh install sees CodeC's two-second in-app mark, the native introduction and privacy acknowledgement, then receives CodeC Arcade — Snake, Block Party, and Tic-Tac-Toe behind a home screen. Its HTML, CSS, and JavaScript are separate editable project files. The Orbit Shift design and one-file prototype documented later in this file are historical and were superseded before shipping.

- **Research and implementation record:** 2026-09-30 (UTC)
- **Scope:** cold-start handoff, first-run education and privacy acknowledgement, replacement starter game
- **Current implementation:** `FirstRunIntroScreen.kt`, `GameArenaSample.kt`, `MainActivity.kt`, `app/src/main/assets/game-arena/`
- **Automated verification:** Build APK run [`36724487487`](https://github.com/pabi277/CodeC/actions/runs/36724487487) passed host unit/screenshot tests and debug/release APK builds; no device-specific test is claimed here.

## Current shipped implementation

After the required acknowledgement, `GameArenaSample` seeds the `codec-arcade` web project and opens `index.html` in the editor. The seed copies the bundled project files as ordinary user files; users can inspect, edit, save, and run them through CodeC's existing local preview. No Linux userland, network request, external font, downloaded image, account, or package is needed to play.

The arena contains three independently navigable games:

- **Snake:** touch/swipe and keyboard controls, fruit, collisions, and a locally stored best score.
- **Block Party:** an original, untimed block-placement puzzle with row/column clears.
- **Tic-Tac-Toe:** a pocket CPU, win/draw checks, and accessible status announcements.

The source tree is intentionally modular: `index.html`, `styles/arena.css`, shared `js/main.js` and `js/storage.js`, and one JavaScript file per game under `js/games/`. A seed marker prevents later launches from overwriting edits or silently recreating a project the user deleted. Existing projects are left alone. The intro can be replayed from Settings → About; replaying it does not overwrite an existing arcade.

The original three-step introduction and acknowledgement remain native Compose UI. The in-app CodeC mark is displayed for two seconds, after which the intro continues; this is separate from Android's system splash, which is released when launch readiness completes. Safe mode continues to prioritize recovery rather than trapping a user in onboarding.

## Archived candidate: Orbit Shift (not shipped)

The remaining product research, detailed decisions, proposed device checklist, and source-specific claims in this file preserve the earlier Orbit Shift prototype. They are useful historical context only; they do not describe the current starter or implementation.

## What the app did before

CodeC already has an AndroidX/platform splash screen using the existing CodeC launcher mark.
`LaunchReadiness` releases it when the first route and theme are resolved; it does not hold the logo
for a timer. After that, a fresh install silently wrote the Snake sample, marked first launch
complete and opened the editor. That got a new user to a real file quickly, but did not explain what
CodeC was, what the first Run action would do, or the app's local-first privacy model. The Settings
action still described this as showing the old welcome screen.

The app is a native Android Compose IDE, not a web app. The first-run change therefore stays in the
native shell; the game is an HTML sample because the existing local preview can run it without
installing CodeC's Linux userland.

## Online research checked

Sources were read on 2026-09-30. Platform and research guidance informed the interaction; it is not
a claim that CodeC has been user-tested on a handset.

1. **Android splash screen guidance** — [Splash
   screens](https://developer.android.com/develop/ui/views/launch/splash-screen). The system splash
   is a short launch handoff; the app should dismiss it when its first stable UI is ready, not keep
   it visible for decoration. CodeC retains its existing app-mark splash and readiness gate, adds no
   minimum delay, and renders the introduction as real content after the handoff.
2. **Android onboarding guidance** —
   [Onboarding](https://developer.android.com/design/ui/mobile/guides/patterns/onboarding). The
   app's value should be shown before requesting permissions or account setup; walkthroughs should
   be considered critically, provide progress signposting and a skip path, and permissions should be
   explained when needed. CodeC's intro is three short screens, can skip directly to the required
   acknowledgement, and requests no device permissions or signup.
3. **Android permission principles** — [Request runtime
   permissions](https://developer.android.com/training/permissions/requesting). Requests should
   happen in context, with an explanation and a graceful decline path. CodeC continues to request
   file/camera access only at the feature that needs it; first run does not bundle permission
   prompts into the agreement.
4. **Compose accessibility defaults** — [API
   defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
   Interactive targets should be at least 48dp and standard Compose controls provide useful
   semantics. The intro uses Material buttons/checkboxes and the app's `CodecTokens.MIN_TOUCH`; the
   Orbit game has named HTML controls and keyboard alternatives.
5. **Onboarding tutorial research** — [Nielsen Norman Group: Onboarding Tutorials vs. Contextual
   Help](https://www.nngroup.com/articles/onboarding-tutorials/). NN/g describes walkthrough
   tutorials as disruptive, frequently skipped and difficult to remember out of context, and
   recommends easy dismissal/retrieval, progressive disclosure and practice when appropriate. This
   supports a deliberately small tour (not a tour of every feature), an explicit skip to the
   acknowledgement, an animated real task preview, and a replay action in Settings. This does not
   prove the flow's performance for CodeC users.
6. **Mobile web-game controls** — [MDN: Mobile touch
   controls](https://developer.mozilla.org/en-US/docs/Games/Techniques/Control_mechanisms/Mobile_touch).
   MDN documents direct touch input for moving a game object and the need to prevent browser
   gestures from taking over gameplay. Orbit Shift uses simple ring-selection buttons and a canvas,
   plus keyboard controls; the page itself remains scrollable outside the game surface.
7. **Reduced motion** — [MDN:
   `prefers-reduced-motion`](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/At-rules/@media/prefers-reduced-motion).
   The HTML sample reduces decorative canvas motion and omits screen shake when reduced motion is
   requested. The Compose intro uses the existing `CodecMotion`/`rememberMotionSpecs` policy so
   Android's remove-animations setting makes the reveal immediate.
8. **Canvas and accessible fallback** — [MDN: Basic usage of
   canvas](https://developer.mozilla.org/en-US/docs/Web/API/Canvas_API/Tutorial/Basic_usage). Canvas
   provides a self-contained 2D drawing surface, but its visual content needs useful alternate text.
   Orbit Shift names the canvas with an accessible label and includes fallback instructions inside
   the `<canvas>` element.

## Product decisions

### Launch sequence

1. Keep the existing CodeC launcher logo splash. It remains a true startup handoff, not a timed
   brand interstitial.
2. On a fresh install, show a native three-step introduction after the preference read. Use CodeC's
   existing app mark, a code-to-output reveal and an original Orbit illustration; show a visible
`Step 1 of 3` progress indicator. Safe mode intentionally bypasses the intro and seeding so crash
   recovery is not held behind onboarding.
3. Offer **Skip to agreement** from the first two steps. This shortens the tour but never skips the
   final privacy acknowledgement.
4. Make **Back** available; place the primary action in a stable footer. Respect Android's
   remove-animations preference.
5. On acceptance, create the sample on IO, save the editor launch state before completing the
   existing preference, then open the editor on `orbit-shift/index.html`. If storage refuses the
   seed, leave the gate cleanly and use the existing Projects fallback rather than trapping the
   user.
6. Returning users bypass the intro and retain CodeC's existing resume behavior. **Settings → About
   → Replay the CodeC introduction** requests a replay for the next launch, without yanking a user
   out of Settings.

### Agreement and privacy wording

There is no separate CodeC Terms of Service in this repository, so the UI does **not** invent or
claim one. It asks the user to acknowledge the concise privacy summary before proceeding:

- source projects live in CodeC's app storage by default;
- CodeC has no advertising, analytics, tracking or automatic crash-report upload;
- internet work (Git, package downloads and app updates) starts only when the user starts it;
- shared-folder access is optional; Android's picker remains available;
- a crash record stays local unless the user chooses to share it.

The acknowledgement is not a request to grant Android permissions. The **Read the full privacy
summary** modal expands the details before the user checks the box; Settings → About keeps the
permission rows accessible afterward. The source of truth remains
[`DATA_AND_PRIVACY.md`](../guides/DATA_AND_PRIVACY.md).

### Replacement game: Orbit Shift

`Orbit Shift` is an original, one-thumb orbital arcade game rather than a reskin of Snake. The
player selects an inner or outer ring, gathers gold light shards and avoids red comets. It is
intentionally simple enough to understand from one animated illustration and to inspect/edit as a
first project.

- Project: `orbit-shift`, type `web`, entry `index.html`.
- One self-contained HTML/CSS/JavaScript file: no CDN, third-party framework, remote image/font,
  external request or package install.
- The game runs in CodeC's existing local web preview, so the first play works offline and does not
  wait for the optional Linux userland.
- Touch targets select INNER/OUTER; arrows/I/O and Space work with a keyboard. Score, shields, a
  best-score record stored locally when available, visible instructions, and `aria-live` status text
  are provided.
- A seed marker makes the project a one-time sample. It is never rewritten, and deleting it does not
  make it silently reappear if the user later replays the intro.
- Existing `snake/` projects are not moved, deleted or overwritten. The legacy `SnakeSample` source
  remains for historical compatibility but is no longer part of new-user routing.

No external image asset was needed: the Android illustration is drawn with Compose Canvas and the
playable sample draws its own art. This avoids network dependencies and keeps the first-run APK
asset footprint small.

## Historical Orbit candidate verification notes and proposed device checklist

Host/source tests cover the first-run gate and ordering, privacy checkbox, one-time seed/deletion
rules, standalone/offline sample, touch/keyboard controls, responsive page height, shared
motion/tokens and Settings copy. CI/Android build is the executor for Compose compilation and lint.

**Workspace status at authoring:** `git diff --check`, embedded JavaScript syntax check and a Node
fake-DOM smoke test passed (initial state, touch-button lane selection, start-lane preservation,
keyboard input). The command `./gradlew :app:testDebugUnitTest --no-daemon` could not start: this
workspace has no `java` executable or `JAVA_HOME`. No Kotlin/Compose build, JUnit suite, lint, APK
or device run has passed yet. Re-run them on a JDK 17 Android build host before treating the
implementation as verified.

A real-device round is still valuable; do not infer a handset pass from host tests. Check:

1. Fresh install: branded splash releases into the intro without a second blank/loading frame.
2. Back/Next and system Back, page count, animation and reduced-motion setting; verify text
   scaling/scroll on a short window.
3. Skip jumps to the privacy agreement, but the start action is disabled until the box is checked;
   **no** runtime permission dialog appears during onboarding.
4. Agree: one `Orbit Shift` project is created and the editor opens on `index.html`; tap RUN and
   play without network/setup.
5. Use both lane buttons and keyboard controls; background/reopen, resize and try airplane mode.
6. Settings → About replay; verify an existing Orbit file remains unchanged, a deleted Orbit project
   stays deleted, and an existing Snake project remains untouched.

## Files referenced by the archived Orbit proposal

- `app/src/main/java/com/codeci/ide/ui/screens/FirstRunIntroScreen.kt` — native intro, privacy
  details/acknowledgement, motion/accessibility.
- `app/src/main/java/com/codeci/ide/ui/projects/OrbitSample.kt` — idempotent sample seeding and the
  offline game.
- `app/src/main/java/com/codeci/ide/MainActivity.kt` — first-run gate, acceptance ordering, editor
  route.
- Settings/About/search, `CodecMotion`, startup/game tests, README and this record.
