package com.codeci.ide.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codeci.ide.R
import com.codeci.ide.ui.navigation.BackAction
import com.codeci.ide.ui.navigation.BackRouter
import com.codeci.ide.ui.navigation.BackState
import com.codeci.ide.ui.theme.CodecMotion
import com.codeci.ide.ui.theme.CodecPalette
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.rememberMotionSpecs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Phase 98 — one page of the illustrated tour.
 *
 * The tour's words and art live together in [INTRO_STORIES] rather than inline
 * in the composition, so `IntroStoriesTest` can check the deck the way the
 * other screens' copy is checked: every page names a real file, every page
 * carries a description for TalkBack, and the five pages stay in the order the
 * tour promises (offline first, then the edit-run-check loop, the toolkit, the
 * sample projects, and finally the agreement).
 *
 * [art] is a bundled WebP card (`app/src/main/res/drawable-nodpi/intro_*.webp`,
 * 900 px, ~25 KB each). The renders carry their own pale backdrop in the app's
 * green, so they are used as they were drawn instead of being tinted per
 * accent — the same reason the colour is not a `MaterialTheme` role.
 */
data class IntroStory(
    val eyebrow: String,
    val title: String,
    val body: String,
    @DrawableRes val art: Int,
    /** What the illustration shows, for a screen reader (never null: art with no words is a hole). */
    val artDescription: String,
    /** One extra honest line, only where a page has a fact worth keeping. */
    val caption: String? = null,
)

/**
 * The five pages, in the order the first run shows them. Page 5 is the privacy
 * agreement, which is the only *required* one; the first four are the tour the
 * owner asked for (2026-10-08): **coding offline**, the **edit-run-check loop**,
 * the **toolkit**, and the **sample projects**.
 */
val INTRO_STORIES: List<IntroStory> = listOf(
    IntroStory(
        eyebrow = "OFFLINE-FIRST CODING",
        title = "A coding studio in your pocket.",
        body = "Write and run C, Python, JavaScript, and HTML on your phone. C works offline with no setup; web projects open in an on-device preview. No account required.",
        art = R.drawable.intro_01_offline,
        artDescription = "A phone showing a code editor with a green tick on its screen, a crossed-out network arrow beside it, and a green compiler chip: coding that works with no internet.",
    ),
    IntroStory(
        eyebrow = "EDIT · RUN · CHECK",
        title = "Keep the whole loop together.",
        body = "Open a project, edit a file, tap RUN, then check output or compiler errors. Save and run again to test your changes. Web projects open in CodeC's local preview.",
        art = R.drawable.intro_02_loop,
        artDescription = "Three cards joined in a circle by two arrows: a pencil on lines of code, a green play button, and an output card with a green tick — the edit, run and check loop.",
    ),
    IntroStory(
        eyebrow = "TOOLKIT WHEN YOU NEED IT",
        title = "Start simple. Add tools later.",
        body = "C works offline with no setup. Python, Node, shell commands, and packages need optional Linux tools; start that setup from Terminal or Packages only when you choose. Nothing downloads during this tour.",
        art = R.drawable.intro_03_tools,
        artDescription = "A terminal window with a glowing prompt, surrounded by a wrench, a gear, a sealed package with a download arrow and a stack of modules — extra tools that plug in when you ask for them.",
    ),
    IntroStory(
        eyebrow = "SAMPLE PROJECTS",
        title = "Meet CodeC Arcade.",
        body = "Tap RUN to open your game arena. Pick Snake, Block Party (a block-blast-style puzzle), or Tic-Tac-Toe. The home screen, styles, game rules, and controls live in separate files—play first, then explore one piece at a time.",
        art = R.drawable.intro_04_sample,
        artDescription = "A game controller beside a project folder with a rocket, behind three tiles showing a snake, stacked blocks with a falling piece, and a tic-tac-toe grid.",
        caption = "Three familiar games, ready offline. No account, install, or network connection needed.",
    ),
    IntroStory(
        eyebrow = "BEFORE YOU START",
        title = "Your code. Your call.",
        body = "Projects stay in CodeC on this device by default. There are no ads, analytics, or tracking. Git, package downloads, updates, and crash sharing happen only when you choose.",
        art = R.drawable.intro_05_privacy,
        artDescription = "A shield with a padlock in front of a phone showing a folder, with a dotted line to a cloud crossed out — projects stay on this device until you choose otherwise.",
    ),
)

/**
 * First-run starts with the CodeC mark, then becomes a swipeable story tour with
 * one 3D illustration per page. Only the privacy acknowledgement is required; it
 * is an in-app summary, not a claim that CodeC has a separate Terms of Service.
 * Device permissions are still requested only when a feature needs them.
 */
@Composable
fun FirstRunIntroScreen(
    preparing: Boolean,
    onStart: () -> Unit,
    logoRemainingMs: Long = FIRST_RUN_LOGO_DURATION_MS,
) {
    var storiesVisible by rememberSaveable { mutableStateOf(logoRemainingMs <= 0L) }

    LaunchedEffect(logoRemainingMs) {
        if (logoRemainingMs > 0L) delay(logoRemainingMs)
        storiesVisible = true
    }

    if (!storiesVisible) {
        IntroLogoOpening()
        return
    }

    FirstRunStories(preparing = preparing, onStart = onStart)
}

const val FIRST_RUN_LOGO_DURATION_MS = 2_000L

@Composable
private fun IntroLogoOpening() {
    val background = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(background, surfaceVariant.copy(alpha = 0.42f))))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.S)),
        ) {
            Image(
                painter = painterResource(R.drawable.app_mark),
                contentDescription = "CodeC app logo",
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.L))),
            )
            Text(
                text = "CodeC",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "YOUR POCKET CODING STUDIO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FirstRunStories(
    preparing: Boolean,
    onStart: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }
    var showPrivacyDetails by rememberSaveable { mutableStateOf(false) }
    var timerPaused by rememberSaveable { mutableStateOf(false) }
    val motion = rememberMotionSpecs()
    val heroReveal = remember(page) { Animatable(0f) }
    val storyProgress = remember(page) { Animatable(0f) }
    val storyScrollState = remember(page) { ScrollState(0) }

    LaunchedEffect(page, motion.useSpring) {
        heroReveal.snapTo(0f)
        if (motion.useSpring) {
            heroReveal.animateTo(1f, animationSpec = CodecMotion.introReveal)
        } else {
            heroReveal.snapTo(1f)
        }
    }

    // This is elapsed story time, not decorative movement: it remains a real
    // ten-second reading window even when Android's reduced-motion switch is on.
    LaunchedEffect(page, showPrivacyDetails, preparing, timerPaused) {
        if (!showPrivacyDetails && !preparing && !timerPaused) {
            val remainingMs = (
                CodecMotion.Duration.STORY * (1f - storyProgress.value)
            ).roundToInt().coerceAtLeast(1)
            storyProgress.animateTo(
                targetValue = 1f,
                animationSpec = CodecMotion.storyTimer(remainingMs),
            )
            if (page < INTRO_PAGE_COUNT - 1) page++
        }
    }

    BackHandler(enabled = page > 0 && !showPrivacyDetails) {
        when (BackRouter.decide(BackState(firstRunIntroPage = page))) {
            BackAction.PreviousIntroPage -> page = (page - 1).coerceAtLeast(0)
            else -> Unit
        }
    }

    val background = MaterialTheme.colorScheme.background
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val swipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        CodecTokens.space(CodecTokens.Space.XXL).toPx()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(background, surfaceVariant.copy(alpha = 0.36f))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.S)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodecTokens.space(CodecTokens.Space.L)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(CodecTokens.space(CodecTokens.Space.HUGE))
                            .clip(RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)))
                            .background(Color(CodecPalette.SURFACE_PANEL)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.app_mark),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(CodecTokens.space(CodecTokens.Space.S)))
                    Column {
                        Text(
                            text = "CodeC",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "APP TOUR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (page < INTRO_PAGE_COUNT - 1) {
                    TextButton(
                        onClick = { page = INTRO_PAGE_COUNT - 1 },
                        modifier = Modifier
                            .heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH))
                            .semantics { contentDescription = "Skip to agreement" },
                    ) {
                        Text("Skip")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(page, swipeThresholdPx, preparing, showPrivacyDetails) {
                        var horizontalDrag = 0f
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { _, dragAmount -> horizontalDrag += dragAmount },
                            onDragEnd = {
                                if (!preparing && !showPrivacyDetails) {
                                    when {
                                        horizontalDrag > swipeThresholdPx -> page = (page - 1).coerceAtLeast(0)
                                        horizontalDrag < -swipeThresholdPx -> page = (page + 1).coerceAtMost(INTRO_PAGE_COUNT - 1)
                                    }
                                }
                                horizontalDrag = 0f
                            },
                            onDragCancel = { horizontalDrag = 0f },
                        )
                    }
                    .verticalScroll(storyScrollState)
                    .padding(horizontal = CodecTokens.space(CodecTokens.Space.L))
            ) {
                Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.M)))
                Crossfade(
                    targetState = page,
                    animationSpec = motion.floatOrSnap(CodecMotion.crossfadeSpec),
                    label = "first-run-story",
                ) { currentPage ->
                    val current = INTRO_STORIES[currentPage]
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.L)),
                    ) {
                        IntroArtwork(story = current, reveal = heroReveal.value)
                        IntroHeading(story = current)
                        current.caption?.let { caption ->
                            Text(
                                text = caption,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (currentPage == INTRO_PAGE_COUNT - 1) {
                            PrivacySummary(onReadDetails = { showPrivacyDetails = true })
                            PrivacyAgreement(
                                accepted = privacyAccepted,
                                onAcceptedChange = { privacyAccepted = it },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.L)))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = CodecTokens.space(CodecTokens.Space.L),
                        vertical = CodecTokens.space(CodecTokens.Space.S),
                    ),
                verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XS)),
            ) {
                // Phase 98 — the honest progress indicator, at the bottom where
                // the tour's own art and words are not competing with it: one
                // dot per story, the current one wide and filling with the real
                // reading window (never a decorative animation).
                IntroDots(page = page, progress = storyProgress.value)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            timerPaused -> "Paused · swipe or use Next to continue"
                            page == INTRO_PAGE_COUNT - 1 ->
                                "Final story · the timer waits here for your agreement"
                            else -> "Swipe left or right · auto-advances in 10 seconds"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { timerPaused = !timerPaused },
                        modifier = Modifier.heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH)),
                    ) {
                        Text(if (timerPaused) "Resume timer" else "Pause timer", maxLines = 1)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.S)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (page > 0) {
                        OutlinedButton(
                            onClick = { page-- },
                            enabled = !preparing,
                            modifier = Modifier
                                .weight(0.85f)
                                .heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH)),
                        ) {
                            Text("Back")
                        }
                    }
                    Button(
                        onClick = {
                            if (page < INTRO_PAGE_COUNT - 1) page++ else onStart()
                        },
                        enabled = !preparing && (page < INTRO_PAGE_COUNT - 1 || privacyAccepted),
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            text = when {
                                preparing -> "Preparing your first project…"
                                page == INTRO_PAGE_COUNT - 1 -> "Agree & start coding"
                                else -> "Next"
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    if (showPrivacyDetails) {
        AlertDialog(
            onDismissRequest = { showPrivacyDetails = false },
            icon = { Icon(Icons.Filled.Security, contentDescription = null) },
            title = { Text("CodeC privacy, in plain language") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.S)),
                ) {
                    PrivacyBullet("Your source projects are stored in CodeC's app storage by default.")
                    PrivacyBullet("Shared-folder access is optional. If you decline it, CodeC can still open a folder or file through Android's picker.")
                    PrivacyBullet("No advertising, analytics, tracking, or automatic crash-report uploads are included.")
                    PrivacyBullet("Git, package downloads and app updates connect only when you start those actions.")
                    PrivacyBullet("Crash details stay on this device unless you choose a share, copy, or send action.")
                    Text(
                        text = "More details, including the full permission table, are available in Settings → About → Privacy & permissions and in the CodeC repository's privacy note.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDetails = false }) { Text("Got it") }
            },
        )
    }
}

private const val INTRO_PAGE_COUNT = 5

/**
 * Phase 98 — the page's hero: the bundled 3D render on a card, square, centred,
 * capped so a tablet does not get a poster. The reveal is the same
 * reduced-motion-aware `CodecMotion.introReveal` the tour always used; it is
 * applied to the art, which is the thing worth revealing.
 */
@Composable
private fun IntroArtwork(story: IntroStory, reveal: Float) {
    val settled = reveal.coerceIn(0f, 1f)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = CodecTokens.space(CodecTokens.Space.HUGE * 7f))
            .aspectRatio(1f)
            .graphicsLayer {
                alpha = settled
                scaleX = 0.94f + 0.06f * settled
                scaleY = scaleX
            },
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.XL)),
        colors = CardDefaults.cardColors(containerColor = Color(CodecPalette.INTRO_ART_BACKDROP)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = CodecTokens.elevation(CodecTokens.Elevation.CARD),
        ),
    ) {
        Image(
            painter = painterResource(story.art),
            contentDescription = story.artDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(CodecTokens.space(CodecTokens.Space.S)),
        )
    }
}

/**
 * Phase 98 — eyebrow chip, bold heading, subtext: the shape the owner's
 * reference tour uses, centred. The chip is the theme's own container colour,
 * so it follows the accent the user picked in setup.
 */
@Composable
private fun IntroHeading(story: IntroStory) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.S)),
    ) {
        Surface(
            shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.XL)),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = story.eyebrow,
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
        Text(
            text = story.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = story.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Phase 98 — the progress indicator, at the bottom of the tour (the owner's
 * reference, and where a thumb is not covering the words). One dot per story;
 * the current dot is a wide pill that fills with the page's real ten-second
 * window, so it measures something true rather than decorating. Screen readers
 * get the page and the elapsed fraction in one sentence.
 */
@Composable
private fun IntroDots(page: Int, progress: Float) {
    val normalized = progress.coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Story ${page + 1} of $INTRO_PAGE_COUNT, ${(normalized * 100).toInt()} percent of its reading window"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    (page + normalized) / INTRO_PAGE_COUNT,
                    0f..1f,
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(INTRO_PAGE_COUNT) { index ->
            val current = index == page
            Box(
                modifier = Modifier
                    .padding(horizontal = CodecTokens.space(CodecTokens.Space.XXS))
                    .width(
                        CodecTokens.space(
                            if (current) CodecTokens.Space.XXL else CodecTokens.Space.S
                        )
                    )
                    .height(CodecTokens.space(CodecTokens.Space.XS))
                    .clip(CircleShape)
                    .background(
                        when {
                            index < page -> MaterialTheme.colorScheme.primary
                            current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                        }
                    ),
            ) {
                if (current && normalized > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(normalized)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacySummary(onReadDetails: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XS)),
    ) {
        PrivacyBullet("Shared-folder access is optional; Android's file picker remains available.")
        PrivacyBullet("Git, package downloads and updates use the network only when you start them.")
        TextButton(
            onClick = onReadDetails,
            modifier = Modifier.heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH)),
        ) {
            Text("Read the full privacy summary")
        }
    }
}

@Composable
private fun PrivacyBullet(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.S)),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = CodecTokens.space(CodecTokens.Space.XS))
                .size(CodecTokens.space(CodecTokens.Space.XS))
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PrivacyAgreement(accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = accepted,
                role = Role.Checkbox,
                onValueChange = onAcceptedChange,
            ),
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.M)),
        color = if (accepted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (accepted) MaterialTheme.colorScheme.primary.copy(alpha = 0.72f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.42f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = CodecTokens.space(CodecTokens.Space.S)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XXS)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = null)
                Spacer(Modifier.width(CodecTokens.space(CodecTokens.Space.XS)))
                Text(
                    text = "I understand and accept this privacy summary.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (!accepted) {
                Text(
                    text = "Check this box to continue.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = CodecTokens.space(CodecTokens.Space.HUGE)),
                )
            }
        }
    }
}
