package com.codeci.ide

import com.codeci.ide.ui.theme.CodecMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 101 — the five screens hold together as *one* look, in the source.
 *
 * The owner's brief (2026-10-08): *"a deep black background and pure white,
 * high-contrast typography for all text. Each step must feature sleek, custom
 * 3D icons and smooth, modern gradients for buttons and status indicators.
 * Format options in clear, well-defined cards, ending with a prominent,
 * gradient-accented 'Start coding' call-to-action button."*
 *
 * A screenshot cannot be pinned; the decisions that produce it can, and they
 * are all source facts:
 *
 * - **The five screens take no colour from the theme.** Not the tour, not its
 *   logo opening, not its privacy dialog: everything is `OnboardingStage`.
 *   That is what makes "pure white on deep black" true on a phone in light
 *   mode as well as on a phone in dark mode — before this phase, the opening
 *   and the dialog were drawn by `MaterialTheme`, so a light-mode device got a
 *   white flash before the black tour.
 * - **There is exactly one ink.** Every `color =` in the tour is a stage token;
 *   the only two that are not white are the card's hairline and the two surface
 *   fills. The rule is asserted by enumerating them, so a new grey fails.
 * - **Each step's icon is a custom 3D render in a gradient-ringed tile**, and
 *   the render still comes through the one shared card (`StepArt`, Phase 99).
 * - **The gradients do the work the brief gives them**: the CTA, the icon's
 *   ring and halo, and the progress dots.
 * - **The flow still ends on the gradient "Start coding" button**, and the
 *   option cards it ends through are the flow's own cards.
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

    /** The call header: from the opening paren to its match. */
    private fun callHeader(code: String, callAt: Int): String {
        var depth = 0
        for (j in code.indexOf('(', callAt) until code.length) {
            when (code[j]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return code.substring(callAt, j + 1)
                }
            }
        }
        error("unbalanced parens after index $callAt")
    }

    @Test
    fun `the five screens take no colour from the theme`() {
        val tour = code(tourPath)
        // The brief's first clause, as a prohibition: a themed role anywhere in
        // this file is a screen that will not be black-and-white on a device
        // whose theme says otherwise. The logo opening and the privacy dialog
        // were exactly that until this phase.
        assertFalse(
            "no MaterialTheme colour may reach the onboarding screens",
            tour.contains("MaterialTheme.colorScheme"),
        )
        // And the stage is the only source of colour there.
        for (needle in listOf(
            "OnboardingStage.backdrop()",
            "OnboardingStage.accent()",
            "OnboardingStage.iconGlow()",
            "OnboardingStage.STAGE_CARD",
            "OnboardingStage.STAGE_STROKE",
            "OnboardingStage.STAGE_CHIP",
            "OnboardingStage.STAGE_LANE",
            "OnboardingStage.ON_ACCENT",
            "OnboardingStage.STAGE_CONTROL_STROKE",
        )) {
            assertTrue("the tour must draw $needle", tour.contains(needle))
        }
        // The first screen of the five is staged too: a themed logo opening is
        // a flash of a different app before the tour begins.
        val opening = tour.substringAfter("private fun IntroLogoOpening()")
            .substringBefore("private val LogoMarkSize")
        assertTrue(
            "the logo opening is on the stage",
            opening.contains("OnboardingStage.backdrop()") &&
                opening.contains("Color(OnboardingStage.ON_STAGE)"),
        )
        // The setup is the other half of the same flow, and it must NOT be
        // frozen: S4 re-themes the app in front of the user.
        val setup = code(setupPath)
        assertFalse(
            "the setup is theme-driven on purpose (S4 re-themes live) - it must not use the stage",
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
    fun `every word on the stage is pure white`() {
        val tour = code(tourPath)
        // Enumerate every place the file names a colour, and hold it to the
        // stage's palette. White is the only ink; the other three values are
        // the card's hairline and the two surface fills (a card and the
        // agreement chip), which are containers, not type.
        val surfaces = setOf(
            "Color(OnboardingStage.STAGE_STROKE)",
            "Color(OnboardingStage.STAGE_CARD)",
            "Color(OnboardingStage.STAGE_CHIP)",
        )
        val inks = setOf("stageInk", "ink", "Color(OnboardingStage.ON_STAGE)")
        val named = Regex("""color = ([^,\n]+)""").findAll(tour).map { it.groupValues[1].trim() }.toList()
        assertTrue("the scan must find the colours (found ${named.size})", named.size >= 12)
        val unknown = named.filterNot { it in surfaces || it in inks }
        assertTrue(
            "every colour in the tour is a stage token or white: $unknown",
            unknown.isEmpty(),
        )
        // The type half of that: every `Text` that sets its own colour sets it
        // to the stage's white. A `Text` with no colour inherits from the
        // control it sits in (`TextButton(contentColor = stageInk)`), which the
        // same rule covers because those are asserted above.
        var checked = 0
        for (m in Regex("""\bText\s*\(""").findAll(tour)) {
            val header = callHeader(tour, m.range.first)
            val declared = Regex("""color = ([^,\n]+)""").findFirstMatchIn(header)?.groupValues?.get(1)?.trim()
                ?: continue
            checked++
            assertTrue(
                "a word on the stage must be white, found \"$declared\"",
                declared in inks,
            )
        }
        assertTrue("the scan must visit the tour's text (visited $checked)", checked >= 6)
    }

    @Test
    fun `each step's icon is the shared 3D render in a gradient-ringed tile`() {
        val tour = code(tourPath)
        assertTrue("the step icon is drawn by the tile", tour.contains("IntroIconTile("))
        // Still the one illustration path: the render, its required
        // description and its reduced-motion reveal all come from `StepArt`.
        assertTrue(
            "the tile hosts the shared illustration card",
            tour.contains("StepArt(") &&
                tour.contains("art = current.art") &&
                tour.contains("description = current.artDescription") &&
                tour.contains("reveal = heroReveal.value"),
        )
        // The framing is what makes it an icon: a gradient ring, a soft halo,
        // and a tile size that is a token expression (this file is one of the
        // six `TokenAdoptionTest` holds to the scale).
        assertTrue(
            "the ring is the accent gradient",
            tour.contains("border(width = 2.dp, brush = OnboardingStage.accent()"),
        )
        assertTrue(
            "the tile sits on its own halo, which rides the tile's entrance",
            tour.contains("background(OnboardingStage.iconGlow())") &&
                tour.contains(".alpha(reveal.coerceIn(0f, 1f))"),
        )
        assertTrue(
            "the tile size is a token expression, not a literal",
            tour.contains("private val IconTileSize = CodecTokens.space(CodecTokens.Space.HUGE * 3.5f)"),
        )
        assertTrue("the tile is sized by that constant", tour.contains(".size(IconTileSize)"))
        // And it really is an icon now, not the poster it replaced: the 336 dp
        // art panel (HUGE * 7f) is gone from this screen.
        assertFalse(
            "the art is no longer a poster-sized panel",
            tour.contains("Space.HUGE * 7f"),
        )
    }

    @Test
    fun `the gradients draw the button and every status indicator`() {
        val tour = code(tourPath)
        // Button: the same gradient CTA the setup uses, with the stage's accent.
        assertTrue(
            "the primary action is the gradient CTA",
            tour.contains("GradientButton(") &&
                tour.contains("brush = OnboardingStage.accent()") &&
                tour.contains("onAccent = Color(OnboardingStage.ON_ACCENT)"),
        )
        // Status: the progress dots are the gradient over the stage's lane, and
        // the lane exists as a token rather than an inline alpha.
        val dots = tour.substringAfter("private fun IntroDots(").substringBefore("\n@Composable")
        assertTrue("the dots fill with the gradient", dots.contains("OnboardingStage.accent()"))
        assertTrue("the lane is the stage's own", dots.contains("OnboardingStage.STAGE_LANE"))
        assertTrue("and the dots are announced as progress", dots.contains("progressBarRangeInfo"))
        // Status: the icon's ring, and the opening mark's ring.
        assertTrue("the opening mark carries the accent ring", tour.contains("background(OnboardingStage.accent(), shape)"))
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
        // The flow ends on the owner's wording: the last beat's action is the
        // literal "Start coding", and it is that same gradient button.
        val copy = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/setup/SetupFlowCopy.kt").readText()
        assertTrue(
            "the flow must end on Start coding",
            copy.contains("const val BUTTON_FINISH = \"Start coding\""),
        )
        val ready = code(setupPath).substringAfter("private fun ReadyStep(")
        assertTrue(
            "and the summary beat is where it is pressed",
            ready.contains("PrimaryButton(label = SetupFlowCopy.BUTTON_FINISH"),
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
        // The tour cannot use the themed helper - there is no theme yet - so it
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
    fun `the clock is a fallback, so it is quiet and it is short`() {
        // Two more clauses of the brief, in one place: *"reduce the Pause timer
        // affordance"* and let the tour *"advance faster"*.
        //
        // The control is not decoration - a reader who needs longer has to be
        // able to stop the clock - so it keeps its 48 dp target and gains a
        // *fuller* announcement than it had. What it loses is the second and
        // third word, which is what made a fallback look like the page's
        // action. Screen readers get the sentence; the screen gets one word.
        val tour = raw(tourPath)
        assertTrue(
            "the visible label is the state, not a sentence",
            tour.contains("Text(if (timerPaused) \"Resume\" else \"Pause\", maxLines = 1)"),
        )
        assertTrue(
            "and the announcement still names the timer it stops",
            tour.contains("contentDescription = if (timerPaused) \"Resume timer\" else \"Pause timer\""),
        )
        // The control itself: the `TextButton` that owns the toggle, from its
        // own opening paren to just past its lambda.
        val pauseCode = code(tourPath)
        val toggleAt = pauseCode.indexOf("onClick = { timerPaused = !timerPaused }")
        assertTrue("the pause toggle must exist", toggleAt > 0)
        val pauseBlock = pauseCode.substring(
            pauseCode.lastIndexOf("TextButton(", toggleAt),
            toggleAt + 400,
        )
        assertTrue(
            "the quiet control still keeps its touch target",
            pauseBlock.contains("heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH))"),
        )
        assertTrue(
            "and it is still a text button, not a bare glyph",
            pauseBlock.startsWith("TextButton("),
        )
        // The pace. Ten seconds per page was a slide deck's speed for a screen
        // the reader can already swipe past; the band below is the brief's
        // "faster" without going under a readable glance, and the sentence
        // under the page reads the number off the constant so the two cannot
        // drift apart.
        assertTrue(
            "the story window must be faster than the 10 s it was, and still readable (is ${CodecMotion.Duration.STORY} ms)",
            CodecMotion.Duration.STORY in 5_000..8_000,
        )
        assertTrue(
            "the hint must read its number off the constant",
            tour.contains("auto-advances in \${CodecMotion.Duration.STORY / 1000} seconds"),
        )
    }

    @Test
    fun `the stage's palette is one file and every value is used`() {
        val stage = raw(stagePath)
        assertEquals(
            "the stage is twelve consts: two backdrop ends, card, hairline, lane, ink, chip, control edge, halo, and the three accent values",
            12,
            Regex("""const val """).findAll(stage).count(),
        )
        // No dead token: a colour that nothing draws is a colour that will
        // drift. `STAGE_TOP`/`STAGE_BOTTOM` are used by `backdrop()` itself,
        // `ACCENT_TO` and `STAGE_GLOW` by `accent()`/`iconGlow()`; the rest are
        // used by the tour.
        val all = RepoFiles.mainKotlinSources()
            .joinToString("\n") { RepoFiles.codeOnly(it.readText()) }
        for (name in listOf(
            "STAGE_TOP", "STAGE_BOTTOM", "STAGE_CARD", "STAGE_STROKE", "STAGE_LANE",
            "ON_STAGE", "STAGE_CHIP", "STAGE_CONTROL_STROKE", "STAGE_GLOW",
            "ACCENT_FROM", "ACCENT_TO", "ON_ACCENT",
        )) {
            val uses = Regex("""\b$name\b""").findAll(all).count()
            assertTrue("$name is declared but never drawn (found $uses references)", uses >= 2)
        }
        // The brief's "pure white for all text" is also a negative: the two
        // inks this stage used to carry are gone, not merely unused. Scanned
        // on code, not on the file: the docs above are allowed to name them
        // and explain why they went.
        val stageCode = RepoFiles.codeOnly(stage)
        assertFalse("a muted grey ink must not come back", stageCode.contains("ON_STAGE_MUTED"))
        assertFalse("the chip's own ink must not come back", stageCode.contains("ON_STAGE_CHIP"))
    }

    @Test
    fun `the call to action declares its three states`() {
        val gb = code(buttonPath)
        // Pressed: a scale you can feel and a wash you can see - one of them
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

    @Test
    fun `an option card is a card in both of its states`() {
        // "Format options in clear, well-defined cards": the pick beat's cards
        // carry a fill, a rounded shape, a defined edge, the option's own words
        // and a tick when chosen - and the chosen one is edged with the accent.
        val setup = code(setupPath)
        val card = setup.substringAfter("private fun ChoiceCard(").substringBefore("@Composable")
        assertTrue("the card is a surface with a shape", card.contains("RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.L))"))
        assertTrue("it has a defined edge in both states", card.contains("width = if (selected) 2.dp else 1.dp"))
        assertTrue("the resting edge is a real outline", card.contains("listOf(scheme.outline, scheme.outline)"))
        assertTrue("the chosen edge is the accent gradient", card.contains("codecAccentGradient(scheme.primary, scheme.tertiary)"))
        assertTrue("and the chosen card is ticked", card.contains("Icons.Filled.CheckCircle"))
        assertTrue("the card is selectable to a screen reader", card.contains("role = Role.RadioButton"))
    }
}
