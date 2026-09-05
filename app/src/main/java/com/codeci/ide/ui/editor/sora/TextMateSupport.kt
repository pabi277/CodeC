package com.codeci.ide.ui.editor.sora

import android.content.Context
import android.util.Log
import com.codeci.ide.ui.theme.EditorThemeType
import com.codeci.ide.ui.utils.LanguageType
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.DefaultGrammarDefinition
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * Phase 29.1 — process-wide TextMate plumbing (T2: load once per process).
 *
 * Three jobs, all idempotent and thread-safe:
 *  1. [ensureInitialized] — install the assets [AssetsFileResolver] and make
 *     sure a real theme is active (`TextMateAnalyzer` reads
 *     `ThemeRegistry.getCurrentThemeModel()` at CONSTRUCTION time, so the
 *     default theme must be in before the first language is created).
 *  2. [ensureLanguageLoaded] — register the grammar set for one language
 *     (its own grammar + the grammars its files embed). Called from the
 *     editor's language effect; normally a no-op because [warmUp] already
 *     did the work on a background thread.
 *  3. [warmUp] — background preload of the 29.1 core set so the FIRST file
 *     open finds its grammar already parsed.
 *
 * Nothing here touches the UI thread by design: heavy work is dispatched by
 * the caller (`EditorScreen` starts [warmUp] on `Dispatchers.Default`; the
 * language effect in `SoraEditorHost` suspends to `Dispatchers.Default`
 * before building the language).
 */
object TextMateSupport {

    private const val TAG = "TextMateSupport"

    private val lock = Any()

    @Volatile
    private var initialized = false

    /** Scopes already registered in [GrammarRegistry] (guarded by [lock]). */
    private val loadedScopes = mutableSetOf<String>()

    /** Install assets access + default theme. Safe to call from any thread. */
    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            FileProviderRegistry.getInstance().addFileProvider(
                AssetsFileResolver(context.applicationContext.assets)
            )
            // A real theme must be current before the first TextMateLanguage
            // is created (the analyzer snapshots it in its constructor).
            if (ThemeRegistry.getInstance().currentThemeModel === ThemeModel.EMPTY) {
                runCatching { TextMateThemes.applyTheme(EditorThemeType.VS_CODE_DARK_PLUS) }
                    .onFailure { Log.w(TAG, "Default TextMate theme unavailable", it) }
            }
            initialized = true
        }
    }

    /**
     * Register every grammar [fileName] of [language] needs. Idempotent;
     * a grammar that fails to load is skipped (the editor falls back to the
     * regex analyzer for that file instead of crashing).
     */
    fun ensureLanguageLoaded(language: LanguageType, fileName: String? = null) {
        val set = TextMateGrammars.grammarSetFor(language, fileName) ?: return
        synchronized(lock) {
            for (asset in set) {
                if (asset.scope in loadedScopes) continue
                try {
                    val stream = FileProviderRegistry.getInstance().tryGetInputStream(asset.path)
                    if (stream == null) {
                        Log.w(TAG, "Grammar asset missing: ${asset.path}")
                        continue
                    }
                    val source = IGrammarSource.fromInputStream(stream, asset.path, Charsets.UTF_8)
                    val definition = DefaultGrammarDefinition.withGrammarSource(
                        source, asset.name, asset.scope
                    )
                    GrammarRegistry.getInstance().loadGrammar(definition)
                    loadedScopes.add(asset.scope)
                } catch (e: Exception) {
                    // One bad grammar must never take the editor down.
                    Log.w(TAG, "Failed to load grammar ${asset.scope}", e)
                }
            }
        }
    }

    /**
     * Background preload of the core grammar sets (29.1 T3). Runs while the
     * user is still navigating to the editor; each language is also loaded
     * on demand by its first open, so this is purely a latency optimization.
     */
    fun warmUp(context: Context) {
        ensureInitialized(context)
        for (language in TextMateGrammars.warmUpLanguages) {
            ensureLanguageLoaded(language)
        }
    }
}

/**
 * Phase 29.1 — editor themes as TextMate theme assets (T4).
 *
 * `textmate/themes/` holds four JSONs: `dark-plus.json` (the flattened
 * vscode dark_vs + dark_plus — the DEFAULT), `monokai.json` (vscode
 * theme-monokai), `dracula.json` and `github-dark.json` (authored from the
 * CodeC palettes with the same scope structure as Dark+). The editor's
 * color scheme is sora's [TextMateColorScheme], which resolves TextMate
 * token scopes through the ACTIVE theme — replacing Phase 25.2's slot-based
 * `CodeCScheme`.
 */
object TextMateThemes {

    private const val TAG = "TextMateThemes"

    /** Registry name (= file name without extension) for each editor theme. */
    fun nameFor(type: EditorThemeType): String = when (type) {
        EditorThemeType.VS_CODE_DARK_PLUS -> "vscode-dark-plus"
        EditorThemeType.MONOKAI -> "monokai"
        EditorThemeType.DRACULA -> "dracula"
        EditorThemeType.GITHUB_DARK -> "github-dark"
    }

    private fun pathFor(name: String): String = "textmate/themes/$name.json"

    /**
     * Make [type] the active TextMate theme (loading its asset on first
     * use) and return a FRESH [TextMateColorScheme] for the editor — sora
     * enforces single scheme ownership, so one new object per application.
     * The scheme attaches itself to the registry's change notifications,
     * and attaching it to the editor re-runs the analysis so token colors
     * pick up the new theme immediately.
     */
    fun applyTheme(type: EditorThemeType): TextMateColorScheme {
        val registry = ThemeRegistry.getInstance()
        val name = nameFor(type)
        if (!registry.setTheme(name)) {
            val path = pathFor(name)
            val stream = FileProviderRegistry.getInstance().tryGetInputStream(path)
            if (stream == null) {
                // Assets ship inside the APK — this is a build error, not a
                // runtime state; the guard exists so a packaging mistake
                // degrades (sora default colors) instead of crashing.
                Log.w(TAG, "Theme asset missing: $path")
                return TextMateColorScheme.create(registry)
            }
            val model = ThemeModel(
                IThemeSource.fromInputStream(stream, path, Charsets.UTF_8),
                name
            )
            model.isDark = true
            registry.loadTheme(model) // loadTheme(...) also makes it current
        }
        return TextMateColorScheme.create(registry)
    }
}
