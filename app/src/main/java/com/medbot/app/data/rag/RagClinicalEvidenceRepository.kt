package com.medbot.app.data.rag

import com.medbot.app.domain.model.ClinicalEvidence
import com.medbot.app.domain.model.ClinicalEvidenceKind
import com.medbot.app.domain.model.ClinicalEvidenceSourceRole
import com.medbot.app.domain.model.EvidenceQuery
import com.medbot.app.domain.model.EvidenceResult
import com.medbot.app.domain.repository.ClinicalEvidenceRepository
import com.medbot.app.domain.repository.RagFailure
import com.medbot.app.domain.repository.RagUnavailableException

/**
 * Adapts the real Room/vector RAG index to the clinical evidence boundary.
 * Product catalogue rows never enter this path, and chunks without a usable
 * source role are rejected instead of being promoted to treatment evidence.
 */
class RagClinicalEvidenceRepository(
    private val ragRepository: com.medbot.app.domain.repository.RagRepository,
    private val retrievalPlanner: ClinicalEvidenceQueryPlanner = ClinicalEvidenceQueryPlanner()
) : ClinicalEvidenceRepository {

    override suspend fun retrieve(query: EvidenceQuery): EvidenceResult {
        val normalized = query.text.trim()
        if (normalized.isBlank()) return EvidenceResult.NoEvidence

        return try {
            val searchResults = retrievalPlanner.queries(normalized, query.medicationRequest)
                .flatMap { searchQuery ->
                    ragRepository.searchSimilarChunks(
                        query = searchQuery,
                        topK = query.topK.coerceIn(MIN_TOP_K, MAX_TOP_K)
                    )
                }
                .distinctBy { result ->
                    result.citationId.ifBlank { "${result.chunk.docId}:${result.chunk.chunkIndex}" }
                }
                .sortedByDescending { it.similarityScore }

            val evidence = searchResults.mapNotNull { result ->
                val role = ClinicalEvidenceSourceRole.fromWireValue(result.sourceRole)
                val kind = ClinicalEvidenceKind.fromWireValue(result.evidenceKind)
                val item = ClinicalEvidence(
                    evidenceId = result.citationId.ifBlank {
                        "local:${result.chunk.docId}:${result.chunk.chunkIndex}"
                    },
                    text = result.chunk.textContent,
                    title = result.documentTitle,
                    sourceRole = role,
                    evidenceKind = kind,
                    sourceUri = result.documentUri.takeIf { it.isNotBlank() },
                    sourceUrl = result.sourceUrl.takeIf { it.isNotBlank() },
                    pageNumber = result.chunk.pageNumber.takeIf { it > 0 },
                    section = result.chunk.sectionTitle.takeIf { it.isNotBlank() },
                    recordId = result.recordId.takeIf { it.isNotBlank() },
                    sourceSha256 = result.sourceSha256.takeIf { it.isNotBlank() },
                    revision = result.revision.takeIf { it.isNotBlank() },
                    similarityScore = result.similarityScore
                )
                val eligible = if (query.medicationRequest || query.prescriptionOrCompoundingRequest) {
                    item.medicationEligible
                } else {
                    item.generalClinicalEligible
                }
                item.takeIf {
                    eligible &&
                        it.text.isNotBlank() &&
                        it.title.isNotBlank() &&
                        isTopicallyRelevant(normalized, it, query.medicationRequest)
                }
            }

            if (evidence.isEmpty()) EvidenceResult.NoEvidence
            else EvidenceResult.Ready(evidence.take(query.topK.coerceIn(MIN_TOP_K, MAX_TOP_K)))
        } catch (error: RagUnavailableException) {
            if (error.failure == RagFailure.EMBEDDER_UNAVAILABLE) {
                EvidenceResult.EmbedderUnavailable
            } else {
                EvidenceResult.InvalidSource(error.message ?: "Local evidence is unavailable")
            }
        }
    }

    private fun isTopicallyRelevant(
        query: String,
        evidence: ClinicalEvidence,
        medicationRequest: Boolean
    ): Boolean {
        val queryTerms = query
            .lowercase()
            .split(WORD_BOUNDARY)
            .filter { it.length >= 4 && it !in GENERIC_TERMS }
            .flatMap { term -> TERM_ALIASES[term].orEmpty().ifEmpty { listOf(term) } }
            .toSet()
        val sourceText = listOf(evidence.title, evidence.section.orEmpty(), evidence.text)
            .joinToString(" ")
            .lowercase()
        val sourceTerms = sourceText.split(WORD_BOUNDARY).filter(String::isNotBlank).toSet()
        val titleTerms = evidence.title.lowercase().split(WORD_BOUNDARY).filter(String::isNotBlank).toSet()
        val topicInText = queryTerms.any(sourceTerms::contains)
        val topicInTitle = queryTerms.any(titleTerms::contains)
        if (queryTerms.isEmpty()) {
            return evidence.similarityScore >= MIN_SEMANTIC_RELEVANCE
        }

        // A medication query must not be grounded by a monograph that merely
        // mentions the symptom in an adverse-effect list. Require an explicit
        // clinical-management signal and an exact topic hit in the title or the
        // management section. The measured embedding score remains a secondary
        // ranking signal; it cannot promote an unrelated drug.
        if (medicationRequest) {
            val managementSignal = MANAGEMENT_TERMS.any(sourceText::contains)
            return managementSignal && topicInText && (
                topicInTitle || appearsInManagementSection(sourceText, queryTerms)
            )
        }

        return topicInText || topicInTitle || evidence.similarityScore >= MIN_SEMANTIC_RELEVANCE
    }

    private fun appearsInManagementSection(sourceText: String, queryTerms: Set<String>): Boolean {
        val sectionMarkers = listOf(
            "indikasi", "indication", "penatalaksanaan", "tatalaksana", "terapi",
            "treatment", "management", "dosis", "dose", "protokol", "protocol",
            "rehidrasi", "rehydration", "oralit", "zink", "zinc"
        )
        return sectionMarkers.any { marker ->
            var start = sourceText.indexOf(marker)
            while (start >= 0) {
                val end = (start + MANAGEMENT_SECTION_WINDOW).coerceAtMost(sourceText.length)
                val window = sourceText.substring(start, end)
                if (queryTerms.any(window.split(WORD_BOUNDARY).toSet()::contains)) return true
                start = sourceText.indexOf(marker, start + marker.length)
            }
            false
        }
    }

    private companion object {
        const val MIN_TOP_K = 1
        const val MAX_TOP_K = 16
        const val MIN_SEMANTIC_RELEVANCE = 0.58f
        val WORD_BOUNDARY = Regex("[^\\p{L}\\p{N}]+")
        val GENERIC_TERMS = setOf(
            "yang", "dan", "atau", "untuk", "dengan", "dari", "pada", "dalam", "tidak",
            "ada", "apa", "saya", "ini", "itu", "bisa", "harus", "lebih", "obat", "dosis",
            "bagaimana", "penanganan", "penatalaksanaan", "treatment", "terapi", "symptom",
            "the", "and", "or", "for", "with", "from", "this", "that", "what", "how", "can",
            "medicine", "medication", "drug", "dose"
        )
        val TERM_ALIASES = mapOf(
            "diare" to listOf("diare", "diarrhea", "diarrhoea"),
            "diarrhea" to listOf("diare", "diarrhea", "diarrhoea"),
            "diarrhoea" to listOf("diare", "diarrhea", "diarrhoea"),
            "demam" to listOf("demam", "fever", "febrile"),
            "fever" to listOf("demam", "fever", "febrile"),
            "batuk" to listOf("batuk", "cough"),
            "cough" to listOf("batuk", "cough"),
            "muntah" to listOf("muntah", "vomiting", "emesis"),
            "vomiting" to listOf("muntah", "vomiting", "emesis")
        )
        val MANAGEMENT_TERMS = listOf(
            "penatalaksanaan", "tatalaksana", "terapi", "treatment", "management",
            "indikasi", "indication", "dosis", "dose", "monograf", "monograph",
            "rehidrasi", "rehydration", "oralit", "zink", "zinc", "protokol", "protocol"
        )
        const val MANAGEMENT_SECTION_WINDOW = 900
    }
}

/** Keeps ID/English retrieval deterministic without depending on UI wording. */
class ClinicalEvidenceQueryPlanner {
    fun queries(text: String, medicationRequest: Boolean): List<String> {
        val normalized = text.trim().replace(WHITESPACE, " ")
        val translatedTerms = MEDICAL_SYNONYMS.entries
            .filter { (term, _) -> normalized.contains(term, ignoreCase = true) }
            .flatMap { (_, synonyms) -> synonyms }
        val suffix = if (medicationRequest) {
            " obat monograf dosis kontraindikasi interaksi"
        } else {
            " diagnosis penatalaksanaan red flags"
        }
        return (listOf(normalized, "$normalized$suffix") + translatedTerms)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_QUERY_VARIANTS)
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val MEDICAL_SYNONYMS = linkedMapOf(
            "diare" to listOf("diarrhea", "acute diarrhea", "dehidrasi"),
            "diarrhea" to listOf("diare", "dehydration"),
            "demam" to listOf("fever", "febrile illness"),
            "fever" to listOf("demam"),
            "batuk" to listOf("cough", "respiratory symptom"),
            "cough" to listOf("batuk"),
            "muntah" to listOf("vomiting", "emesis"),
            "vomiting" to listOf("muntah", "emesis"),
            "obat" to listOf("medication", "drug", "therapy"),
            "medicine" to listOf("obat", "medication"),
            "medication" to listOf("obat", "medicine")
        )
        const val MAX_QUERY_VARIANTS = 6
    }
}
