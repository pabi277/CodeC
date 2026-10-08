package com.codeci.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 100 — the redesign holds together as *one* look, in the source.
 *
 * The owner's brief (2026-10-08): a minimalist, modern onboarding with a deep
 * dark backdrop, crisp type, custom icons, gradient accents, explicit button
 * states and card-based layouts — "something like this with a better approach
 * that fit CodeC". A screenshot cannot be pinned; the *decisions* that produce
 * it can, and they are all source facts:
 *
 * - **One stage, one theme.** The tour is fixed-stage (`OnboardingStage`): it
 *   runs before the app has a theme, so it cannot borrow the user's. The setup
 *   is theme-driven on purpose — S4 re-themes the app live, which is that
 *   beat's whole point — so it draws its accents from `primary`/`tertiary`.
 *   Both facts are pinned here, including the negative one (the setup must not
 *   import the stage).
 * - **One primary action.** Both halves press the same gradient CTA, and no
 *   screen in the flow keeps a raw Material `Button`: a second button style is
 *   how a design system starts to drift.
 * - **One place the gradient comes from.** `codecAccentGradient` is defined
 *   once; the tour's fixed accent is `OnboardingStage.accent()`.
 * - **The states are real.** Press scale *and* press wash, a disabled fill, no
 *   hover (a touch screen has none), the 48 dp floor inside the component
 *   itself, and a progress bar that keeps its semantics after being painted by
 *   hand.
 *
 * `OnboardingContrastTest` owns the numbers; this test owns the wiring.
 */
class OnboardingStyleTest {

    private val tourPath = "app/src/main/java/com/codeci/ide/ui/screens/FirstRunIntroScreen.kt"
    private val setupPath = "app/src/main/java/com/codeci/ide/ui/setup/SetupFlowScreen.kt"
    private val buttonPath = "app/src/main/java/com/codeci/ide/ui/components/GradientButton.kt"
    private val progressPath = "app/src/main/java/com/codeci/ide/ui/components/GradientProgress.kt"
    private val stagePath = "app/src/main/java/com/codeci/ide/ui/theme/OnboardingStage.kt"

    private fun raw(path: String): String = RepoFiles.mainSource(path).readText()

    private fun code(path: String): String = RepoFiles.codeOnly(raw(path))

    /** `Button(` — never `TextButton(` / `OutlinedButton(` / `Role.Button`. */
    private val materialButton = Regex("""(?<![A-Za-z])Button\s*\(""")

    @Test
    fun `the tour is staged and the setup is themed`() {
        val tour = code(tourPath)
        // The tour's whole surface, ink, chip and accent come from the stage…
        for (needle in listOf(
            "OnboardingStage.backdrop()",
            "OnboardingStage.accent()",
            "OnboardingStage.STAGE_CARD",
            "OnboardingStage.STAGE_STROKE",
            "OnboardingStage.STAGE_CHIP",
            "OnboardingStage.ON_STAGE_CHIP",
            "OnboardingStage.ON_ACCENT",
            "OnboardingStage.STAGE_CONTROL_STROKE",
        )) {
            assertTrue("the tour must draw $needle", tour.contains(needle))
        }
        // …and invents no colour of its own: every value is in OnboardingStage.
        assertFalse(
            "a hex literal in the tour is a colour that escaped the stage",
            Regex("""Color\(\s*0x""").containsMatchIn(tour),
        )
        assertFalse(
            "the tour must not take its accent from the live theme (it runs before one exists)",
            tour.contains("MaterialTheme.colorScheme.primary"),
        )
        // The setup is the other half of the same look, and it must NOT be
        // frozen: S4 re-themes the app in front of the user.
        val setup = code(setupPath)
        assertFalse(
            "the setup is theme-driven on purpose (S4 re-themes live) — it must not use the stage",
            setup.contains("OnboardingStage"),
        )
        assertFalse(
            "the setup must not import the tour's stage object",
            raw(setupPath).contains("import com.codeci.ide.ui.theme.OnboardingStage"),
        )
        assertTrue(
            "the setup's accents follow the theme's own roles",
            setup.contains("codecAccentGradient(scheme.primary, scheme.tertiary)"),
        )
    }

    @Test
    fun `both halves press the same gradient call to action`() {
        assertTrue(
            "the tour's primary action is the gradient CTA",
            code(tourPath).contains("GradientButton("),
        )
        val setup = code(setupPath)
        assertEquals(
            "the setup's PrimaryButton wraps the same CTA (one call site, one style)",
            1,
            Regex("""\bGradientButton\s*\(""").findAll(setup).count(),
        )
        // And nothing keeps a Material button behind: a `Button` that is not
        // `TextButton`/`OutlinedButton` would be a second primary style.
        assertEquals(
            "no raw Material Button survives in the tour",
            0,
            materialButton.findAll(code(tourPath)).count(),
        )
        assertEquals(
            "no raw Material Button survives in the setup",
            0,
            materialButton.findAll(setup).count(),
        )
        assertTrue(
            "the label is still the full-width primary action it was",
            setup.contains("GradientButton(") &&
                setup.contains("onAccent = scheme.onPrimary"),
        )
    }

    @Test
    fun `the gradient is defined once and the tour's is the stage's own`() {
        val sources = RepoFiles.mainKotlinSources()
        val definers = sources.filter { "fun codecAccentGradient(" in RepoFiles.codeOnly(it.readText()) }
        assertEquals(
            "the gradient helper has exactly one definition",
            listOf("OnboardingStage.kt"),
            definers.map { it.name },
        )
        val users = sources
            .filter { "codecAccentGradient(" in RepoFiles.codeOnly(it.readText()) }
            .map { it.name }
            .filter { it != "OnboardingStage.kt" }
        assertEquals(
            "and exactly one half of onboarding paints with it (the themed half)",
            listOf("SetupFlowScreen.kt"),
            users,
        )
        // The tour cannot use the themed helper — there is no theme yet — so it
        // paints the stage's fixed accent instead.
        val tour = code(tourPath)
        assertTrue(tour.contains("brush = OnboardingStage.accent()"))
        assertEquals(
            "the tour must not reach for the themed gradient",
            0,
            Regex("""codecAccentGradient\s*\(""").findAll(tour).count(),
        )
    }

    @Test
    fun `the stage's palette is one file and every value is used`() {
        val stage = raw(stagePath)
        assertEquals(
            "the stage is twelve consts: two backdrop ends, card, hairline, two inks, chip pair, three accent values, one control edge",
            12,
            Regex("""const val """).findAll(stage).count(),
        )
        // No dead token: a colour that nothing draws is a colour that will
        // drift. `STAGE_TOP`/`STAGE_BOTTOM` are used by `backdrop()` itself,
        // `ACCENT_TO` by `accent()`; the rest are used by the tour.
        val all = RepoFiles.mainKotlinSources()
            .joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
        for (name in listOf(
            "STAGE_TOP", "STAGE_BOTTOM", "STAGE_CARD", "STAGE_STROKE",
            "ON_STAGE", "ON_STAGE_MUTED", "STAGE_CHIP", "ON_STAGE_CHIP",
            "ACCENT_FROM", "ACCENT_TO", "ON_ACCENT", "STAGE_CONTROL_STROKE",
        )) {
            val uses = Regex("""\b$name\b""").findAll(all).count()
            assertTrue("$name is declared but never drawn (found $uses references)", uses >= 2)
        }
    }

    @Test
    fun `the call to action declares its three states`() {
        val gb = code(buttonPath)
        // Pressed: a scale you can feel and a wash you can see — one of them
        // alone is invisible under a fingertip or too subtle on the teal end.
        assertTrue("the press has a scale", gb.contains("PRESSED_SCALE"))
        assertTrue("the press has a wash", gb.contains("PRESSED_WASH"))
        assertTrue("and it reads the press, it does not guess it", gb.contains("collectIsPressedAsState"))
        // Disabled: its own fill and its own ink, so a blocked action looks
        // blocked rather than like a quieter call to action.
        assertTrue("the disabled state has its own container", gb.contains("disabledContainer"))
        assertTrue("the disabled state has its own ink", gb.contains("disabledContent"))
        // The one movement it has still obeys the platform: with "remove
        // animations" on, the press snaps and only the wash is left.
        assertTrue(
            "the CTA's own movement respects reduce-motion",
            gb.contains("rememberMotionSpecs()") &&
                gb.contains("motion.floatOrSnap(CodecMotion.effectsSpring)"),
        )
        // No hover: this is a touch screen. `hoverable`/`onHover` would also
        // fight the pressed state on a device with a mouse attached.
        assertFalse("no hover state on a touch screen", gb.contains("hover"))
        // The ripple is off on purpose: it would paint *under* the gradient.
        assertTrue("the ripple is explicitly off", gb.contains("indication = null"))
        assertTrue("the button is a button to a screen reader", gb.contains("role = Role.Button"))
        // The floor lives inside the component, so no call site can forget it
        // (`TouchTargetTest` scans `ui/screens/`, and this component is newer).
        assertTrue(
            "the CTA declares the 48 dp floor itself",
            gb.contains("heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH))"),
        )
    }

    @Test
    fun `the build bar is painted by hand but still announces itself`() {
        val setup = code(setupPath)
        assertTrue(
            "the build beat draws the gradient bar",
            setup.contains("GradientProgress("),
        )
        // `String.count(…)` is Kotlin's character predicate, so this is a
        // `contains`: the point is that Material's flat bar is gone entirely.
        assertFalse(
            "Material's flat bar is gone from the setup",
            setup.contains("LinearProgressIndicator"),
        )
        assertTrue(
            "the bar is filled with the accent gradient, not a flat colour",
            setup.substringAfter("GradientProgress(").contains("codecAccentGradient("),
        )
        val gp = code(progressPath)
        // The semantics Material would have set are set by hand, or the bar is
        // silent to TalkBack.
        assertTrue(
            "the hand-painted bar keeps its progress semantics",
            gp.contains("progressBarRangeInfo = ProgressBarRangeInfo("),
        )
        assertTrue("and it clamps a value it did not produce", gp.contains("coerceIn(0f, 1f)"))
        // The track is the surface, not `surfaceVariant`: see
        // OnboardingContrastTest for the measured 2.46:1 that ruled it out.
        assertTrue(
            "the empty lane is the surface the bar sits on",
            gp.contains("track: Color = MaterialTheme.colorScheme.surface"),
        )
        assertFalse(
            "surfaceVariant is the track that failed the non-text floor",
            gp.contains("surfaceVariant"),
        )
    }
}
