package com.codeci.ide.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import com.codeci.ide.ui.theme.CodecType
import com.codeci.ide.ui.theme.rememberMotionSpecs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A short, skippable first-run introduction. Only the privacy acknowledgement
 * is required; it is an in-app summary, not a claim that CodeC has a separate
 * Terms of Service. Device permissions are still requested only when a feature
 * needs them.
 */
@Composable
fun FirstRunIntroScreen(
    preparing: Boolean,
    onStart: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var privacyAccepted by rememberSaveable { mutableStateOf(false) }
    var showPrivacyDetails by rememberSaveable { mutableStateOf(false) }
    val motion = rememberMotionSpecs()
    val heroReveal = remember(page) { Animatable(0f) }

    LaunchedEffect(page, motion.useSpring) {
        heroReveal.snapTo(0f)
        if (motion.useSpring) {
            heroReveal.animateTo(1f, animationSpec = CodecMotion.introReveal)
        } else {
            heroReveal.snapTo(1f)
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CodecTokens.space(CodecTokens.Space.L))
            ) {
                Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.M)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                text = "YOUR POCKET CODING STUDIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (page < INTRO_PAGE_COUNT - 1) {
                        TextButton(
                            onClick = { page = INTRO_PAGE_COUNT - 1 },
                            modifier = Modifier.heightIn(min = CodecTokens.space(CodecTokens.MIN_TOUCH)),
                        ) {
                            Text("Skip to agreement")
                        }
                    }
                }

                Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.L)))
                IntroProgress(page = page)
                Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.M)))

                Crossfade(
                    targetState = page,
                    animationSpec = motion.floatOrSnap(CodecMotion.crossfadeSpec),
                ) { currentPage ->
                    Column(verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.M))) {
                        when (currentPage) {
                            0 -> {
                                CodeRunArtwork(progress = heroReveal.value)
                                IntroHeading(
                                    eyebrow = "MAKE SOMETHING REAL",
                                    title = "Your phone is a place to build.",
                                    body = "CodeC is a mobile IDE for C, Python, JavaScript and web projects. C compiles offline; HTML previews locally. Python, Node and Linux tools are optional downloads when you need them.",
                                )
                                LanguagePills()
                            }
                            1 -> {
                                OrbitArtwork(progress = heroReveal.value)
                                IntroHeading(
                                    eyebrow = "YOUR FIRST PROJECT",
                                    title = "A little game. Yours to change.",
                                    body = "Meet Orbit Shift: tap RUN, switch between two rings, catch gold shards and dodge comets. Then edit index.html and make the game your own.",
                                )
                                Text(
                                    text = "No signup. No install for the game. Just your first idea, in motion.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> {
                                PrivacyArtwork(progress = heroReveal.value)
                                IntroHeading(
                                    eyebrow = "BEFORE YOU START",
                                    title = "Your code. Your call.",
                                    body = "Projects stay on this device by default. CodeC has no ads, analytics or tracking. A crash log stays here unless you choose to share it.",
                                )
                                PrivacySummary(onReadDetails = { showPrivacyDetails = true })
                                PrivacyAgreement(
                                    accepted = privacyAccepted,
                                    onAcceptedChange = { privacyAccepted = it },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(CodecTokens.space(CodecTokens.Space.L)))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = CodecTokens.space(CodecTokens.Space.L),
                        vertical = CodecTokens.space(CodecTokens.Space.S),
                    ),
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

private const val INTRO_PAGE_COUNT = 3

@Composable
private fun IntroProgress(page: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XS))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "FIRST RUN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${page + 1} of $INTRO_PAGE_COUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XS))) {
            repeat(INTRO_PAGE_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(CodecTokens.space(CodecTokens.Space.XS))
                        .clip(CircleShape)
                        .background(
                            if (index <= page) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        )
                )
            }
        }
    }
}

@Composable
private fun IntroHeading(eyebrow: String, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XS))) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CodeRunArtwork(progress: Float) {
    val accent = Color(CodecPalette.IDENTITY_GREEN)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp)
            .semantics { contentDescription = "A code editor turns a short C program into its first running output." },
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.L)),
        colors = CardDefaults.cardColors(containerColor = Color(CodecPalette.SURFACE_CODE)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(CodecTokens.space(CodecTokens.Space.M)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.S)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(CodecTokens.space(CodecTokens.Space.S))
                        .background(accent, CircleShape)
                )
                Spacer(Modifier.width(CodecTokens.space(CodecTokens.Space.S)))
                Text(
                    text = "main.c",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontFamily = CodecType.codeFamily,
                )
                Spacer(Modifier.weight(1f))
                Text("C11  ·  READY", style = MaterialTheme.typography.labelSmall, color = Color(CodecPalette.MUTED_TEXT))
            }
            Column(verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XXS))) {
                CodeLine("#include <stdio.h>", Color(CodecPalette.INFO))
                CodeLine("int main(void) {", Color(CodecPalette.SUCCESS))
                CodeLine("  printf(\"Hello, world!\");", Color(CodecPalette.WARNING))
                CodeLine("}", Color(CodecPalette.SUCCESS))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.S)))
                    .background(Color(CodecPalette.SURFACE_PANEL))
                    .padding(
                        horizontal = CodecTokens.space(CodecTokens.Space.S),
                        vertical = CodecTokens.space(CodecTokens.Space.XS),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("▶  RUN", style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(CodecTokens.space(CodecTokens.Space.S)))
                Box(
                    modifier = Modifier
                        .width(CodecTokens.space(CodecTokens.Space.XXS))
                        .height(CodecTokens.space(CodecTokens.Space.L))
                        .background(Color(CodecPalette.MUTED_TEXT).copy(alpha = 0.45f))
                )
                Spacer(Modifier.width(CodecTokens.space(CodecTokens.Space.S)))
                Text(
                    text = "Hello, world!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontFamily = CodecType.codeFamily,
                    modifier = Modifier
                        .offset(y = ((1f - progress.coerceIn(0f, 1f)) * 8f).dp)
                        .alpha(progress.coerceIn(0f, 1f)),
                )
            }
        }
    }
}

@Composable
private fun CodeLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontFamily = CodecType.codeFamily,
        maxLines = 1,
    )
}

@Composable
private fun LanguagePills() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XS)),
    ) {
        listOf("C · offline", "Python · optional", "Web · local preview").forEach { label ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(vertical = CodecTokens.space(CodecTokens.Space.S)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun OrbitArtwork(progress: Float) {
    val mint = Color(CodecPalette.IDENTITY_GREEN)
    val violet = MaterialTheme.colorScheme.tertiary
    val surface = Color(CodecPalette.SURFACE_PANEL)
    val danger = MaterialTheme.colorScheme.error
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.L)))
            .background(
                Brush.linearGradient(
                    listOf(surface, MaterialTheme.colorScheme.surfaceVariant, surface),
                )
            )
            .semantics { contentDescription = "Orbit Shift illustration: a small ship circles a violet planet between two rings." },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width * 0.5f
            val centerY = size.height * 0.53f
            val outer = size.minDimension * 0.37f
            val inner = outer * 0.62f
            val planet = size.minDimension * 0.105f
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = outer * 1.28f,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            )
            drawCircle(
                color = violet.copy(alpha = 0.44f),
                radius = outer,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = CodecTokens.space(CodecTokens.Space.XXS).toPx()),
            )
            drawCircle(
                color = mint.copy(alpha = 0.48f),
                radius = inner,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                style = Stroke(width = CodecTokens.space(CodecTokens.Space.XXS).toPx()),
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(violet.copy(alpha = 0.95f), violet.copy(alpha = 0.3f)), radius = planet * 1.5f),
                radius = planet,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f),
                radius = planet * 0.21f,
                center = androidx.compose.ui.geometry.Offset(centerX - planet * 0.3f, centerY - planet * 0.26f),
            )
            val sweep = -PI.toFloat() * 0.72f + (PI.toFloat() * 1.12f * progress.coerceIn(0f, 1f))
            val shipX = centerX + cos(sweep) * outer
            val shipY = centerY + sin(sweep) * outer
            drawCircle(color = mint.copy(alpha = 0.18f), radius = CodecTokens.space(CodecTokens.Space.M).toPx(), center = androidx.compose.ui.geometry.Offset(shipX, shipY))
            drawCircle(color = mint, radius = CodecTokens.space(CodecTokens.Space.XS).toPx(), center = androidx.compose.ui.geometry.Offset(shipX, shipY))
            val shardAngle = 2.55f
            drawCircle(
                color = Color(CodecPalette.WARNING),
                radius = CodecTokens.space(CodecTokens.Space.XS).toPx(),
                center = androidx.compose.ui.geometry.Offset(centerX + cos(shardAngle) * inner, centerY + sin(shardAngle) * inner),
            )
            val cometAngle = 4.05f
            drawLine(
                color = danger.copy(alpha = 0.78f),
                start = androidx.compose.ui.geometry.Offset(centerX + cos(cometAngle) * inner, centerY + sin(cometAngle) * inner),
                end = androidx.compose.ui.geometry.Offset(centerX + cos(cometAngle) * inner - CodecTokens.space(CodecTokens.Space.M).toPx(), centerY + sin(cometAngle) * inner + CodecTokens.space(CodecTokens.Space.XS).toPx()),
                strokeWidth = CodecTokens.space(CodecTokens.Space.XS).toPx(),
                cap = StrokeCap.Round,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(CodecTokens.space(CodecTokens.Space.M)),
        ) {
            Text("ORBIT SHIFT", style = MaterialTheme.typography.labelLarge, color = mint, fontWeight = FontWeight.Bold)
            Text("A CodeC original", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CodecTokens.space(CodecTokens.Space.S)),
            horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XS)),
        ) {
            OrbitChip("INNER", mint)
            OrbitChip("SHIFT", Color.White)
            OrbitChip("OUTER", violet)
        }
    }
}

@Composable
private fun OrbitChip(label: String, color: Color) {
    Surface(
        shape = CircleShape,
        color = Color(CodecPalette.SURFACE_PANEL).copy(alpha = 0.86f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.48f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = CodecTokens.space(CodecTokens.Space.S), vertical = CodecTokens.space(CodecTokens.Space.XS)),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun PrivacyArtwork(progress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.L)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.64f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(CodecTokens.space(CodecTokens.Space.M)),
            verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.S)),
        ) {
            PrivacyFeature(Icons.Filled.Folder, "Projects live here", "Saved in CodeC on this device")
            PrivacyFeature(Icons.Filled.Lock, "No tracking", "No ads, analytics or crash uploads")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = primary, modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)))
                Spacer(Modifier.width(CodecTokens.space(CodecTokens.Space.S)))
                Column(modifier = Modifier.weight(1f)) {
                    Text("You choose when to connect", style = MaterialTheme.typography.labelLarge)
                    Text("Git, installs and updates start when you ask", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier
                        .size(CodecTokens.icon(CodecTokens.Icon.ACTION))
                        .alpha(progress.coerceIn(0f, 1f)),
                )
            }
        }
    }
}

@Composable
private fun PrivacyFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)))
        Spacer(Modifier.width(CodecTokens.space(CodecTokens.Space.S)))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrivacySummary(onReadDetails: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CodecTokens.space(CodecTokens.Space.XS))) {
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
