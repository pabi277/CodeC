package com.codeci.ide

import com.codeci.ide.ui.theme.AppThemeMode
import com.codeci.ide.ui.theme.ThemeManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 24.8 — AUTO/DARK/LIGHT theme decision is pure and host-testable. */
class ThemeManagerTest {

    @Test
    fun `AUTO follows the system both ways`() {
        assertTrue(ThemeManager.effectiveDark(AppThemeMode.SYSTEM, systemDark = true))
        assertFalse(ThemeManager.effectiveDark(AppThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun `DARK stays dark even when system is light`() {
        assertTrue(ThemeManager.effectiveDark(AppThemeMode.DARK, systemDark = false))
    }

    @Test
    fun `LIGHT stays light even when system is dark`() {
        assertFalse(ThemeManager.effectiveDark(AppThemeMode.LIGHT, systemDark = true))
    }
}
