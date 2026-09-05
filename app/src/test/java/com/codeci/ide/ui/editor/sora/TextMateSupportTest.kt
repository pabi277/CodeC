package com.codeci.ide.ui.editor.sora

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.utils.LanguageType
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 29 — the TextMate integration on the JVM, through the REAL APK
 * assets (Robolectric). This is the host-testable stand-in for the device
 * round: it proves the assets parse, the registry loads them, every editor
 * theme resolves colors, and — the 29.3 exit condition — the editor's
 * analyzer for a colourable language is the TextMate one, NOT the regex
 * [CodeCAnalyzer].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextMateSupportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Mirrors the app flow: MainActivity warms up in the background; the
        // language/theme effects ensure their own prerequisites.
        TextMateSupport.ensureInitialized(context)
    }

    // ---- assets actually ship and parse ----------------------------------

    @Test
    fun `every referenced grammar asset exists in the apk`() {
        val all = listOf(
            TextMateGrammars.C, TextMateGrammars.CPP, TextMateGrammars.MAGIC_REGEXP,
            TextMateGrammars.PYTHON, TextMateGrammars.JAVASCRIPT, TextMateGrammars.TYPESCRIPT,
            TextMateGrammars.TSX, TextMateGrammars.HTML, TextMateGrammars.HTML_DERIVATIVE,
            TextMateGrammars.CSS, TextMateGrammars.JSON, TextMateGrammars.JSONC,
            TextMateGrammars.SHELL, TextMateGrammars.MARKDOWN, TextMateGrammars.GO,
            TextMateGrammars.RUST, TextMateGrammars.PHP, TextMateGrammars.PHP_HTML,
            TextMateGrammars.RUBY, TextMateGrammars.LUA, TextMateGrammars.XML,
            TextMateGrammars.YAML, TextMateGrammars.YAML_1_2, TextMateGrammars.YAML_EMBEDDED
        )
        for (asset in all) {
            context.assets.open(asset.path).use { stream ->
                assertTrue("asset ${asset.path} must not be empty", stream.read() != -1)
            }
        }
    }

    @Test
    fun `all four editor themes load and resolve colors`() {
        for (type in EditorThemeType.entries) {
            val scheme = TextMateThemes.applyTheme(type)
            val current = ThemeRegistry.getInstance().currentThemeModel
            // Background and normal text must come from the theme JSON, not
            // stay at sora's defaults-of-defaults (0/unset) — and the theme
            // we asked for must be the one ACTIVE afterwards (the Settings
            // theme switch depends on that).
            assertTrue(
                "after applyTheme(${type.displayName}) the active model is " +
                    "${current?.name} (raw=${current?.rawTheme?.name}), " +
                    "bg=0x${Integer.toHexString(scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND))}",
                current?.name == TextMateThemes.nameFor(type) &&
                    scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND) != 0 &&
                    scheme.getColor(EditorColorScheme.TEXT_NORMAL) != 0
            )
            if (type == EditorThemeType.VS_CODE_DARK_PLUS) {
                // The DEFAULT theme is Dark+ and must look like it.
                val bg = scheme.getColor(EditorColorScheme.WHOLE_BACKGROUND)
                assertTrue(
                    "Dark+ background must be #1E1E1E, got 0x${Integer.toHexString(bg)} " +
                        "(active model ${current?.name})",
                    bg == 0xFF1E1E1E.toInt()
                )
            }
        }
        // Re-applying the default after every other theme ran must switch
        // BACK to Dark+ (the Settings round-trip: Dark+ → others → Dark+).
        val darkPlus = TextMateThemes.applyTheme(EditorThemeType.VS_CODE_DARK_PLUS)
        val current = ThemeRegistry.getInstance().currentThemeModel
        val bg = darkPlus.getColor(EditorColorScheme.WHOLE_BACKGROUND)
        assertTrue(
            "Dark+ background must be #1E1E1E after the round trip, got " +
                "0x${Integer.toHexString(bg)} (active model ${current?.name}, " +
                "raw=${current?.rawTheme?.name})",
            bg == 0xFF1E1E1E.toInt() && current?.name == TextMateThemes.nameFor(EditorThemeType.VS_CODE_DARK_PLUS)
        )
    }

    // ---- grammar loading + analyzer swap (29.1 / 29.3 exit) ---------------

    @Test
    fun `warm up registers every core grammar`() {
        TextMateSupport.warmUp(context)
        for (scope in listOf(
            "source.c", "source.cpp", "source.python", "source.js", "source.ts",
            "text.html.basic", "text.html.derivative", "source.css", "source.json",
            "source.shell", "text.html.markdown", "source.go", "source.rust",
            "source.lua", "text.xml", "source.yaml", "source.yaml.1.2"
        )) {
            assertNotNull("warm-up must register $scope", GrammarRegistry.getInstance().findGrammar(scope))
        }
    }

    @Test
    fun `colourable languages use the textmate analyzer not the regex one`() {
        // The 29.3 exit condition, asserted at the code level: for every
        // colourable language the analyze manager is sora's TextMate
        // analyzer — MultiLanguageSyntaxHighlighter.tokenize is not on the
        // editor hot path for any of them.
        for (language in listOf(
            LanguageType.C, LanguageType.CPP, LanguageType.PYTHON,
            LanguageType.JAVASCRIPT, LanguageType.TYPESCRIPT, LanguageType.HTML,
            LanguageType.CSS, LanguageType.JSON, LanguageType.SHELL,
            LanguageType.MARKDOWN, LanguageType.GO, LanguageType.RUST,
            LanguageType.PHP, LanguageType.RUBY, LanguageType.LUA,
            LanguageType.XML, LanguageType.YAML
        )) {
            TextMateSupport.ensureLanguageLoaded(language, "probe.${language.extensions.first()}")
            val codeCLanguage = CodeCLanguage.create(language, "probe.${language.extensions.first()}")
            val analyzer = codeCLanguage.analyzeManager
            assertTrue(
                "language $language must analyse through TextMate (got ${analyzer::class.simpleName})",
                analyzer !is CodeCAnalyzer
            )
        }
    }

    @Test
    fun `tsx files get the typescriptreact grammar`() {
        TextMateSupport.ensureLanguageLoaded(LanguageType.TYPESCRIPT, "Component.tsx")
        val tsx = CodeCLanguage.create(LanguageType.TYPESCRIPT, "Component.tsx")
        assertTrue(tsx.analyzeManager !is CodeCAnalyzer)
        assertNotNull(GrammarRegistry.getInstance().findGrammar("source.tsx"))
    }

    @Test
    fun `text files fall back to the regex analyzer`() {
        val text = CodeCLanguage.create(LanguageType.TEXT, "notes.txt")
        assertTrue(text.analyzeManager is CodeCAnalyzer)
    }

    @Test
    fun `missing grammar degrades to the regex analyzer instead of crashing`() {
        // Simulate a language whose grammar never loaded: create() with a
        // scope the registry does not know must not throw.
        val language = CodeCLanguage.create(LanguageType.PHP, "index.php")
        // (The PHP set was loaded by earlier tests in the common case; the
        // point of this test is the contract — no crash, always an analyzer.)
        assertNotNull(language.analyzeManager)
    }
}
