package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 42.2 — [docs/chat-phase42/PART_42_2_APK_WEIGHT.md §assets/tcc]: the
 * bundled-engine set is NEVER allowed to drift from the split set. For every
 * ABI in the natural filter list, either:
 *   - jniLibs/<abi>/libtcc.so AND assets/tcc/<abi> both exist (the offline
 *     C toolchain rides the artifact), or
 *   - the ABI is in NO_BUNDLED_TCC, and then EmbeddedCompiler.tccBinary()
 *     already returns null for it (typed null at apiDir==null, Phase 33.3).
 *
 * Note the trap this protects: splits/abiFilters filter ONLY native libs.
 * assets/tcc/<abi> (≈7.3 MB arm64 / 3.6 MB x86_64) rides every split until
 * the owner picks an exclude mechanism — recorded in the part doc.
 */
class BundledEngineAbiCoverageTest {

  companion object {
    /** ABIs that ship in abiFilters but carry no bundled TCC by design. */
    val NO_BUNDLED_TCC = setOf("armeabi-v7a", "x86")
  }

  private val gradle: String by lazy {
    RepoFiles.mainSource("app/build.gradle.kts").readText()
  }

  private fun abiFilters(): List<String> =
    Regex("""abiFilters\s*\+=\s*listOf\(([^)]*)\)""")
      .find(gradle)?.groupValues?.get(1)
      ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
      ?: error("ndk.abiFilters listOf(...) not found in app/build.gradle.kts")

  @Test
  fun `every split abi has consistent bundled or no-bundled material`() {
    for (abi in abiFilters()) {
      val lib = RepoFiles.mainSource("app/src/main/jniLibs/$abi/libtcc.so")
      val assets = RepoFiles.mainSource("app/src/main/assets/tcc/$abi")
      if (abi in NO_BUNDLED_TCC) {
        assertTrue("$abi is declared offline-no-bundled-TCC, so the .so must NOT ship", !lib.isFile)
        assertTrue("$abi is declared offline-no-bundled-TCC, so assets/tcc/$abi must NOT ship", !assets.isDirectory)
      } else {
        assertTrue("jniLibs/$abi/libtcc.so must ship (ABI is filtered but not declared no-bundled)", lib.isFile)
        assertTrue("assets/tcc/$abi must ship (the toolchain sysroot/include set)", assets.isDirectory)
      }
    }
  }

  @Test
  fun `embedded compiler abi list matches jniLibs exactly`() {
    // The ARMs/x86_64 pair EmbeddedCompiler supports is exactly the set of
    // jniLibs dirs that carry libtcc.so — the drift this test pins is a
    // device that silently loses its offline compiler after a filter tweak.
    val source = RepoFiles.mainSource(
      "app/src/main/java/com/codeci/ide/ui/services/EmbeddedCompiler.kt").readText()
    val abiDirs = Regex("""ABI_DIRS\s*=\s*listOf\(([^)]*)\)""")
      .find(source)?.groupValues?.get(1)
      ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
      ?: error("EmbeddedCompiler.ABI_DIRS not found")
    val jniLibs = RepoFiles.mainSource("app/src/main/jniLibs")
      .listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted()
      ?: error("jniLibs tree missing")
    assertEquals("EmbeddedCompiler.ABI_DIRS must equal the jniLibs ABI dirs",
      abiDirs.sorted(), jniLibs)
  }

  @Test
  fun `no-bundled set is the true 32-bit lanes`() {
    // Recorded, not assumed: armeabi-v7a (a 32-bit ARM device like the
    // J7 Prime) and x86 (emulator lane) carry no bundled C compiler.
    assertEquals(setOf("armeabi-v7a", "x86"), NO_BUNDLED_TCC)
  }
}
