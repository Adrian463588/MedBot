package com.medbot.app

import com.medbot.app.domain.agents.QueryRewriter
import com.medbot.app.domain.model.ChatMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryRewriterTest {

    @Test
    fun `QueryRewriter merges conversational history when pronouns or references are used`() {
        val rewriter = QueryRewriter()
        val history = listOf(
            ChatMessage(sessionId = "s1", text = "Saya demam tinggi dan sakit kepala sejak 2 hari.", isUser = true),
            ChatMessage(sessionId = "s1", text = "Apakah ada batuk atau nyeri tenggorokan?", isUser = false)
        )

        val rewritten = rewriter.rewriteQueryWithHistory("Obat apa yang cocok untuk itu?", history)
        assertTrue(rewritten.contains("Riwayat Sebelumnya:"))
        assertTrue(rewritten.contains("demam tinggi"))
        assertTrue(rewritten.contains("Obat apa yang cocok untuk itu?"))
    }

    @Test
    fun `medical word containing it does not pull unrelated history into retrieval`() {
        val rewriter = QueryRewriter()
        val history = listOf(
            ChatMessage(sessionId = "s1", text = "Saya demam tinggi.", isUser = true)
        )

        val rewritten = rewriter.rewriteQueryWithHistory("Saya sakit diare", history)

        assertTrue(rewritten == "Saya sakit diare")
    }
}
