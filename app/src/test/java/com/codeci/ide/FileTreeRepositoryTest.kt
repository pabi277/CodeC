package com.codeci.ide

import com.codeci.ide.ui.projects.FileNode
import com.codeci.ide.ui.projects.FileTreeRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileTreeRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `tree is directories first and expansion flattens descendants`() {
        val root = tmp.newFolder("project")
        File(root, "src/main.c").apply { parentFile?.mkdirs(); writeText("main") }
        File(root, "README.md").writeText("readme")
        File(root, "include/calc.h").apply { parentFile?.mkdirs(); writeText("header") }

        val collapsed = FileTreeRepository.flattenVisible(FileTreeRepository.buildTree(root))
        assertEquals(listOf("include", "src", "README.md"), collapsed.map { it.file.name })
        assertTrue(collapsed[0] is FileNode.DirectoryNode)

        val expanded = FileTreeRepository.flattenVisible(
            FileTreeRepository.buildTree(root, setOf("src", "include"))
        )
        assertEquals(
            listOf("include", "calc.h", "src", "main.c", "README.md"),
            expanded.map { it.file.name }
        )
    }

    @Test
    fun `nested mutations remain confined to project root`() {
        val root = tmp.newFolder("project")
        FileTreeRepository.createDirectory(root, "", "src").getOrThrow()
        val created = FileTreeRepository.createFile(root, "src", "main.c", "code").getOrThrow()
        assertEquals("src/main.c", created)
        assertTrue(File(root, created).isFile)

        val renamed = FileTreeRepository.rename(root, created, "app.c").getOrThrow()
        assertEquals("src/app.c", renamed)
        assertTrue(File(root, renamed).isFile)
        assertFalse(FileTreeRepository.delete(root, "../project"))
        assertTrue(FileTreeRepository.delete(root, renamed))
    }
}
