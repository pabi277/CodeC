package com.codeci.ide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 50.4 — the six transitions use the shared specs, the spec
 * constructors live in exactly one file, and the animation-free zones
 * (sora host, coach marks, back navigation) stay animation-free
 * (source scan).
 */
class MotionWiringTest {

    private fun mainSource(path: String): String =
        RepoFiles.mainSource(path).readText()

    @Test
    fun `forward navigation fades through CodecMotion`() {
        val main = mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt")
        assertTrue(main.contains("CodecMotion.tabEnter"))
        assertTrue(main.contains("CodecMotion.tabExit"))
    }

    @Test
    fun `back navigation is always instant`() {
        val main = mainSource("app/src/main/java/com/codeci/ide/MainActivity.kt")
        val navHost = main.substringAfter("NavHost(").substringBefore("\n            ) {")
        assertTrue(
            "NavHost must pin popEnterTransition to None (Phase 49 decides back)",
            navHost.contains("popEnterTransition = { EnterTransition.None }"),
        )
        assertTrue(
            "NavHost must pin popExitTransition to None (Phase 49 decides back)",
            navHost.contains("popExitTransition = { ExitTransition.None }"),
        )
        assertTrue(
            "BackRouter must not reference transitions at all",
            !mainSource("app/src/main/java/com/codeci/ide/ui/navigation/BackRouter.kt")
                .contains("Transition"),
        )
    }

    @Test
    fun `the output panel and find bar use the shared specs`() {
        val editor = mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt",
        )
        assertTrue(editor.contains("CodecMotion.panelEnter"))
        assertTrue(editor.contains("CodecMotion.panelExit"))
        assertTrue(editor.contains("CodecMotion.findEnter"))
        assertTrue(editor.contains("CodecMotion.findExit"))
    }

    @Test
    fun `the chrome run-state and hub crossfades use the shared spec`() {
        val editor = mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt",
        )
        val hub = mainSource(
            "app/src/main/java/com/codeci/ide/ui/screens/FileManagerScreen.kt",
        )
        val panel = mainSource(
            "app/src/main/java/com/codeci/ide/ui/components/OutputPanelView.kt",
        )
        assertTrue(editor.contains("CodecMotion.crossfadeSpec"))
        assertTrue(hub.contains("CodecMotion.crossfadeSpec"))
        assertTrue(panel.contains("CodecMotion.crossfadeSpec"))
    }

    @Test
    fun `spec constructors live only in CodecMotion dot kt`() {
        // `between(` must not trip the `tween(` needle: word boundaries.
        val needles = Regex("""\b(spring|tween|snap|cubicBezier)\s*\(""")
        val offenders = RepoFiles.mainKotlinSources()
            .filter { it.name != "CodecMotion.kt" }
            .filter { needles.containsMatchIn(RepoFiles.codeOnly(it.readText())) }
            .map { it.name }
        assertTrue(
            "spec constructors outside CodecMotion.kt: $offenders",
            offenders.isEmpty(),
        )
        assertTrue(
            "CodecMotion.kt must actually construct specs",
            needles.containsMatchIn(
                RepoFiles.codeOnly(
                    mainSource("app/src/main/java/com/codeci/ide/ui/theme/CodecMotion.kt"),
                ),
            ),
        )
    }

    @Test
    fun `the sora host contains no animation call`() {
        val host = RepoFiles.codeOnly(
            mainSource("app/src/main/java/com/codeci/ide/ui/editor/sora/SoraEditorHost.kt"),
        )
        for (needle in animationNeedles()) {
            assertFalse(
                "SoraEditorHost.kt must not animate ($needle) — Phase 48 owns that layout",
                needle.containsMatchIn(host),
            )
        }
    }

    @Test
    fun `the coach marks contain no animation call`() {
        val guideDir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/guide")
        val hits = guideDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val code = RepoFiles.codeOnly(file.readText())
                animationNeedles().any { it.containsMatchIn(code) }
            }
            .map { it.name }
            .toList()
        assertTrue("animated guide files: $hits", hits.isEmpty())
    }

    private fun animationNeedles(): List<Regex> = listOf(
        Regex("""\bAnimatedVisibility\s*\("""),
        Regex("""\bAnimatedContent\s*\("""),
        Regex("""\bCrossfade\s*\("""),
        Regex("""\bfadeIn\s*\("""),
        Regex("""\bfadeOut\s*\("""),
        Regex("""\bexpandVertically\s*\("""),
        Regex("""\bshrinkVertically\s*\("""),
        Regex("""\bspring\s*\("""),
        Regex("""\btween\s*\("""),
        Regex("""\bsnap\s*\("""),
        Regex("""\bgraphicsLayer\s*\("""),
        Regex("""\bEnterTransition\b"""),
        Regex("""\bExitTransition\b"""),
        Regex("""\banimationSpec\b"""),
        Regex("""CodecMotion"""),
        Regex("""rememberMotionSpecs"""),
    )
}
