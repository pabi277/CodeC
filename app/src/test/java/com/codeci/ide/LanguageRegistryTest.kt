package com.codeci.ide

import com.codeci.ide.ui.services.LanguageRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 21.1 — the language registry is the single source of truth for
 * "how do I build and run this file?". Everything here is pure Kotlin.
 */
class LanguageRegistryTest {

    @Test
    fun forExtension_c_returns_C_profile() {
        assertEquals("C", LanguageRegistry.forExtension("c")?.displayName)
    }

    @Test
    fun forExtension_cpp_cc_cxx_all_resolve() {
        listOf("cpp", "cc", "cxx").forEach { ext ->
            assertEquals("C++ for .$ext", "C++", LanguageRegistry.forExtension(ext)?.displayName)
        }
    }

    @Test
    fun forExtension_py_returns_python_profile() {
        val profile = LanguageRegistry.forExtension("py")
        assertEquals("Python", profile?.displayName)
        assertNull(profile?.buildTemplate)
        assertEquals("python3 \$SRC", profile?.runTemplate)
    }

    @Test
    fun forExtension_is_case_insensitive_and_accepts_a_dot() {
        assertEquals("C", LanguageRegistry.forExtension(".C")?.displayName)
        assertEquals("Python", LanguageRegistry.forExtension("PY")?.displayName)
    }

    @Test
    fun forExtension_html_returns_web_preview_sentinel() {
        val profile = LanguageRegistry.forExtension("html")
        assertEquals(LanguageRegistry.WEB_PREVIEW, profile?.runTemplate)
        assertTrue(profile!!.isWebPreview)
    }

    @Test
    fun forExtension_unknown_returns_null() {
        assertNull(LanguageRegistry.forExtension("xyz"))
        assertNull(LanguageRegistry.forExtension(""))
    }

    @Test
    fun forFile_full_path_resolves() {
        assertEquals("C", LanguageRegistry.forFile("/home/user/main.c")?.displayName)
        assertEquals("Ruby", LanguageRegistry.forFile("/a/b c/script.rb")?.displayName)
    }

    @Test
    fun forFile_extensionless_file_returns_null() {
        assertNull(LanguageRegistry.forFile("/home/user/Makefile"))
        assertNull(LanguageRegistry.forFile("README"))
    }

    @Test
    fun forFile_dotfile_in_a_dotted_directory_is_not_misread() {
        // "/home/u.d/notes" has a dot in the DIRECTORY, not in the leaf.
        assertNull(LanguageRegistry.forFile("/home/u.d/notes"))
    }

    @Test
    fun expandTemplate_substitutes_src_and_out() {
        assertEquals(
            "gcc a.c -o a.out -lm",
            LanguageRegistry.expandTemplate("gcc \$SRC -o \$OUT -lm", "a.c", "a.out")
        )
    }

    @Test
    fun expandTemplate_no_double_substitution() {
        // A path that literally contains "$OUT" must survive verbatim.
        val expanded = LanguageRegistry.expandTemplate("run \$SRC", "/tmp/\$OUT/x.c", "bin/x.out")
        assertEquals("run /tmp/\$OUT/x.c", expanded)
    }

    @Test
    fun outputNameFor_sanitizes_and_appends_out() {
        assertEquals("main.out", LanguageRegistry.outputNameFor("main.c"))
        assertEquals("my_prog.out", LanguageRegistry.outputNameFor("src/my prog.cpp"))
        assertEquals("main.out", LanguageRegistry.outputNameFor(".c"))
    }

    @Test
    fun planFor_compiled_language_in_a_project_builds_into_bin() {
        val profile = LanguageRegistry.forExtension("c")!!
        val plan = LanguageRegistry.planFor(profile, "/p/demo", "src/main.c", "bin")!!
        assertEquals("mkdir -p bin && cc src/main.c -o bin/main.out", plan.build)
        assertEquals("./bin/main.out", plan.run)
        assertEquals(
            "cd /p/demo && mkdir -p bin && cc src/main.c -o bin/main.out && ./bin/main.out",
            plan.terminal
        )
    }

    @Test
    fun planFor_interpreted_language_has_no_build_step() {
        val profile = LanguageRegistry.forExtension("py")!!
        val plan = LanguageRegistry.planFor(profile, "/p/demo", "app.py", "bin")!!
        assertNull(plan.build)
        assertEquals("python3 app.py", plan.run)
        assertEquals("cd /p/demo && python3 app.py", plan.terminal)
    }

    @Test
    fun planFor_quotes_paths_with_spaces() {
        val profile = LanguageRegistry.forExtension("py")!!
        val plan = LanguageRegistry.planFor(profile, "/p/my demo", "a b.py", null)!!
        assertEquals("python3 'a b.py'", plan.run)
        assertTrue(plan.terminal.startsWith("cd '/p/my demo' && "))
    }

    @Test
    fun planFor_web_preview_profile_returns_null() {
        val profile = LanguageRegistry.forExtension("html")!!
        assertNull(LanguageRegistry.planFor(profile, "/p", "index.html", null))
    }

    @Test
    fun no_duplicate_extensions_in_registry() {
        val all = LanguageRegistry.profiles.flatMap { it.extensions }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun all_extensions_are_lowercase_and_dotless() {
        LanguageRegistry.profiles.flatMap { it.extensions }.forEach { ext ->
            assertEquals(ext, ext.lowercase())
            assertFalse(ext.startsWith("."))
        }
    }

    @Test
    fun all_profiles_have_non_blank_display_name_and_run_template() {
        LanguageRegistry.profiles.forEach {
            assertTrue(it.displayName.isNotBlank())
            assertTrue(it.runTemplate.isNotBlank())
        }
    }

    @Test
    fun every_compiled_profile_runs_the_binary_it_builds() {
        LanguageRegistry.profiles.filter { it.buildTemplate != null }.forEach {
            assertTrue("${it.displayName} build must name \$OUT", it.buildTemplate!!.contains("\$OUT"))
            assertTrue("${it.displayName} run must use \$OUT", it.runTemplate.contains("\$OUT"))
        }
    }

    @Test
    fun every_profile_with_a_package_names_a_probe_binary() {
        LanguageRegistry.profiles.filter { it.requiredPackage != null }.forEach {
            assertNotNull("${it.displayName} needs a probeBinary", it.probeBinary)
        }
    }

    @Test
    fun c_uses_the_builtin_tcc_and_is_never_gated_behind_an_install() {
        // Owner decision 2026-09-03: TCC is the default C compiler. A .c file
        // must run offline, out of the box, with no ~90 MB download.
        val c = LanguageRegistry.forExtension("c")!!
        assertNull(c.requiredPackage)
        assertNull(c.probeBinary)
        assertEquals("cc \$SRC -o \$OUT", c.buildTemplate)
    }

    @Test
    fun cpp_still_needs_clang_because_tcc_cannot_build_cpp() {
        val cpp = LanguageRegistry.forExtension("cpp")!!
        assertEquals("clang", cpp.requiredPackage)
        assertEquals("g++", cpp.probeBinary)
    }

    @Test
    fun the_c_build_line_keeps_the_tcc_link_order_with_o_last() {
        // TCC link-order invariant: -o must be the FINAL argument.
        val c = LanguageRegistry.forExtension("c")!!
        assertTrue(c.buildTemplate!!.trimEnd().endsWith("\$OUT"))
        val plan = LanguageRegistry.planFor(c, "/p", "main.c", "bin")!!
        assertTrue(plan.build!!.trimEnd().endsWith("bin/main.out"))
    }

    @Test
    fun only_the_c_profile_compiles_through_the_cc_frontend() {
        // `cc` is CodeC's built-in TCC frontend and is C-only; no other
        // language may route through it.
        LanguageRegistry.profiles
            .filter { it.displayName != "C" }
            .forEach {
                assertFalse(
                    "${it.displayName} must not use the cc frontend",
                    it.buildTemplate?.startsWith("cc ") == true
                )
            }
    }

    @Test
    fun the_registry_never_gates_a_language_the_app_ships_itself() {
        // C (TCC), Shell and HTML are always available — no install prompt.
        listOf("c", "sh", "html").forEach { ext ->
            assertNull(
                "'.$ext' must not require a package",
                LanguageRegistry.forExtension(ext)!!.requiredPackage
            )
        }
    }

    @Test
    fun formatter_command_expands_and_quotes_the_source() {
        val profile = LanguageRegistry.forExtension("c")!!
        assertEquals(
            "clang-format -i 'a b.c'",
            LanguageRegistry.formatterCommand(profile, "a b.c")
        )
        assertNull(LanguageRegistry.formatterCommand(LanguageRegistry.forExtension("php")!!, "x.php"))
    }
}
