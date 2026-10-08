package com.codeci.ide.ui.services

/**
 * Phase 102 — who may receive the APK handoff intent, as pure host-testable
 * code (the same discipline [UpdatePolicy] lives under: the mistake here is
 * a *URI grant landing in the wrong app*).
 *
 * The pre-102 updater fired an IMPLICIT `ACTION_VIEW` for the APK MIME with
 * `FLAG_GRANT_READ_URI_PERMISSION` and no `setPackage`: any app on the device
 * that registers an intent filter for `application/vnd.android.package-archive`
 * resolved as a candidate and could have been handed the read grant to the
 * verified APK. The session install ([ApkUpdateManager.installViaSession]) is
 * the primary path and has no intent at all; this policy guards the fallback:
 * the intent is scoped to the package the policy picks here, and the policy
 * never picks a non-system app it does not know.
 */
object InstallHandoffPolicy {

    /** One package the system resolved for the APK-view MIME type. */
    data class Resolver(val packageName: String, val isSystemApp: Boolean)

    /**
     * The installer package names Android ships: AOSP's package installer and
     * Google's own (the one Play Protect rides in). OEM skins keep one of
     * these or ship a system app of their own — the [isSystemApp] branch
     * below covers the latter without ever trusting a third-party app.
     */
    private val KNOWN_INSTALLERS = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller"
    )

    /**
     * The package the fallback handoff intent must be scoped to, or null when
     * no trustworthy candidate exists (the caller logs and falls through to
     * the documented last resort). Preference order: a KNOWN installer that
     * IS a system app, then a known name at all, then ANY system app — and
     * never a non-system unknown package, however many intent filters it
     * registered.
     */
    fun pickInstallerPackage(candidates: List<Resolver>): String? =
        candidates.firstOrNull { it.packageName in KNOWN_INSTALLERS && it.isSystemApp }?.packageName
            ?: candidates.firstOrNull { it.packageName in KNOWN_INSTALLERS }?.packageName
            ?: candidates.firstOrNull { it.isSystemApp }?.packageName
            ?: null
}
