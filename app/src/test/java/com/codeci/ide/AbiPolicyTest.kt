package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 42.2 — [docs/chat-phase42/PART_42_2_APK_WEIGHT.md]: the per-ABI
 * artifact set must be exactly the ABI set the natural filters allow. A
 * drift between `ndk.abiFilters` (what native code is BUILT for) and
 * `splits.abi.include` (what is PACKAGED) yields either an APK with no
 * native libs for a split ABI (bigger universal, broken per-ABI) or a
 * split nobody can install.
 */
class AbiPolicyTest {

  private val gradle: String by lazy { RepoFiles.mainSource("app/build.gradle.kts").readText() }

  @Test
  fun `splits include list mirrors abiFilters exactly`() {
    val filters = Regex("""abiFilters\s*\+=\s*listOf\(([^)]*)\)""")
      .find(gradle)?.groupValues?.get(1)
      ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
      ?: error("ndk.abiFilters listOf(...) not found in app/build.gradle.kts")
    val splits = Regex("""include\(([^)]*)\)""")
      .find(gradle)?.groupValues?.get(1)
      ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toList() }
      ?: error("splits.abi.include(...) not found in app/build.gradle.kts")
    assertEquals(
      "splits.abi.include must mirror ndk.abiFilters exactly " +
        "(AbiPolicyTest — a drift means a split ships empty or a filter " +
        "never gets its own artifact)",
      filters, splits)
  }

  @Test
  fun `splits keep the universal apk as the default`() {
    // The universal build is the "just install it" lane; per-ABI is an
    // acceleration, never a requirement (PART_42_2 §5).
    assertTrue(
      "splits { abi { isUniversalApk = true } } must stay — the universal " +
        "APK is the default lane",
      gradle.contains("isUniversalApk = true"))
  }

  @Test
  fun `abi filters keep the true 32-bit ARM lane`() {
    // 42.2 exit 5: armeabi-v7a stays in the natural ABI set so 32-bit ARM
    // devices install; their lack of a built-in C compiler is documented
    // behaviour (BETA.md), not an exclusion.
    val filters = Regex("""abiFilters\s*\+=\s*listOf\(([^)]*)\)""")
      .find(gradle)?.groupValues?.get(1) ?: error("abiFilters missing")
    assertTrue(filters.contains("\"armeabi-v7a\""))
  }
}
