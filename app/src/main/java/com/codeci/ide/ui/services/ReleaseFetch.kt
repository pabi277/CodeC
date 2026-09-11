package com.codeci.ide.ui.services

import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 42.1 — GitHub Releases API JSON → the [AppRelease] model the update
 * policy consumes. The ONLY thing this file does is decode: every decision
 * (which release, which asset, offer-or-refuse, install-or-delete) lives in
 * the pure [UpdatePolicy], where host tests can reach it.
 *
 * House style: `org.json` (already the updater's and the GitHub API code's
 * JSON) — no new dependency for a version check. The fetch itself stays in
 * `ApkUpdateManager` (network is an Android-side concern); this parser takes
 * the raw body string.
 *
 * `GET /repos/{o}/{r}/releases?per_page=10` returns an ARRAY of releases
 * (not the single object `/releases/latest` returned): drafts and prereleases
 * are included in that list, so both flags are decoded and the policy decides
 * ([UpdatePolicy.newestAppRelease] filters them out for end users).
 */
object ReleaseFetch {

    /**
     * Decodes the releases-list body. Malformed body or a non-array → null
     * (the caller reports "update check failed", it never guesses).
     */
    fun parseReleases(body: String): List<AppRelease>? = try {
        val array = JSONArray(body)
        buildList {
            for (i in 0 until array.length()) {
                parseRelease(array.getJSONObject(i))?.let { add(it) }
            }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * One release object. A release with an unreadable tag or no asset ARRAY
     * is skipped (null) rather than half-decoded: the tag IS the version
     * contract, and the assets array IS the install contract.
     */
    private fun parseRelease(json: JSONObject): AppRelease? {
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val assetsJson = json.optJSONArray("assets") ?: return null
        return AppRelease(
            tag = tag,
            title = json.optString("name").ifBlank { tag },
            draft = json.optBoolean("draft", false),
            prerelease = json.optBoolean("prerelease", false),
            notesBody = json.optString("body", ""),
            assets = parseAssets(assetsJson)
        )
    }

    private fun parseAssets(array: JSONArray): List<UpdatePolicy.Asset> = buildList {
        for (i in 0 until array.length()) {
            val a = array.optJSONObject(i) ?: continue
            val name = a.optString("name").takeIf { it.isNotBlank() } ?: continue
            // `browser_download_url` is the public URL; `url` is the API URL
            // (needs an Accept: application/octet-stream dance and auth on
            // private repos) — the fixture test pins this choice because a
            // hand parser always gets the pair wrong.
            val downloadUrl = a.optString("browser_download_url").ifBlank {
                a.optString("url")
            }
            add(
                UpdatePolicy.Asset(
                    name = name,
                    sizeBytes = a.optLong("size", -1L),
                    downloadUrl = downloadUrl
                )
            )
        }
    }
}
