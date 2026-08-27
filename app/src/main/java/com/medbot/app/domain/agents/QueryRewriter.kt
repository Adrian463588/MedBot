package com.medbot.app.domain.agents

import com.medbot.app.domain.model.ChatMessage

class QueryRewriter {

    fun rewriteQueryWithHistory(currentQuery: String, history: List<ChatMessage>): String {
        if (history.isEmpty()) return currentQuery.trim()

        val recentTurns = history.takeLast(6)
        val contextPieces = mutableListOf<String>()

        for (msg in recentTurns) {
            val role = if (msg.isUser) "Pasien" else "Dokter"
            val textSnippet = if (msg.text.length > 120) msg.text.take(120) + "..." else msg.text
            contextPieces.add("$role: $textSnippet")
        }

        // Match reference words as words, not substrings. A substring check
        // made every Indonesian query containing "sakit" look like it
        // contained the English pronoun "it", causing irrelevant history to
        // pollute retrieval and the web fallback query.
        val hasReferenceWords = REFERENCE_PATTERN.containsMatchIn(currentQuery)

        return if (hasReferenceWords && contextPieces.isNotEmpty()) {
            "Riwayat Sebelumnya:\n${contextPieces.joinToString("\n")}\n\nPertanyaan Terbaru Pasien:\n$currentQuery"
        } else {
            currentQuery.trim()
        }
    }

    private companion object {
        val REFERENCE_PATTERN = Regex(
            "(?i)(?:\\b(?:dia|itu|tadi|same|it|again)\\b|obat\\s+tersebut|lanjutannya|gejala\\s+sama)"
        )
    }
}
