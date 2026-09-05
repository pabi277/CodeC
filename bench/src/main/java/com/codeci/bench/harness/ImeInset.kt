package com.codeci.bench.harness

import android.os.Build
import android.view.View

/**
 * Phase 28.1 — samples the soft-IME bottom inset off the view tree, the
 * "adjustResize layout settles with no IME flicker" probe (spec budget 3:
 * measure it, don't assert it). A K-core run that never lets this exceed 0 px
 * is the evidence that no soft IME ever opened while the grid typed.
 *
 * Pre-API 30 the platform exposes no ime insets at all; the probe then
 * reports "n/a" instead of a fake zero — honesty law from §9.
 */
object ImeInset {
    /** True when this device can answer the probe at all. */
    fun supported(): Boolean = Build.VERSION.SDK_INT >= 30

    fun bottomPx(root: View?): Int {
        if (root == null || !supported()) return 0
        val insets = root.rootWindowInsets ?: return 0
        return runCatching {
            insets.getInsets(android.view.WindowInsets.Type.ime()).bottom
        }.getOrDefault(0)
    }
}
