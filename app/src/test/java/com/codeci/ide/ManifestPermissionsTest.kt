package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 42.3 §5 — the manifest is an attack surface ("share-readiness"),
 * so the permission list is machine-pinned exactly like the Settings audit:
 * adding a permission without a verdict fails here; deleting one without
 * deleting its consumer fails here. The table's reasons traveled with the
 * OWNER through the whole set; share-readiness means being able to say out
 * loud why each is there — DATA_AND_PRIVACY.md carry the same list.
 */
class ManifestPermissionsTest {

    private val manifest: String
        get() = RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText()

    private val privacyDoc: String
        get() = RepoFiles.mainSource("docs/DATA_AND_PRIVACY.md").readText()

    private fun mainSources(): String =
        RepoFiles.mainKotlinSources().joinToString("\n") { it.readText() }

    /** The permitted set, in manifest order, each with its reason. The third
     * entry is the reader symbol: code the 42.3 test must grep in
     * `app/src/main/java`, so a permission can never outlive its consumer. */
    private val permitted = linkedMapOf(
        "android.permission.INTERNET" to (("repo sync, git clone/push, GitHub update checks, module downloads") to "HttpURLConnection"),
        "android.permission.ACCESS_NETWORK_STATE" to (("LanAddressProvider: reachability for the LAN address surface") to "ConnectivityManager"),
        "android.permission.ACCESS_WIFI_STATE" to (("LanAddressProvider: WiFi interface address for the LAN URL") to "WifiManager"),
        "android.permission.READ_EXTERNAL_STORAGE" to (("projects under shared storage on legacy devices") to "READ_EXTERNAL_STORAGE"),
        "android.permission.WRITE_EXTERNAL_STORAGE" to (("same, capped at maxSdkVersion 32 (scoped-storage boundary)") to "WRITE_EXTERNAL_STORAGE"),
        "android.permission.MANAGE_EXTERNAL_STORAGE" to (("file manager over the user's own project folders (opt-in grant)") to "isExternalStorageManager"),
        "android.permission.REQUEST_INSTALL_PACKAGES" to (("userland bootstrap + the updater's verified APK install") to "canRequestPackageInstalls"),
        "android.permission.WAKE_LOCK" to (("TerminalViewModel wake lock while a terminal session runs") to "PowerManager"),
        "android.permission.FOREGROUND_SERVICE" to (("RunForegroundService keeps long builds/runs alive") to "RunForegroundService"),
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC" to (("the same foreground service's data-sync type on API 34+") to "startForegroundService"),
        "android.permission.POST_NOTIFICATIONS" to (("codec-notify + the run/terminal service notifications") to "NotificationManagerCompat"),
        "android.permission.VIBRATE" to (("codec-vibrate haptics (keyboard, long-press, notifications)") to "Vibrator"),
        "android.permission.CAMERA" to (("the identification camera capture; hardware marked required=false") to "TakePicture"),
        "com.termux.permission.RUN_COMMAND" to (("the optional Termux compiler bridge (declared by Termux, guarded)") to "RUN_COMMAND")
    )

    private fun manifestPermissions(): List<String> =
        Regex("""<uses-permission\s+android:name="([^"]+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun `the manifest declares exactly the pinned permission set, each with a reason`() {
        val actual = manifestPermissions().toSet()
        val allowed = permitted.keys
        val missing = allowed - actual
        val added = actual - allowed
        assertTrue(
            "permissions removed from the manifest without updating the pin: $missing " +
                "(did you also delete the consumer?)",
            missing.isEmpty()
        )
        assertTrue(
            "NEW permissions declared without a 42.3 verdict + reason: $added " +
                "(add the reason to the pinned table and DATA_AND_PRIVACY.md in the same commit)",
            added.isEmpty()
        )
        assertEquals(
            "each permission must appear exactly once",
            actual.size,
            manifestPermissions().size
        )
    }

    /**
     * 42.3's stronger pin: each manifest permission MUST have a row in
     * `docs/DATA_AND_PRIVACY.md` (the short name appears in the doc) AND a
     * live reader symbol in `app/src/main/java`. A permission with no row,
     * or a row whose code is gone, fails the build — the only way the list
     * stays true after the beta.
     */
    @Test
    fun `every permission has a privacy-doc row and a live reader in main sources`() {
        val sources by lazy { mainSources() }
        permitted.forEach { (perm, pair) ->
            val shortName = perm.substringAfterLast('.')
            assertTrue(
                "docs/DATA_AND_PRIVACY.md has no row naming '$shortName' — " +
                    "the promise 'About can say this with a straight face' is empty",
                privacyDoc.contains("`$shortName`")
            )
            val reader = pair.second
            assertTrue(
                "permission $perm has no reader '$reader' in app/src/main/java — " +
                    "delete the permission or name the code that uses it",
                sources.contains(reader)
            )
        }
    }

    @Test
    fun `the two standing claims survive - no telemetry, f-droid-readable`() {
        val sources = mainSources()
        // Word-boundary matching — VibrationEffect.DEFAULT_AMPLITUDE is a
        // haptics constant, not the analytics SDK.
        listOf("firebase", "crashlytics", "admob", "amplitude").forEach { tracker ->
            assertTrue(
                "telemetry SDK marker '$tracker' crept in — the DATA_AND_PRIVACY claim dies here",
                !Regex("\\b$tracker\\b").containsMatchIn(sources.lowercase())
            )
        }
        assertTrue(
            "README/privacy doc must link the BETA/known-issues page for a stranger (42.3's 'difference between a beta and a dump')",
            privacyDoc.contains("BETA.md") ||
                RepoFiles.mainSource("docs/RELEASE_NOTES.md").readText().contains("BETA.md")
        )
    }

    @Test
    fun `no data-sewer drains - no sms contacts location accounts mic bluetooth phone`() {
        val banned = listOf("SMS", "CONTACTS", "LOCATION", "GET_ACCOUNTS", "RECORD_AUDIO", "BLUETOOTH", "PHONE", "READ_PHONE_STATE")
        for (b in banned) {
            assertTrue(
                "CodeC has no business with $b; if one ever appears, the verdict debate goes in the part doc first",
                !manifest.contains("android.permission.$b")
            )
        }
    }

    @Test
    fun `the camera feature stays optional so non-camera devices install`() {
        val feature =
            Regex("""<uses-feature\s+android:name="android\.hardware\.camera"\s+android:required="([a-z]+)"""")
                .find(manifest)
        assertEquals(
            "uses-feature camera must be required=false — the IDE works fine on devices without one",
            "false",
            feature?.groupValues?.get(1)
        )
    }

    @Test
    fun `no source manifest anywhere is ever debuggable - the release-flag law`() {
        assertTrue(
            "android:debuggable=\"true\" must never sit in a source manifest " +
                "(release debuggability is how a lost phone leaks the private files)",
            !manifest.contains("""android:debuggable="true"""")
        )
        assertTrue(
            "cleartext traffic stays off unless a part doc opts in on the record",
            !manifest.contains("""android:usesCleartextTraffic="true"""")
        )
    }

    @Test
    fun `only the launcher activity is exported, nothing else leaves the app`() {
        val exported = Regex("""<(activity|service|receiver|provider)[\s\S]*?android:exported="true"[\s\S]*?>""")
            .findAll(manifest)
            .map { it.value }
            .toList()
        assertTrue(
            "exactly one exported component is expected (the launcher): got ${exported.size}",
            exported.size == 1
        )
        assertTrue(
            "the exported component must be the MainActivity — a codec-api or file-surface " +
                "export is a stranger reaching into private files",
            exported.single().contains("MainActivity")
        )
    }
}
