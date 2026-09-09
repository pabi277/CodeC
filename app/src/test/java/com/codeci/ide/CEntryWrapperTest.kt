package com.codeci.ide

import com.codeci.ide.ui.services.CEntryWrapper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 33 — RUN a self-contained C file whose entry is not `main` by
 * wrapping it in a generated main() (owner model: practice files whose entry
 * may be named program01 / solve / run / …).
 */
class CEntryWrapperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fragment = """
        #include <stdio.h>
        #include "projects.h"

        void program01(void)
        {
            int num;
            printf("--- Program 01 ---\n");
            scanf("%d", &num);
        }
    """.trimIndent()

    @Test
    fun `a single non-main function is the entry`() {
        assertFalse(CEntryWrapper.hasMain(fragment))
        assertEquals("program01", CEntryWrapper.singleEntry(fragment))
    }

    @Test
    fun `a file defining main is not wrapped`() {
        val source = """
            #include <stdio.h>
            int main(void) { return 0; }
        """.trimIndent()
        assertTrue(CEntryWrapper.hasMain(source))
        // main is filtered out, so there is no *other* single entry.
        assertNull(CEntryWrapper.singleEntry(source))
    }

    @Test
    fun `several functions with no main are ambiguous`() {
        val source = """
            int helper(void) { return 1; }
            int other(void) { return 2; }
        """.trimIndent()
        assertFalse(CEntryWrapper.hasMain(source))
        assertNull(CEntryWrapper.singleEntry(source))
    }

    @Test
    fun `main with a helper is not wrapped even though one helper exists`() {
        val source = """
            int helper(void) { return 1; }
            int main(void) { return helper(); }
        """.trimIndent()
        assertTrue(CEntryWrapper.hasMain(source))
        assertNull(CEntryWrapper.singleEntry(source))
    }

    @Test
    fun `declarations and calls are not mistaken for definitions`() {
        // prototypes end with ';' — must not count; a call is not a definition.
        val source = """
            #include <stdio.h>
            void program01(void);
            void program02(void) { program01(); }
        """.trimIndent()
        assertEquals(listOf("program02"), CEntryWrapper.functionNames(source))
        assertEquals("program02", CEntryWrapper.singleEntry(source))
    }

    @Test
    fun `wrapper includes the target and calls the entry`() {
        val target = File(tmp.newFolder("proj"), "C Programming/main.c").apply {
            parentFile.mkdirs()
            writeText(fragment)
        }
        val body = CEntryWrapper.wrapperFor(target, "program01")
        assertNotNull(body)
        assertTrue(body!!.contains("#include \"${target.absolutePath.replace('\\', '/')}\""))
        assertTrue(body.contains("int main(void) { program01(); return 0; }"))
    }

    @Test
    fun `write places the wrapper outside the project and re-writes it`() {
        val cache = tmp.newFolder("cache")
        val target = File(tmp.newFolder("p"), "01_x.c").apply { writeText(fragment) }
        val wrapper = CEntryWrapper.write(cache, target, "program01")
        assertNotNull(wrapper)
        assertTrue(wrapper!!.isFile)
        assertFalse(wrapper.absolutePath.startsWith(target.parentFile!!.absolutePath))
        assertTrue(wrapper.readText().contains("program01"))
    }

    @Test
    fun `the real Code-with-C fragment resolves to program01`() {
        // The exact shape fetched from the owner's example repo (minus the
        // header comment) — must produce a single entry named program01.
        val source = """
            #include <stdio.h>
            #include "projects.h"

            void program01(void)
            {
                int num;
                printf("--- Program 01: Number Base Conversion ---\n");
                printf("Enter the number: ");
                scanf("%d", &num);
                printf("Decimal format is     : %d\n", num);
            }
        """.trimIndent()
        assertFalse(CEntryWrapper.hasMain(source))
        assertEquals("program01", CEntryWrapper.singleEntry(source))
    }
}
