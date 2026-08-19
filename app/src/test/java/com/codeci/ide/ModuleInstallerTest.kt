package com.codeci.ide

import com.codeci.ide.ui.modules.ModuleInstaller
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModuleInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `extractZip flattens a single top-level toolchain folder`() {
        val zip = tmp.newFile("module.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            listOf(
                "clang-compiler/bin/clang",
                "clang-compiler/bin/compiler-wrapper.sh",
                "clang-compiler/lib/libLLVM.so",
                "clang-compiler/module.json"
            ).forEach { name ->
                out.putNextEntry(ZipEntry(name))
                out.write("content-$name".toByteArray())
                out.closeEntry()
            }
        }

        val dest = File(tmp.root, "installed")
        ModuleInstaller.extractZip(zip, dest)

        assertTrue(File(dest, "bin/clang").isFile)
        assertTrue(File(dest, "bin/compiler-wrapper.sh").isFile)
        assertTrue(File(dest, "lib/libLLVM.so").isFile)
        assertTrue(File(dest, "module.json").isFile)
        assertFalse(File(dest, "clang-compiler").exists())
    }

    @Test
    fun `extractZip leaves a flat archive untouched`() {
        val zip = tmp.newFile("flat.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            listOf("bin/clang", "lib/libLLVM.so", "module.json").forEach { name ->
                out.putNextEntry(ZipEntry(name))
                out.write("content-$name".toByteArray())
                out.closeEntry()
            }
        }

        val dest = File(tmp.root, "installed")
        ModuleInstaller.extractZip(zip, dest)

        assertTrue(File(dest, "bin/clang").isFile)
        assertTrue(File(dest, "lib/libLLVM.so").isFile)
        assertFalse(File(dest, "clang").exists())
    }

    @Test
    fun `flattenedSymlinkTarget resolves path text and ignores real files`() {
        val bin = File(tmp.root, "bin").apply { mkdirs() }
        val real = File(bin, "clang-21").apply { writeText("not really an elf") }
        val link = File(bin, "clang").apply { writeText("clang-21") }
        val missing = File(bin, "missing").apply { writeText("nope") }

        assertEquals(real.canonicalFile, ModuleInstaller.flattenedSymlinkTarget(link)?.canonicalFile)
        // A file whose "target" doesn't exist is not a flattened symlink.
        assertNull(ModuleInstaller.flattenedSymlinkTarget(missing))
        // The candidate itself (text, no existing target) is not treated as a link.
        assertNull(ModuleInstaller.flattenedSymlinkTarget(real))
    }

    @Test
    fun `materializeFlattenedSymlinks makes placeholders resolve to the target`() {
        val bin = File(tmp.root, "bin").apply { mkdirs() }
        File(bin, "clang-21").writeText("REALBINARY")
        val link = File(bin, "clang").apply { writeText("clang-21") }

        ModuleInstaller.materializeFlattenedSymlinks(bin)

        // The placeholder must now be either a real symlink or a copy of the target;
        // either way reading it yields the binary content.
        assertTrue(link.exists())
        assertEquals("REALBINARY", link.readText())
    }

    @Test
    fun `markBinariesExecutable sets exec bits on bin files`() {
        val dir = File(tmp.root, "m")
        val clang = File(dir, "bin/clang").apply {
            parentFile.mkdirs()
            writeText("elf")
        }

        val ok = ModuleInstaller.markBinariesExecutable(dir)

        assertTrue(ok)
        assertTrue(clang.canExecute())
    }
}
