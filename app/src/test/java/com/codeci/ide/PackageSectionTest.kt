package com.codeci.ide

import com.codeci.ide.ui.modules.PackageCatalog
import com.codeci.ide.ui.modules.PackageCategory
import com.codeci.ide.ui.modules.PackageSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 33.2 — the Packages hub's human-first grouping. "Languages &
 * IntelliSense" is the always-open section (languages, compilers, and the
 * Phase 31 IntelliSense cards); everything else folds into the collapsed
 * "Unix tools" section. These tests pin the pure [PackageCatalog.sectionOf]
 * mapping and the render order.
 */
class PackageSectionTest {

    @Test
    fun `languages and compilers land in languages and intellisense`() {
        for (section in listOf(PackageCategory.LANGUAGES, PackageCategory.COMPILERS)) {
            for (pkg in PackageCatalog.ALL_PACKAGES.filter { it.category == section }) {
                assertEquals(
                    "package ${pkg.id} (${pkg.category}) must be in Languages & IntelliSense",
                    PackageSection.LANGUAGES_INTELLISENSE,
                    PackageCatalog.sectionOf(pkg),
                )
            }
        }
    }

    @Test
    fun `editors, cli tools and utils land in unix tools`() {
        for (section in listOf(PackageCategory.EDITORS, PackageCategory.CLI_TOOLS, PackageCategory.UTILS)) {
            for (pkg in PackageCatalog.ALL_PACKAGES.filter { it.category == section }) {
                assertEquals(
                    "package ${pkg.id} (${pkg.category}) must be in Unix tools",
                    PackageSection.UNIX_TOOLS,
                    PackageCatalog.sectionOf(pkg),
                )
            }
        }
    }

    @Test
    fun `every intellisense card is in the language section`() {
        for (pkg in PackageCatalog.ALL_PACKAGES.filter { it.id.startsWith("intellisense-") }) {
            assertEquals(
                "intellisense card ${pkg.id} must be in Languages & IntelliSense",
                PackageSection.LANGUAGES_INTELLISENSE,
                PackageCatalog.sectionOf(pkg),
            )
        }
    }

    @Test
    fun `every package maps to exactly one section`() {
        val sections = PackageSection.entries.toSet()
        for (pkg in PackageCatalog.ALL_PACKAGES) {
            assertTrue("package ${pkg.id} has no section", sections.contains(PackageCatalog.sectionOf(pkg)))
        }
    }

    @Test
    fun `render order opens on the language section`() {
        assertEquals(
            listOf(PackageSection.LANGUAGES_INTELLISENSE, PackageSection.UNIX_TOOLS),
            PackageSection.ordered,
        )
    }
}
