package com.codeci.ide

import com.codeci.ide.ui.services.ReleaseFetch
import com.codeci.ide.ui.services.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 42.1 — `ReleaseFetch` decoding against a REALISTIC `releases` list
 * fixture (Robolectric, because `org.json` is an Android framework class;
 * the fixture style is the CodecJsonParserTest habit: one body, every
 * corner the house parser could trip on, decoded and pinned).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleaseFetchTest {

    /** One fake releases-list page with every corner the policy must see. */
    private val fixture = """
        [
          {
            "tag_name": "app-v1.4.1",
            "name": "CodeC IDE v1.4.1 (release candidate)",
            "draft": false,
            "prerelease": true,
            "body": "notes with a sha256 line\nsha256: ${"c".repeat(64)}  CodeC-IDE-1.4.1-universal.apk",
            "assets": [
              {
                "name": "CodeC-IDE-1.4.1-universal.apk",
                "size": 31123456,
                "url": "https://api.github.com/repos/pabi277/CodeC/releases/assets/111",
                "browser_download_url": "https://github.com/pabi277/CodeC/releases/download/app-v1.4.1/CodeC-IDE-1.4.1-universal.apk"
              }
            ]
          },
          {
            "tag_name": "app-v1.4.0",
            "name": "CodeC IDE v1.4.0",
            "draft": true,
            "prerelease": false,
            "body": "draft — must never reach a user",
            "assets": [
              {
                "name": "CodeC-IDE-1.4.0-universal.apk",
                "size": 30000000,
                "browser_download_url": "https://github.com/pabi277/CodeC/releases/download/app-v1.4.0/CodeC-IDE-1.4.0-universal.apk"
              }
            ]
          },
          {
            "tag_name": "app-v1.3.17",
            "name": "CodeC IDE v1.3.17",
            "draft": false,
            "prerelease": false,
            "body": "## Checksums\nsha256: ${"a".repeat(64)}  CodeC-IDE-1.3.17-arm64-v8a.apk\nsha256: ${"b".repeat(64)}  CodeC-IDE-1.3.17-universal.apk",
            "assets": [
              {
                "name": "CodeC-IDE-1.3.17-universal.apk",
                "size": 24847906,
                "browser_download_url": "https://github.com/pabi277/CodeC/releases/download/app-v1.3.17/CodeC-IDE-1.3.17-universal.apk"
              },
              {
                "name": "CodeC-IDE-1.3.17-arm64-v8a.apk",
                "size": 21000000,
                "url": "https://api.github.com/repos/pabi277/CodeC/releases/assets/222",
                "browser_download_url": "https://github.com/pabi277/CodeC/releases/download/app-v1.3.17/CodeC-IDE-1.3.17-arm64-v8a.apk"
              },
              {
                "name": "CodeC-IDE-1.3.17-universal.apk.sha256",
                "size": 96,
                "browser_download_url": "https://github.com/pabi277/CodeC/releases/download/app-v1.3.17/CodeC-IDE-1.3.17-universal.apk.sha256"
              }
            ]
          },
          {
            "tag_name": "app-v1.3.16",
            "name": "CodeC IDE v1.3.16",
            "draft": false,
            "prerelease": false,
            "body": "a release with no assets at all",
            "assets": []
          },
          {
            "tag_name": "app-v1.3.15",
            "name": "CodeC IDE v1.3.15",
            "draft": false,
            "prerelease": false,
            "body": "a release whose only files are sidecars",
            "assets": [
              {
                "name": "checksums.txt",
                "size": 512,
                "browser_download_url": "https://github.com/pabi277/CodeC/releases/download/app-v1.3.15/checksums.txt"
              },
              {
                "name": "CodeC-IDE-1.3.15-universal.apk.sha256",
                "size": 96,
                "browser_download_url": "https://github.com/pabi277/CodeC/releases/download/app-v1.3.15/x.sha256"
              }
            ]
          },
          {
            "tag_name": "userland-v2-dev",
            "name": "CodeC userland bootstrap v2 (dev)",
            "draft": false,
            "prerelease": false,
            "body": "the bootstrap the updater must NEVER offer as an app update",
            "assets": [
              {
                "name": "bootstrap-phase3-aarch64.tar.gz",
                "size": 174000000,
                "browser_download_url": "https://github.com/pabi277/CodeC/releases/download/userland-v2-dev/bootstrap-phase3-aarch64.tar.gz"
              }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `the whole page decodes, flags and all`() {
        val releases = ReleaseFetch.parseReleases(fixture)
        assertNotNull(releases)
        releases!!
        assertEquals(6, releases.size)

        val rc = releases.first { it.tag == "app-v1.4.1" }
        assertTrue(rc.prerelease)
        assertFalse(rc.draft)
        assertEquals("CodeC IDE v1.4.1 (release candidate)", rc.title)

        val draft = releases.first { it.tag == "app-v1.4.0" }
        assertTrue(draft.draft)

        val noAssets = releases.first { it.tag == "app-v1.3.16" }
        assertTrue(noAssets.assets.isEmpty())

        val sidecarsOnly = releases.first { it.tag == "app-v1.3.15" }
        assertEquals(2, sidecarsOnly.assets.size)
        assertTrue(sidecarsOnly.assets.none { it.name.endsWith(".apk", ignoreCase = true) })
    }

    @Test
    fun `browser_download_url is used, never the API asset url`() {
        // The field pair a hand parser always gets wrong: `url` is the API
        // URL (auth + octet-stream Accept dance), `browser_download_url` is
        // the public one — the fixture gives them DIFFERENT values so the
        // test pins which is used.
        val release = ReleaseFetch.parseReleases(fixture)!!.first { it.tag == "app-v1.3.17" }
        val arm = release.assets.first { it.name.contains("arm64") }
        assertEquals(
            "https://github.com/pabi277/CodeC/releases/download/app-v1.3.17/CodeC-IDE-1.3.17-arm64-v8a.apk",
            arm.downloadUrl
        )
        assertEquals(21_000_000L, arm.sizeBytes)
    }

    @Test
    fun `the policy over the decoded page- the bootstrap is invisible`() {
        val releases = ReleaseFetch.parseReleases(fixture)!!
        val newest = UpdatePolicy.newestAppRelease(releases)
        assertEquals(
            "draft 1.4.0 and prerelease 1.4.1 lose to the real 1.3.17",
            "app-v1.3.17",
            newest?.tag
        )
        // And the decision the SETTINGS row makes from it on an arm64 phone.
        val asset = UpdatePolicy.pickAsset(newest!!.assets, listOf("arm64-v8a"))
        assertEquals("CodeC-IDE-1.3.17-arm64-v8a.apk", asset?.name)
        val digests = UpdatePolicy.digestsFromNotes(newest.notesBody)
        assertEquals("a".repeat(64), digests[asset!!.name])
    }

    @Test
    fun `malformed bodies decode to null, never to a half model`() {
        assertNull(ReleaseFetch.parseReleases("not json"))
        assertNull(ReleaseFetch.parseReleases("{\"tag_name\": \"app-v1.0.0\"}"))
        assertEquals(emptyList<Any>(), ReleaseFetch.parseReleases("[]"))
        // A release without a tag is skipped; the page around it still decodes.
        val oneBad = """
            [
              {"name": "no tag here", "assets": []},
              {"tag_name": "app-v1.0.0", "assets": []}
            ]
        """.trimIndent()
        val releases = ReleaseFetch.parseReleases(oneBad)!!
        assertEquals(1, releases.size)
        assertEquals("app-v1.0.0", releases[0].tag)
    }

    @Test
    fun `a single-object body (the latest-shape) is not confused for a list`() {
        // /releases/latest answers an OBJECT; the channel now reads the LIST
        // endpoint, and feeding one shape into the other must fail, not guess.
        assertNull(ReleaseFetch.parseReleases("{\"tag_name\": \"userland-v1\", \"assets\": []}"))
    }
}
