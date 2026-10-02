package com.codeci.ide

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.codeci.ide.ui.ai.*
import java.io.File
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real metadata/deletion code with deliberately corrupt blobs; not an Android crypto-success claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AiKeyStoreSlotsTest {
    private data class Fixture(val dir: File, val store: AiKeyStore) {
        val settings get() = File(dir, "ai_settings.properties")
        fun key(p: AiProviderId) = File(dir, "${p.slot}_key.bin")
        fun seed(geminiTerms: Boolean = true, nvidiaTerms: Boolean = true) {
            // Version 99 is intentionally invalid, so decrypt failure is deterministic, no crypto stub.
            AiProviderId.entries.forEach { key(it).writeBytes(byteArrayOf(99, 1, 2)) }
            val props = Properties().apply {
                setProperty("model", "gemini-3-flash-preview")
                setProperty("nvidia_model", "nvidia/nemotron-3-super-120b-a12b")
                if (geminiTerms) { setProperty("terms_version", AiKeySetup.TERMS_VERSION.toString()); setProperty("terms_accepted_at", "1") }
                if (nvidiaTerms) { setProperty("nvidia_terms_version", AiProviders.NVIDIA_TERMS_VERSION.toString()); setProperty("nvidia_terms_accepted_at", "2") }
                setProperty("bubble_show", "false")
            }
            settings.outputStream().use { props.store(it, null) }
        }
    }
    private fun fixture(): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.noBackupFilesDir, "ai").apply { deleteRecursively(); mkdirs() }
        return Fixture(dir, AiKeyStore(context))
    }
    private fun Fixture.journal(): File = File(dir, "undo/test-project/task.journal").apply { parentFile!!.mkdirs(); writeText("synthetic preimage") }

    @Test fun `empty settings have independent defaults and existing layout defaults`() {
        val f = fixture()
        assertEquals(AiModel.DEFAULT, f.store.model())
        assertEquals(AiProviders.NVIDIA_DEFAULT, f.store.model(AiProviderId.NVIDIA))
        assertTrue(f.store.showBubble())
        assertFalse(f.store.isReady(AiProviderId.GEMINI))
        assertFalse(f.store.isReady(AiProviderId.NVIDIA))
    }
    @Test fun `each model update preserves the other provider and common layout`() {
        val f = fixture()
        assertTrue(f.store.setModel("gemini-test", AiProviderId.GEMINI))
        assertTrue(f.store.setShowBubble(false))
        assertTrue(f.store.setModel("nvidia/another-model", AiProviderId.NVIDIA))
        assertEquals("gemini-test", f.store.model())
        assertEquals("nvidia/another-model", f.store.model(AiProviderId.NVIDIA))
        assertFalse(f.store.showBubble())
        assertFalse(f.store.setModel("https://bad-host", AiProviderId.NVIDIA))
        assertEquals("nvidia/another-model", f.store.model(AiProviderId.NVIDIA))
    }
    @Test fun `unconfirmed key saves are refused at the store boundary without touching either slot`() {
        val f = fixture().also { it.seed() }
        for (p in AiProviderId.entries) {
            assertFalse(f.store.saveKey("synthetic-" + "x".repeat(35), AiProviders.defaultModel(p), p, confirmed = false))
            assertTrue(f.key(p).readBytes().contentEquals(byteArrayOf(99, 1, 2)))
        }
    }
    @Test fun `one provider's current acceptance never authorizes another key`() {
        val f = fixture().also { it.seed(geminiTerms = false) }
        assertFalse(f.store.isReady(AiProviderId.GEMINI))
        assertFalse(f.key(AiProviderId.GEMINI).exists())
        assertTrue(f.store.isReady(AiProviderId.NVIDIA))
        assertEquals(AiProviders.NVIDIA_TERMS_VERSION, f.store.acceptedTermsVersion(AiProviderId.NVIDIA))
        assertFalse(f.store.showBubble())
    }
    @Test fun `corrupt NVIDIA decrypt clears only its blob acceptance and the shared last-task journal`() {
        val f = fixture().also { it.seed() }
        val journal = f.journal()
        assertNull(f.store.loadKey(AiProviderId.NVIDIA))
        assertFalse(f.key(AiProviderId.NVIDIA).exists())
        assertNull(f.store.acceptedTermsVersion(AiProviderId.NVIDIA))
        assertTrue(f.key(AiProviderId.GEMINI).exists())
        assertEquals(AiKeySetup.TERMS_VERSION, f.store.acceptedTermsVersion(AiProviderId.GEMINI))
        assertEquals(AiProviders.NVIDIA_DEFAULT, f.store.model(AiProviderId.NVIDIA))
        assertFalse(f.store.showBubble())
        assertFalse(journal.exists())
    }
    @Test fun `deleting Gemini preserves NVIDIA credential consent model and common layout`() {
        val f = fixture().also { it.seed() }
        val journal = f.journal()
        f.store.deleteKey(AiProviderId.GEMINI)
        assertFalse(f.key(AiProviderId.GEMINI).exists())
        assertNull(f.store.acceptedTermsVersion(AiProviderId.GEMINI))
        assertTrue(f.key(AiProviderId.NVIDIA).exists())
        assertTrue(f.store.isReady(AiProviderId.NVIDIA))
        assertEquals(AiProviders.NVIDIA_DEFAULT, f.store.model(AiProviderId.NVIDIA))
        assertFalse(f.store.showBubble())
        assertFalse(journal.exists())
    }
    @Test fun `concurrent store instances preserve both models consent and layout`() {
        val f = fixture().also { it.seed() }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val another = AiKeyStore(context)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val tasks = listOf<() -> Unit>(
                { repeat(30) { assertTrue(f.store.setModel("gemini-test", AiProviderId.GEMINI)) } },
                { repeat(30) { assertTrue(another.setModel("nvidia/test", AiProviderId.NVIDIA)) } },
                { repeat(30) { assertTrue(f.store.setShowBubble(false)) } },
                { repeat(30) { assertTrue(another.setOutputConflict(AiOutputConflict.OPEN_FULL)) } }
            ).map { workers.submit(it) }
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertEquals("gemini-test", f.store.model(AiProviderId.GEMINI))
            assertEquals("nvidia/test", another.model(AiProviderId.NVIDIA))
            assertEquals(AiKeySetup.TERMS_VERSION, f.store.acceptedTermsVersion())
            assertEquals(AiProviders.NVIDIA_TERMS_VERSION, another.acceptedTermsVersion(AiProviderId.NVIDIA))
            assertFalse(f.store.showBubble())
            assertEquals(AiOutputConflict.OPEN_FULL, another.outputConflict())
            assertFalse(File(f.dir, "ai_settings.tmp").exists())
        } finally {
            workers.shutdownNow()
        }
    }
    @Test fun `failed atomic metadata write preserves previous complete settings`() {
        val f = fixture().also { it.seed() }
        File(f.dir, "ai_settings.tmp").mkdirs() // deterministic outputStream failure, not a permission assumption
        assertFalse(f.store.setModel("nvidia/new-model", AiProviderId.NVIDIA))
        assertEquals(AiProviders.NVIDIA_DEFAULT, f.store.model(AiProviderId.NVIDIA))
        assertEquals(AiKeySetup.TERMS_VERSION, f.store.acceptedTermsVersion())
        assertEquals(AiProviders.NVIDIA_TERMS_VERSION, f.store.acceptedTermsVersion(AiProviderId.NVIDIA))
        assertTrue(f.store.isReady(AiProviderId.GEMINI))
        assertTrue(f.store.isReady(AiProviderId.NVIDIA))
    }
}
