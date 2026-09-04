package com.codeci.ide.ui.editor.sora

import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.codeci.ide.ui.theme.EditorThemeColors
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.theme.getEditorTheme
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Phase 25.2 — CodeC editor themes mapped onto sora-editor's color scheme.
 *
 * Sora colors are referenced by SLOT ID (its [EditorColorScheme] holds an
 * id→ARGB table; analyzer spans carry slot ids, not colors), so the adapter
 * overwrites the well-known slots with the CodeC theme palette AFTER the
 * base defaults are applied.
 *
 * ⚠️ Construction order (the 25.2 device crash): sora's CONSTRUCTOR calls
 * `applyDefault()`. A Kotlin override of `applyDefault` that reads a
 * subclass `val` sees NULL there — the property is assigned only after
 * `super()` returns (`getEditorTheme(type)` threw NPE on the owner's device
 * the moment the editor composed). CodeC colors are therefore applied by
 * [apply], called from [of] AFTER construction; `applyDefault` is NOT
 * overridden.
 *
 * Every editor instance needs its OWN scheme object (sora enforces single
 * ownership) — [of] always builds a fresh one.
 */
class CodeCScheme : EditorColorScheme() {

    /** Overwrite the sora defaults with one CodeC theme. Post-construction only. */
    fun apply(colors: EditorThemeColors) {
        for ((id, color) in CodeCThemeMap.entries(colors)) {
            setColor(id, color)
        }
    }

    companion object {

        /** Fresh scheme per call — never assign one object to two editors. */
        fun of(type: EditorThemeType): CodeCScheme =
            CodeCScheme().apply { apply(getEditorTheme(type)) }
    }
}

/**
 * The pure slot→ARGB mapping, host-tested (see CodeCThemeMapTest).
 */
object CodeCThemeMap {

    /**
     * The full slot mapping for one CodeC theme. ARGB ints, no
     * android.graphics — pure Kotlin for CI.
     */
    fun entries(colors: EditorThemeColors): List<Pair<Int, Int>> {
        val background = colors.background.toArgb()
        val muted = colors.text.copy(alpha = 0.40f).toArgb()
        val mutedStrong = colors.text.copy(alpha = 0.14f).toArgb()
        val currentLine = lerp(colors.background, androidx.compose.ui.graphics.Color.White, 0.05f).toArgb()
        return listOf(
            EditorColorScheme.WHOLE_BACKGROUND to background,
            EditorColorScheme.TEXT_NORMAL to colors.text.toArgb(),
            EditorColorScheme.LINE_NUMBER to muted,
            EditorColorScheme.LINE_NUMBER_BACKGROUND to background,
            EditorColorScheme.LINE_DIVIDER to mutedStrong,
            EditorColorScheme.CURRENT_LINE to currentLine,
            EditorColorScheme.SELECTION_INSERT to colors.function.toArgb(),
            EditorColorScheme.TEXT_SELECTED to colors.text.toArgb(),
            EditorColorScheme.MATCHED_TEXT_BACKGROUND to mutedStrong,
            EditorColorScheme.MATCHED_TEXT_BORDER to colors.function.toArgb(),
            EditorColorScheme.KEYWORD to colors.keyword.toArgb(),
            EditorColorScheme.LITERAL to colors.string.toArgb(),
            EditorColorScheme.COMMENT to colors.comment.toArgb(),
            EditorColorScheme.FUNCTION_NAME to colors.function.toArgb(),
            EditorColorScheme.OPERATOR to colors.operator.toArgb(),
            EditorColorScheme.ANNOTATION to colors.number.toArgb(),
            EditorColorScheme.IDENTIFIER_NAME to colors.text.toArgb(),
            EditorColorScheme.IDENTIFIER_VAR to colors.text.toArgb(),
            EditorColorScheme.HTML_TAG to colors.keyword.toArgb(),
            EditorColorScheme.ATTRIBUTE_NAME to colors.function.toArgb(),
            EditorColorScheme.ATTRIBUTE_VALUE to colors.string.toArgb(),
            EditorColorScheme.SCROLL_BAR_THUMB to muted,
            EditorColorScheme.SCROLL_BAR_THUMB_PRESSED to colors.function.toArgb(),
            EditorColorScheme.SCROLL_BAR_TRACK to background,
            EditorColorScheme.BLOCK_LINE to mutedStrong,
            EditorColorScheme.SIDE_BLOCK_LINE to mutedStrong
        )
    }
}
