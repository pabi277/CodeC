package com.codeci.ide.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Space
import com.codeci.ide.ui.theme.CodecType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/*
 * Phase 88 (Level 11, 88.3) — the answer surface: formatted, selectable, inert.
 *
 * Draws `AiMarkdown`'s model (88.1) as Compose text. Model output is untrusted
 * (S3): no HTML is interpreted (there is no HTML type to interpret), images show
 * only their alt text, and a link becomes tappable only when `AiLinkPolicy`
 * (88.2) returns Openable — and then a tap only raises the confirm dialog. The
 * one network effect, `openUri`, sits behind the dialog's Open button.
 *
 * Nothing in the answer is truncated: no line cap, no height cap and no nested
 * vertical scroll — the sheet's own scroll carries the answer; code and tables
 * scroll sideways only. Colours come from the theme only. Code blocks Copy
 * through `copyAnswer`, the one way out of the AI surface (D1/S6): never insert,
 * apply or run.
 */

/**
 * Theme colours the inline builder needs. A data class, remembered on its three
 * values, so a streaming recomposition with an unchanged parse skips the blocks
 * instead of rebuilding every paragraph's text on every chunk.
 */
/**
 * Phase 90 — how many lines of one fence are DRAWN. The block's Copy button
 * always copies every line; this only keeps a pathological fence from costing a
 * huge layout, and [AiCopy.CODE_BLOCK_TRIMMED] says so on screen.
 */
private const val MAX_DRAWN_CODE_LINES = 200

private data class AiMdColors(
    val link: Color,
    val muted: Color,
    val codeBackground: Color,
    /** Phase 90 — the frame around a code block, so it reads as a block on any bubble. */
    val frame: Color
)

/**
 * The Markdown answer. While [streaming], the text is re-parsed off the main
 * thread at most once per [AiMarkdown.STREAM_REPARSE_MS], always on the newest
 * text; at DONE the final text is parsed exactly once.
 */
@Composable
internal fun AiMarkdownAnswer(text: String, streaming: Boolean) {
    val blocks = rememberAnswerBlocks(text, streaming)
    var pending by remember { mutableStateOf<AiLink.Openable?>(null) }
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val codeColor = MaterialTheme.colorScheme.surfaceVariant
    val frameColor = MaterialTheme.colorScheme.outlineVariant
    val colors = remember(linkColor, mutedColor, codeColor, frameColor) {
        AiMdColors(linkColor, mutedColor, codeColor, frameColor)
    }
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
            MdBlocks(
                blocks = blocks,
                depth = 0,
                colors = colors,
                onLink = { pending = it },
                onCopy = { copyAnswer(context, it) }
            )
        }
    }
    pending?.let { link -> AiLinkConfirmDialog(link, onDismiss = { pending = null }) }
}

@Composable
private fun rememberAnswerBlocks(text: String, streaming: Boolean): List<AiMdBlock> {
    if (!streaming) return remember(text) { AiMarkdown.parse(text) }
    val latest by rememberUpdatedState(text)
    val parsed by produceState(initialValue = emptyList<AiMdBlock>()) {
        snapshotFlow { latest }
            .conflate()
            .collect { current ->
                value = withContext(Dispatchers.Default) { AiMarkdown.parse(current) }
                delay(AiMarkdown.STREAM_REPARSE_MS)
            }
    }
    return parsed
}

@Composable
private fun MdBlocks(
    blocks: List<AiMdBlock>,
    depth: Int,
    colors: AiMdColors,
    onLink: (AiLink.Openable) -> Unit,
    onCopy: (String) -> Unit
) {
    blocks.forEach { block -> MdBlock(block, depth, colors, onLink, onCopy) }
}

@Composable
private fun MdBlock(
    block: AiMdBlock,
    depth: Int,
    colors: AiMdColors,
    onLink: (AiLink.Openable) -> Unit,
    onCopy: (String) -> Unit
) {
    val gap = CodecTokens.space(Space.S)
    when (block) {
        is AiMdBlock.Heading -> Text(
            text = inlineText(block.inlines, colors, onLink),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            modifier = Modifier.semantics { heading() }
        )

        is AiMdBlock.Paragraph -> Text(
            text = inlineText(block.inlines, colors, onLink),
            style = MaterialTheme.typography.bodyMedium
        )

        is AiMdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.XS))) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(
                        text = listMarker(block, item, index, depth),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .widthIn(min = CodecTokens.space(Space.L))
                            .padding(end = CodecTokens.space(Space.XS))
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.XS))
                    ) {
                        MdBlocks(item.blocks, depth + 1, colors, onLink, onCopy)
                    }
                }
            }
        }

        is AiMdBlock.Quote -> {
            val bar = MaterialTheme.colorScheme.outlineVariant
            val barWidth = CodecTokens.space(Space.XXS)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind { drawRect(bar, size = Size(barWidth.toPx(), size.height)) }
                    .padding(start = barWidth + gap),
                verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.XS))
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.muted) {
                    MdBlocks(block.blocks, depth + 1, colors, onLink, onCopy)
                }
            }
        }

        is AiMdBlock.Code -> {
            // Phase 90 (90.3) — the owner asked for code that reads as a *block*:
            // a real frame, a header strip with the language and Copy, and the
            // code on its own surface. Copy still copies the WHOLE block; only
            // the drawing is capped for a pathological fence, and the note says so.
            val lines = block.text.lines()
            val drawn = if (lines.size > MAX_DRAWN_CODE_LINES) lines.take(MAX_DRAWN_CODE_LINES) else lines
            val trimmed = drawn.size < lines.size
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.codeBackground, RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.S)))
                    .border(1.dp, colors.frame, RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.S)))
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = gap,
                        end = CodecTokens.space(Space.XS),
                        top = CodecTokens.space(Space.XS)
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = block.language.ifBlank { AiCopy.CODE_BLOCK_LABEL },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                        modifier = Modifier.weight(1f)
                    )
                    DisableSelection {
                        // Copy only: the one way out of the AI surface. Never insert, apply or run (S6).
                        TextButton(
                            onClick = { onCopy(block.text) },
                            modifier = Modifier.semantics { contentDescription = AiCopy.CODE_COPY_DESCRIPTION }
                        ) {
                            Text(AiCopy.CODE_COPY, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                HorizontalDivider(color = colors.frame)
                Text(
                    text = drawn.joinToString("\n"),
                    fontFamily = CodecType.codeFamily,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = false,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(
                            start = gap,
                            end = gap,
                            top = CodecTokens.space(Space.XS),
                            bottom = CodecTokens.space(Space.XS)
                        )
                )
                if (trimmed) {
                    Text(
                        text = AiCopy.CODE_BLOCK_TRIMMED,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted,
                        modifier = Modifier.padding(
                            start = gap, end = gap, bottom = CodecTokens.space(Space.XS)
                        )
                    )
                }
            }
        }

        is AiMdBlock.Table -> Text(
            text = tableGrid(block, colors, onLink),
            fontFamily = CodecType.codeFamily,
            style = MaterialTheme.typography.bodySmall,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )

        AiMdBlock.Rule -> HorizontalDivider()
    }
}

private fun listMarker(block: AiMdBlock.Bullets, item: AiMdItem, index: Int, depth: Int): String = when {
    item.checked == true -> "☑"
    item.checked == false -> "☐"
    block.ordered -> "${block.start + index}."
    else -> when (depth % 3) {
        0 -> "•"
        1 -> "◦"
        else -> "▪"
    }
}

private fun inlineText(
    inlines: List<AiMdInline>,
    colors: AiMdColors,
    onLink: (AiLink.Openable) -> Unit,
    lineBreak: String = "\n"
): AnnotatedString = buildAnnotatedString { appendInlines(inlines, colors, onLink, lineBreak) }

/**
 * The one place a model-written link can become tappable. [AiLinkPolicy.classify]
 * decides; only an Openable gets a LinkAnnotation, and it is always built WITH a
 * listener, so a tap raises the confirm dialog instead of opening the browser.
 * An inert link stays text and shows its target as plain muted text.
 */
private fun AnnotatedString.Builder.appendInlines(
    inlines: List<AiMdInline>,
    colors: AiMdColors,
    onLink: (AiLink.Openable) -> Unit,
    lineBreak: String
) {
    for (span in inlines) {
        when (span) {
            is AiMdInline.Text -> append(span.text)
            is AiMdInline.Code -> withStyle(SpanStyle(fontFamily = CodecType.codeFamily, background = colors.codeBackground)) {
                append(span.text)
            }
            is AiMdInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlines(span.children, colors, onLink, lineBreak)
            }
            is AiMdInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(span.children, colors, onLink, lineBreak)
            }
            is AiMdInline.Strike -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendInlines(span.children, colors, onLink, lineBreak)
            }
            is AiMdInline.ImageAlt -> withStyle(SpanStyle(color = colors.muted)) {
                append(AiCopy.answerImage(span.alt))
            }
            AiMdInline.LineBreak -> append(lineBreak)
            is AiMdInline.Link -> when (val link = AiLinkPolicy.classify(span.target)) {
                is AiLink.Openable -> {
                    val linkStyles = TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))
                    withLink(LinkAnnotation.Url(link.url, linkStyles) { onLink(link) }) {
                        appendInlines(span.children, colors, onLink, lineBreak)
                    }
                }
                is AiLink.Inert -> {
                    appendInlines(span.children, colors, onLink, lineBreak)
                    val target = span.target.trim()
                    if (target.isNotEmpty() && AiMarkdown.inlineText(span.children).trim() != target) {
                        withStyle(SpanStyle(color = colors.muted)) { append(" ($target)") }
                    }
                }
            }
        }
    }
}

/** A monospace grid: cells padded to their column's width, the header bold. */
private fun tableGrid(block: AiMdBlock.Table, colors: AiMdColors, onLink: (AiLink.Openable) -> Unit): AnnotatedString {
    val columns = maxOf(block.header.size, block.rows.maxOfOrNull { it.size } ?: 0)
    val cells = (listOf(block.header) + block.rows).map { row ->
        (0 until columns).map { i ->
            row.getOrNull(i)?.let { inlineText(it.inlines, colors, onLink, lineBreak = " ") } ?: AnnotatedString("")
        }
    }
    val widths = (0 until columns).map { i -> maxOf(1, cells.maxOf { it[i].length }) }
    return buildAnnotatedString {
        cells.forEachIndexed { r, row ->
            if (r > 0) append('\n')
            row.forEachIndexed { i, cell ->
                if (i > 0) append(" │ ")
                if (r == 0) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(cell) } else append(cell)
                if (i < columns - 1) append(" ".repeat(widths[i] - cell.length))
            }
            if (r == 0) {
                append('\n')
                widths.forEachIndexed { i, w ->
                    if (i > 0) append("─┼─")
                    append("─".repeat(w))
                }
            }
        }
    }
}

/**
 * The confirm every https link goes through: the host, then the FULL URL
 * (selectable, wrapped, never cut), then Open / Copy link / Cancel. Host, URL
 * and what Open passes on are all the same [AiLink.Openable].
 */
@Composable
private fun AiLinkConfirmDialog(link: AiLink.Openable, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(AiCopy.LINK_DIALOG_TITLE) },
        text = {
            // The dialog is its own window, so a long URL scrolls here rather than being cut.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))
            ) {
                Text(link.host, style = MaterialTheme.typography.titleMedium)
                SelectionContainer {
                    Text(link.url, fontFamily = CodecType.codeFamily, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    AiCopy.LINK_DIALOG_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching { uriHandler.openUri(link.url) }
                onDismiss()
            }) { Text(AiCopy.LINK_OPEN) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { copyAnswer(context, link.url) }) { Text(AiCopy.LINK_COPY) }
                TextButton(onClick = onDismiss) { Text(AiCopy.LINK_CANCEL) }
            }
        }
    )
}
