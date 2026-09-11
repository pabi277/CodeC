package com.codeci.ide.ui.services

/**
 * Phase 42.1 — the rules that decide whether the app may install a binary it
 * just downloaded, as pure host-testable code (no Android, no network — this
 * is the code whose mistake is a *wrong binary on the user's phone*, so it
 * lives where a JUnit can starve it, lie to it, and feed it garbage).
 *
 * The problem these rules close (evidence in docs/chat-phase42): the old
 * updater asked GitHub for `releases/latest` — which resolved to
 * `userland-v1`, a BOOTSTRAP release with no app APK — and then installed
 * the FIRST asset whose name ended in `.apk`, with no version comparison,
 * no size check, no digest, and no cleanup. The rules here are the exact
 * discipline `UserlandInstaller` already applies to the userland download:
 * this is the same download problem, so the updater inherits the careful
 * one's behaviour instead of growing a second careless path.
 *
 * Asset naming (the contract `scripts/check_release_apks.sh` and the
 * publish step in `.github/workflows/build-apk.yml` produce):
 *
 *     CodeC-IDE-<version>-universal.apk      — always present, the default
 *     CodeC-IDE-<version>-<abi>.apk          — per-ABI (42.2 flavors)
 *
 * [pickAsset] chooses by the device's ABI preference order and otherwise
 * falls back to universal; it NEVER returns "the first .apk" again.
 */
object UpdatePolicy {

    /** `app-v1.3.17` only — a userland/bootstrap tag can never be an app update. */
    private val APP_TAG = Regex("^app-v(\\d+(?:\\.\\d+){0,2})$")

    /** Installed `versionName` shape: `1.3.16` or `1.3.16 (3419922)` (Phase 29's CI run number). */
    private val VERSION_TEXT = Regex("^(\\d+(?:\\.\\d+){0,2})(?:\\s*\\([^)]*\\))?\\s*$")

    /** A semver-ish version; comparisons are numeric per part, equal lengths win lexically. */
    data class Version(val parts: List<Int>) : Comparable<Version> {
        val text: String get() = parts.joinToString(".")

        override fun compareTo(other: Version): Int {
            val n = maxOf(parts.size, other.parts.size)
            for (i in 0 until n) {
                val a = parts.getOrElse(i) { 0 }
                val b = other.parts.getOrElse(i) { 0 }
                if (a != b) return a.compareTo(b)
            }
            return 0
        }

        companion object {
            /** Parses `1.3.16`, `1.3.16 (3419922)`, `app-v1.3.17`; null on anything else. */
            fun parse(raw: String?): Version? {
                val s = raw?.trim() ?: return null
                val body = APP_TAG.matchEntire(s)?.groupValues?.get(1)
                    ?: VERSION_TEXT.matchEntire(s)?.groupValues?.get(1)
                    ?: return null
                val parts = body.split('.').map { it.toIntOrNull() ?: return null }
                if (parts.isEmpty() || parts.any { it < 0 }) return null
                return Version(parts)
            }
        }
    }

    /** A release asset reduced to what the policy needs. */
    data class Asset(
        val name: String,
        val sizeBytes: Long,
        val downloadUrl: String
    )

    /** The outcome of comparing the installed build to a candidate release. */
    sealed class Decision {
        /** Candidate is strictly newer: offer the download. */
        data object Newer : Decision()

        /** Same version: say "you are up to date" — never re-offer an install. */
        data object Same : Decision()

        /** Candidate is OLDER than the installed build: refuse, with a reason. */
        data class Older(val reason: String) : Decision()

        /** Either side failed to parse: never install what we cannot version. */
        data class Unsafe(val reason: String) : Decision()
    }

    /** The outcome of verifying a finished download against its release facts. */
    sealed class Verification {
        /** Size matches (when known) and the SHA-256 matches: install. */
        data object Ok : Verification()

        /**
         * No digest was published for this asset (a hand-made release): the
         * app REFUSES to auto-install but may offer "open in browser".
         */
        data object RefuseBrowserOnly : Verification()

        /** Size or digest mismatch: never install, delete the file, say why. */
        data class NeverInstall(val reason: String) : Verification()
    }

    /** Hard ceiling for an APK download; a release asset bigger than this is an error. */
    const val MAX_APK_BYTES = 200L * 1024L * 1024L

    fun isAppRelease(tag: String): Boolean = APP_TAG.matches(tag.trim())

    /**
     * The app release worth offering from a list of parsed releases: app
     * releases only, drafts and prereleases excluded, newest version first
     * (a release LIST order is GitHub's, not ours — sort by parsed version).
     */
    fun newestAppRelease(releases: List<AppRelease>): AppRelease? =
        releases
            .filter { !it.draft && !it.prerelease && isAppRelease(it.tag) }
            .maxWithOrNull(compareBy({ Version.parse(it.tag) ?: Version(listOf(0)) }, { it.tag }))

    /**
     * Chooses the APK asset for this device: the first ABI in [abis] that has
     * a matching `…-<abi>.apk` asset wins (device preference order), else the
     * `…-universal.apk`; never "the first asset that happens to end in .apk".
     */
    fun pickAsset(assets: List<Asset>, abis: List<String>): Asset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        for (abi in abis) {
            apks.firstOrNull { it.name.endsWith("-$abi.apk", ignoreCase = true) }?.let { return it }
        }
        return apks.firstOrNull { it.name.endsWith("-universal.apk", ignoreCase = true) }
    }

    /** Offer / re-assure / refuse, with the reason carried for the UI. */
    fun shouldOffer(installed: Version?, candidate: Version?): Decision =
        when {
            candidate == null ->
                Decision.Unsafe("the release has no readable version")
            installed == null ->
                Decision.Unsafe("this install has no readable version")
            candidate > installed -> Decision.Newer
            candidate == installed -> Decision.Same
            else -> Decision.Older(
                "release v${candidate.text} is older than the installed v${installed.text}"
            )
        }

    /**
     * Parses the `sha256:` lines the publish workflow writes into the release
     * notes (`sha256: <64hex>  <asset-name>`). Returns asset-name → digest.
     * Lines without a valid 64-hex digest are ignored (no half-credit).
     */
    fun digestsFromNotes(notesBody: String?): Map<String, String> {
        if (notesBody.isNullOrBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        val line = Regex("^\\s*sha256:\\s*([0-9a-fA-F]{64})\\s+(.+?)\\s*$")
        for (l in notesBody.lines()) {
            val m = line.matchEntire(l) ?: continue
            map[m.groupValues[2]] = m.groupValues[1].lowercase()
        }
        return map
    }

    /**
     * The pre-download gate: refuse BEFORE writing anything when the release's
     * own metadata already proves the download cannot be trusted (declared
     * size above the cap, or no/blank download URL).
     */
    fun preflight(asset: Asset): Verification = when {
        asset.downloadUrl.isBlank() ->
            Verification.NeverInstall("release asset has no download URL")
        asset.sizeBytes > MAX_APK_BYTES ->
            Verification.NeverInstall(
                "release asset declares ${asset.sizeBytes} bytes (cap $MAX_APK_BYTES)"
            )
        else -> Verification.Ok
    }

    /**
     * The post-download gate: the file on disk against the release's facts.
     * A digest mismatch, a truncation, or a size lie is NEVER installable;
     * a missing digest allows only the browser path.
     */
    fun verifyDownload(
        fileBytes: Long,
        actualSha256: String,
        asset: Asset,
        expectedSha256: String?
    ): Verification {
        if (fileBytes <= 0L) return Verification.NeverInstall("download is empty")
        if (fileBytes > MAX_APK_BYTES) {
            return Verification.NeverInstall("download exceeds the $MAX_APK_BYTES byte cap")
        }
        if (asset.sizeBytes > 0 && fileBytes != asset.sizeBytes) {
            return Verification.NeverInstall(
                "download is $fileBytes bytes, the release listed ${asset.sizeBytes}"
            )
        }
        if (expectedSha256 == null) return Verification.RefuseBrowserOnly
        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
            return Verification.NeverInstall("SHA-256 mismatch (got $actualSha256)")
        }
        return Verification.Ok
    }
}

/** A GitHub release reduced to what the updater needs (parser in [ReleaseFetch]). */
data class AppRelease(
    val tag: String,
    val title: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val notesBody: String,
    val assets: List<UpdatePolicy.Asset>
)
