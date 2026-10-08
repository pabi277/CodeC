package com.codeci.ide.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeci.ide.R
import com.codeci.ide.ui.components.GradientButton
import com.codeci.ide.ui.components.GradientProgress
import com.codeci.ide.ui.components.StepArt
import com.codeci.ide.ui.projects.ProjectNameProblem
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecType
import com.codeci.ide.ui.theme.codecAccentGradient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 97 — the seven-beat setup flow that follows the privacy agreement.
 *
 * Shape, from the owner's brief (2026-10-08): seven screens, and **the first
 * project is picked and named by the user** — the CodeC Arcade sample stops
 * being an imposition and becomes one option among four (plus its own door on
 * the last screen). Design record and evidence:
 * `docs/research/ONBOARDING_PERSONALISATION_DESIGN_20261008.md`.
 *
 * What this file is responsible for, and nothing else:
 * - rendering [SetupStep] in order, with the progress strip and one back door,
 * - carrying the [SetupChoice] and the [SetupPicks] the user made,
 * - calling [onBuild] once, on the build beat, with the same
 *   `onFileWritten` callback the seeder fires from the disk writes,
 * - handing the finished answers to [onFinish].
 *
 * It owns **no** disk work (that is [SetupSeeding]), **no** decision logic
 * (that is [SetupFlowPolicy]) and **no** words of its own (that is
 * [SetupFlowCopy]) — so the copy and the laws stay host-testable while this
 * file stays a drawing.
 *
 * Accessibility: the title of each beat is a heading, the option rows carry
 * radio roles and their selected state, the build list and the download line
 * are polite live regions (the audit's noted gap: the app had none), and every
 * control keeps [CodecTokens.MIN_TOUCH].
 */
@Composable
fun SetupFlowScreen(
    initialChoice: SetupChoice,
    existingProjectNames: List<String>,
    onSkip: () -> Unit,
    onThemePicked: (SetupTheme) -> Unit,
    onOpenLink: (String) -> Unit,
    /** Creates the project; returns its name, or null when nothing was built. */
    onBuild: suspend (SetupChoice, (String) -> Unit) -> String?,
    onFinish: (SetupChoice, SetupPicks) -> Unit,
    /** The build failed: let the user out to Projects rather than trap them. */
    onAbandon: () -> Unit,
) {
    var step by remember { mutableStateOf(SetupStep.WELCOME) }
    var choice by remember { mutableStateOf(initialChoice) }
    var picks by remember { mutableStateOf(SetupPicks()) }
    var building by remember { mutableStateOf(false) }
    var buildFailed by remember { mutableStateOf(false) }
    // Bumped by "Use C instead": the step stays BUILDING, so the step value
    // alone would not restart the effect.
    var buildToken by remember { mutableStateOf(0) }
    // Written from the seeder's own file callbacks, which run on the IO
    // dispatcher: a snapshot list is written atomically, and every reader here
    // is a recomposition.
    val writtenFiles = remember { mutableStateListOf<String>() }

    val verdict = SetupFlowPolicy.nameVerdict(choice, existingProjectNames)
    val plan = SetupFlowPolicy.planFor(choice)

    // The build beat: one real run, one real file list, never a timer.
    LaunchedEffect(step, buildToken) {
        if (step != SetupStep.BUILDING || building) return@LaunchedEffect
        building = true
        buildFailed = false
        writtenFiles.clear()
        val built = withContext(Dispatchers.IO) {
            // The seeder fires this from its own write loop; a mutable snapshot
            // is what makes a background write to composition state legal.
            onBuild(choice) { path -> Snapshot.withMutableSnapshot { writtenFiles.add(path) } }
        }
        building = false
        if (built == null) {
            buildFailed = true
        } else {
            step = SetupStep.READY
        }
    }

    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        // The flow's one decoration: a soft wash behind the first and last
        // beats. It is not content, so it carries no semantics.
        if (step == SetupStep.WELCOME || step == SetupStep.READY) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = CodecTokens.space(CodecTokens.Space.XL),
                    vertical = CodecTokens.space(CodecTokens.Space.XL),
                ),
        ) {
            StepProgress(index = SetupFlowPolicy.progressIndex(step))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = CodecTokens.MIN_TOUCH.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back is offered while the questions are still being answered
                // (pick -> helps). The build beat is not re-entrant while it is
                // working, and the receipt is a receipt: leaving it is *Start
                // coding*, not a step backwards.
                val canGoBack = step.ordinal in
                    SetupStep.PICK.ordinal..SetupStep.HELPS.ordinal
                if (canGoBack) {
                    SetupFlowPolicy.previous(step)?.let {
                        TextButton(onClick = { step = it }) { Text(SetupFlowCopy.BUTTON_BACK) }
                    }
                }
            }
            Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))

            when (step) {
                SetupStep.WELCOME -> WelcomeStep(
                    onSkip = onSkip,
                    onContinue = { step = SetupStep.PICK },
                )

                SetupStep.PICK -> PickStep(
                    choice = choice,
                    onPick = { start ->
                        choice = SetupChoice(
                            start = start,
                            variantId = SetupFlowPolicy.variantsFor(start).firstOrNull()?.id,
                            projectName = start.defaultProjectName,
                        )
                    },
                    onOpenTemplates = {
                        choice = choice.copy(start = SetupStart.C)
                        step = SetupStep.NAME
                    },
                    onContinue = { step = SetupStep.NAME },
                )

                SetupStep.NAME -> NameStep(
                    choice = choice,
                    verdict = verdict,
                    onVariant = { id -> choice = choice.copy(variantId = id) },
                    onName = { raw -> choice = choice.copy(projectName = raw) },
                    onContinue = { step = SetupStep.LOOKS },
                )

                SetupStep.LOOKS -> LooksStep(
                    picks = picks,
                    plan = plan,
                    onSize = { picks = picks.copy(textSize = it) },
                    onTheme = {
                        picks = picks.copy(theme = it)
                        onThemePicked(it)
                    },
                    onContinue = { step = SetupStep.HELPS },
                )

                SetupStep.HELPS -> HelpsStep(
                    picks = picks,
                    onPlainWords = { picks = picks.copy(plainWords = it) },
                    onHints = { picks = picks.copy(hints = it) },
                    onLines = { picks = picks.copy(lineNumbers = it) },
                    onWrap = { picks = picks.copy(wordWrap = it) },
                    onContinue = { step = SetupStep.BUILDING },
                )

                SetupStep.BUILDING -> BuildingStep(
                    plan = plan,
                    written = writtenFiles.toList(),
                    failed = buildFailed,
                    onAbandon = onAbandon,
                    onEscapeToC = {
                        choice = SetupChoice(
                            start = SetupStart.C,
                            variantId = SetupFlowPolicy.variantsFor(SetupStart.C).firstOrNull()?.id,
                            projectName = SetupStart.C.defaultProjectName,
                        )
                        buildToken += 1
                    },
                )

                SetupStep.READY -> ReadyStep(
                    plan = plan,
                    picks = picks,
                    onOpenLearn = { onOpenLink(LearningLinks.LEARN_URL) },
                    onOpenFaq = { onOpenLink(LearningLinks.FAQ_URL) },
                    onFinish = { onFinish(choice, picks) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- shared pieces

/** The six-segment hairline strip: progress by steps, never a timed bar. */
@Composable
private fun StepProgress(index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XS)),
    ) {
        repeat(SetupFlowPolicy.PROGRESS_SEGMENTS) { segment ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        color = if (segment < index) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

/**
 * Phase 98 — the beat's chip: the same shape as the illustrated tour's eyebrow
 * (`FirstRunIntroScreen.IntroHeading`), so the two halves of onboarding are one
 * design. It is read as ordinary text by a screen reader, in front of the
 * heading `StepTitle` marks as a heading — the chip names the beat's job, which
 * is context, not decoration.
 */
/**
 * Phase 99 - one illustration size for all six beats, so the steps line up. It
 * is deliberately modest: every beat still has to fit its own answers and its
 * *Continue* on a phone screen, and the art is the beat's anchor, not its
 * content.
 */
private val StepArtSize = 168.dp

/** S1's centred mark. */
private val WelcomeMarkSize = 88.dp

/** Phase 100 - the width of the gradient ring around the welcome mark. */
private val WelcomeRing = 2.dp

@Composable
private fun StepEyebrow(text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.XL)),
        color = MaterialTheme.colorScheme.primaryContainer,
        // Phase 100 - the accent gradient as a hairline, exactly as the tour's
        // chip wears it. Here the two ends are the theme's own primary and
        // tertiary, so the chip follows whatever accent the user picked.
        border = BorderStroke(1.dp, codecAccentGradient(scheme.primary, scheme.tertiary)),
        modifier = Modifier
            .padding(top = CodecTokens.space(CodecTokens.Space.S)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = CodecTokens.space(CodecTokens.Space.M),
                vertical = CodecTokens.space(CodecTokens.Space.XS),
            ),
        )
    }
}

@Composable
private fun StepTitle(text: String, small: Boolean = false) {
    Text(
        text = text,
        style = if (small) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.headlineMedium
        },
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = CodecTokens.space(CodecTokens.Space.S))
            .semantics { heading() },
    )
}

@Composable
private fun StepSub(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            top = CodecTokens.space(CodecTokens.Space.XS),
            bottom = CodecTokens.space(CodecTokens.Space.L),
        ),
    )
}

/**
 * Phase 100 - the flow's primary action is the same gradient CTA the tour uses
 * (`ui/components/GradientButton.kt`), so both halves of onboarding press the
 * same way. The two gradient ends are the theme's `primary` and `tertiary`, and
 * the ink on them is the theme's `onPrimary` - the roles Material 3 already
 * guarantees a readable pairing for (and `ChromeContrastTest` already audits).
 */
@Composable
private fun PrimaryButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    GradientButton(
        label = label,
        onClick = onClick,
        enabled = enabled,
        brush = codecAccentGradient(scheme.primary, scheme.tertiary),
        onAccent = scheme.onPrimary,
    )
}

/** A card whose "picture" is the real first line of the file it creates. */
@Composable
private fun ChoiceCard(
    title: String,
    sample: String,
    note: String,
    cost: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (selected) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.L)),
        // Phase 100 - the chosen card is edged with the accent gradient at 2 dp
        // and marked with a tick, so "which one did I pick" is answered by shape
        // and colour instead of by a tint that differs by 2 %.
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            brush = if (selected) {
                codecAccentGradient(scheme.primary, scheme.tertiary)
            } else {
                Brush.horizontalGradient(listOf(scheme.outlineVariant, scheme.outlineVariant))
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CodecTokens.MIN_TOUCH.dp)
            .clickable { onClick() }
            .semantics {
                role = Role.RadioButton
                this.selected = selected
                stateDescription = if (selected) SetupFlowCopy.A11Y_SELECTED else SetupFlowCopy.A11Y_NOT_SELECTED
            },
    ) {
        Row(
            modifier = Modifier.padding(CodecTokens.space(CodecTokens.Space.M)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = sample,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = CodecType.codeFamily,
                    // Phase 100 - this line stays `onSurfaceVariant` even when
                    // the card is chosen. Drawn in `primary` on the tinted card
                    // it measured 3.86:1 for most accents (and 3.96:1 at
                    // Phase 99's 10 % tint): under AA, for a line that is body
                    // text, not a graphic. The chosen card is carried by the
                    // gradient edge, the tick and the tint instead.
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = CodecTokens.space(CodecTokens.Space.XXS)),
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = CodecTokens.space(CodecTokens.Space.XS)),
                )
                Text(
                    text = cost,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = CodecTokens.space(CodecTokens.Space.XXS)),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)),
                )
            }
        }
    }
}

/** One labelled row of segmented choices (S/M/L, Dark/Light/Auto, Plain words/Raw). */
@Composable
private fun SegRow(
    label: String,
    detail: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = CodecTokens.space(CodecTokens.Space.M))) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))
        Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.S))) {
            options.forEachIndexed { index, option ->
                val on = index == selectedIndex
                Surface(
                    color = if (on) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
                    border = BorderStroke(
                        width = if (on) 1.5.dp else 1.dp,
                        color = if (on) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = CodecTokens.MIN_TOUCH.dp)
                        .clickable { onSelect(index) }
                        .semantics {
                            role = Role.RadioButton
                            selected = on
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CodecTokens.space(CodecTokens.Space.M)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CodecTokens.MIN_TOUCH.dp)
            .padding(vertical = CodecTokens.space(CodecTokens.Space.S)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(CodecTokens.space(CodecTokens.Space.S)))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DoorRow(label: String, detail: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CodecTokens.MIN_TOUCH.dp)
            .clickable { onClick() },
    ) {
        Column(modifier = Modifier.padding(CodecTokens.space(CodecTokens.Space.M))) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The dashed "more of the library" line — one row, never a second flow. */
@Composable
private fun LibraryDoor(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CodecTokens.MIN_TOUCH.dp)
            .clickable { onClick() }
            .padding(vertical = CodecTokens.space(CodecTokens.Space.M))
            .semantics { role = Role.Button },
    )
}

// ------------------------------------------------------------------ the beats

/**
 * Phase 99 - S1 is the welcome beat the owner's brief asks for: the CodeC mark
 * centred, the app's promise under it, and then what this flow will really do
 * (the three numbered answers). The two exits stay where Phase 97 put them:
 * *Let us go*, and *Skip setup* - which is the sample game, unchanged. Phase 100
 * gave the mark its accent ring, so S1 stays one picture and one sentence before
 * the choices.
 */
@Composable
private fun WelcomeStep(onSkip: () -> Unit, onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WelcomeMark()
            Text(
                text = SetupFlowCopy.WELCOME_TITLE,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = CodecTokens.space(CodecTokens.Space.L))
                    .semantics { heading() },
            )
            Text(
                text = SetupFlowCopy.WELCOME_TAGLINE,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = CodecTokens.space(CodecTokens.Space.S)),
            )
        }
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.XL)))
        PlanLine("1", SetupFlowCopy.WELCOME_PLAN_1, SetupFlowCopy.WELCOME_PLAN_1_DETAIL)
        PlanLine("2", SetupFlowCopy.WELCOME_PLAN_2, SetupFlowCopy.WELCOME_PLAN_2_DETAIL)
        PlanLine("3", SetupFlowCopy.WELCOME_PLAN_3, SetupFlowCopy.WELCOME_PLAN_3_DETAIL)
        Text(
            text = SetupFlowCopy.WELCOME_TIME,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CodecTokens.space(CodecTokens.Space.M)),
        )
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.L)))
        PrimaryButton(label = SetupFlowCopy.BUTTON_START, onClick = onContinue)
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CodecTokens.MIN_TOUCH.dp),
        ) { Text(SetupFlowCopy.SKIP_LABEL, textAlign = TextAlign.Center) }
    }
}

/**
 * Phase 100 - the centred logo, wearing the accent as a 2 dp ring rather than a
 * flat plate. The tagline below it stays the only other thing on the first
 * screen, which is the whole brief: logo, tagline, then the options.
 */
@Composable
private fun WelcomeMark() {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.XL))
    Box(
        modifier = Modifier
            .padding(top = CodecTokens.space(CodecTokens.Space.XL))
            .size(WelcomeMarkSize)
            .background(codecAccentGradient(scheme.primary, scheme.tertiary), shape),
        contentAlignment = Alignment.Center,
    ) {
        // The inner plate is inset by the ring's width, so the gradient reads as
        // an outline around the mark itself.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(WelcomeRing)
                .clip(shape)
                .background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.app_mark),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            )
        }
    }
}

@Composable
private fun PlanLine(number: String, title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CodecTokens.space(CodecTokens.Space.S)),
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(CodecTokens.space(CodecTokens.Space.XL)),
        )
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PickStep(
    choice: SetupChoice,
    onPick: (SetupStart) -> Unit,
    onOpenTemplates: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepEyebrow(SetupFlowCopy.PICK_EYEBROW)
        StepTitle(SetupFlowCopy.PICK_TITLE, small = true)
        StepSub(SetupFlowCopy.PICK_SUB)
        // Phase 99 - this beat's illustration, on the same card
        // the first-run tour uses (ui/components/StepArt.kt).
        StepArt(
            art = R.drawable.setup_01_pick,
            description = SetupFlowCopy.ART_PICK,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(StepArtSize),
        )
        SetupStart.values().forEach { start ->
            val title = when (start) {
                SetupStart.ARCADE -> SetupFlowCopy.PICK_ARCADE
                SetupStart.C -> SetupFlowCopy.PICK_C
                SetupStart.PYTHON -> SetupFlowCopy.PICK_PYTHON
                SetupStart.WEB -> SetupFlowCopy.PICK_WEB
            }
            val sample = when (start) {
                SetupStart.ARCADE -> SetupFlowCopy.PICK_ARCADE_SAMPLE
                SetupStart.C -> SetupFlowCopy.PICK_C_SAMPLE
                SetupStart.PYTHON -> SetupFlowCopy.PICK_PYTHON_SAMPLE
                SetupStart.WEB -> SetupFlowCopy.PICK_WEB_SAMPLE
            }
            val note = when (start) {
                SetupStart.ARCADE -> SetupFlowCopy.PICK_ARCADE_SUB
                SetupStart.C -> SetupFlowCopy.PICK_C_SUB
                SetupStart.PYTHON -> SetupFlowCopy.PICK_PYTHON_SUB
                SetupStart.WEB -> SetupFlowCopy.PICK_WEB_SUB
            }
            val cost = when (start) {
                SetupStart.ARCADE -> SetupFlowCopy.PICK_ARCADE_COST
                SetupStart.C -> SetupFlowCopy.PICK_C_COST
                SetupStart.PYTHON -> SetupFlowCopy.PICK_PYTHON_COST
                SetupStart.WEB -> SetupFlowCopy.PICK_WEB_COST
            }
            ChoiceCard(
                title = title,
                sample = sample,
                note = note,
                cost = cost,
                selected = choice.start == start,
                onClick = { onPick(start) },
            )
            Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))
        }
        LibraryDoor(label = SetupFlowCopy.PICK_TEMPLATES_DOOR, onClick = onOpenTemplates)
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.L)))
        PrimaryButton(label = SetupFlowCopy.BUTTON_CONTINUE, onClick = onContinue)
    }
}

@Composable
private fun NameStep(
    choice: SetupChoice,
    verdict: com.codeci.ide.ui.projects.ProjectNameVerdict,
    onVariant: (String) -> Unit,
    onName: (String) -> Unit,
    onContinue: () -> Unit,
) {
    val variants = SetupFlowPolicy.variantsFor(choice.start)
    Column(modifier = Modifier.fillMaxWidth()) {
        StepEyebrow(SetupFlowCopy.NAME_EYEBROW)
        StepTitle(SetupFlowCopy.NAME_TITLE, small = true)
        StepSub(SetupFlowCopy.NAME_SUB)
        // Phase 99 - this beat's illustration, on the same card
        // the first-run tour uses (ui/components/StepArt.kt).
        StepArt(
            art = R.drawable.setup_02_name,
            description = SetupFlowCopy.ART_NAME,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(StepArtSize),
        )

        if (variants.isNotEmpty()) {
            Text(
                text = SetupFlowCopy.NAME_START_FROM,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))
            val selectedVariant = SetupFlowPolicy.effectiveVariantId(choice)
            variants.forEach { variant ->
                ChoiceCard(
                    title = variant.label,
                    sample = variant.detail,
                    note = SetupFlowCopy.NAME_VARIANT_LEVEL_PREFIX + variant.difficulty,
                    cost = "",
                    selected = variant.id == selectedVariant,
                    onClick = { onVariant(variant.id) },
                )
                Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))
            }
        }

        OutlinedTextField(
            value = choice.projectName,
            onValueChange = onName,
            singleLine = true,
            isError = verdict.showsError,
            label = { Text(SetupFlowCopy.NAME_TITLE) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CodecTokens.space(CodecTokens.Space.M)),
        )
        val problem = verdict.problem
        if (problem != null) {
            Text(
                text = when (problem) {
                    ProjectNameProblem.EMPTY -> SetupFlowCopy.NAME_ERROR_EMPTY
                    ProjectNameProblem.INVALID -> SetupFlowCopy.NAME_ERROR_INVALID
                    ProjectNameProblem.TAKEN -> SetupFlowCopy.NAME_ERROR_TAKEN
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = CodecTokens.space(CodecTokens.Space.XS)),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.S)),
            modifier = Modifier.padding(top = CodecTokens.space(CodecTokens.Space.S)),
        ) {
            listOf(
                SetupFlowCopy.NAME_HINT_CHIP_1,
                SetupFlowCopy.NAME_HINT_CHIP_2,
                SetupFlowCopy.NAME_HINT_CHIP_3,
            ).forEach { suggestion ->
                TextButton(
                    onClick = { onName(suggestion) },
                    modifier = Modifier.heightIn(min = CodecTokens.MIN_TOUCH.dp),
                ) { Text(suggestion) }
            }
        }

        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.L)))
        PrimaryButton(
            label = SetupFlowCopy.BUTTON_CONTINUE,
            enabled = verdict.ok,
            onClick = onContinue,
        )
    }
}

@Composable
private fun LooksStep(
    picks: SetupPicks,
    plan: SetupPlan,
    onSize: (SetupTextSize) -> Unit,
    onTheme: (SetupTheme) -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepEyebrow(SetupFlowCopy.LOOKS_EYEBROW)
        StepTitle(SetupFlowCopy.LOOKS_TITLE, small = true)
        StepSub(SetupFlowCopy.LOOKS_SUB)
        // Phase 99 - this beat's illustration, on the same card
        // the first-run tour uses (ui/components/StepArt.kt).
        StepArt(
            art = R.drawable.setup_03_looks,
            description = SetupFlowCopy.ART_LOOKS,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(StepArtSize),
        )

        // The mirror: the app's own chrome, carrying the user's own project
        // name. Not a screenshot, not an illustration.
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CodecTokens.space(CodecTokens.Space.M)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${plan.projectName} / ${plan.entryFile}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = CodecType.codeFamily,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)),
                    )
                }
                val size = picks.textSize.fontSp
                Text(
                    text = when (plan.projectType) {
                        "python" -> SetupFlowCopy.MIRROR_PYTHON
                        "c" -> SetupFlowCopy.MIRROR_C
                        else -> SetupFlowCopy.MIRROR_WEB
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = CodecType.codeFamily,
                    fontSize = size.sp,
                    modifier = Modifier.padding(
                        start = CodecTokens.space(CodecTokens.Space.M),
                        end = CodecTokens.space(CodecTokens.Space.M),
                        bottom = CodecTokens.space(CodecTokens.Space.M),
                    ),
                )
            }
        }
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.L)))

        SegRow(
            label = SetupFlowCopy.LOOKS_SIZE,
            detail = SetupFlowCopy.LOOKS_SIZE_DETAIL,
            options = SetupTextSize.values().map { it.label },
            selectedIndex = SetupTextSize.values().indexOf(picks.textSize),
            onSelect = { index -> onSize(SetupTextSize.values()[index]) },
        )
        SegRow(
            label = SetupFlowCopy.LOOKS_THEME,
            detail = SetupFlowCopy.LOOKS_THEME_DETAIL,
            options = SetupTheme.values().map { it.label },
            selectedIndex = SetupTheme.values().indexOf(picks.theme),
            onSelect = { index -> onTheme(SetupTheme.values()[index]) },
        )
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.M)))
        PrimaryButton(label = SetupFlowCopy.BUTTON_CONTINUE, onClick = onContinue)
    }
}

@Composable
private fun HelpsStep(
    picks: SetupPicks,
    onPlainWords: (Boolean) -> Unit,
    onHints: (Boolean) -> Unit,
    onLines: (Boolean) -> Unit,
    onWrap: (Boolean) -> Unit,
    onContinue: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepEyebrow(SetupFlowCopy.HELPS_EYEBROW)
        StepTitle(SetupFlowCopy.HELPS_TITLE, small = true)
        StepSub(SetupFlowCopy.HELPS_SUB)
        // Phase 99 - this beat's illustration, on the same card
        // the first-run tour uses (ui/components/StepArt.kt).
        StepArt(
            art = R.drawable.setup_04_helps,
            description = SetupFlowCopy.ART_HELPS,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(StepArtSize),
        )

        SegRow(
            label = SetupFlowCopy.HELPS_PLAIN,
            detail = SetupFlowCopy.HELPS_PLAIN_DETAIL,
            options = listOf(SetupFlowCopy.HELPS_PLAIN_ON, SetupFlowCopy.HELPS_PLAIN_OFF),
            selectedIndex = if (picks.plainWords) 0 else 1,
            onSelect = { index -> onPlainWords(index == 0) },
        )
        // The change is shown, not described.
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.S)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(CodecTokens.space(CodecTokens.Space.M))) {
                if (picks.plainWords) {
                    Text(
                        text = SetupFlowCopy.HELPS_PLAIN_SAMPLE,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = SetupFlowCopy.HELPS_PLAIN_RAW,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = CodecType.codeFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.M)))

        ToggleRow(
            label = SetupFlowCopy.HELPS_HINTS,
            detail = SetupFlowCopy.HELPS_HINTS_DETAIL,
            checked = picks.hints,
            onChange = onHints,
        )
        ToggleRow(
            label = SetupFlowCopy.HELPS_LINES,
            detail = SetupFlowCopy.HELPS_LINES_DETAIL,
            checked = picks.lineNumbers,
            onChange = onLines,
        )
        ToggleRow(
            label = SetupFlowCopy.HELPS_WRAP,
            detail = SetupFlowCopy.HELPS_WRAP_DETAIL,
            checked = picks.wordWrap,
            onChange = onWrap,
        )
        Text(
            text = SetupFlowCopy.HELPS_AI,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = CodecTokens.space(CodecTokens.Space.M)),
        )
        PrimaryButton(label = SetupFlowCopy.BUTTON_CONTINUE, onClick = onContinue)
    }
}

@Composable
private fun BuildingStep(
    plan: SetupPlan,
    written: List<String>,
    failed: Boolean,
    onEscapeToC: () -> Unit,
    onAbandon: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepEyebrow(SetupFlowCopy.BUILD_EYEBROW)
        StepTitle("${SetupFlowCopy.BUILD_TITLE_PREFIX}${plan.projectName}", small = true)
        StepSub(SetupFlowCopy.BUILD_SUB)
        // Phase 99 - this beat's illustration, on the same card
        // the first-run tour uses (ui/components/StepArt.kt).
        StepArt(
            art = R.drawable.setup_05_build,
            description = SetupFlowCopy.ART_BUILD,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(StepArtSize),
        )
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.M)))
        // Phase 99 - the honest bar: it fills with the files the seeder has
        // really written, never with a timer. The rows below are the same fact,
        // one line per file.
        GradientProgress(
            progress = if (plan.files.isEmpty()) 1f else written.size.toFloat() / plan.files.size,
            brush = codecAccentGradient(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.tertiary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            plan.files.forEach { path ->
                val done = written.contains(path)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CodecTokens.space(CodecTokens.Space.XS)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE)),
                        )
                    } else {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.INLINE)),
                        )
                    }
                    Spacer(Modifier.width(CodecTokens.space(CodecTokens.Space.M)))
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = CodecType.codeFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (plan.needsDownload) {
            Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.M)))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    text = "${SetupFlowCopy.BUILD_DOWNLOAD_PREFIX}${plan.projectType}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = SetupFlowCopy.BUILD_DOWNLOAD_LINE,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.M)))
            TextButton(
                onClick = onEscapeToC,
                modifier = Modifier.heightIn(min = CodecTokens.MIN_TOUCH.dp),
            ) { Text(SetupFlowCopy.BUILD_ESCAPE) }
        } else {
            Text(
                text = SetupFlowCopy.BUILD_NO_DOWNLOAD,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = CodecTokens.space(CodecTokens.Space.M)),
            )
        }

        if (failed) {
            Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.L)))
            Text(
                text = SetupFlowCopy.BUILD_FAILED,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.M)))
            DoorRow(
                label = SetupFlowCopy.BUILD_FAILED_ACTION,
                detail = SetupFlowCopy.BUILD_FAILED_DETAIL,
                onClick = onAbandon,
            )
        }
    }
}

@Composable
private fun ReadyStep(
    plan: SetupPlan,
    picks: SetupPicks,
    onOpenLearn: () -> Unit,
    onOpenFaq: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepEyebrow(SetupFlowCopy.READY_EYEBROW)
        StepArt(
            art = R.drawable.setup_06_ready,
            description = SetupFlowCopy.ART_READY,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(StepArtSize),
        )
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))
        Text(
            text = SetupFlowCopy.READY_TITLE,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(
                    top = CodecTokens.space(CodecTokens.Space.XL),
                    bottom = CodecTokens.space(CodecTokens.Space.L),
                )
                .semantics { heading() },
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.L)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(CodecTokens.space(CodecTokens.Space.M))) {
                Text(
                    text = plan.projectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = SetupFlowPolicy.receiptProjectLine(plan),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = CodecType.codeFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(SetupFlowCopy.READY_CHIP_SIZE_PREFIX)
                        append(picks.textSize.label)
                        append(SetupFlowCopy.READY_CHIP_SEPARATOR)
                        append(picks.theme.label)
                        if (picks.plainWords) {
                            append(SetupFlowCopy.READY_CHIP_SEPARATOR)
                            append(SetupFlowCopy.READY_CHIP_PLAIN_WORDS)
                        }
                        if (picks.hints) {
                            append(SetupFlowCopy.READY_CHIP_SEPARATOR)
                            append(SetupFlowCopy.READY_CHIP_HINTS)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = CodecTokens.space(CodecTokens.Space.S)),
                )
            }
        }
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.L)))
        DoorRow(
            label = LearningLinks.FLOW_DOOR_LABEL,
            detail = LearningLinks.LEARN_SUBTITLE,
            onClick = onOpenLearn,
        )
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))
        DoorRow(
            label = LearningLinks.FAQ_TITLE,
            detail = SetupFlowCopy.READY_FAQ_DOOR_DETAIL,
            onClick = onOpenFaq,
        )
        Text(
            text = SetupFlowCopy.READY_SETTINGS_LINE,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = CodecTokens.space(CodecTokens.Space.M)),
        )
        Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))
        PrimaryButton(label = SetupFlowCopy.BUTTON_FINISH, onClick = onFinish)
    }
}
