package com.codeci.ide

import com.codeci.ide.ui.projects.StorageAccessPolicy
import com.codeci.ide.ui.projects.StorageAccessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 93 — the storage-permission model (pure host tests).
 *
 * The owner, 2026-10-05: *"check the agent permission … when I really say to
 * change something in file it's showing error no permission"* and *"read all the
 * permissions that the agent will have and make them correct, because now many
 * permissions are not usable."*
 *
 * These cases pin the three defects the audit found, so none of them can come
 * back silently:
 *
 *  1. the Android 13 trap — `READ_EXTERNAL_STORAGE` capped at 32, so no reader
 *     must consult it as the phone's route on API 33+;
 *  2. one answer everywhere — the terminal gate, the runtime request, the AI
 *     preflight and the self-check report all read [StorageAccessPolicy];
 *  3. a refusal names the switch — for the version of Android the phone runs,
 *     not for a settings menu that may not exist.
 */
class StorageAccessTest {

    private fun facts(sdk: Int, allFiles: Boolean = false, read: Boolean = false, write: Boolean = false) =
        StorageAccessPolicy.facts(sdk, allFiles, read, write)

    // ---- which route this phone has ----------------------------------------

    @Test
    fun `API 30 and up answers from the all-files switch alone`() {
        for (api in 30..36) {
            assertEquals(
                "API $api with the grant",
                StorageAccessState.ALL_FILES,
                facts(api, allFiles = true).state
            )
            assertEquals(
                "API $api without it",
                StorageAccessState.MISSING,
                facts(api, allFiles = false).state
            )
            assertTrue("the all-files route applies on API $api", facts(api).allFilesApplies)
        }
        // Android 11 and 12 (30–32) still honour the legacy permission — it is
        // capped at 32, not at 29 — but a project folder on shared storage is not
        // media, so the all-files switch is what actually decides. Holding only
        // the legacy grant there is still MISSING, and that is the point.
        for (api in 30..32) {
            assertTrue("API $api is inside the legacy cap", facts(api).legacyApplies)
            assertEquals(
                "API $api with legacy read but no all-files grant",
                StorageAccessState.MISSING,
                facts(api, read = true, write = true).state
            )
        }
        // Android 13 (33) ended it: the platform caps both at 32 in the manifest.
        for (api in 33..36) assertFalse("API $api is past the legacy cap", facts(api).legacyApplies)
    }

    @Test
    fun `below API 30 the legacy read and write grants are the route`() {
        for (api in 24..29) {
            assertEquals(
                "API $api fully granted",
                StorageAccessState.LEGACY,
                facts(api, read = true, write = true).state
            )
            assertEquals("read only is not enough", StorageAccessState.MISSING, facts(api, read = true).state)
            assertEquals("write only is not enough", StorageAccessState.MISSING, facts(api, write = true).state)
            assertFalse("the all-files switch does not exist below 30", facts(api, allFiles = true).allFilesApplies)
        }
    }

    @Test
    fun `API 33 and 34 can never be told they lack a legacy permission`() {
        // The defect: on Android 13+ `READ_EXTERNAL_STORAGE` is not grantable at
        // all. A phone holding the all-files grant is fully fine, and the report
        // must not call the legacy field "not granted" — that is a false alarm.
        for (api in 33..36) {
            val f = facts(api, allFiles = true)
            assertTrue("holding all-files is granted on API $api", f.granted)
            assertTrue("read is granted too", f.canRead)
            assertTrue("and write", f.canWrite)
            assertEquals(
                "the legacy row says why it is empty",
                "not applicable (API $api+)",
                StorageAccessPolicy.legacyLabel(f)
            )
        }
    }

    @Test
    fun `read and write are separate questions exactly where Android separates them`() {
        // Below API 30 a phone can read a project and still not save an edit.
        val readOnly = facts(29, read = true)
        assertTrue("it can read", readOnly.canRead)
        assertFalse("it cannot write", readOnly.canWrite)
        assertFalse("so it is not fully granted", readOnly.granted)

        // API 30+ is one switch: it is all of it or none of it.
        assertTrue(facts(34, allFiles = true).canWrite)
        assertFalse(facts(34, allFiles = true).canRead && !facts(34, allFiles = true).canWrite)
    }

    // ---- the switch a refusal names ----------------------------------------

    @Test
    fun `a granted phone is told to fix nothing`() {
        assertNull("nothing to flip", StorageAccessPolicy.fixSteps(facts(34, allFiles = true)))
        assertNull("nothing to flip", StorageAccessPolicy.fixSteps(facts(29, read = true, write = true)))
    }

    @Test
    fun `the fix names a path that exists on the phone it is shown on`() {
        val modern = StorageAccessPolicy.fixSteps(facts(34))!!
        assertTrue("the modern route", modern.contains("Settings → Apps → CodeC → Permissions"))
        assertTrue("and the switch itself", modern.contains("Allow management of all files"))
        assertFalse("never the private-storage promise", modern.contains("Scoped storage"))

        val legacy = StorageAccessPolicy.fixSteps(facts(28))!!
        assertTrue("the legacy route", legacy.contains("Settings → Apps → CodeC → Permissions"))
        assertTrue("and its own label", legacy.contains("Storage"))
        assertFalse(
            "the modern label does not exist on Android 9",
            legacy.contains("Files and media")
        )
    }

    @Test
    fun `the report line prints both facts and never a false denial`() {
        assertEquals(
            "Android API 34 · all-files access: granted · storage permission: not applicable (API 34+)",
            StorageAccessPolicy.reportLine(facts(34, allFiles = true))
        )
        assertEquals(
            "Android API 34 · all-files access: not granted · storage permission: not applicable (API 34+)",
            StorageAccessPolicy.reportLine(facts(34))
        )
        assertEquals(
            "Android API 28 · all-files access: not granted · storage permission: granted",
            StorageAccessPolicy.reportLine(facts(28, read = true, write = true))
        )
        assertEquals(
            "Android API 28 · all-files access: not granted · storage permission: not granted",
            StorageAccessPolicy.reportLine(facts(28))
        )
    }

    // ---- CodeC's own storage needs no permission ----------------------------

    @Test
    fun `CodeC's own project folder is never told to ask for a permission`() {
        val privateRoots = listOf("/data/user/0/com.codeci.ide/files", "/data/user/0/com.codeci.ide/no_backup")
        assertFalse(
            "a project CodeC created",
            StorageAccessPolicy.needsExternalAccess(
                "/data/user/0/com.codeci.ide/files/CodeC/projects/arcade",
                privateRoots
            )
        )
        assertFalse("the root itself", StorageAccessPolicy.needsExternalAccess(privateRoots[0], privateRoots))
        assertTrue(
            "a project on shared storage",
            StorageAccessPolicy.needsExternalAccess("/storage/emulated/0/Download/myapp", privateRoots)
        )
        assertTrue(
            "a prefix that only looks like a private root",
            StorageAccessPolicy.needsExternalAccess(
                "/data/user/0/com.codeci.ide/filesBackup/app",
                privateRoots
            )
        )
        assertFalse("an empty path is not a permission problem", StorageAccessPolicy.needsExternalAccess("", privateRoots))
    }
}
