package com.codeci.ide

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderWiringTest {
    private val dir = RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/ai")
    private fun raw(name: String) = File(dir, name).readText()
    private fun src(name: String) = RepoFiles.codeOnly(raw(name))
    @Test fun `provider and model are immutable prompt fields not re-read during sending`() {
        assertTrue(src("AiContext.kt").contains("val provider: AiProviderId = AiProviderId.GEMINI"))
        val vm = src("AiViewModel.kt")
        assertTrue(vm.contains("result.prompt.copy(provider = it.provider, model = it.model)"))
        val send = vm.substringAfter("fun send()").substringBefore("fun stop()")
        assertTrue(send.contains("val model = prompt.model"))
        assertTrue(send.contains("val provider = prompt.provider"))
        assertFalse(send.contains("val model = _state.value.model"))
    }
    @Test fun `manual selection clears old preview and is blocked while busy`() {
        val vm = src("AiViewModel.kt")
        val select = vm.substringAfter("fun selectProvider(").substringBefore("fun saveKey(")
        assertTrue(select.contains("settingsBusy()"))
        assertTrue(select.contains("clear()"))
        assertFalse(select.contains("client.stream("))
        assertTrue(src("AiHome.kt").contains("clickable(enabled = !busy) { onSelectProvider(provider) }"))
        val editor = RepoFiles.codeOnly(RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/screens/EditorScreen.kt").readText())
        assertTrue(editor.contains("onSelectProvider = aiViewModel::selectProvider"))
        assertTrue(editor.contains("onStop = aiViewModel::stop"))
    }
    @Test fun `NVIDIA consent is independently required in UI VM and store`() {
        assertTrue(src("AiHome.kt").contains("AiProviders.canSaveKey(state.provider, key, model, confirmed)"))
        assertTrue(src("AiHome.kt").contains("AiCopy.confirmation(state.provider)"))
        assertTrue(src("AiHome.kt").contains("AiCopy.NVIDIA_TERMS_URL"))
        assertTrue(src("AiViewModel.kt").contains("store.saveKey(rawKey, model, provider, confirmedAdultAndTerms)"))
        assertTrue(src("AiKeyStore.kt").contains("AiProviders.canSaveKey(provider, rawKey, model, confirmed)"))
        assertTrue(src("AiKeyStore.kt").contains("acceptedTermsVersion(provider) != AiProviders.termsVersion(provider)"))
    }
    @Test fun `independent encrypted slots keep Gemini compatibility and key deletion clears undo`() {
        val store = raw("AiKeyStore.kt")
        assertTrue(store.contains("codec_ai_gemini_key_v1"))
        assertTrue(store.contains("codec_ai_nvidia_key_v1"))
        assertTrue(store.contains("${'$'}{provider.slot}_key.bin"))
        assertTrue(store.contains("nvidia_terms_version"))
        assertTrue(store.contains("nvidia_terms_accepted_at"))
        assertTrue(src("AiKeyStore.kt").contains("keyFile(provider).delete()"))
        assertTrue(src("AiKeyStore.kt").contains("p.remove(termsProp(provider))"))
        assertTrue(src("AiKeyStore.kt").contains("AiEditApplier.clearAllJournals("))
    }
    @Test fun `provider selection and chat are not persisted with new credentials`() {
        val store = raw("AiKeyStore.kt")
        for (bad in listOf("\"provider\"", "\"chat\"", "\"answer\"", "\"retry_countdown\"", "\"agent_")) assertFalse(bad, store.contains(bad))
        val vm = src("AiViewModel.kt")
        for (bad in listOf("SavedStateHandle", "rememberSaveable", "Properties", "SharedPreferences")) assertFalse(bad, vm.contains(bad))
        val state = vm.substringAfter("data class AiUiState(").substringBefore(")\n")
        assertFalse(state.contains("apiKey"))
        assertEquals(3, Regex("client[.]stream[(]").findAll(vm).count())
    }
    @Test fun `both providers preview the exact same strings and disclose every agent turn`() {
        val sheet = src("AiChatSheet.kt")
        assertTrue(sheet.contains("AiCopy.previewHeader(it.provider, it.model, it.sentChars)"))
        assertTrue(sheet.contains("AiCopy.providerDataNote(it.provider)"))
        assertTrue(sheet.contains("SentText(it.systemInstruction"))
        assertTrue(sheet.contains("step.sentSystemInstruction"))
        assertTrue(sheet.contains("step.sentUserText.orEmpty()"))
        val turn = src("AiViewModel.kt").substringAfter("private fun agentTurn(").substringBefore("private fun usage(")
        assertTrue(turn.contains("sentSystemInstruction = session.systemInstruction"))
        assertTrue(turn.contains("sentUserText = packed.text"))
        assertTrue(turn.contains("client.stream(session.provider, key, session.model, body"))
    }
    @Test fun `seam routes exhaustively and never calls one provider after another fails`() {
        val client = src("AiProviderClient.kt")
        assertTrue(client.contains("when (provider)"))
        assertFalse(client.contains("catch"))
        assertFalse(client.contains("retry"))
        assertFalse(client.contains("fallback"))
        for (name in listOf("AiTools.kt", "AiToolRunner.kt", "AiEditProposal.kt")) {
            assertFalse("provider cannot change permissions in $name", src(name).contains("AiProviderId"))
        }
    }
    @Test fun `both literals and NVIDIA key shape are scrubbed from support attachments`() {
        val card = RepoFiles.codeOnly(RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/support/FeedbackSectionCard.kt").readText())
        assertTrue(card.contains("AiProviderId.entries.mapNotNull"))
        assertTrue(card.contains("aiStore.loadKey(provider)"))
        assertTrue(card.contains("extraSecrets = aiKeys"))
        val draft = RepoFiles.codeOnly(RepoFiles.mainSource("app/src/main/java/com/codeci/ide/ui/support/FeedbackDraft.kt").readText())
        assertTrue(draft.contains("shapeNvidiaApiKey"))
    }
    @Test fun `provider seam adds no dependency permission or custom endpoint and Test remains content-free`() {
        assertFalse(src("AiHome.kt").contains("client."))
        assertFalse(src("AiProviderClient.kt").contains("baseUrl"))
        assertFalse(src("AiProviders.kt").contains("HttpURLConnection"))
        val test = src("AiViewModel.kt").substringAfter("fun testConnection()").substringBefore("private class AgentDeadlineReached")
        assertTrue(test.contains("GeminiRequest.testBody()"))
        assertTrue(test.contains("NvidiaRequest.testBody(model)"))
        assertFalse(test.contains("AiProviderRequests.body(prompt)"))
        assertFalse(test.contains("AiToolRunner"))
    }
}
