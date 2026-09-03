package com.codeci.ide

import com.codeci.ide.ui.services.InteractiveInputBuffer
import com.codeci.ide.ui.viewmodels.OutputRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 23.1 — the inline stdin line. [OutputRunState] defaults pin that the
 * panel shows no input field and holds no text until a program actually
 * waits for input, and [InteractiveInputBuffer] pins the submit semantics
 * the ViewModel delegates to: typing updates the line, submit returns the
 * line and clears it, and an empty line sends nothing.
 */
class InteractiveInputBufferTest {

    @Test
    fun `output state is not waiting for input by default`() {
        assertFalse(OutputRunState().waitingForInput)
    }

    @Test
    fun `output state input buffer is empty by default`() {
        assertEquals("", OutputRunState().inputBuffer)
    }

    @Test
    fun `onChange updates the current input line`() {
        val buffer = InteractiveInputBuffer()
        buffer.onChange("Alice")
        assertEquals("Alice", buffer.current())
    }

    @Test
    fun `submit returns the line and clears the buffer`() {
        val buffer = InteractiveInputBuffer("Bob")
        assertEquals("Bob", buffer.submit())
        assertEquals("", buffer.current())
    }

    @Test
    fun `submit of an empty buffer sends nothing`() {
        val buffer = InteractiveInputBuffer()
        assertNull(buffer.submit())
    }

    @Test
    fun `submit keeps whitespace-only lines as typed`() {
        val buffer = InteractiveInputBuffer("   ")
        assertEquals("   ", buffer.submit())
    }

    @Test
    fun `waitingForInput survives a line append via copy`() {
        val state = OutputRunState(waitingForInput = true, inputBuffer = "a")
        val next = state.copy(inputBuffer = "a\t")
        assertTrue(next.waitingForInput)
        assertEquals("a\t", next.inputBuffer)
    }
}
