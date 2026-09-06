package com.codeci.ide

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.projects.EditorLaunchState
import com.codeci.ide.ui.projects.ProjectManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 29 device-round reproduction attempt (2026-09-06).
 *
 * The owner's device crashes on opening a file or the direct editor: a
 * main-thread exception during a MEASURE pass (visible stack tail:
 * AnimatedContent → PaddingValues → …, thrown inside the editor screen's
 * subtree; the exception header was lost — see the crash-log fix shipped
 * with this test).
 *
 * The app's "open where I left off" launches STRAIGHT into the editor when
 * a last-open project file exists. This test seeds that state, launches the
 * real [MainActivity], and drives measure/layout frames while idling the
 * main looper (Compose effects, style deliveries) and yielding to the
 * background dispatchers (TextMate warm-up, language creation, analysis) —
 * the exact machinery a file open runs on device. If the crash is in
 * Compose/editor measure logic (pure JVM), it reproduces here with the full
 * stack in the failure. Either way this stays as the cold-start-into-editor
 * integration smoke for future phases.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorLaunchMeasureReproTest {

    @Test
    fun `cold start into last-open file measures the editor without crashing`() {
        // The failure's STACK is the diagnosis, and CI annotations (the only
        // channel readable from this sandbox) carry just the message — so on
        // any crash, rethrow with the trace embedded in the message.
        try {
            driveEditorLaunchAndMeasure()
        } catch (t: Throwable) {
            val trace = android.util.Log.getStackTraceString(t).lineSequence()
                .take(60).joinToString("\n")
            throw AssertionError(
                "Editor-launch measure crashed: ${t.javaClass.name}: ${t.message}\n$trace", t
            )
        }
    }

    private fun driveEditorLaunchAndMeasure() {
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

        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        val content = controller.get().findViewById<ViewGroup>(android.R.id.content)

        // Drive frames: idle main (composition effects, analyzer style
        // deliveries posted to the UI thread) → measure + layout → give the
        // background dispatchers (grammar warm-up, language creation,
        // async analysis) a moment → repeat. The editor receives its
        // TextMate language, its color scheme, and the VM's text replay
        // across these iterations — the same interleaving as a real open.
        repeat(12) {
            shadowOf(Looper.getMainLooper()).idle()
            content.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY)
            )
            content.layout(0, 0, 1080, 2340)
            Thread.sleep(150)
            shadowOf(Looper.getMainLooper()).idle()
        }

        // Rotation-like resize AFTER the analyzer has settled — forces a
        // full remeasure with fresh constraints on the composed editor.
        content.measure(
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY)
        )
        content.layout(0, 0, 720, 1600)
        repeat(3) {
            Thread.sleep(150)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
