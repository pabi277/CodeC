package com.codeci.ide

import com.codeci.ide.ui.projects.ProjectPathUtils
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectPathUtilsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `relative paths reject traversal and absolute paths`() {
        assertEquals("src/main.c", ProjectPathUtils.sanitizeRelativePath("src/main.c"))
        assertNull(ProjectPathUtils.sanitizeRelativePath("../outside.c"))
        assertNull(ProjectPathUtils.sanitizeRelativePath("src/../main.c"))
        assertNull(ProjectPathUtils.sanitizeRelativePath("/absolute.c"))
        assertNull(ProjectPathUtils.sanitizeRelativePath("src//main.c"))
    }

    @Test
    fun `resolveInside accepts nested files and stays below root`() {
        val root = tmp.newFolder("project")
        val nested = File(root, "src/main.c").apply {
            parentFile?.mkdirs()
            writeText("int main(void) { return 0; }")
        }

        assertEquals(nested.canonicalPath, ProjectPathUtils.resolveInside(root, "src/main.c")?.canonicalPath)
        assertTrue(ProjectPathUtils.resolveInside(root, "src/main.c")!!.path.startsWith(root.canonicalPath))
        assertNull(ProjectPathUtils.resolveInside(root, "../main.c"))
    }
}
