package com.codeci.ide

import com.codeci.ide.ui.services.AppRelease
import com.codeci.ide.ui.services.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 42.1 — the whole *install-or-refuse* exit list of PART_42_1,
 * expressed as data (pure JVM; this is the code that decides whether a
 * downloaded binary reaches the user's phone).
 */
class UpdatePolicyTest {

    private fun v(text: String) = UpdatePolicy.Version.parse(text)

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<UpdatePolicy.Asset> = emptyList(),
        notes: String = ""
    ) = AppRelease(tag = tag, title = tag, draft = draft, prerelease = prerelease, notesBody = notes, assets = assets)

    private fun apk(name: String, size: Long = 24_000_000L) =
        UpdatePolicy.Asset(name = name, sizeBytes = size, downloadUrl = "https://example.test/$name")

    // ---- channel: only app-v* tags are app updates ----

    @Test
    fun `userland tags can never be app updates`() {
        assertFalse(UpdatePolicy.isAppRelease("userland-v1"))
        assertFalse(UpdatePolicy.isAppRelease("userland-v2-dev"))
        assertFalse(UpdatePolicy.isAppRelease("bootstrap-phase3"))
        assertTrue(UpdatePolicy.isAppRelease("app-v1.3.17"))
        assertTrue(UpdatePolicy.isAppRelease("app-v1.4"))
        assertFalse(UpdatePolicy.isAppRelease("app-vx"))
        assertFalse(UpdatePolicy.isAppRelease("app-v1.4.0-rc1"))
        assertFalse(UpdatePolicy.isAppRelease("v1.3.17"))
    }

    @Test
    fun `newest app release ignores drafts, prereleases, bootstraps and list order`() {
        val releases = listOf(
            release("userland-v2-dev", assets = listOf(apk("bootstrap-phase3-aarch64.tar.gz"))),
            release("app-v1.4.0", draft = true, assets = listOf(apk("CodeC-IDE-1.4.0-universal.apk"))),
            release("app-v1.4.1", prerelease = true, assets = listOf(apk("CodeC-IDE-1.4.1-universal.apk"))),
            release("app-v1.3.17", assets = listOf(apk("CodeC-IDE-1.3.17-universal.apk"))),
            release("app-v1.9.9", assets = listOf(apk("CodeC-IDE-1.9.9-universal.apk")))
        )
        // 1.9.9 is NOT the answer even if listed last: numeric version wins.
        assertEquals("app-v1.9.9", UpdatePolicy.newestAppRelease(releases)?.tag)
        assertNull(UpdatePolicy.newestAppRelease(listOf(release("userland-v1"))))
        assertEquals(
            "a draft app release is invisible to users",
            null,
            UpdatePolicy.newestAppRelease(listOf(release("app-v1.0.0", draft = true)))
        )
    }

    // ---- asset picking: abi first, universal fallback, never "first .apk" ----

    @Test
    fun `pickAsset matches the device abi, falls back to universal, never guesses`() {
        val assets = listOf(
            apk("CodeC-IDE-1.3.17-universal.apk", size = 30_000_000L),
            apk("CodeC-IDE-1.3.17-x86_64.apk"),
            apk("CodeC-IDE-1.3.17-arm64-v8a.apk"),
            apk("checksums.txt", size = 500L)
        )
        assertEquals(
            "CodeC-IDE-1.3.17-arm64-v8a.apk",
            UpdatePolicy.pickAsset(assets, listOf("arm64-v8a"))?.name
        )
        assertEquals(
            "CodeC-IDE-1.3.17-x86_64.apk",
            UpdatePolicy.pickAsset(assets, listOf("x86_64"))?.name
        )
        assertEquals(
            "device preference order: the first supported abi wins",
            "CodeC-IDE-1.3.17-x86_64.apk",
            UpdatePolicy.pickAsset(assets, listOf("x86_64", "arm64-v8a"))?.name
        )
        assertEquals(
            "armeabi-v7a has no split: universal is the fallback",
            "CodeC-IDE-1.3.17-universal.apk",
            UpdatePolicy.pickAsset(assets, listOf("armeabi-v7a"))?.name
        )
        assertNull(
            "no universal and no abi match: offer nothing (never 'the first .apk')",
            UpdatePolicy.pickAsset(
                assets.filterNot { it.name.contains("universal") },
                listOf("armeabi-v7a")
            )
        )
        assertNull(
            "a bootstrap tarball release has no installable asset",
            UpdatePolicy.pickAsset(
                listOf(apk("bootstrap-phase3-aarch64.tar.gz")),
                listOf("arm64-v8a")
            )
        )
    }

    // ---- version compare on parsed X.Y.Z, never string equality ----

    @Test
    fun `version compare - newer, same, older, unsafe`() {
        assertEquals(
            UpdatePolicy.Decision.Newer,
            UpdatePolicy.shouldOffer(v("1.3.16 (3419922)"), v("1.3.17"))
        )
        assertEquals(
            UpdatePolicy.Decision.Same,
            UpdatePolicy.shouldOffer(v("1.3.17"), v("1.3.17"))
        )
        assertEquals(
            "two-part versions live in the same space (1.4 > 1.3.17)",
            UpdatePolicy.Decision.Newer,
            UpdatePolicy.shouldOffer(v("1.3.17"), v("1.4"))
        )
        assertEquals(
            UpdatePolicy.Decision.Newer,
            UpdatePolicy.shouldOffer(v("1.9.9"), v("2"))
        )
        assertTrue(
            UpdatePolicy.shouldOffer(v("1.3.17"), v("1.3.16")) is UpdatePolicy.Decision.Older
        )
        assertTrue(
            "garbage on either side is UNSAFE, never a silent Same",
            UpdatePolicy.shouldOffer(null, v("1.3.17")) is UpdatePolicy.Decision.Unsafe &&
                UpdatePolicy.shouldOffer(v("1.3.17"), null) is UpdatePolicy.Decision.Unsafe
        )
    }

    // ---- pre-download gate ----

    @Test
    fun `preflight refuses before a single byte is written`() {
        assertTrue(
            UpdatePolicy.preflight(apk("x.apk", size = UpdatePolicy.MAX_APK_BYTES + 1))
                is UpdatePolicy.Verification.NeverInstall
        )
        assertTrue(
            UpdatePolicy.preflight(UpdatePolicy.Asset("x.apk", 100L, ""))
                is UpdatePolicy.Verification.NeverInstall
        )
        assertEquals(UpdatePolicy.Verification.Ok, UpdatePolicy.preflight(apk("x.apk")))
    }

    // ---- post-download gate ----

    private val sha: String = "a".repeat(64)

    @Test
    fun `digest mismatch is NEVER installed`() {
        val verdict = UpdatePolicy.verifyDownload(
            fileBytes = 1_000L,
            actualSha256 = "b".repeat(64),
            asset = apk("x.apk", size = 1_000L),
            expectedSha256 = sha
        )
        assertTrue(verdict is UpdatePolicy.Verification.NeverInstall)
    }

    @Test
    fun `absent digest refuses auto-install but allows the browser`() {
        assertEquals(
            UpdatePolicy.Verification.RefuseBrowserOnly,
            UpdatePolicy.verifyDownload(1_000L, sha, apk("x.apk", 1_000L), expectedSha256 = null)
        )
    }

    @Test
    fun `size lies and empty files are never installed`() {
        assertTrue(
            UpdatePolicy.verifyDownload(999L, sha, apk("x.apk", 1_000L), sha)
                is UpdatePolicy.Verification.NeverInstall
        )
        assertTrue(
            UpdatePolicy.verifyDownload(0L, sha, apk("x.apk", 1_000L), sha)
                is UpdatePolicy.Verification.NeverInstall
        )
        assertTrue(
            UpdatePolicy.verifyDownload(UpdatePolicy.MAX_APK_BYTES + 1, sha, apk("x.apk", -1L), sha)
                is UpdatePolicy.Verification.NeverInstall
        )
        assertEquals(
            UpdatePolicy.Verification.Ok,
            UpdatePolicy.verifyDownload(1_000L, sha, apk("x.apk", 1_000L), sha)
        )
    }

    // ---- release-notes digests ----

    @Test
    fun `digests are read from the notes sha256 lines only`() {
        val notes = """
            ## What's new
            Phase 42.

            ## Checksums
            sha256: ${"a".repeat(64)}  CodeC-IDE-1.3.17-arm64-v8a.apk
            sha256: ${"b".repeat(64)}  CodeC-IDE-1.3.17-universal.apk
            sha256: not-a-digest  CodeC-IDE-1.3.17-universal.apk
        """.trimIndent()
        val digests = UpdatePolicy.digestsFromNotes(notes)
        assertEquals("a".repeat(64), digests["CodeC-IDE-1.3.17-arm64-v8a.apk"])
        assertEquals("b".repeat(64), digests["CodeC-IDE-1.3.17-universal.apk"])
        assertEquals(2, digests.size)
        assertTrue(UpdatePolicy.digestsFromNotes(null).isEmpty())
        assertTrue(UpdatePolicy.digestsFromNotes("no checksums here").isEmpty())
    }
}
