package com.medbot.app.domain.clinical

/**
 * Creates bounded retrieval queries for a clinical conversation.
 *
 * This class only adds source-section vocabulary. It never adds patient facts,
 * a diagnosis, a medicine, or a dose. Keeping the original query as the first
 * candidate preserves exact-title matches while the second candidate helps a
 * small local embedder find anamnesis, triage, and treatment sections.
 */
class ClinicalRetrievalPlanner {
    fun queries(query: String, medicationRequest: Boolean): List<String> {
        val normalized = query
            .trim()
            .replace(WHITESPACE, " ")
            .take(MAX_QUERY_CHARS)
        if (normalized.isBlank()) return emptyList()

        val sectionTerms = if (medicationRequest) {
            "indikasi kontraindikasi penatalaksanaan terapi monograf efek samping interaksi"
        } else {
            "anamnesis diagnosis banding triase tanda bahaya penatalaksanaan"
        }
        return listOf(
            normalized,
            "$normalized $sectionTerms"
        ).distinct()
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val MAX_QUERY_CHARS = 800
    }
}
