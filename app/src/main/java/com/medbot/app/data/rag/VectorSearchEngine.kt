package com.medbot.app.data.rag

import com.medbot.app.domain.model.DocChunk
import com.medbot.app.domain.model.SearchResult
import kotlin.math.sqrt

class VectorSearchEngine {

    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0.0f
        var dot = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f

        for (i in v1.indices) {
            val a = v1[i]
            val b = v2[i]
            dot += a * b
            norm1 += a * a
            norm2 += b * b
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 1e-6f) (dot / denominator) else 0.0f
    }

    fun searchTopK(
        queryVector: FloatArray,
        chunksWithTitle: List<Pair<DocChunk, String>>,
        topK: Int = 4,
        minSimilarity: Float = 0.15f
    ): List<SearchResult> {
        val scored = mutableListOf<SearchResult>()

        for ((chunk, title) in chunksWithTitle) {
            val sim = cosineSimilarity(queryVector, chunk.embedding)
            if (sim >= minSimilarity) {
                scored.add(
                    SearchResult(
                        chunk = chunk,
                        similarityScore = sim,
                        documentTitle = title
                    )
                )
            }
        }

        return scored.sortedByDescending { it.similarityScore }.take(topK)
    }
}
