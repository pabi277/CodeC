package com.codeci.ide.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class EditorThemeType(val displayName: String) {
    // Phase 29.1 — VS Code Dark+ is the DEFAULT editor theme (owner 2026-09-05:
    // "colour is very bad"; the exit condition is "looks like VS Code Dark+").
    // First in the enum = first in the Settings picker.
    VS_CODE_DARK_PLUS("VS Code Dark+"),
    MONOKAI("Monokai"),
    DRACULA("Dracula"),
    GITHUB_DARK("GitHub Dark");
}

data class EditorThemeColors(
    val background: Color,
    val text: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val function: Color,
    val operator: Color
)

/**
 * Phase 29.1 — the Compose-side palette for VS Code Dark+. This drives the
 * NON-editor surfaces (ghost text color, settings theme preview, status
 * accents); the editor itself colours through the TextMate theme asset
 * `textmate/themes/dark-plus.json` (the flattened vscode dark_vs+dark_plus).
 * The values below mirror that JSON so both surfaces agree.
 */
val VSCodeDarkPlusTheme = EditorThemeColors(
    background = Color(0xFF1E1E1E),
    text = Color(0xFFD4D4D4),
    keyword = Color(0xFF569CD6),
    string = Color(0xFFCE9178),
    comment = Color(0xFF6A9955),
    number = Color(0xFFB5CEA8),
    function = Color(0xFFDCDCAA),
    operator = Color(0xFFD4D4D4)
)

val MonokaiTheme = EditorThemeColors(
    background = Color(0xFF272822),
    text = Color(0xFFF8F8F2),
    keyword = Color(0xFFF92672),
    string = Color(0xFFE6DB74),
    comment = Color(0xFF75715E),
    number = Color(0xFFAE81FF),
    function = Color(0xFFA6E22E),
    operator = Color(0xFFF8F8F2)
)

val DraculaTheme = EditorThemeColors(
    background = Color(0xFF282A36),
    text = Color(0xFFF8F8F2),
    keyword = Color(0xFFFF79C6),
    string = Color(0xFFF1FA8C),
    comment = Color(0xFF6272A4),
    number = Color(0xFFBD93F9),
    function = Color(0xFF50FA7B),
    operator = Color(0xFFFF79C6)
)

val GitHubDarkTheme = EditorThemeColors(
    background = Color(0xFF24292E),
    text = Color(0xFFE1E4E8),
    keyword = Color(0xFFF97583),
    string = Color(0xFF9ECBFF),
    comment = Color(0xFF6A737D),
    number = Color(0xFF79B8FF),
    function = Color(0xFFB392F0),
    operator = Color(0xFFF97583)
)

fun getEditorTheme(type: EditorThemeType): EditorThemeColors {
    return when (type) {
        EditorThemeType.VS_CODE_DARK_PLUS -> VSCodeDarkPlusTheme
        EditorThemeType.MONOKAI -> MonokaiTheme
        EditorThemeType.DRACULA -> DraculaTheme
        EditorThemeType.GITHUB_DARK -> GitHubDarkTheme
    }
}

enum class TerminalThemeType {
    DRACULA, MONOKAI, GITHUB_DARK, CLASSIC_DARK
}

data class TerminalThemeColors(
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val selection: Color = Color(0x6680CBC4),
    val topBarBackground: Color = Color(0xFF1E1E1E),
    val backgroundRgb: Int = 0x121212,
    val foregroundRgb: Int = 0xE5E5E5
)

val ClassicDarkTerminalTheme = TerminalThemeColors(
    background = Color(0xFF121212),
    foreground = Color(0xFFE5E5E5),
    cursor = Color(0xFF55FF55),
    selection = Color(0x6680CBC4),
    topBarBackground = Color(0xFF1E1E1E),
    backgroundRgb = 0x121212,
    foregroundRgb = 0xE5E5E5
)

val DraculaTerminalTheme = TerminalThemeColors(
    background = Color(0xFF282A36),
    foreground = Color(0xFFF8F8F2),
    cursor = Color(0xFF50FA7B),
    selection = Color(0x6644475A),
    topBarBackground = Color(0xFF21222C),
    backgroundRgb = 0x282A36,
    foregroundRgb = 0xF8F8F2
)

val MonokaiTerminalTheme = TerminalThemeColors(
    background = Color(0xFF272822),
    foreground = Color(0xFFF8F8F2),
    cursor = Color(0xFFA6E22E),
    selection = Color(0x6649483E),
    topBarBackground = Color(0xFF1E1F1C),
    backgroundRgb = 0x272822,
    foregroundRgb = 0xF8F8F2
)

val GitHubDarkTerminalTheme = TerminalThemeColors(
    background = Color(0xFF24292E),
    foreground = Color(0xFFE1E4E8),
    cursor = Color(0xFF79B8FF),
    selection = Color(0x663B4048),
    topBarBackground = Color(0xFF1F2428),
    backgroundRgb = 0x24292E,
    foregroundRgb = 0xE1E4E8
)

fun getTerminalTheme(type: TerminalThemeType): TerminalThemeColors {
    return when (type) {
        TerminalThemeType.DRACULA -> DraculaTerminalTheme
        TerminalThemeType.MONOKAI -> MonokaiTerminalTheme
        TerminalThemeType.GITHUB_DARK -> GitHubDarkTerminalTheme
        TerminalThemeType.CLASSIC_DARK -> ClassicDarkTerminalTheme
    }
}

