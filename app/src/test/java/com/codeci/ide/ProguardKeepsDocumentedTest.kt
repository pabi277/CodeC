package com.codeci.ide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 42.2 — [docs/chat-phase42/PART_42_2_APK_WEIGHT.md §R8 file law]: a
 * ProGuard rule file with unexplained keeps turns into a cargo cult where
 * nobody knows what a line protects, changes rot into it, and there is no
 * WHY to read. This test is the mechanism that keeps the file honest:
 * every -keep* line must be preceded (same section block) by a comment
 * naming the failure it prevents, and the non-negotiable line-table keep
 * must be present.
 */
class ProguardKeepsDocumentedTest {

  private val rules: List<String> by lazy {
    RepoFiles.mainSource("app/proguard-rules.pro").readLines()
  }

  @Test
  fun `keep rules never stand alone - each is preceded by a comment`() {
    val keepLines = rules.withIndex().filter { (_, line) ->
      line.trim().startsWith("-keep")
    }
    keepLines.forEach { (i, line) ->
      // Walk back through contiguous non-empty lines: the block a keep
      // belongs to must contain at least one '#' comment.
      var j = i - 1
      var foundComment = false
      while (j >= 0 && rules[j].isNotBlank()) {
        if (rules[j].trim().startsWith("#")) { foundComment = true; break }
        j--
      }
      assertTrue(
        "rule '${line.trim()}' (line ${i + 1}) is undocumented — every " +
          "keep must carry a comment naming the failure it prevents",
        foundComment)
    }
  }

  @Test
  fun `line-number tables are kept - crash logs must stay readable`() {
    assertTrue(
      "-keepattributes SourceFile,LineNumberTable is non-negotiable " +
        "(a crash record the owner cannot read is the worst third-party flake)",
      rules.any { it.replace(" ", "").contains("SourceFile,LineNumberTable") })
  }

  @Test
  fun `release build type enables minify and shrink resources`() {
    // The host-testable half of "the release build shaves"; the byte
    // numbers themselves are recorded from CI annotations in the part doc.
    val gradle = RepoFiles.mainSource("app/build.gradle.kts").readText()
    val release = Regex("""release\s*\{(.*?)\n    \}""", RegexOption.DOT_MATCHES_ALL)
      .find(gradle)?.groupValues?.get(1) ?: error("release buildType block not found")
    assertTrue("release must minify", release.contains("isMinifyEnabled = true"))
    assertTrue("release must shrink resources", release.contains("isShrinkResources = true"))
    assertTrue(
      "debug stays untouched (R8 is release-only)",
      gradle.contains("debug {") && !Regex("""debug\s*\{(.*?)\n    \}""", RegexOption.DOT_MATCHES_ALL)
        .find(gradle)!!.groupValues[1].contains("isMinifyEnabled"))
  }
}
