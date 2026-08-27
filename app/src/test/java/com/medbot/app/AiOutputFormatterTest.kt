package com.medbot.app

import com.medbot.app.core.common.AiOutputFormatter
import org.junit.Assert.*
import org.junit.Test

class AiOutputFormatterTest {

    @Test
    fun `parse extracts thinking content and leaves clean visible response`() {
        val rawInput = """
            <think>
            The patient has a headache and fever for 2 days.
            Differential diagnosis includes dengue or common cold.
            </think>
            Halo! Terima kasih atas pertanyaannya. Untuk demam 2 hari disertai sakit kepala:
            1. Perbanyak istirahat dan minum air putih.
            2. Pantau suhu tubuh secara berkala.
        """.trimIndent()

        val parsed = AiOutputFormatter.parse(rawInput)

        assertNotNull(parsed.thinkingContent)
        assertTrue(parsed.thinkingContent!!.contains("The patient has a headache"))
        assertFalse(parsed.isThinking)
        assertFalse(parsed.displayContent.contains("<think>"))
        assertFalse(parsed.displayContent.contains("</think>"))
        assertTrue(parsed.displayContent.startsWith("Halo! Terima kasih"))
    }

    @Test
    fun `parse handles streaming in-progress thinking state`() {
        val streamingThink = "<think>\nAnalyzing medical symptoms..."
        val parsed = AiOutputFormatter.parse(streamingThink, isGenerating = true)

        assertTrue(parsed.isThinking)
        assertEquals("Analyzing medical symptoms...", parsed.thinkingContent)
        assertEquals("", parsed.displayContent)
    }

    @Test
    fun `parse returns raw text when no think tags are present`() {
        val normalText = "Halo, selamat pagi! Ada yang bisa saya bantu terkait keluhan kesehatan Anda?"
        val parsed = AiOutputFormatter.parse(normalText)

        assertNull(parsed.thinkingContent)
        assertFalse(parsed.isThinking)
        assertEquals(normalText, parsed.displayContent)
    }

    @Test
    fun `cleanThinkingTags strips think blocks completely`() {
        val raw = "<think>Internal clinical reasoning</think>Halo dokter!"
        val clean = AiOutputFormatter.cleanThinkingTags(raw)

        assertEquals("Halo dokter!", clean)
    }

    @Test
    fun `cleanSessionTitle removes think tags, slop prefixes, and formats title cleanly`() {
        val slopTitle = "<think>\nOkay, the user is asking about diarrhea"
        val cleaned = AiOutputFormatter.cleanSessionTitle(slopTitle)
        assertFalse(cleaned.contains("<think>"))
        assertFalse(cleaned.contains("Okay, the user"))
        assertEquals("Konsultasi Medis", cleaned)

        val userQueryTitle = "saya sedang diare obat apa yang cocok diminum?"
        val cleanedUser = AiOutputFormatter.cleanSessionTitle(userQueryTitle)
        assertTrue(cleanedUser.startsWith("Saya sedang diare"))
        assertTrue(cleanedUser.length <= 37)

        val markdownTitle = "### **Keluhan Sakit Kepala & Demam**"
        val cleanedMarkdown = AiOutputFormatter.cleanSessionTitle(markdownTitle)
        assertEquals("Keluhan Sakit Kepala & Demam", cleanedMarkdown)
    }

    @Test
    fun `sanitizeMedicalText removes CJK artifacts without changing medical claims`() {
        val raw = "Pastikan Anda memiliki k\u5556 (cairan) dan waspadai perut berdebu serta kekurangannya."
        val sanitized = AiOutputFormatter.sanitizeMedicalText(raw)
        assertFalse(sanitized.contains("\u5556"))
        assertTrue(sanitized.contains("perut berdebu"))
        assertTrue(sanitized.contains("kekurangannya"))
    }
}
