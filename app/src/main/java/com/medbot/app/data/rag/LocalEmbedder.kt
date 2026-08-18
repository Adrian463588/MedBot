package com.medbot.app.data.rag

/**
 * Embedding boundary. A real ONNX/LiteRT embedder is not wired in this
 * checkout, so production calls fail explicitly instead of hashing text.
 */
class LocalEmbedder(val dimensions: Int = 384) {
    init {
        require(dimensions > 0) { "Embedding dimensions must be positive" }
    }

    fun embed(text: String): FloatArray {
        if (text.isBlank()) throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Cannot embed empty text")
        throw RagProcessingException(
            RagFailureCode.EMBEDDER_UNAVAILABLE,
            "No local embedding model is wired"
        )
    }

    fun vectorToCsv(vector: FloatArray): String {
        require(vector.size == dimensions) { "Embedding dimension mismatch" }
        require(vector.all { it.isFinite() }) { "Embedding contains a non-finite value" }
        return vector.joinToString(",")
    }

    fun csvToVector(csv: String): FloatArray {
        if (csv.isBlank()) throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Stored embedding is empty")
        val parts = csv.split(',')
        if (parts.size != dimensions) {
            throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Stored embedding dimension mismatch")
        }
        return FloatArray(dimensions) { index ->
            parts[index].toFloatOrNull()?.takeIf { it.isFinite() }
                ?: throw RagProcessingException(RagFailureCode.INVALID_DOCUMENT, "Stored embedding is invalid")
        }
    }
}
