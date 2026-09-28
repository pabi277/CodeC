package com.codeci.ide

import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.ComponentActivity
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Device round 2026-09-29 (owner: *"in a python code I write p, it shows
 * print(, I click it and it is on the screen — after that when I write the
 * next letter the print( is gone and only p + the letters I typed remain"*).
 *
 * The mechanism, run against the REAL sora 0.24.6 `CodeEditor` and its own
 * `EditorInputConnection` (what Gboard talks to), not a model of it:
 *
 *  1. a soft keyboard types a word as COMPOSING text — `setComposingText("p")`;
 *     sora remembers the word as a range (`ComposingText`, 0..1);
 *  2. the accept replayed `rint(` into the buffer with one `Content.replace`
 *     (the 2026-09-13 incremental path) — behind the input method's back;
 *  3. sora's range grew over the insert because it touched the range's end
 *     (`ComposingText.shiftOnInsert`: `startIndex <= insertStart && endIndex >=
 *     insertStart`) — the accepted text became part of the IME's word;
 *  4. the next letter arrived as `setComposingText("pr")` — the IME's own
 *     word — and sora replaced the whole range with it
 *     (`setComposingTextCompat`: `print(` starts with `pr`, so the tail was
 *     deleted). The VM never held `pr`; sora did, and the listener pushed it.
 *
 * The fix is sora's own discipline for a programmatic edit while composing
 * (`EditorAutoCompletion.select()` brackets `performCompletion` the same way):
 * `restartInput()` before the edit and after it. The first case pins the
 * mechanism (so a sora upgrade that changes it is noticed), the others pin
 * the bracket the host now applies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposingReplayTest {

    private fun editor(): Pair<CodeEditor, InputConnection> {
        // A real window, as in the app: the editor is a content view of an
        // Activity (the same ComponentActivity the compose rule uses).
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val editor = CodeEditor(activity)
        // The stock panel would start completion threads on every insert;
        // the app replaces it anyway (CodeCCompletionComponent), and the
        // mechanism under test is the input connection, not the panel.
        editor.getComponent(EditorAutoCompletion::class.java).isEnabled = false
        activity.setContentView(editor)
        editor.setText("")
        awaitEditable(editor)
        val connection = editor.onCreateInputConnection(EditorInfo())
        assertTrue("sora hands out its input connection once editable", connection != null)
        return editor to connection
    }

    /**
     * sora measures a new layout on a worker and clears `layoutBusy` with a
     * post to the main looper (LineBreakLayout.measureAllLines → TaskMonitor →
     * EditorHandler); until then `isEditable()` is false and the connection
     * refuses input — exactly what a real IME would meet. Idle the looper
     * until the editor is editable.
     */
    private fun awaitEditable(editor: CodeEditor) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!editor.isEditable && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue("sora's layout settles (layoutBusy=false) before the IME can type", editor.isEditable)
    }

    private fun settle(editor: CodeEditor) {
        shadowOf(Looper.getMainLooper()).idle()
        awaitEditable(editor)
    }

    @Test
    fun `mechanism - sora grows the composing word of the IME over an insert at its end and the next composing update replaces it all`() {
        val (editor, ime) = editor()
        ime.setComposingText("p", 1)
        assertEquals("p", editor.text.toString())
        assertTrue("Gboard composes the word it is typing", editor.hasComposingText())

        // The host's replay of an accepted `print(` — one delta, no restart.
        editor.text.replace(1, 1, "rint(")
        assertEquals("print(", editor.text.toString())
        settle(editor)

        // The IME's next letter: ITS word is `pr`.
        ime.setComposingText("pr", 1)
        assertEquals("the owner's symptom", "pr", editor.text.toString())
    }

    @Test
    fun `fix - restartInput before and after the replay keeps the accepted text and the next letter follows it`() {
        val (editor, ime) = editor()
        ime.setComposingText("p", 1)
        assertTrue(editor.hasComposingText())

        // The host's bracket (SoraEditorHost, `composing`).
        editor.restartInput()
        assertFalse("sora's composing range is dropped before the edit", editor.hasComposingText())
        editor.text.replace(1, 1, "rint(")
        editor.setSelection(0, 6)
        editor.restartInput()
        assertEquals("print(", editor.text.toString())
        assertEquals(6, editor.cursor.left)
        settle(editor)

        // A restarted IME begins a fresh word after the caret.
        ime.setComposingText("r", 1)
        assertEquals("print(r", editor.text.toString())
    }

    @Test
    fun `fix - even a stale composing update from before the restart cannot take the accepted text away`() {
        val (editor, ime) = editor()
        ime.setComposingText("p", 1)
        editor.restartInput()
        editor.text.replace(1, 1, "rint(")
        editor.setSelection(0, 6)
        editor.restartInput()
        settle(editor)

        // The IME had not yet processed the restart and still sent its old
        // word: the worst case is two extra letters after the accepted text —
        // never the accepted text replaced.
        ime.setComposingText("pr", 1)
        assertTrue(editor.text.toString().startsWith("print("))
        assertEquals("print(pr", editor.text.toString())
    }

    @Test
    fun `no composing text - a hardware or CodeC Keys edit needs no restart and is left alone`() {
        val (editor, ime) = editor()
        ime.commitText("p", 1)
        assertFalse("committed text is not composing", editor.hasComposingText())
        editor.text.replace(1, 1, "rint(")
        editor.setSelection(0, 6)
        settle(editor)
        ime.commitText("r", 1)
        assertEquals("print(r", editor.text.toString())
    }
}
