package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 48 — the one-owner pin (PART_48_1 §Tests): exactly ONE
 * `ensurePositionVisible(` call site exists in the whole app, it lives in
 * `SoraEditorHost`, and it is performed the way the spec demands — inside a
 * `post`/`postDelayed` (never inline against the old layout metrics),
 * wrapped in `runCatching` (a cosmetic call must never kill the app), with
 * `noAnimation = true` (a chrome-driven scroll must not animate).
 *
 * A second caller would mean two owners of the scroll position, which is
 * how scroll bugs become unfixable.
 */
class CaretCallSiteTest {

    private fun sources(): List<File> = RepoFiles.mainKotlinSources()

    @Test
    fun `exactly one ensurePositionVisible call site exists in the app`() {
        val hits = sources().filter { it.readText().contains("ensurePositionVisible(") }
        assertEquals(
            "ensurePositionVisible must have exactly one owner in app/src/main, found in: " +
                hits.map { it.name },
            listOf("SoraEditorHost.kt"),
            hits.map { it.name }
        )
    }

    @Test
    fun `the call site is scheduled, guarded and unanimated`() {
        val host = sources().first { it.name == "SoraEditorHost.kt" }.readText()
        val body = host.substringAfter("fun scheduleCaretRescroll")
        assertTrue(
            "the rescroll must run after a layout pass (postDelayed), not inline",
            body.contains("editor.postDelayed(task,")
        )
        assertTrue(
            "the rescroll must be crash-proof (runCatching)",
            body.contains("runCatching {")
        )
        // Phase 69.1 — the pin MOVED with its reason: the single call now
        // passes the reveal target the pure policy computed (the caret's line
        // plus the air), not the raw caret pair. Still one call, still
        // unanimated (`true`).
        assertTrue(
            "the rescroll must stay unanimated (noAnimation = true)",
            body.contains("editor.ensurePositionVisible(targetLine, targetColumn, true)")
        )
        assertTrue(
            "the rescroll must coalesce (cancel-and-replace the pending task)",
            body.contains("editor.removeCallbacks(it)")
        )
    }

    @Test
    fun `the owner asks through the pure policy for one line of air`() {
        // Phase 69.1 — the intent (KEEP_LINES_BELOW) was declared in 48 and is
        // paid here: the target is decided by the pure policy and clamped
        // against the LIVE buffer, because the posted task can outlive the
        // buffer it was scheduled from.
        val host = sources().first { it.name == "SoraEditorHost.kt" }.readText()
        val body = host.substringAfter("fun scheduleCaretRescroll").substringBefore("AndroidView(")
        assertTrue(
            "the reveal line must come from the policy (the air rule)",
            body.contains("CaretVisibilityPolicy.revealLine(line, editor.text.lineCount)")
        )
        assertTrue(
            "the reveal column must come from the policy (clamped to the target line)",
            body.contains("CaretVisibilityPolicy.revealColumn(") &&
                body.contains("editor.text.getColumnCount(targetLine)")
        )
        // The air comes from the VIEWPORT, never from the caret or the buffer.
        assertFalse(
            "the rescroll must never move the caret",
            body.contains("setSelection(") || body.contains("setSelectionRegion(")
        )
        assertFalse(
            "the rescroll must never write the buffer",
            body.contains("text.replace(") || body.contains(".setText(")
        )
    }

    @Test
    fun `the edge observes resizes through the pure policy`() {
        val host = sources().first { it.name == "SoraEditorHost.kt" }.readText()
        assertTrue(host.contains("onSizeChanged { size ->"))
        assertTrue(host.contains("CaretVisibilityPolicy.owesRescroll(previous, vp)"))
        assertTrue(host.contains("CaretVisibilityPolicy.debounceMs(previous, vp)"))
        // The replay-follow half of 5.A ("accept a suggestion and it goes
        // down") rides the same one owner, not a second call site.
        assertEquals(
            "the replay paths must schedule through the one owner",
            2,
            Regex("scheduleCaretRescroll\\(endPos\\.line, endPos\\.column, 0L\\)").findAll(host).count()
        )
    }

    @Test
    fun `the screen hands over the chrome facts`() {
        val screen = RepoFiles.mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt"
        ).readText()
        assertTrue(screen.contains("imeVisible = imeVisible,"))
        assertTrue(screen.contains("codecKeysVisible = codecKeysUp,"))
        assertTrue(screen.contains("stripVisible = keysVisible,"))
        assertTrue(screen.contains("outputExpanded = outputExpanded,"))
        assertTrue(
            screen.contains("statusVisible = caretPlaced && !imeVisible && !codecKeysUp,")
        )
    }
}
