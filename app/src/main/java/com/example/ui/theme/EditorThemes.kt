package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class EditorThemeType {
    MONOKAI, DRACULA, GITHUB_DARK
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
        EditorThemeType.MONOKAI -> MonokaiTheme
        EditorThemeType.DRACULA -> DraculaTheme
        EditorThemeType.GITHUB_DARK -> GitHubDarkTheme
    }
}
