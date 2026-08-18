package com.medbot.app.data.rag

import kotlin.math.sqrt

class LocalEmbedder(val dimensions: Int = 384) {

    fun embed(text: String): FloatArray {
        val vector = FloatArray(dimensions)
        val normalized = text.lowercase().replace(Regex("[^a-z0-9\\s]"), " ")
        val tokens = normalized.split(Regex("\\s+")).filter { it.length >= 2 }

        if (tokens.isEmpty()) {
            return vector
        }

        // Feature hashing over n-grams and word tokens
        for (token in tokens) {
            val h1 = token.hashCode()
            val idx1 = Math.floorMod(h1, dimensions)
            val sign1 = if ((h1 and 1) == 0) 1.0f else -1.0f
            vector[idx1] += sign1 * 1.5f

            // Subword char-trigram hashing for morphology matching
            if (token.length >= 3) {
                for (i in 0..token.length - 3) {
                    val tri = token.substring(i, i + 3)
                    val hTri = tri.hashCode()
                    val idxTri = Math.floorMod(hTri, dimensions)
                    val signTri = if ((hTri and 1) == 0) 0.5f else -0.5f
                    vector[idxTri] += signTri
                }
            }
        }

        // L2 normalize vector
        var sumSquares = 0.0f
        for (v in vector) {
            sumSquares += v * v
        }

        val norm = sqrt(sumSquares)
        if (norm > 1e-6f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }

        return vector
    }

    fun vectorToCsv(vector: FloatArray): String {
        return vector.joinToString(",") { "%.5f".format(it) }
    }

    fun csvToVector(csv: String): FloatArray {
        if (csv.isBlank()) return FloatArray(dimensions)
        val parts = csv.split(",")
        val result = FloatArray(dimensions)
        for (i in 0 until minOf(parts.size, dimensions)) {
            result[i] = parts[i].toFloatOrNull() ?: 0.0f
        }
        return result
    }
}
