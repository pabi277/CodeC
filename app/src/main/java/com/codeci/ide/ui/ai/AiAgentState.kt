package com.codeci.ide.ui.ai

/*
 * Phase 88 (Level 11, 88.4) — two plain state types moved here, unchanged, from
 * `AiViewModel.kt`, so the pure progress policy (`AiLevel11Policies.kt`) and its
 * host test compile without the Android-bound ViewModel. Same package, same
 * names, same fields: no caller changes. Display state only, in memory (D6).
 */

/** Where one helper exchange stands. */
enum class AiPhase { IDLE, PREVIEW, STREAMING, DONE, FAILED }

/** Phase 80 — a snapshot of the task's usage counters (in memory, display only). */
data class AiAgentUsage(
    val turns: Int,
    val toolCalls: Int,
    val runs: Int,
    /** Phase 84 (fix 3): refused blocks, counted separately from executions. */
    val refused: Int = 0,
    /** Phase 84 (fix 3): duplicate reads served from a working set (0 until Level 9). */
    val reused: Int = 0,
    /**
     * Phase 87 (Level 10, 87.6): the caps this task actually ran under. They
     * equal the constants unless the user accepted one budget extension, and
     * the counter must render against them, not against the constants.
     */
    val turnCap: Int = AiAgentLimits.MAX_TURNS,
    val readCap: Int = AiAgentLimits.MAX_TOOL_CALLS
)
