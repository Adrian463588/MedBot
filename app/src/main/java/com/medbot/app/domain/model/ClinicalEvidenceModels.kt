package com.medbot.app.domain.model

/** Trust classification for local clinical evidence. */
enum class ClinicalEvidenceSourceRole(val wireValue: String) {
    NATIONAL_CLINICAL_GUIDELINE("national_clinical_guideline"),
    PHARMACEUTICAL_DRUG_COMPENDIUM("pharmaceutical_drug_compendium"),
    PRIMARY_RESEARCH("primary_research"),
    CLINICAL_TEXTBOOK_REFERENCE("clinical_textbook_reference"),
    USER_PROVIDED("user_provided"),
    SECONDARY_EDUCATION("secondary_web_education"),
    CLASSIFICATION_ONLY("classification_only"),
    UNKNOWN("unknown");

    val supportsGeneralClinicalGrounding: Boolean
        get() = this != CLASSIFICATION_ONLY && this != SECONDARY_EDUCATION && this != UNKNOWN

    companion object {
        fun fromWireValue(value: String): ClinicalEvidenceSourceRole {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { role ->
                role.wireValue == normalized || role.name.lowercase() == normalized
            } ?: when {
                normalized.contains("guideline") || normalized.contains("ppk") -> NATIONAL_CLINICAL_GUIDELINE
                normalized.contains("drug") || normalized.contains("pharma") || normalized.contains("mims") || normalized.contains("iso") -> PHARMACEUTICAL_DRUG_COMPENDIUM
                normalized.contains("primary") || normalized.contains("research") -> PRIMARY_RESEARCH
                normalized.contains("textbook") || normalized.contains("reference") -> CLINICAL_TEXTBOOK_REFERENCE
                normalized.contains("classification") || normalized.contains("skdi") -> CLASSIFICATION_ONLY
                normalized.contains("secondary") || normalized.contains("education") || normalized.contains("halodoc") || normalized.contains("k24") -> SECONDARY_EDUCATION
                normalized.contains("user") || normalized.contains("upload") || normalized.contains("saf") -> USER_PROVIDED
                else -> UNKNOWN
            }
        }
    }
}

/** The kind of source material represented by one retrieved chunk. */
enum class ClinicalEvidenceKind(val wireValue: String) {
    GUIDELINE("guideline"),
    DRUG_MONOGRAPH("drug_monograph"),
    DRUG_INTERACTION("drug_interaction"),
    COMPOUNDING_PROTOCOL("compounding_protocol"),
    DIAGNOSTIC_GUIDANCE("diagnostic_guidance"),
    CLINICAL_REFERENCE("clinical_reference"),
    EDUCATION("education"),
    CLASSIFICATION("classification"),
    PRODUCT_CATALOG("product_catalog"),
    USER_PROVIDED_DOCUMENT("user_provided_document"),
    UNKNOWN("unknown");

    companion object {
        fun fromWireValue(value: String): ClinicalEvidenceKind {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.wireValue == normalized || it.name.lowercase() == normalized }
                ?: when {
                    normalized.contains("compound") || normalized.contains("racik") -> COMPOUNDING_PROTOCOL
                    normalized.contains("interaction") -> DRUG_INTERACTION
                    normalized.contains("monograph") || normalized.contains("drug") || normalized.contains("pharma") -> DRUG_MONOGRAPH
                    normalized.contains("diagnos") -> DIAGNOSTIC_GUIDANCE
                    normalized.contains("textbook") || normalized.contains("reference") -> CLINICAL_REFERENCE
                    normalized.contains("guideline") || normalized.contains("ppk") -> GUIDELINE
                    normalized.contains("classification") || normalized.contains("skdi") -> CLASSIFICATION
                    normalized.contains("product") || normalized.contains("catalog") -> PRODUCT_CATALOG
                    normalized.contains("education") || normalized.contains("secondary") -> EDUCATION
                    else -> UNKNOWN
                }
        }
    }
}

/** Query contract passed from the clinical orchestration layer to local RAG. */
data class EvidenceQuery(
    val text: String,
    val medicationRequest: Boolean,
    val prescriptionOrCompoundingRequest: Boolean = false,
    val topK: Int = 8
)

/** One source-backed item that may be cited by a generated claim. */
data class ClinicalEvidence(
    val evidenceId: String,
    val text: String,
    val title: String,
    val sourceRole: ClinicalEvidenceSourceRole,
    val evidenceKind: ClinicalEvidenceKind,
    val sourceUri: String? = null,
    val sourceUrl: String? = null,
    val pageNumber: Int? = null,
    val section: String? = null,
    val recordId: String? = null,
    val sourceSha256: String? = null,
    val revision: String? = null,
    val retrievedAt: Long? = null,
    val similarityScore: Float = 0f
) {
    val medicationEligible: Boolean
        get() = sourceRole != ClinicalEvidenceSourceRole.SECONDARY_EDUCATION &&
            sourceRole != ClinicalEvidenceSourceRole.CLASSIFICATION_ONLY &&
            evidenceKind in setOf(
                ClinicalEvidenceKind.GUIDELINE,
                ClinicalEvidenceKind.DRUG_MONOGRAPH,
                ClinicalEvidenceKind.DRUG_INTERACTION,
                ClinicalEvidenceKind.COMPOUNDING_PROTOCOL
            )

    val generalClinicalEligible: Boolean
        get() = when {
            sourceRole == ClinicalEvidenceSourceRole.USER_PROVIDED ->
                // A user-selected source is never upgraded to an official
                // source, but its declared clinical material kind is still
                // useful for retrieval. The bytes and provenance remain
                // visible to the user, and unsupported/education-only rows
                // stay outside the clinical gate.
                evidenceKind in setOf(
                    ClinicalEvidenceKind.USER_PROVIDED_DOCUMENT,
                    ClinicalEvidenceKind.GUIDELINE,
                    ClinicalEvidenceKind.DRUG_MONOGRAPH,
                    ClinicalEvidenceKind.DRUG_INTERACTION,
                    ClinicalEvidenceKind.COMPOUNDING_PROTOCOL,
                    ClinicalEvidenceKind.DIAGNOSTIC_GUIDANCE,
                    ClinicalEvidenceKind.CLINICAL_REFERENCE
                )
            else -> sourceRole.supportsGeneralClinicalGrounding &&
                evidenceKind != ClinicalEvidenceKind.CLASSIFICATION &&
                evidenceKind != ClinicalEvidenceKind.PRODUCT_CATALOG &&
                evidenceKind != ClinicalEvidenceKind.UNKNOWN
        }
}

sealed interface EvidenceResult {
    data class Ready(val evidence: List<ClinicalEvidence>) : EvidenceResult
    data object NoEvidence : EvidenceResult
    data object EmbedderUnavailable : EvidenceResult
    data class InvalidSource(val reason: String) : EvidenceResult
}
