package com.codeci.ide

import com.codeci.ide.ui.modules.PackageCatalog
import com.codeci.ide.ui.modules.PackageCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageCatalogTest {

    @Test
    fun `package catalog has packages across all categories`() {
        val packages = PackageCatalog.ALL_PACKAGES
        assertTrue(packages.isNotEmpty())

        val categories = packages.map { it.category }.toSet()
        assertTrue(categories.contains(PackageCategory.COMPILERS))
        assertTrue(categories.contains(PackageCategory.EDITORS))
        assertTrue(categories.contains(PackageCategory.LANGUAGES))
        assertTrue(categories.contains(PackageCategory.CLI_TOOLS))
        assertTrue(categories.contains(PackageCategory.UTILS))
    }

    @Test
    fun `every package has valid non-empty id, binary, and install command`() {
        for (pkg in PackageCatalog.ALL_PACKAGES) {
            assertTrue("Package ID must not be blank", pkg.id.isNotBlank())
            assertTrue("Package binary must not be blank", pkg.binary.isNotBlank())
            assertTrue("Package name must not be blank", pkg.name.isNotBlank())
            assertTrue("Install command must not be blank", pkg.installCommand.isNotBlank())
            assertTrue("Run command must not be blank", pkg.runCommand.isNotBlank())
        }
    }

    @Test
    fun `quick actions contain essential pkg and storage commands`() {
        val actions = PackageCatalog.QUICK_ACTIONS
        assertTrue(actions.isNotEmpty())
        assertTrue(actions.any { it.command == "pkg update" })
        assertTrue(actions.any { it.command == "pkg upgrade -y" })
        assertTrue(actions.any { it.command == "codec-setup-storage" })
    }
}
