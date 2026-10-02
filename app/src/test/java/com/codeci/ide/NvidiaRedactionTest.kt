package com.codeci.ide

import com.codeci.ide.ui.support.FeedbackDraft
import com.codeci.ide.ui.support.FeedbackInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NvidiaRedactionTest {
    @Test fun `NVIDIA shape is scrubbed even without a stored key`() {
        val key = "nvapi-" + "K_a-7".repeat(12)
        val clean = FeedbackDraft.redact(listOf("copied $key into terminal"), null).single()
        assertFalse(clean.contains(key))
        assertTrue(clean.contains("<redacted>"))
    }
    @Test fun `both stored provider literals are scrubbed regardless of their prefix`() {
        val gemini = "unusual-Google-key-without-known-prefix"
        val nvidia = "unusual-NVIDIA-key-without-known-prefix"
        val clean = FeedbackDraft.redact(listOf("$gemini $nvidia"), null, extraSecrets = listOf(gemini, nvidia)).single()
        assertFalse(clean.contains(gemini))
        assertFalse(clean.contains(nvidia))
    }
    @Test fun `log and crash attachments redact provider credentials before trimming`() {
        val key = "nvapi-" + "x".repeat(40)
        val report = FeedbackDraft.build(FeedbackInput(
            "1", "14", 34, "test phone", "arm64", userText = "safe test",
            includeLog = true, logTail = listOf("request $key"),
            includeCrash = true, crashRecord = "exception $key", extraSecrets = listOf(key)
        ))
        assertFalse(report.contains(key))
        assertTrue(report.contains("safe test"))
    }
}
