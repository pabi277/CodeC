package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 42.2 — [docs/chat-phase42/PART_42_2_APK_WEIGHT.md]: after the
 * 2026-09-11 revert, ONE universal APK per build type is the whole
 * shipping set. The per-ABI split machinery measured 0.94-1.77 % savings
 * under the assets/tcc trap and was reverted by the exit-4 law + the
 * owner's call; this test pins the reverted state so splits cannot
 * quietly come back (if that ever becomes right — e.g. someone ships the
 * packaging-hook or flavor mechanism — the part doc's decision card must
 * be revisited and this test CHANGED DELIBERATELY, in the same commit).
 */
class AbiPolicyTest {

  private val gradle: String by lazy {
    RepoFiles.mainSource("app/build.gradle.kts").readText()
  }

  @Test
  fun `abi splits stay reverts - universal is the whole artifact set`() {
    // The 15 % floor failed (0.94-1.77 % measured, runs 34571675385 /
    // 34572206168): per the part doc's own revert law + the owner's
    // 2026-09-11 decision, an enabled abi splits block must NOT exist.
    val splitsEnabled = Regex("""splits\s*\{(?:(?!^  \}).)*isEnable\s*=\s*true""", RegexOption.DOT_MATCHES_ALL)
      .containsMatchIn(gradle)
    assertFalse(
      "abi splits were REVERTED in 42.2 (savings <15 % per the part doc's " +
        "numbers); re-adding them means updating the doc's decision card too",
      splitsEnabled)
  }

  @Test
  fun `abi filters keep all four natural lanes - x86 included`() {
    // Owner call (2026-09-11): x86 STAYS (32-bit Intel emulator lane);
    // armeabi-v7a stays by exit 5 (32-bit ARM devices are real; their
    // lack of a built-in C compiler is documented behaviour, BETA.md B-4).
    val filters = Regex("""abiFilters\s*\+=\s*listOf\(([^)]*)\)""")
      .find(gradle)?.groupValues?.get(1) ?: error("abiFilters missing")
    for (abi in listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")) {
      assertTrue("abiFilters must keep $abi (owner call, kept in PART_42_2)",
        filters.contains("\"$abi\""))
    }
  }

  @Test
  fun `the update-channel naming hook survives the revert`() {
    // One universal APK still must be BORN named on the updater's grammar,
    // so onVariants/VariantOutputImpl naming stays even with splits gone.
    assertTrue(gradle.contains("androidComponents {") && gradle.contains("onVariants"))
    assertTrue(gradle.contains("CodeC-IDE-\$version-\$abi\$suffix.apk"))
  }
}
