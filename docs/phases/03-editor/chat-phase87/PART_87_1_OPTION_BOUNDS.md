# CodeC Phase 87.1 — Option bounds and storage

> **Status:** 📋 PLANNED · **Cost:** `[client-only]` · **Effort:** M
> **Owner row (verbatim):** *"more options to use in the app for better optimization."*
> **Parent brief:** [Phase 87 README](README.md)

## First move: evidence, not code

Read 2026-10-03 against `main` @ `6838ea6`.

- There is no `AiOptions` type today. `grep -rn "AiOptions" app/src/main` returns nothing.
- Non-secret AI settings already have a home: `AiKeyStore.kt:189-197` declares `PROP_MODEL`,
  `PROP_TERMS`, `PROP_ACCEPTED_AT`, `PROP_NVIDIA_MODEL`, `PROP_NVIDIA_TERMS`,
  `PROP_NVIDIA_ACCEPTED_AT`, `PROP_BUBBLE_POS`, `PROP_BUBBLE_SHOW`, `PROP_SHEET_OUTPUT`.
- The existing accessors are thin and delegate their parsing to pure policy:
  `AiKeyStore.kt:58` `bubble()` → `AiBubblePolicy.decode(...)`; `:70` `outputConflict()` →
  `AiSheetPolicy.decodeConflict(...)`. Neither throws on a bad stored string.
- The two-option precedent for a stored choice is `AiSheetPolicy.AiOutputConflict`
  (`REPLACE_OUTPUT` / `OPEN_FULL`) with the comment recording that the owner picked one on the
  device.
- The UI slot is `AiHome.kt:169-182`, where `Switch(checked = state.showBubble, …)` already
  renders an AI preference, fed from `EditorScreen.kt:1732`.

So this part adds nothing new to the storage *mechanism* — only entries, one policy, and rows.

## Design

New pure file `ui/ai/AiOptionsPolicy.kt`. Android-free, no `@Composable`, no `File`, no
DataStore import — the same shape as `AiBubblePolicy.kt` and `AiSheetPolicy.kt`.

```kotlin
enum class AiAnswerDetail { BRIEF, NORMAL, THOROUGH }
enum class AiActivityDisplay { COLLAPSED, EXPANDED }
enum class AiBackupMode { OFF, MANUAL }
enum class AiBudgetOffer { OFFER, NO_OFFER }
enum class AiReviewer { OFF, ON }

data class AiOptions(
    val readWindowLines: Int = AiOptionsPolicy.DEFAULT_READ_WINDOW_LINES,
    val workingSetDepth: Int = AiOptionsPolicy.DEFAULT_WORKING_SET_DEPTH,
    val taskMemory: Boolean = true,
    val answerDetail: AiAnswerDetail = AiAnswerDetail.NORMAL,
    val activity: AiActivityDisplay = AiActivityDisplay.COLLAPSED,
    val backup: AiBackupMode = AiBackupMode.OFF,
    val budgetOffer: AiBudgetOffer = AiBudgetOffer.OFFER,
    val reviewer: AiReviewer = AiReviewer.OFF,
)

object AiOptionsPolicy {
    const val MIN_READ_WINDOW_LINES = 50
    const val DEFAULT_READ_WINDOW_LINES = 400
    const val MAX_READ_WINDOW_LINES = 400
    const val MIN_WORKING_SET_DEPTH = 2
    const val DEFAULT_WORKING_SET_DEPTH = 4
    const val MAX_WORKING_SET_DEPTH = 8

    fun clampReadWindow(raw: Int): Int
    fun clampWorkingSetDepth(raw: Int): Int
    fun answerDetail(raw: String?): AiAnswerDetail
    fun activity(raw: String?): AiActivityDisplay
    fun backup(raw: String?): AiBackupMode
    fun budgetOffer(raw: String?): AiBudgetOffer
    fun reviewer(raw: String?): AiReviewer
    fun booleanOn(raw: String?): Boolean
    fun decode(readWindow: String?, depth: String?, memory: String?, detail: String?,
               activity: String?, backup: String?, budget: String?, reviewer: String?): AiOptions
}
```

**`MAX_READ_WINDOW_LINES` must equal `AiToolLimits.MAX_READ_LINES`.** The test asserts the
equality rather than restating `400`, so raising the tool ceiling without raising the option
ceiling — or the reverse — fails the build instead of silently letting the model ask for more
than the runner allows.

`booleanOn` deliberately mirrors the existing convention at `AiKeyStore.kt:64`
(`getProperty(PROP_BUBBLE_SHOW) != "false"` — i.e. absent means **on**), because task memory
defaults to on.

### Storage

Nine new `PROP_*` constants beside `AiKeyStore.kt:197`, following the existing naming:

`PROP_READ_WINDOW`, `PROP_WORKING_SET`, `PROP_TASK_MEMORY`, `PROP_ANSWER_DETAIL`,
`PROP_TOOL_ACTIVITY`, `PROP_BACKUP_MODE`, `PROP_BUDGET_OFFER`, `PROP_REVIEWER`.

That is **eight** properties for **nine** controls, because *Request inspection* (control 6) is
deliberately not stored: it is always on and has no off state to persist. It is rendered as a
non-interactive row in 87.5 so the user can see it cannot be disabled.

Accessors: `fun options(): AiOptions` and one setter per control, each
`synchronized(STORE_LOCK)` exactly like `setShowBubble` at `AiKeyStore.kt:65`.

## The Android edge

- `AiViewModel`: `AiOptions` joins the state; loaded once on init beside
  `AiViewModel.kt:182-183` (`withContext(Dispatchers.IO) { store.showBubble() }`), and one
  setter per control following `AiViewModel.kt:223-225`.
- `AiHome.kt`: the rows render from state and call the VM setters, like
  `onShowBubbleChange` at `:57` / `:182`. `EditorScreen.kt:1725-1740` passes them.
- **No** change to `SettingsScreen.kt` (1 808 lines, zero AI rows) or `SettingsManager.kt`
  (zero AI keys).

This part must not change any behaviour. The values are read and displayed; nothing consumes
them yet. 87.2–87.8 do the consuming, one part per control, so a regression is attributable.

## Exit condition

- All nine controls are visible in the AI panel and persist across process death.
- Every stored value round-trips through `decode` unchanged.
- A corrupt or absent property yields the default, never a crash.
- No production behaviour differs from `6838ea6`.

## Tests (plan)

- `AiOptionsPolicyTest` — **16**: clamp at `MIN-1`, `MIN`, `DEFAULT`, `MAX`, `MAX+1`, and a
  far-out value for both ints; each of the five enums decodes a valid, an unknown and a `null`
  raw string; `booleanOn` for `null`/`"false"`/`"true"`; a full `decode` round-trip.
- `AiLevel10CeilingTest` — **9**: `MAX_READ_WINDOW_LINES == AiToolLimits.MAX_READ_LINES`;
  `MAX_WORKING_SET_DEPTH == 8`; and one case per control asserting no path from the option
  reaches `MAX_TURNS`, `MAX_TOOL_CALLS`, `MAX_BATCH_READS`, `MAX_READ_CHARS` or the run count.
- `AiLevel10WiringTest` — **2** (this part): source pin that `AiKeyStore` declares the eight new
  properties and that `AiHome` renders one row per control.

## Sources (record)

1. Repository: `AiKeyStore.kt:58-77,189-197`, `AiBubblePolicy.kt`, `AiSheetPolicy.kt`,
   `AiHome.kt:57,95,169,182`, `EditorScreen.kt:1725-1740`, `AiViewModel.kt:182-183,223-225` —
   read 2026-10-03 against `main` @ `6838ea6`.

## Deferred / rejected with reasons

- **A new DataStore file** — rejected; the property bag is already backup-excluded and tested.
- **Storing *Request inspection*** — rejected; a persisted "on" implies a persisted "off".
- **Consuming the values in this same part** — rejected; it would make a red CI run ambiguous
  across nine controls.
