package com.codeci.ide

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.projects.EditorLaunchState
import com.codeci.ide.ui.projects.ProjectManager
import com.codeci.ide.ui.screens.EditorScreen
import com.codeci.ide.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 29 device-round reproduction attempt (2026-09-06).
 *
 * The owner's device crashes on opening a file or the direct editor: a
 * main-thread exception during a MEASURE pass (visible stack tail:
 * AnimatedContent → PaddingValues → …, thrown inside the editor screen's
 * subtree; the exception header was lost — the header-first crash-log fix
 * shipped in the same phase addresses that for future device reports).
 *
 * This test composes the REAL [EditorScreen] with a real project file
 * open — the screen builds its own EditorViewModel, sora CodeEditor,
 * ThemeManager/SettingsManager, TextMate language + scheme effects, and
 * the VM's file-open + text replay — and drives composition, measure and
 * layout frames plus the compose clock. If the crash is in Compose/editor
 * measure logic (pure JVM), it reproduces here with the full stack in the
 * CI failure. Either way it stays as the editor-screen integration smoke
 * for future phases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorLaunchMeasureReproTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `editor screen with an open file measures without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = ProjectManager(context)
            .createProject("repro", includeStarter = false)
            .getOrThrow()
        File(info.root, "bench.c").writeText(
            """
            #include <stdio.h>

            int main(void) {
                const char *msg = "hello"; /* a comment */
                int count = 42;
                for (int i = 0; i < count; i++) {
                    printf("%s %d\n", msg, i);
                }
                return 0;
            }
            """.trimIndent()
        )
        EditorLaunchState.save(context, "repro", "bench.c")

        // The failure's STACK is the diagnosis, and CI annotations (the only
        // channel readable from this sandbox) carry a short message — so on
        // any crash, rethrow with a COMPRESSED trace (boilerplate reflect /
        // junit / robolectric runner frames dropped) embedded in the message.
        try {
            compose.setContent {
                MyApplicationTheme {
                    EditorScreen(projectName = "repro", fileName = "bench.c")
                }
            }
            // Let composition, measure, layout and the LaunchedEffects run:
            // language creation (awaits TextMate warm-up on Dispatchers.Default),
            // scheme application, VM file open + text replay into the editor,
            // async analysis style deliveries back to the main thread.
            repeat(10) {
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(500)
            }
            compose.waitForIdle()
        } catch (t: Throwable) {
            val dropped = intArrayOf(0)
            val trace = android.util.Log.getStackTraceString(t).lineSequence()
                .filter { line ->
                    if (!line.startsWith("\tat ")) return@filter !line.startsWith("\tat ")
                    val boilerplate = line.contains("java.base/") ||
                        line.contains("org.junit.") ||
                        line.contains("org.robolectric") ||
                        line.contains("java.lang.reflect") ||
                        line.contains("androidx.test") ||
                        line.contains("android.app.Instrumentation")
                    if (boilerplate) { dropped[0]++; false } else true
                }
                .take(90)
                .joinToString("\n")
            throw AssertionError(
                "Editor-screen measure crashed: ${t.javaClass.name}: ${t.message}\n$trace\n" +
                    "(+${dropped[0]} boilerplate frames dropped)", t
            )
        }
    }
}
