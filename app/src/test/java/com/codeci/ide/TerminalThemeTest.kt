package com.codeci.ide

import com.codeci.ide.ui.theme.ClassicDarkTerminalTheme
import com.codeci.ide.ui.theme.DraculaTerminalTheme
import com.codeci.ide.ui.theme.GitHubDarkTerminalTheme
import com.codeci.ide.ui.theme.MonokaiTerminalTheme
import com.codeci.ide.ui.theme.TerminalThemeType
import com.codeci.ide.ui.theme.getTerminalTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TerminalThemeTest {

    @Test
    fun `all terminal theme types map to non-null color palettes`() {
        for (themeType in TerminalThemeType.values()) {
            val themeColors = getTerminalTheme(themeType)
            assertNotNull(themeColors)
            assertNotNull(themeColors.background)
            assertNotNull(themeColors.foreground)
            assertNotNull(themeColors.cursor)
            assertNotNull(themeColors.selection)
        }
    }

    @Test
    fun `terminal theme mappings resolve correctly`() {
        assertEquals(DraculaTerminalTheme, getTerminalTheme(TerminalThemeType.DRACULA))
        assertEquals(MonokaiTerminalTheme, getTerminalTheme(TerminalThemeType.MONOKAI))
        assertEquals(GitHubDarkTerminalTheme, getTerminalTheme(TerminalThemeType.GITHUB_DARK))
        assertEquals(ClassicDarkTerminalTheme, getTerminalTheme(TerminalThemeType.CLASSIC_DARK))
    }

    @Test
    fun `terminal themes provide distinct rgb values`() {
        assertEquals(0x282a36, DraculaTerminalTheme.backgroundRgb)
        assertEquals(0xf8f8f2, DraculaTerminalTheme.foregroundRgb)

        assertEquals(0x272822, MonokaiTerminalTheme.backgroundRgb)
        assertEquals(0xf8f8f2, MonokaiTerminalTheme.foregroundRgb)

        assertEquals(0x24292e, GitHubDarkTerminalTheme.backgroundRgb)
        assertEquals(0xe1e4e8, GitHubDarkTerminalTheme.foregroundRgb)

        assertEquals(0x121212, ClassicDarkTerminalTheme.backgroundRgb)
        assertEquals(0xe5e5e5, ClassicDarkTerminalTheme.foregroundRgb)
    }
}
