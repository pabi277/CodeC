package com.codeci.ide

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

    /**
     * The DEVICE crash's exact shape (2026-09-06 record): navigating INTO the
     * editor through the NavHost AnimatedContent transition (slide+fade
     * enter/exit), a draw-driven remeasure measures the editor Column's child
     * after it was detached — IllegalStateException "LayoutNode should be
     * attached to an owner" (compose 1.7.1, BOM 2024.09.00). This test drives
     * a REAL NavHost transition frame by frame (autoAdvance off) so
     * recomposition + measure + layout interleave exactly as on device. The
     * BOM bump to 2024.12.01 (compose 1.7.6) is the fix candidate; this test
     * is the regression pin for that class of bug.
     */
    @Test
    fun `navigating into the editor through a nav transition measures without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = ProjectManager(context)
            .createProject("repro2", includeStarter = false)
            .getOrThrow()
        File(info.root, "bench.c").writeText(
            "#include <stdio.h>\nint main(void) { return 0; }\n"
        )

        try {
            compose.mainClock.autoAdvance = false
            var navRef: NavHostController? = null
            compose.setContent {
                MyApplicationTheme {
                    val nav = rememberNavController().also { navRef = it }
                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            Box(Modifier.fillMaxSize()) { Text("home") }
                        }
                        composable(
                            route = "editor?projectName={projectName}&fileName={fileName}",
                            arguments = listOf(
                                navArgument("projectName") { nullable = true },
                                navArgument("fileName") { nullable = true }
                            )
                        ) { entry ->
                            EditorScreen(
                                projectName = entry.arguments?.getString("projectName"),
                                fileName = entry.arguments?.getString("fileName")
                            )
                        }
                    }
                }
            }
            repeat(5) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
            compose.runOnIdle { navRef!!.navigate("editor?projectName=repro2&fileName=bench.c") }
            // Step the whole transition (~700 ms) frame by frame; each frame
            // runs recomposition, measure and layout while background
            // dispatchers (TextMate warm-up, language creation, analysis)
            // deliver results back to the main thread.
            repeat(60) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                Thread.sleep(30)
            }
            compose.mainClock.autoAdvance = true
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
                "Nav-transition-into-editor crashed: ${t.javaClass.name}: ${t.message}\n$trace\n" +
                    "(+${dropped[0]} boilerplate frames dropped)", t
            )
        }
    }
}
