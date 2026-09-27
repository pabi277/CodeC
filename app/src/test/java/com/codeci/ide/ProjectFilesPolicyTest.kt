package com.codeci.ide

import com.codeci.ide.ui.editor.ProjectFilesPolicy
import com.codeci.ide.ui.editor.ProjectSearch
import com.codeci.ide.ui.projects.FileTreeRepository
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectFilesPolicyTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `useful configuration is visible and searchable without exposing internals`() {
        listOf(".env", ".env.local", ".gitignore", ".editorconfig", "Dockerfile", "Makefile").forEach {
            assertTrue(it, ProjectFilesPolicy.visible(it, false))
            assertTrue(it, ProjectSearch.isSearchable(it))
        }
        listOf(".git", "node_modules", "build", ".gradle", ".venv", "__pycache__").forEach {
            assertFalse(it, ProjectFilesPolicy.visible(it, true))
        }
        assertFalse(ProjectFilesPolicy.visible(".secret", false))
        assertTrue(ProjectFilesPolicy.visible(".github", true))
    }

    @Test fun `filtered tree and search agree about eligible config paths`() {
        val root = folder.newFolder()
        val paths = listOf(".env", "Dockerfile", ".github/workflows/build.yml", ".git/config.txt", "dist/out.txt")
        paths.forEach { File(root, it).apply { parentFile.mkdirs(); writeText("needle") } }
        val tree = FileTreeRepository.buildTree(root, setOf(".github", ".github/workflows"),
            include = { ProjectFilesPolicy.visible(it.name, it.isDirectory) })
        val visible = FileTreeRepository.flattenVisible(tree).map { it.relativePath }
        val hits = ProjectSearch.search(root, "needle").map { it.relativePath }
        assertEquals(setOf(".env", "Dockerfile", ".github/workflows/build.yml"), hits.toSet())
        assertTrue(visible.containsAll(hits))
        // The Projects hub's default repository call has not acquired a new filter.
        assertTrue(FileTreeRepository.buildTree(root).children.any { it.relativePath == ".git" })
    }

    @Test fun `cap probe distinguishes exactly two hundred from more even in one file`() {
        val root = folder.newFolder()
        val file = File(root, "a.txt")
        file.writeText("hit\n".repeat(200))
        assertEquals(200, ProjectSearch.search(root, "hit", limit = 201).size)
        file.appendText("hit")
        assertEquals(201, ProjectSearch.search(root, "hit", limit = 201).size)
        assertEquals(200, ProjectSearch.search(root, "hit").size)
    }

    @Test fun `cancelled walk cannot produce results`() {
        val root = folder.newFolder()
        File(root, "a.txt").writeText("hit")
        val result = runCatching {
            ProjectSearch.search(root, "hit", checkActive = { throw IllegalStateException("cancelled") })
        }
        assertTrue(result.isFailure)
    }

    @Test fun `entry matching includes descendants but never similarly named siblings`() {
        assertTrue(ProjectFilesPolicy.affects("src", "src"))
        assertTrue(ProjectFilesPolicy.affects("src", "src/nested/a.c"))
        assertFalse(ProjectFilesPolicy.affects("src", "src2/a.c"))
        assertFalse(ProjectFilesPolicy.affects("a.c", "a.cpp"))
    }
}
