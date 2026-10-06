package com.codeci.ide.ui.projects

/*
 * Phase 93 — the storage-permission model, in ONE place.
 *
 * Why this file exists (the owner, 2026-10-05): *"Check the agent permission
 * because when I really say to change something in file it's showing error no
 * permission"* and *"read all the permissions that the agent will have and make
 * them correct, because now many permissions are not usable."*
 *
 * The audit found three separate defects, each fixed here rather than at its
 * symptom:
 *
 *  1. **`READ_EXTERNAL_STORAGE` had no `maxSdkVersion`.** Android 13 (API 33)
 *     stopped granting it: an app that still *declares* it without a cap keeps
 *     asking for a permission the platform can never give, and every
 *     `checkSelfPermission` for it answers "denied" on a phone where nothing is
 *     actually wrong. The manifest now caps it at API 32 like its write twin.
 *  2. **The two readers disagreed.** `ShellEnvironment.hasStoragePermission`
 *     and the self-check each had their own copy of the same question (all-files
 *     on API 30+, the legacy read below it), so a report could say one thing and
 *     the terminal gate another. Both now call [StorageAccessPolicy].
 *  3. **A refusal never named its cause.** An AI edit that could not reach a
 *     project folder said only "Project folder is not accessible" — the owner's
 *     *"no permission"* — with no switch to flip. [fixSteps] is that switch,
 *     spelled for the phone it is on.
 *
 * Pure Kotlin: no `android.*`, no `java.io`, no clock — so the host harness can
 * pin every branch for API 24 through 36 (`StorageAccessTest`). The callers pass
 * the two reads in; the reads themselves live where the Android context is
 * (`MainActivity`, `ShellEnvironment`, `AiViewModel`).
 */

/** The platform levels the two storage models meet at. */
object StorageApi {
    /** API 30+ — "All files access" (`MANAGE_EXTERNAL_STORAGE`, a Settings switch). */
    const val ALL_FILES_FROM = 30

    /** The last API on which `READ_/WRITE_EXTERNAL_STORAGE` are real runtime grants. */
    const val LEGACY_UNTIL = 32
}

/** How storage access is held on this phone right now. */
enum class StorageAccessState {
    /** API 30+ with the user's all-files grant — the whole shared tree is readable and writable. */
    ALL_FILES,

    /** API ≤ 32 with the legacy runtime permission granted. */
    LEGACY,

    /** The route this phone has is not granted. */
    MISSING
}

/**
 * The two reads, plus the API level, in one value. [legacyWrite] is carried for
 * honesty in the report: below API 29 the write side is its own runtime grant, and
 * a phone that granted read but not write cannot save an applied edit.
 */
data class StorageFacts(
    val sdkInt: Int,
    val allFiles: Boolean,
    val legacyRead: Boolean,
    val legacyWrite: Boolean = false
) {
    /** API 30+ — the phone's route is the Settings switch, not a dialog. */
    val allFilesApplies: Boolean get() = sdkInt >= StorageApi.ALL_FILES_FROM

    /** API ≤ 32 — the legacy runtime permission is still a real grant. */
    val legacyApplies: Boolean get() = sdkInt <= StorageApi.LEGACY_UNTIL

    val state: StorageAccessState get() = when {
        allFilesApplies -> if (allFiles) StorageAccessState.ALL_FILES else StorageAccessState.MISSING
        legacyApplies -> if (legacyRead && legacyWrite) StorageAccessState.LEGACY else StorageAccessState.MISSING
        else -> StorageAccessState.MISSING
    }

    /** Both sides held. The undo journal and an applied edit need the write side. */
    val granted: Boolean get() = state != StorageAccessState.MISSING

    /**
     * Phase 93 — read is its own question below API 30, where read and write are
     * separate grants: a phone that allowed one and refused the other can still be
     * *asked about a project*, but must not be promised an edit that cannot be
     * saved. On API 30+ the single all-files switch is all-or-nothing.
     */
    val canRead: Boolean get() = when {
        allFilesApplies -> allFiles
        legacyApplies -> legacyRead
        else -> false
    }

    val canWrite: Boolean get() = when {
        allFilesApplies -> allFiles
        legacyApplies -> legacyRead && legacyWrite
        else -> false
    }
}

object StorageAccessPolicy {

    /** Builds the facts from the three reads. One constructor, so no caller invents its own rule. */
    fun facts(sdkInt: Int, allFiles: Boolean, legacyRead: Boolean, legacyWrite: Boolean = false) =
        StorageFacts(sdkInt = sdkInt, allFiles = allFiles, legacyRead = legacyRead, legacyWrite = legacyWrite)

    /**
     * The exact switch to flip, or null when access is already held. The words
     * follow the phone's own Android version, because "Privacy & permissions" does
     * not exist on every release and a wrong path is worse than none.
     */
    fun fixSteps(facts: StorageFacts): String? = when {
        facts.granted -> null
        facts.allFilesApplies ->
            "Grant all-files access: Settings → Apps → CodeC → Permissions → " +
                "Files and media → Allow management of all files"
        else ->
            "Grant storage access: Settings → Apps → CodeC → Permissions → Storage → Allow"
    }

    /**
     * The self-check's second storage field. On API 33+ the legacy permission no
     * longer exists — printing "not granted" there was a false alarm on a phone
     * where the correct grant was already held, so it says what it is instead.
     */
    fun legacyLabel(facts: StorageFacts): String = when {
        !facts.legacyApplies -> "not applicable (API ${facts.sdkInt}+)"
        facts.legacyRead -> "granted"
        else -> "not granted"
    }

    /** The one-line report row both facts are printed on. */
    fun reportLine(facts: StorageFacts): String =
        "Android API ${facts.sdkInt}" +
            " · all-files access: " + (if (facts.allFilesApplies && facts.allFiles) "granted" else "not granted") +
            " · storage permission: " + legacyLabel(facts)

    /**
     * True when a project at [path] lives outside CodeC's own private storage and
     * therefore needs the shared-storage grant at all. Projects CodeC created sit
     * in `filesDir/CodeC/projects`, where **no permission exists to grant** — a
     * fix that demanded one for them would be a lie, and this function is what
     * keeps the preflight from telling it.
     */
    fun needsExternalAccess(path: String, privateRoots: List<String>): Boolean {
        if (path.isBlank()) return false
        val target = path.trimEnd('/')
        return privateRoots.none { root ->
            root.isNotBlank() && (target == root.trimEnd('/') || target.startsWith(root.trimEnd('/') + "/"))
        }
    }
}
