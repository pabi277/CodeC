package com.codeci.ide.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.codeci.ide.ui.theme.CodecTokens
import com.codeci.ide.ui.theme.CodecTokens.Space

/**
 * Phase 77.3 — the ✨ rail slot, now **AI home only** (owner: *"the 3 ber ai
 * place only for full setup ai"*). Setup, the model, Test connection, Delete
 * key, the "Show AI button" switch and "Open AI chat". There is no question
 * field and no answer here any more — chatting lives in [AiChatSheet], over
 * the code, where the answer does not hide what it explains.
 *
 * Draws and reports; decides nothing (availability comes from [AiGate]).
 */
@Composable
fun AiHome(
    state: AiUiState,
    availability: AiAvailability,
    onSaveKey: (key: String, model: String, confirmed: Boolean) -> Unit,
    onSaveModel: (String) -> Unit,
    onTest: () -> Unit,
    onDeleteKey: () -> Unit,
    onShowBubbleChange: (Boolean) -> Unit,
    onOpenChat: () -> Unit,
    onOutputConflictChange: (AiOutputConflict) -> Unit,
    onSelectProvider: (AiProviderId) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CodecTokens.space(Space.L)),
        verticalArrangement = Arrangement.spacedBy(CodecTokens.space(Space.M))
    ) {
        Text(AiCopy.title(state.provider), style = MaterialTheme.typography.titleMedium)
        Muted(AiCopy.READ_ONLY)

        val busy = homeBusy(state)
        Text(AiCopy.PROVIDER_LABEL, style = MaterialTheme.typography.titleSmall)
        for (provider in AiProviderId.entries) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onSelectProvider(provider) }
            ) {
                RadioButton(selected = state.provider == provider, onClick = { onSelectProvider(provider) }, enabled = !busy)
                Text(provider.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Muted(AiCopy.PROVIDER_SWITCH_NOTE)
        Muted(AiProviders.constraintLine(state.provider))
        Muted(AiProviders.capabilityLine(state.provider, state.model))
        state.notice?.let { Muted(it) }
        key(state.provider) {
            when (availability) {
                AiAvailability.NEEDS_PROJECT -> Body(AiCopy.NEEDS_PROJECT)
                AiAvailability.NEEDS_KEY -> KeySetup(state, onSaveKey)
                AiAvailability.READY -> Ready(
                    state, onSaveModel, onTest, onDeleteKey,
                    onShowBubbleChange, onOpenChat, onOutputConflictChange, onStop
                )
            }
        }
    }
}

// ---- key setup (O1) ---------------------------------------------------------

@Composable
private fun KeySetup(state: AiUiState, onSaveKey: (String, String, Boolean) -> Unit) {
    var key by remember { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf(state.model) }
    var confirmed by rememberSaveable { mutableStateOf(false) }

    Body(AiCopy.setupIntro(state.provider))
    if (state.provider == AiProviderId.GEMINI) {
        Links(AiCopy.GET_KEY to AiCopy.GET_KEY_URL)
    } else {
        Links("Get your own NVIDIA key" to AiCopy.NVIDIA_GET_KEY_URL)
    }
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(AiCopy.keyLabel(state.provider)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Muted(AiCopy.keyStorageNote(state.provider))
    OutlinedTextField(
        value = model,
        onValueChange = { model = it },
        label = { Text(AiCopy.MODEL_LABEL) },
        singleLine = true,
        isError = !AiProviders.isValidModel(state.provider, model),
        modifier = Modifier.fillMaxWidth()
    )
    Body(AiCopy.providerDataNote(state.provider))
    if (state.provider == AiProviderId.GEMINI) {
        Muted(AiCopy.REGION_NOTE)
        Links(AiCopy.TERMS_LINK to AiCopy.TERMS_URL, AiCopy.POLICY_LINK to AiCopy.POLICY_URL)
    } else {
        Links("NVIDIA API Trial Terms" to AiCopy.NVIDIA_TERMS_URL)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { confirmed = !confirmed }
    ) {
        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
        Text(AiCopy.confirmation(state.provider), style = MaterialTheme.typography.bodyMedium)
    }
    state.setupError?.let { ErrorLine(it) }
    Button(
        onClick = {
            onSaveKey(key, model, confirmed)
            key = ""
        },
        enabled = !state.configuring && AiProviders.canSaveKey(state.provider, key, model, confirmed),
        modifier = Modifier.fillMaxWidth()
    ) { Text(AiCopy.SAVE_KEY) }
}

// ---- set up: open chat, model, button, test, delete ----------------------------

@Composable
private fun Ready(
    state: AiUiState,
    onSaveModel: (String) -> Unit,
    onTest: () -> Unit,
    onDeleteKey: () -> Unit,
    onShowBubbleChange: (Boolean) -> Unit,
    onOpenChat: () -> Unit,
    onOutputConflictChange: (AiOutputConflict) -> Unit,
    onStop: () -> Unit
) {
    var model by rememberSaveable(state.model) { mutableStateOf(state.model) }
    val busy = homeBusy(state)

    // The fallback door to the chat when the floating button is hidden.
    Button(onClick = onOpenChat, enabled = !state.testing && !state.configuring, modifier = Modifier.fillMaxWidth()) { Text(AiCopy.OPEN_CHAT) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(AiCopy.SHOW_BUBBLE, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = state.showBubble, onCheckedChange = onShowBubbleChange)
    }
    Muted(AiCopy.SHOW_BUBBLE_NOTE)
    HorizontalDivider()

    Text(AiCopy.SETTINGS, style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = model,
        onValueChange = { model = it },
        label = { Text(AiCopy.MODEL_LABEL) },
        singleLine = true,
        isError = !AiProviders.isValidModel(state.provider, model),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedButton(
        onClick = { onSaveModel(model) },
        enabled = !busy && AiProviders.isValidModel(state.provider, model) && model.trim() != state.model
    ) { Text(AiCopy.SAVE_MODEL) }
    state.setupError?.let { ErrorLine(it) }
    HorizontalDivider()
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onTest, enabled = !busy) { Text(AiCopy.TEST) }
        if (state.testing) {
            Spacer(Modifier.size(CodecTokens.space(Space.M)))
            CircularProgressIndicator(modifier = Modifier.size(CodecTokens.icon(CodecTokens.Icon.ACTION)))
        }
    }
    Muted(AiCopy.testNote(state.provider, state.model))
    Muted(AiCopy.answerBudgetNote(state.provider, state.model))
    state.retryCountdown?.let { ErrorLine(AiRateLimits.countdownLine(it)) }
    if (state.testing) OutlinedButton(onClick = onStop) { Text(AiCopy.STOP) }
    state.testResult?.let { Body(it) }
    HorizontalDivider()
    TextButton(onClick = onDeleteKey, enabled = !state.configuring && !state.applying) {
        Text(AiCopy.DELETE_KEY, color = MaterialTheme.colorScheme.error)
    }
    Muted(AiCopy.DELETE_NOTE)

    // ---- Phase 77: the user's Output-panel choice (saved in the AI properties file) ----
    HorizontalDivider()
    Text(AiCopy.VARIANT_TITLE, style = MaterialTheme.typography.titleSmall)
    VariantRow(AiCopy.VARIANT_A, state.outputConflict == AiOutputConflict.REPLACE_OUTPUT) {
        onOutputConflictChange(AiOutputConflict.REPLACE_OUTPUT)
    }
    VariantRow(AiCopy.VARIANT_B, state.outputConflict == AiOutputConflict.OPEN_FULL) {
        onOutputConflictChange(AiOutputConflict.OPEN_FULL)
    }
    Muted(AiCopy.VARIANT_NOTE)
}

@Composable
private fun VariantRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Same disable rule as the VM. Key deletion stays available to cancel a request/wait. */
private fun homeBusy(state: AiUiState): Boolean = state.configuring || state.testing || state.gathering ||
    state.applying || state.phase == AiPhase.STREAMING
