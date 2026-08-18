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

        val hasReferenceWords = listOf("dia", "itu", "tadi", "obat tersebut", "lanjutannya", "gejala sama", "same", "it", "again")
            .any { currentQuery.lowercase().contains(it) }

        return if (hasReferenceWords && contextPieces.isNotEmpty()) {
            "Riwayat Sebelumnya:\n${contextPieces.joinToString("\n")}\n\nPertanyaan Terbaru Pasien:\n$currentQuery"
        } else {
            currentQuery.trim()
        }
    }
}
