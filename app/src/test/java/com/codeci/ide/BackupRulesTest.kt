package com.codeci.ide

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Phase 42.3 — pins the real backup rules (the two XMLs replaced the
 * Android Studio samples, whose effective policy was "back up everything",
 * token and toolchain included). Reads the REAL repo files, same habit as
 * [IconAssetSetTest] — these are file-shape contracts, no Robolectric.
 *
 * The policy being pinned (spec: docs/chat-phase42/PART_42_3):
 *  1. only `CodeC/projects` is ever included — a backup carries "my code";
 *  2. the dangerous paths are excluded AND can never be included: the whole
 *     userland (`usr`), its home, the regenerable CodeC temp/modules/tcc
 *     trees, and the crash log;
 *  3. NOTHING touches `datastore` — that is where the GitHub token lives
 *     (GitCredentialsStore on the "settings" DataStore), and a token in a
 *     cloud backup is the one thing GitRedactor exists to prevent;
 *  4. `data_extraction_rules.xml` has a <device-transfer> block with the
 *     SAME lists (the sample's was commented out — the classic omission);
 *  5. the manifest still points at both files with allowBackup=true, and
 *     neither XML contains the string "TODO" any more.
 */
class BackupRulesTest {

    private val expectedExcludes = listOf(
        "usr", "home", "CodeC/temp", "CodeC/modules", "CodeC/tcc", "crash-log.txt"
    )

    private fun parser() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }

    private fun root(xml: File): Element = parser().newDocumentBuilder().parse(xml).documentElement

    private fun ruleSet(section: Element?): List<Pair<String, String>> {
        section ?: return emptyList()
        val rules = mutableListOf<Pair<String, String>>()
        for (tag in listOf("include", "exclude")) {
            val nodes = section.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val e = nodes.item(i) as Element
                rules.add(tag to (e.getAttribute("domain") + ":" + e.getAttribute("path")))
            }
        }
        return rules
    }

    private fun includesOf(section: Element?): List<String> =
        ruleSet(section).filter { it.first == "include" }.map { it.second }

    private fun excludesOf(section: Element?): List<String> =
        ruleSet(section).filter { it.first == "exclude" }.map { it.second }

    @Test
    fun `backup rules include exactly the user's projects`() {
        val xml = root(RepoFiles.mainSource("app/src/main/res/xml/backup_rules.xml"))
        assertEquals("full-backup-content", xml.tagName)
        assertEquals(
            "the auto-backup carries the user's projects and nothing else",
            listOf("file:CodeC/projects"),
            includesOf(xml)
        )
        for (path in expectedExcludes) {
            assertTrue("backup_rules must exclude '$path'", excludesOf(xml).contains("file:$path"))
            assertFalse(
                "'$path' must never be included (belt-and-braces rule)",
                includesOf(xml).contains("file:$path")
            )
        }
    }

    @Test
    fun `no rule anywhere touches the datastore (the token decision)`() {
        // Checked on parsed RULES, not raw text: the XML comments explain WHY
        // datastore/ is left out (that explanation is the documentation value),
        // but no include/exclude element may ever carry it — the GitHub token
        // lives in files/datastore/settings.preferences_pb (GitCredentialsStore).
        for (file in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val doc = root(RepoFiles.mainSource("app/src/main/res/xml/$file"))
            for (tag in listOf("include", "exclude")) {
                val nodes = doc.getElementsByTagName(tag)
                for (i in 0 until nodes.length) {
                    val e = nodes.item(i) as Element
                    assertFalse(
                        "$file has a $tag rule touching datastore/ — " +
                            "that would carry the GitHub token into backups",
                        e.getAttribute("path").contains("datastore", ignoreCase = true)
                    )
                }
            }
        }
    }

    @Test
    fun `extraction rules cover cloud AND device-transfer identically`() {
        val xml = root(RepoFiles.mainSource("app/src/main/res/xml/data_extraction_rules.xml"))
        assertEquals("data-extraction-rules", xml.tagName)
        val sections = xml.childNodes.let { nodes ->
            (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        }
        val cloud = sections.firstOrNull { it.tagName == "cloud-backup" }
        val transfer = sections.firstOrNull { it.tagName == "device-transfer" }
        assertTrue("data_extraction_rules.xml needs a <cloud-backup> block", cloud != null)
        assertTrue(
            "the device-transfer block the sample keeps commented out must EXIST " +
                "with the same rules (the classic omission — spec exit (c))",
            transfer != null
        )
        for (section in listOf(cloud, transfer)) {
            assertEquals(listOf("file:CodeC/projects"), includesOf(section))
            for (path in expectedExcludes) {
                assertTrue(
                    "<${section?.tagName}> must exclude '$path'",
                    excludesOf(section).contains("file:$path")
                )
            }
        }
    }

    @Test
    fun `manifest points at both rule files`() {
        val manifest = RepoFiles.mainSource("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"true\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    @Test
    fun `no TODO template markers survive`() {
        for (file in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            assertFalse(
                "$file still contains the Android Studio sample's TODO",
                RepoFiles.mainSource("app/src/main/res/xml/$file").readText().contains("TODO")
            )
        }
    }

    @Test
    fun `no unscoped external-files include can drag the toolchain in`() {
        // An <include domain="external-files"> without a path, or rooted at
        // ".", would back up EVERYTHING under getExternalFilesDir — including
        // any CodeC/ state that ever lands there. Spec exit: no such rule.
        for (file in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val doc = root(RepoFiles.mainSource("app/src/main/res/xml/$file"))
            val includes = doc.getElementsByTagName("include")
            for (i in 0 until includes.length) {
                val e = includes.item(i) as Element
                assertFalse(
                    "an external-files include needs an explicit, narrow path",
                    e.getAttribute("domain") == "external-files" &&
                        (e.getAttribute("path").isBlank() || e.getAttribute("path") == ".")
                )
                assertFalse(
                    "a root-domain include would back up the entire sandbox",
                    e.getAttribute("domain") == "root"
                )
            }
        }
    }
}
