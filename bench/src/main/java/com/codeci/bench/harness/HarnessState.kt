package com.codeci.bench.harness

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Phase 25.1 — activity-level holder wired between the candidates and the
 * scenario runner. Candidates attach a [TypingTarget] while they are on
 * screen; the runner consumes whichever target is attached.
 */
class HarnessState {

    /** The activity's content root, for event dispatch + coordinate math. */
    var rootView: View? = null

    /** The activity window, for FrameMetrics capture. */
    var window: android.view.Window? = null

    var attachedTarget: TypingTarget? = null
        private set

    var attachedCandidate: String? = null
        private set

    /** Chosen input mode for the next scenario run (owner-selectable). */
    var inputMode by mutableStateOf(InputMode.KEY_EVENTS)

    fun attach(target: TypingTarget, candidate: String) {
        attachedTarget = target
        attachedCandidate = candidate
    }

    fun detach() {
        attachedTarget = null
        attachedCandidate = null
    }

    /** APK size of the bench itself, for the decision table's size row context. */
    fun apkSizeMb(): Double? {
        val context = rootView?.context ?: return null
        return runCatching {
            java.io.File(context.applicationInfo.sourceDir).length() / (1024.0 * 1024.0)
        }.getOrNull()
    }
}
