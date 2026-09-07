package com.codeci.ide.ui.editor.snippets

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.codeci.ide.ui.editor.CompletionItem
import com.codeci.ide.ui.utils.LanguageType
import java.io.IOException

/**
 * Phase 30.1 — process-wide snippet-pack storage (the snippet twin of
 * `TextMateSupport`: load once, cache, warm in the background, degrade to
 * nothing instead of crashing).
 *
 * Two layers so the engine stays pure and the hot path stays cheap:
 *  - a READER (`path → text`) is injected — [attach] installs the APK-asset
 *    reader on a device, [install] hands tests/harnesses a synthetic one. The
 *    engine never sees a [Context].
 *  - pack JSON is parsed ONCE per language ([entriesCache]); the resolved
 *    [CompletionItem] list is cached per (language, file name) because
 *    `${TM_FILENAME_BASE}` resolution depends on the file ([itemsCache],
 *    bounded — an editor opens a handful of files, not a thousand).
 *
 * When no reader is installed, or every pack read fails, [snippetsFor] returns
 * an empty list and `CodeCompletionEngine` falls back to its own built-in
 * tables (the 22.x behaviour) — a missing asset must never leave the phone
 * with no snippets at all.
 */
object SnippetLibrary {

    private const val TAG = "SnippetLibrary"

    /** Resolved-item cache bound (keyed by language + file name). */
    private const val MAX_ITEM_CACHE = 12

    private val lock = Any()

    @Volatile
    private var reader: ((String) -> String?)? = null

    /** The AssetManager [reader] currently reads from (identity-compared). */
    @Volatile
    private var attached: AssetManager? = null

    private val entriesCache = HashMap<LanguageType, List<SnippetEntry>>()
    private val itemsCache = LinkedHashMap<String, List<CompletionItem>>()

    /** True once a reader is installed (device: assets; tests: synthetic). */
    fun isInstalled(): Boolean = reader != null

    /**
     * Install the APK-asset reader. Idempotent: the caches are only dropped
     * when the AssetManager instance really changed (Robolectric recreates the
     * Application — and its AssetManager — per test; a stale one fails every
     * later open, which is exactly the trap `TextMateSupport` documents).
     */
    fun attach(context: Context) {
        val assets = context.applicationContext.assets
        if (attached === assets && reader != null) return
        attached = assets
        install { path ->
            try {
                assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: IOException) {
                null
            }
        }
    }

    /** Inject a reader (tests, harnesses). Clears every cache. */
    fun install(reader: (String) -> String?) {
        synchronized(lock) {
            this.reader = reader
            entriesCache.clear()
            itemsCache.clear()
        }
    }

    /** Test-only: back to "no packs installed" (built-in tables take over). */
    fun reset() {
        synchronized(lock) {
            reader = null
            attached = null
            entriesCache.clear()
            itemsCache.clear()
        }
    }

    /** The resolved snippet items for [language]; empty when none ship/load. */
    fun snippetsFor(language: LanguageType, fileName: String? = null): List<CompletionItem> {
        val current = reader ?: return emptyList()
        if (SnippetAssets.packsFor(language).isEmpty()) return emptyList()
        val key = language.name + "|" + fileName.orEmpty()
        synchronized(lock) {
            itemsCache[key]?.let { return it }
        }
        val entries = entriesFor(language, current)
        if (entries.isEmpty()) return emptyList() // not cached: a later attach may work
        val items = SnippetPacks.items(entries, fileName)
        synchronized(lock) {
            while (itemsCache.size >= MAX_ITEM_CACHE) {
                val eldest = itemsCache.keys.firstOrNull() ?: break
                itemsCache.remove(eldest)
            }
            itemsCache[key] = items
        }
        return items
    }

    /** Languages whose pack JSON is already parsed (warm-up bookkeeping). */
    fun loadedLanguages(): Set<LanguageType> = synchronized(lock) { HashSet(entriesCache.keys) }

    /**
     * Background preload: parse (do NOT resolve) the packs of [languages] so
     * the first completion after opening a file is a cache hit. Called from
     * `MainActivity` on `Dispatchers.Default`, next to the TextMate warm-up.
     */
    fun warmUp(context: Context, languages: List<LanguageType> = SnippetAssets.warmUpLanguages) {
        attach(context)
        val current = reader ?: return
        for (language in languages) {
            if (SnippetAssets.packsFor(language).isEmpty()) continue
            val already = synchronized(lock) { entriesCache.containsKey(language) }
            if (already) continue
            entriesFor(language, current)
        }
    }

    /** Parses (once) every pack of [language]. */
    private fun entriesFor(
        language: LanguageType,
        current: (String) -> String?
    ): List<SnippetEntry> {
        synchronized(lock) {
            entriesCache[language]?.let { return it }
        }
        val entries = ArrayList<SnippetEntry>()
        var read = 0
        for (asset in SnippetAssets.packsFor(language)) {
            val json = runCatching { current(asset.path) }.getOrNull()
            if (json.isNullOrBlank()) {
                Log.w(TAG, "snippet pack missing: ${asset.path}")
                continue
            }
            read++
            entries += SnippetPacks.parse(json)
        }
        if (read == 0) return emptyList()
        synchronized(lock) {
            // A second thread may have parsed it first — first writer wins,
            // the lists are equivalent either way.
            return entriesCache.getOrPut(language) { entries }
        }
    }
}
