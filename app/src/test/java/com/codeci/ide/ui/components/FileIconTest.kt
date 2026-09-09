package com.codeci.ide.ui.components

import com.codeci.ide.ui.utils.LanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileIconTest {

    @Test
    fun testLanguageRegistryCoverage() {
        val missing = mutableListOf<String>()
        LanguageType.entries.forEach { type ->
            if (type == LanguageType.TEXT) return@forEach
            type.extensions.forEach { ext ->
                val icon = FileIcon.resolve("test.$ext")
                if (icon == FileIcon.Default) {
                    missing.add(ext)
                }
            }
        }
        assertTrue("Missing extensions: $missing", missing.isEmpty())
    }

    @Test
    fun testSpecialFilenames() {
        assertEquals(FileIcon.Dockerfile, FileIcon.resolve("Dockerfile"))
        assertEquals(FileIcon.GitIgnore, FileIcon.resolve(".gitignore"))
        assertEquals(FileIcon.PackageJson, FileIcon.resolve("package.json"))
        assertEquals(FileIcon.Makefile, FileIcon.resolve("Makefile"))
        assertEquals(FileIcon.CMakeLists, FileIcon.resolve("CMakeLists.txt"))
        assertEquals(FileIcon.Config, FileIcon.resolve(".bashrc"))
        assertEquals(FileIcon.Folder, FileIcon.resolve("my_folder", isDirectory = true))
        assertEquals(FileIcon.GitFolder, FileIcon.resolve(".git", isDirectory = true))
    }

    @Test
    fun testCaseInsensitive() {
        val upper = FileIcon.resolve("MAIN.C")
        val lower = FileIcon.resolve("main.c")
        assertEquals(FileIcon.C, upper)
        assertEquals(FileIcon.C, lower)

        val dockerUpper = FileIcon.resolve("DOCKERFILE")
        assertEquals(FileIcon.Dockerfile, dockerUpper)
    }

    @Test
    fun testFallback() {
        assertEquals(FileIcon.Default, FileIcon.resolve("unknown.xyz"))
        assertEquals(FileIcon.Default, FileIcon.resolve("noextension"))
    }

    @Test
}
