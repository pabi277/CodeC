package com.codeci.ide

import com.codeci.ide.ui.viewmodels.WebPreviewViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PreviewDiagnosticsTest {
    @Test fun `replacement and disposed sessions reject late callbacks`() {
        val vm = WebPreviewViewModel()
        val old = vm.beginSession()
        vm.addConsole(old, "log", "old", 1)
        val current = vm.beginSession()
        vm.addConsole(old, "error", "late", 1)
        vm.addRequest(old, "GET", "https://example.org/old", true)
        assertTrue(vm.console.value.isEmpty())
        assertTrue(vm.network.value.isEmpty())
        vm.endSession(old) // Old view disposal must not end the replacement session.
        vm.addConsole(current, "info", "current", 2)
        assertEquals("current", vm.console.value.single().message)
        vm.endSession(current)
        vm.addConsole(current, "log", "disposed", 3)
        assertEquals(1, vm.console.value.size)
    }
    @Test fun `worker requests are bounded and clear is independent of console`() {
        val vm = WebPreviewViewModel()
        val token = vm.beginSession()
        val workers = (0..3).map { worker ->
            Thread { repeat(100) { vm.addRequest(token, "GET", "https://example.org/$worker/$it", false) } }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join() }
        assertEquals(200, vm.network.value.size)
        vm.addConsole(token, "warn", "keep", 0)
        vm.clearNetwork()
        assertTrue(vm.network.value.isEmpty())
        assertEquals(1, vm.console.value.size)
        vm.clearConsole()
        assertTrue(vm.console.value.isEmpty())
    }
}
