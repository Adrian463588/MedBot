package com.medbot.app.domain.model

import java.util.UUID

data class RagDocument(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileUri: String,
    val mimeType: String,
    val fileSize: Long,
    val pageCount: Int,
    val chunkCount: Int,
    val sha256: String,
    val indexedAt: Long = System.currentTimeMillis()
)

data class DocChunk(
    val id: String = UUID.randomUUID().toString(),
    val docId: String,
    val chunkIndex: Int,
    val textContent: String,
    val pageNumber: Int,
    val sectionTitle: String,
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocChunk
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class SearchResult(
    val chunk: DocChunk,
    val similarityScore: Float,
    val documentTitle: String
)
