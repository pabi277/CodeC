package com.codeci.ide.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Space

/**
 * Phase 76 — the fifth rail slot's panel: the read-only Gemini helper.
 *
 * This file draws and reports; it decides nothing. Availability comes from
 * [AiGate], prompts from [AiContextBuilder] (built by the editor screen at
 * tap time, from the live buffer), and every action goes through
 * [AiViewModel]. There is no apply, insert, run or write control (D1): the
 * only way out of this panel is **Copy answer**.
 */
@Composable
fun AiPanel(
    state: AiUiState,
    availability: AiAvailability,
    onExplainSelection: (question: String) -> Unit,
    onExplainError: (question: String) -> Unit,
    onSend: () -> Unit,
    onCancelPreview: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onDismissNotice: () -> Unit,
    onToggleSettings: () -> Unit,
    onSaveKey: (key: String, model: String, confirmed: Boolean) -> Unit,
    onSaveModel: (String) -> Unit,
    onTest: () -> Unit,
    onDeleteKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CodecTokens.space(Space.L)),
        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.M))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                AiCopy.TITLE,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (availability == AiAvailability.READY) {
                IconButton(onClick = onToggleSettings) {
                    Icon(
                        if (state.showSettings) Icons.Filled.Close else Icons.Filled.Settings,
                        contentDescription = AiCopy.SETTINGS
                    )
                }
            }
        }
        Muted(AiCopy.READ_ONLY)

        when {
            availability == AiAvailability.NEEDS_PROJECT -> Body(AiCopy.NEEDS_PROJECT)
            availability == AiAvailability.NEEDS_KEY -> KeySetup(state, onSaveKey)
            state.showSettings -> SettingsSection(state, onSaveModel, onTest, onDeleteKey)
            else -> Ask(
                state, onExplainSelection, onExplainError, onSend, onCancelPreview,
                onStop, onRetry, onClear, onDismissNotice
            )
        }
    }
}

// ---- key setup (O1) ---------------------------------------------------------

@Composable
private fun KeySetup(state: AiUiState, onSaveKey: (String, String, Boolean) -> Unit) {
    var key by remember { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(state.model) }
    var confirmed by rememberSaveable { mutableStateOf(false) }

    Body(AiCopy.SETUP_INTRO)
    Links(AiCopy.GET_KEY to AiCopy.GET_KEY_URL)
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(AiCopy.KEY_LABEL) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Muted(AiCopy.KEY_STORAGE_NOTE)
    OutlinedTextField(
        value = model,
        onValueChange = { model = it },
        label = { Text(AiCopy.MODEL_LABEL) },
        singleLine = true,
        isError = !AiModel.isValid(model),
        modifier = Modifier.fillMaxWidth()
    )
    Body(AiCopy.FREE_TIER_NOTE)
    Muted(AiCopy.REGION_NOTE)
    Links(AiCopy.TERMS_LINK to AiCopy.TERMS_URL, AiCopy.POLICY_LINK to AiCopy.POLICY_URL)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { confirmed = !confirmed }
    ) {
        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
        Text(AiCopy.CONFIRM, style = MaterialTheme.typography.bodyMedium)
    }
    state.setupError?.let { ErrorLine(it) }
    Button(
        onClick = {
            onSaveKey(key, model, confirmed)
            key = ""
        },
        enabled = AiKeySetup.canSave(key, confirmed) && AiModel.isValid(model),
        modifier = Modifier.fillMaxWidth()
    ) { Text(AiCopy.SAVE_KEY) }
}

// ---- settings ---------------------------------------------------------------

@Composable
private fun SettingsSection(
    state: AiUiState,
    onSaveModel: (String) -> Unit,
    onTest: () -> Unit,
    onDeleteKey: () -> Unit
) {
    var model by rememberSaveable(state.model) { mutableStateOf(state.model) }
    Text(AiCopy.SETTINGS, style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = model,
        onValueChange = { model = it },
        label = { Text(AiCopy.MODEL_LABEL) },
        singleLine = true,
        isError = !AiModel.isValid(model),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(
        onClick = { onSaveModel(model) },
        enabled = AiModel.isValid(model) && AiModel.normalize(model) != state.model
    ) { Text(AiCopy.SAVE_MODEL) }
    state.setupError?.let { ErrorLine(it) }
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onTest, enabled = !state.testing) { Text(AiCopy.TEST) }
        if (state.testing) {
            Spacer(Modifier.size(CodecTokens.space(Space.M)))
            CircularProgressIndicator(modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)))
        }
    }
    Muted(AiCopy.TEST_NOTE)
    state.testResult?.let { Body(it) }
    HorizontalDivider()
    TextButton(onClick = onDeleteKey) {
        Text(AiCopy.DELETE_KEY, color = MaterialTheme.colorScheme.error)
    }
    Muted(AiCopy.DELETE_NOTE)
}

// ---- asking -----------------------------------------------------------------

@Composable
private fun Ask(
    state: AiUiState,
    onExplainSelection: (String) -> Unit,
    onExplainError: (String) -> Unit,
    onSend: () -> Unit,
    onCancelPreview: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onDismissNotice: () -> Unit
) {
    var question by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    when (state.phase) {
        AiPhase.IDLE -> {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it.take(AiLimits.MAX_QUESTION_CHARS) },
                label = { Text(AiCopy.QUESTION_LABEL) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                Button(onClick = { onDismissNotice(); onExplainSelection(question) }) { Text(AiCopy.EXPLAIN_SELECTION) }
                OutlinedButton(onClick = { onDismissNotice(); onExplainError(question) }) { Text(AiCopy.EXPLAIN_ERROR) }
            }
            state.notice?.let { ErrorLine(it) }
            Muted(AiCopy.NOT_SAVED_NOTE)
        }

        AiPhase.PREVIEW -> state.prompt?.let { prompt ->
            Text(AiCopy.PREVIEW_TITLE, style = MaterialTheme.typography.titleSmall)
            Body(AiCopy.previewHeader(state.model, prompt.sentChars))
            if (prompt.unsaved) Muted(AiCopy.UNSAVED_NOTE)
            Body(AiCopy.FREE_TIER_NOTE)
            SentText(prompt.systemInstruction + "\n\n" + prompt.userText)
            Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                Button(onClick = onSend) { Text(AiCopy.SEND) }
                OutlinedButton(onClick = onCancelPreview) { Text(AiCopy.CANCEL) }
            }
        }

        AiPhase.STREAMING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)))
                Spacer(Modifier.size(CodecTokens.space(Space.M)))
                OutlinedButton(onClick = onStop) { Text(AiCopy.STOP) }
            }
            if (state.answer.isNotEmpty()) Answer(state.answer)
        }

        AiPhase.DONE -> {
            Answer(state.answer)
            if (state.cutShort) Muted(AiErrors.CUT_SHORT)
            Muted(AiCopy.WRONG_NOTE)
            Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                Button(onClick = { copy(context, state.answer) }) { Text(AiCopy.COPY) }
                OutlinedButton(onClick = { question = ""; onClear() }) { Text(AiCopy.NEW_QUESTION) }
            }
        }

        AiPhase.FAILED -> {
            state.error?.let { ErrorLine(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.S))) {
                if (state.keySaved) Button(onClick = onRetry) { Text(AiCopy.TRY_AGAIN) }
                OutlinedButton(onClick = onClear) { Text(AiCopy.NEW_QUESTION) }
            }
        }
    }
}

/** The exact text that leaves the phone, monospaced and selectable (D4). */
@Composable
private fun SentText(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = CodecTokens.space(Space.HUGE) * 5)
            .border(
                width = CodecTokens.space(Space.XXS) / 2,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(CodecTokens.radius(CodecTokens.Radius.S))
            )
            .verticalScroll(rememberScrollState())
            .padding(CodecTokens.space(Space.S))
    ) {
        SelectionContainer {
            Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Answer(text: String) {
    SelectionContainer {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Links(vararg links: Pair<String, String>) {
    val uri = LocalUriHandler.current
    Column {
        links.forEach { (label, url) ->
            // Opens the browser only on a tap; nothing is fetched by CodeC itself.
            TextButton(onClick = { runCatching { uri.openUri(url) } }) {
                Text(label, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun Muted(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun ErrorLine(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error
)

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("CodeC AI answer", text))
    Toast.makeText(context, AiCopy.COPIED, Toast.LENGTH_SHORT).show()
}
