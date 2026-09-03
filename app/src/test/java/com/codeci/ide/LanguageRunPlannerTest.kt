package com.codeci.ide

import com.codeci.ide.ui.services.LanguageRegistry
import com.codeci.ide.ui.services.LanguageRunPlanner
import com.codeci.ide.ui.services.LanguageToolProbe
import com.codeci.ide.ui.services.RunDecision
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 21.1/21.2 — the pure RUN ▶ decision layer and the toolchain probe.
 */
class LanguageRunPlannerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val allInstalled: (String) -> Boolean = { true }
    private val noneInstalled: (String) -> Boolean = { false }

    @Test
    fun c_file_with_gcc_present_produces_an_execute_decision() {
        val decision = LanguageRunPlanner.decide("main.c", "/p/demo", "bin", allInstalled)
        assertTrue(decision is RunDecision.Execute)
        val exec = decision as RunDecision.Execute
        assertEquals("C", exec.profile.displayName)
        assertEquals("mkdir -p bin && gcc main.c -o bin/main.out -lm", exec.plan.build)
        assertEquals("./bin/main.out", exec.plan.run)
    }

    @Test
    fun c_file_without_a_compiler_installs_clang_not_gcc() {
        // Device round 1 (2026-09-03): `pkg install gcc` fails — apt itself
        // says "the following packages replace it: libllvm". Phase 20.1
        // publishes `clang` (a libllvm subpackage) which SHIPS the gcc/g++
        // driver symlinks. Probe gcc, install clang.
        val decision = LanguageRunPlanner.decide("main.c", "/p/demo", "bin", noneInstalled)
        assertTrue(decision is RunDecision.NeedsInstall)
        assertEquals("clang", (decision as RunDecision.NeedsInstall).packageName)
    }

    @Test
    fun no_profile_ever_tries_to_install_a_package_named_gcc() {
        // Standing guard: `gcc` is a binary in the clang deb, never a package.
        LanguageRegistry.profiles.forEach {
            assertFalse("${it.displayName} must not install 'gcc'", it.requiredPackage == "gcc")
        }
    }

    @Test
    fun every_installable_package_is_one_phase_20_1_actually_published() {
        // CODEC_REPOSITORY_PACKAGES (codec-packages/properties.codec.sh) plus
        // the libllvm subpackage names verified live in the dev index.
        val published = setOf(
            "clang", "lld", "llvm", "llvm-tools", "libllvm", "libcompiler-rt",
            "nodejs", "npm", "php", "ruby", "lua54", "python", "python-pip"
        )
        LanguageRegistry.profiles
            .filter { it.requiredPackage != null && it.inRepository }
            .forEach {
                assertTrue(
                    "${it.displayName} wants '${it.requiredPackage}', which is not published",
                    it.requiredPackage in published
                )
            }
    }

    @Test
    fun go_and_rust_report_unavailable_instead_of_a_doomed_install() {
        val go = LanguageRunPlanner.decide("m.go", "/p", null, noneInstalled)
        assertTrue(go is RunDecision.Unavailable)
        assertEquals("golang", (go as RunDecision.Unavailable).packageName)
        assertTrue(LanguageRunPlanner.decide("m.rs", "/p", null, noneInstalled)
            is RunDecision.Unavailable)
    }

    @Test
    fun cpp_probes_gpp_but_installs_the_gcc_package() {
        val probed = mutableListOf<String>()
        val decision = LanguageRunPlanner.decide("m.cpp", "/p", null) { probed += it; false }
        assertEquals(listOf("g++"), probed)
        assertEquals("clang", (decision as RunDecision.NeedsInstall).packageName)
    }

    @Test
    fun python_probes_python3_not_python() {
        val probed = mutableListOf<String>()
        LanguageRunPlanner.decide("a.py", "/p", null) { probed += it; true }
        assertEquals(listOf("python3"), probed)
    }

    @Test
    fun lua_probes_lua_and_installs_lua54() {
        val decision = LanguageRunPlanner.decide("a.lua", "/p", null, noneInstalled)
        assertEquals("lua54", (decision as RunDecision.NeedsInstall).packageName)
    }

    @Test
    fun shell_scripts_never_need_an_install() {
        val decision = LanguageRunPlanner.decide("run.sh", "/p", null, noneInstalled)
        assertTrue(decision is RunDecision.Execute)
        assertEquals("bash run.sh", (decision as RunDecision.Execute).plan.run)
    }

    @Test
    fun html_is_a_web_preview_decision_even_when_nothing_is_installed() {
        assertTrue(
            LanguageRunPlanner.decide("index.html", "/p", null, noneInstalled)
                is RunDecision.WebPreview
        )
    }

    @Test
    fun unknown_extension_is_unsupported() {
        val decision = LanguageRunPlanner.decide("notes.xyz", "/p", null, allInstalled)
        assertEquals("xyz", (decision as RunDecision.Unsupported).extension)
    }

    @Test
    fun scratch_file_without_an_output_dir_builds_next_to_the_source() {
        val decision = LanguageRunPlanner.decide(
            "/files/CodeC/projects/hello.c", "/files/CodeC/projects", null, allInstalled
        ) as RunDecision.Execute
        assertEquals("gcc /files/CodeC/projects/hello.c -o hello.out -lm", decision.plan.build)
        assertEquals("./hello.out", decision.plan.run)
    }

    @Test
    fun install_command_refreshes_the_catalog_before_installing() {
        // Device round 1: a stale catalog produced "E: Unable to locate
        // package" on a device that had never run `pkg update`.
        assertEquals(
            "pkg update && pkg install -y clang",
            LanguageRunPlanner.installCommand("clang")
        )
    }

    @Test
    fun probe_reports_installed_only_when_the_binary_exists() {
        val prefix = temp.newFolder("usr")
        val bin = File(prefix, "bin").apply { mkdirs() }
        assertFalse(LanguageToolProbe.isInstalled(prefix, "gcc"))
        File(bin, "gcc").writeText("#!/bin/sh\n")
        assertTrue(LanguageToolProbe.isInstalled(prefix, "gcc"))
        assertFalse(LanguageToolProbe.isInstalled(prefix, "rustc"))
        assertFalse(LanguageToolProbe.isInstalled(prefix, ""))
    }

    @Test
    fun probe_against_a_missing_prefix_is_false_not_a_crash() {
        assertFalse(LanguageToolProbe.isInstalled(File("/does/not/exist"), "gcc"))
    }
}
