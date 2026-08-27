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
        minSimilarity: Float = 0.15f,
        queryText: String = ""
    ): List<SearchResult> {
        val scored = mutableListOf<SearchResult>()
        val queryTerms = tokenize(queryText)

        for ((chunk, title) in chunksWithTitle) {
            val semanticSimilarity = cosineSimilarity(queryVector, chunk.embedding)
            val textOverlap = lexicalOverlap(queryTerms, tokenize(chunk.textContent))
            val titleOverlap = lexicalOverlap(queryTerms, tokenize(title))
            // The embedding remains mandatory and is computed by the caller.
            // Lexical overlap is only a grounded tie-breaker/recovery path for
            // exact clinical terms that a small embedding model may rank low.
            // A title match is weighted separately so a query such as "obat
            // diare" prefers the Diare record over unrelated records that
            // merely mention diarrhoea in an example or side-effect list.
            val score = (
                semanticSimilarity +
                    textOverlap * LEXICAL_SCORE_WEIGHT +
                    titleOverlap * TITLE_SCORE_WEIGHT
                )
                .coerceAtMost(1.0f)
            if (semanticSimilarity >= minSimilarity || textOverlap > 0.0f || titleOverlap > 0.0f) {
                scored.add(
                    SearchResult(
                        chunk = chunk,
                        similarityScore = score,
                        documentTitle = title
                    )
                )
            }
        }

        return scored.sortedByDescending { it.similarityScore }.take(topK)
    }

    private fun tokenize(text: String): Set<String> = text
        .lowercase()
        .split(WORD_BOUNDARY)
        .asSequence()
        .map { it.trim() }
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .toSet()

    private fun lexicalOverlap(queryTerms: Set<String>, chunkTerms: Set<String>): Float {
        if (queryTerms.isEmpty() || chunkTerms.isEmpty()) return 0.0f
        return queryTerms.intersect(chunkTerms).size.toFloat() / queryTerms.size.toFloat()
    }

    private companion object {
        const val LEXICAL_SCORE_WEIGHT = 0.25f
        const val TITLE_SCORE_WEIGHT = 0.35f
        val WORD_BOUNDARY = Regex("[^\\p{L}\\p{N}]+")
        val STOP_WORDS = setOf(
            "yang", "dan", "atau", "untuk", "dengan", "dari", "pada", "dalam", "tidak",
            "ada", "apa", "saya", "ini", "itu", "nya", "akan", "bisa", "harus", "lebih",
            "the", "and", "or", "for", "with", "from", "this", "that", "what", "how", "can"
        )
    }
}
