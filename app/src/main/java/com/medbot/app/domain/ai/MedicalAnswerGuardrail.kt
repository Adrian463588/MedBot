package com.medbot.app.domain.ai

import com.medbot.app.domain.model.AppLanguage
import com.medbot.app.domain.model.Citation
import com.medbot.app.domain.model.ClinicalEvidence
import com.medbot.app.domain.model.ClinicalEvidenceKind

/** Final review decision applied before any local-model answer is persisted or shown. */
enum class MedicalAnswerDecision {
    ALLOW,
    INSUFFICIENT_EVIDENCE,
    INVALID_OUTPUT
}

data class MedicalAnswerReview(
    val decision: MedicalAnswerDecision,
    val reason: String
)

/**
 * Keeps medication answers evidence-bound. It does not diagnose or select a medicine.
 * A product catalogue alone can never satisfy the evidence requirement.
 */
class MedicalAnswerGuardrail {
    private val medicationTerms = listOf(
        "obat", "resep", "racik", "racikan", "dosis", "takaran", "aturan pakai",
        "antibiotik", "interaksi", "efek samping", "kontraindikasi", "ganti obat",
        "medication", "medicine", "medicines", "drug", "drugs", "pill", "tablet", "capsule",
        "prescription", "compounding", "dose", "dosage", "antibiotic",
        "interaction", "side effect", "contraindication", "replace my medicine"
    )

    private val protocolTerms = listOf(
        "protokol racikan", "formula racikan", "protokol resep", "formula resep",
        "compounding protocol", "compounding formula", "master formula", "standard formula",
        "beyond-use date", "beyond use date"
    )

    private val clinicalEvidenceTerms = listOf(
        "monograf", "monograph", "indikasi", "indication", "dosis", "dose", "dosage",
        "posologi", "posology", "kontraindikasi", "contraindication", "efek samping",
        "side effect", "interaksi", "interaction", "komposisi", "composition",
        "bahan aktif", "active ingredient", "aturan pakai", "administration",
        "protokol", "protocol", "racikan", "compounding", "formula",
        "penatalaksanaan", "tatalaksana", "terapi", "treatment", "oralit",
        "rehidrasi", "rehydration", "zink", "zinc", "antibiotik", "antibiotic",
        "antidiare", "antidiarrheal"
    )

    private val clinicalGroundingTerms = clinicalEvidenceTerms + listOf(
        "anamnesis", "history", "triase", "triage", "tanda bahaya", "warning sign",
        "red flag", "dehidrasi", "dehydration", "pemeriksaan", "examination",
        "diagnosis banding", "differential diagnosis", "kapan ke dokter", "seek care"
    )

    private val clinicalQueryTerms = listOf(
        "demam", "fever", "batuk", "cough", "sesak", "breathless", "nyeri", "pain",
        "pusing", "dizzy", "mual", "nausea", "muntah", "vomit", "diare", "diarrhea",
        "ruam", "rash", "luka", "wound", "gejala", "symptom", "tanda bahaya", "warning sign",
        "diagnosis", "diagnose", "penyakit", "disease", "hasil lab", "lab result", "darah",
        "blood", "kehamilan", "pregnancy", "tekanan darah", "blood pressure", "gula darah",
        "blood sugar", "kapan ke dokter", "when to seek care", "urgent care", "darurat", "emergency",
        "citra", "gambar", "foto", "image", "photo", "picture", "skin"
    )

    private val quantityPattern = Regex(
        "\\b\\d+(?:[.,]\\d+)?\\s*(?:mg|mcg|g|kg|ml|mL|l|%|iu|tablet|kapsul|capsule|drop|tetes|sendok)\\b",
        RegexOption.IGNORE_CASE
    )

    private val schedulePattern = Regex(
        "\\b(?:setiap\\s+|every\\s+)?\\d+(?:[.,]\\d+)?\\s*(?:x|kali|times|hari|days|jam|hours|menit|minutes|minggu|weeks)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isMedicationRequest(query: String): Boolean {
        val normalized = query.trim().lowercase()
        return normalized.isNotBlank() && medicationTerms.any(normalized::contains)
    }

    fun isPrescriptionOrCompoundingRequest(query: String): Boolean {
        val normalized = query.trim().lowercase()
        return listOf(
            "resep", "racik", "racikan", "compounding", "formula obat", "formula racikan",
            "prescription", "compound", "master formula"
        ).any(normalized::contains)
    }

    /** Returns true when a clinical answer must be grounded in an imported source. */
    fun requiresClinicalEvidence(query: String): Boolean {
        val normalized = query.trim().lowercase()
        return isMedicationRequest(normalized) || clinicalQueryTerms.any(normalized::contains)
    }

    /**
     * Returns true only when retrieved source text looks like medication evidence,
     * rather than a product-name/strength catalogue. The source is still shown as
     * user-imported evidence; this check never claims that the source is authoritative.
     */
    fun hasClinicalMedicationEvidence(evidenceText: String): Boolean {
        return hasClinicalManagementEvidence(evidenceText)
    }

    /**
     * Returns true when a source can support triage, probing, or differential
     * reasoning. This is intentionally less strict than medication evidence,
     * but still rejects competency indexes and secondary education pages unless
     * an authoritative clinical source is present in the same retrieved context.
     */
    fun hasClinicalGroundingEvidence(evidenceText: String): Boolean {
        val normalized = evidenceText.trim().lowercase()
        if (normalized.isBlank() || !clinicalGroundingTerms.any(normalized::contains)) {
            return false
        }
        val explicitlyRestricted = normalized.contains("classification_only") ||
            normalized.contains("secondary_web_education") ||
            normalized.contains("education_only") ||
            normalized.contains("source_role=secondary_education")
        return !explicitlyRestricted || hasAuthoritativeSourceMarker(normalized)
    }

    /**
     * Returns true when the retrieved text contains treatment terms and is not
     * an explicitly restricted index/web source. A user-imported source may
     * qualify here, but it remains visible as user-provided evidence and never
     * becomes an individual prescription automatically.
     */
    fun hasClinicalManagementEvidence(evidenceText: String): Boolean {
        val normalized = evidenceText.trim().lowercase()
        if (normalized.isBlank() || !clinicalEvidenceTerms.any(normalized::contains)) {
            return false
        }
        val explicitlyRestricted = normalized.contains("classification_only") ||
            normalized.contains("secondary_web_education") ||
            normalized.contains("education_only") ||
            (normalized.contains("[web_evidence") && !hasAuthoritativeSourceMarker(normalized))
        return !explicitlyRestricted || hasAuthoritativeSourceMarker(normalized)
    }

    /** A recipe or compounding request needs an explicit protocol marker. */
    fun hasCompoundingProtocolEvidence(evidenceText: String): Boolean =
        evidenceText.trim().lowercase().let { text ->
            text.isNotBlank() && protocolTerms.any(text::contains)
        }

    /**
     * Evidence-aware review used by the production pipeline. Eligibility is
     * decided from source metadata, not from a product name or a keyword in a
     * catalogue row. Citation labels in the answer must resolve to the exact
     * citations shown by the UI.
     */
    fun reviewWithEvidence(
        query: String,
        response: String,
        evidence: List<ClinicalEvidence>,
        citations: List<Citation>,
        language: AppLanguage
    ): MedicalAnswerReview {
        val answer = response.trim()
        if (answer.isBlank()) {
            return MedicalAnswerReview(MedicalAnswerDecision.INVALID_OUTPUT, "The local model returned no answer")
        }

        val clinicalRequest = requiresClinicalEvidence(query)
        val medicationRequest = isMedicationRequest(query)
        val eligibleClinicalEvidence = evidence.filter { it.generalClinicalEligible }
        if (clinicalRequest && eligibleClinicalEvidence.isEmpty()) {
            return MedicalAnswerReview(
                MedicalAnswerDecision.INSUFFICIENT_EVIDENCE,
                clinicalEvidenceBlockedReason(language)
            )
        }

        val eligibleMedicationEvidence = evidence.filter { it.medicationEligible }
        if (medicationRequest && eligibleMedicationEvidence.isEmpty()) {
            return MedicalAnswerReview(MedicalAnswerDecision.INSUFFICIENT_EVIDENCE, blockedReason(language))
        }
        if (isPrescriptionOrCompoundingRequest(query) &&
            evidence.none { it.evidenceKind == ClinicalEvidenceKind.COMPOUNDING_PROTOCOL }
        ) {
            return MedicalAnswerReview(
                MedicalAnswerDecision.INSUFFICIENT_EVIDENCE,
                "A prescription or compounding request requires an explicit protocol source"
            )
        }

        if (clinicalRequest) {
            val citationIds = citations.map { it.citationId.trim() }
                .filter { it.isNotBlank() }
                .toSet()
            val evidenceById = evidence
                .filter { it.evidenceId.isNotBlank() }
                .associateBy { it.evidenceId.trim() }
            val citedIds = CITATION_PATTERN.findAll(answer)
                .map { it.groupValues[1] }
                .toSet()
            val boundCitedIds = citedIds
                .intersect(citationIds)
                .filter { evidenceById[it] != null }
                .toSet()
            if (citationIds.isEmpty() || boundCitedIds.isEmpty()) {
                return MedicalAnswerReview(
                    MedicalAnswerDecision.INVALID_OUTPUT,
                    "The clinical answer does not bind its claims to a displayed citation"
                )
            }
            val clinicalEvidenceIds = eligibleClinicalEvidence.map { it.evidenceId }.toSet()
            if (boundCitedIds.intersect(clinicalEvidenceIds).isEmpty()) {
                return MedicalAnswerReview(
                    MedicalAnswerDecision.INSUFFICIENT_EVIDENCE,
                    "The displayed citation is not eligible clinical evidence"
                )
            }
            if (medicationRequest) {
                val medicationEvidenceIds = eligibleMedicationEvidence.map { it.evidenceId }.toSet()
                if (boundCitedIds.intersect(medicationEvidenceIds).isEmpty()) {
                    return MedicalAnswerReview(
                        MedicalAnswerDecision.INSUFFICIENT_EVIDENCE,
                        "Medication claims must cite medication-eligible evidence"
                    )
                }
            }
        }

        if (medicationRequest) {
            val normalizedEvidence = normalizeQuantityText(
                eligibleMedicationEvidence.joinToString(" ") { it.text }
            )
            val unsupportedQuantities = (quantityPattern.findAll(answer) + schedulePattern.findAll(answer))
                .map { normalizeQuantityText(it.value) }
                .filterNot { normalizedEvidence.contains(it) }
                .toList()
            if (unsupportedQuantities.isNotEmpty()) {
                return MedicalAnswerReview(
                    MedicalAnswerDecision.INVALID_OUTPUT,
                    "The answer contains a quantity that is absent from eligible medication evidence"
                )
            }
        }

        return MedicalAnswerReview(MedicalAnswerDecision.ALLOW, "Answer is bound to typed clinical evidence")
    }

    /**
     * A medication answer must expose the clinical reasoning contract in the
     * UI. This prevents a short generic paragraph from being presented as a
     * diagnosis or medication recommendation even when a source was retrieved.
     */
    fun hasRequiredClinicalStructure(response: String): Boolean {
        val normalized = response.trim().lowercase()
        val hasTriage = normalized.contains("triase") || normalized.contains("triage")
        val hasProbing = normalized.contains("probing") || normalized.contains("anamnesis") ||
            normalized.contains("pertanyaan") || normalized.contains("questions")
        val hasTreatment = normalized.contains("penatalaksanaan") ||
            normalized.contains("tatalaksana") || normalized.contains("terapi") ||
            normalized.contains("obat") || normalized.contains("medication") ||
            normalized.contains("treatment")
        val hasDifferential = normalized.contains("diagnosis banding") ||
            normalized.contains("differential") || normalized.contains("kemungkinan")
        val hasSafety = normalized.contains("tanda bahaya") || normalized.contains("red flag") ||
            normalized.contains("kapan") || normalized.contains("seek care") ||
            normalized.contains("urgent")
        return hasTriage && hasProbing && hasDifferential && hasTreatment && hasSafety
    }

    fun review(
        query: String,
        response: String,
        evidenceText: String,
        citationCount: Int,
        language: AppLanguage
    ): MedicalAnswerReview {
        val answer = response.trim()
        if (answer.isBlank()) {
            return MedicalAnswerReview(
                MedicalAnswerDecision.INVALID_OUTPUT,
                "The local model returned no answer"
            )
        }

        if (requiresClinicalEvidence(query) &&
            (citationCount == 0 || !hasClinicalGroundingEvidence(evidenceText))
        ) {
            return MedicalAnswerReview(
                MedicalAnswerDecision.INSUFFICIENT_EVIDENCE,
                clinicalEvidenceBlockedReason(language)
            )
        }

        if (!isMedicationRequest(query)) {
            return MedicalAnswerReview(MedicalAnswerDecision.ALLOW, "No medication evidence gate required")
        }

        if (citationCount == 0 || !hasClinicalMedicationEvidence(evidenceText)) {
            return MedicalAnswerReview(
                MedicalAnswerDecision.INSUFFICIENT_EVIDENCE,
                blockedReason(language)
            )
        }

        if (isPrescriptionOrCompoundingRequest(query) && !hasCompoundingProtocolEvidence(evidenceText)) {
            return MedicalAnswerReview(
                MedicalAnswerDecision.INSUFFICIENT_EVIDENCE,
                "A prescription or compounding request requires an explicit protocol source"
            )
        }

        val normalizedEvidence = normalizeQuantityText(evidenceText)
        val unsupportedQuantities = (quantityPattern.findAll(answer) + schedulePattern.findAll(answer))
            .map { normalizeQuantityText(it.value) }
            .filterNot { normalizedEvidence.contains(it) }
            .toList()
        if (unsupportedQuantities.isNotEmpty()) {
            return MedicalAnswerReview(
                MedicalAnswerDecision.INVALID_OUTPUT,
                "The answer contains a quantity that is absent from the cited evidence"
            )
        }

        return MedicalAnswerReview(MedicalAnswerDecision.ALLOW, "Medication answer is grounded")
    }

    private fun normalizeQuantityText(value: String): String = value
        .lowercase()
        .replace(',', '.')
        .replace(Regex("\\s+"), "")

    fun blockedMessage(language: AppLanguage): String {
        return if (language == AppLanguage.ENGLISH) {
            "INSUFFICIENT_DATA\n\nI cannot provide a prescription, dose, drug combination, or compounding formula because this device has no verified medication monograph or compounding protocol for this request. Import a real source document through Knowledge Base, then ask again. This app is educational and does not replace a clinician."
        } else {
            "INSUFFICIENT_DATA\n\nSaya belum dapat memberikan resep, dosis, kombinasi obat, atau formula racikan karena perangkat ini belum memiliki monograf obat atau protokol racikan yang tervalidasi untuk pertanyaan tersebut. Impor dokumen sumber yang nyata melalui Basis Pengetahuan, lalu coba lagi. Aplikasi ini bersifat edukatif dan bukan pengganti dokter."
        }
    }

    /** Honest fail-closed response for clinical questions without retrieved source text. */
    fun clinicalEvidenceUnavailableMessage(language: AppLanguage): String {
        return if (language == AppLanguage.ENGLISH) {
            "INSUFFICIENT_DATA\n\nI cannot provide a grounded clinical answer because no relevant source was retrieved. Import a real clinical document through Knowledge Base, or enable the optional web-evidence switch and ask again. The built-in medicine catalogue contains product identity only; it is not a monograph or treatment protocol."
        } else {
            "INSUFFICIENT_DATA\n\nSaya belum dapat memberikan jawaban klinis yang berbasis sumber karena tidak ada dokumen relevan yang ditemukan. Impor dokumen klinis nyata melalui Basis Pengetahuan, atau aktifkan pilihan evidence web lalu coba lagi. Katalog obat bawaan hanya berisi identitas produk; bukan monograf atau protokol terapi."
        }
    }

    /** Honest state when the optional network evidence path did not return a source. */
    fun onlineEvidenceUnavailableMessage(language: AppLanguage): String {
        return if (language == AppLanguage.ENGLISH) {
            "INSUFFICIENT_DATA\n\nNo safe online evidence was returned. The question was not sent to a cloud model and no diagnosis or individual prescription was generated. Check connectivity, remove identifiers, or import a real local source."
        } else {
            "INSUFFICIENT_DATA\n\nTidak ada evidence web yang aman untuk pertanyaan ini. Pertanyaan tidak dikirim ke model cloud dan tidak ada diagnosis atau resep individual yang dibuat. Periksa koneksi, hapus identitas pribadi, atau impor sumber lokal yang nyata."
        }
    }

    /** Honest state when the local embedding/RAG runtime cannot be queried. */
    fun ragUnavailableMessage(language: AppLanguage): String {
        return if (language == AppLanguage.ENGLISH) {
            "INSUFFICIENT_DATA\n\nThe local evidence index could not be queried because the verified embedding runtime is unavailable. No diagnosis or medication recommendation was generated. Check the local embedding assets and rebuild the index from a real source document."
        } else {
            "INSUFFICIENT_DATA\n\nIndeks evidence lokal tidak dapat dicari karena runtime embedding yang tervalidasi tidak tersedia. Tidak ada diagnosis atau rekomendasi obat yang dibuat. Periksa asset embedding lokal lalu bangun ulang indeks dari dokumen sumber yang nyata."
        }
    }

    /** Honest state when a loaded local model ignores the required clinical format. */
    fun structuredAnswerUnavailableMessage(language: AppLanguage): String {
        return if (language == AppLanguage.ENGLISH) {
            "INSUFFICIENT_DATA\n\nThe local model did not return the required triage, probing, differential, treatment-evidence, and red-flag structure. No clinical recommendation was saved. Try again or use a model that supports the MedBot clinical response contract."
        } else {
            "INSUFFICIENT_DATA\n\nModel lokal tidak mengembalikan struktur triase, probing, diagnosis banding, evidence terapi, dan tanda bahaya yang diwajibkan. Tidak ada rekomendasi klinis yang disimpan. Coba lagi atau gunakan model yang mendukung kontrak respons klinis MedBot."
        }
    }

    private fun blockedReason(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) {
            "A medication request has no cited monograph or protocol evidence"
        } else {
            "Permintaan obat belum memiliki bukti monograf atau protokol yang disitasi"
        }

    private fun clinicalEvidenceBlockedReason(language: AppLanguage): String =
        if (language == AppLanguage.ENGLISH) {
            "A clinical answer requires a relevant cited source"
        } else {
            "Jawaban klinis membutuhkan sumber relevan yang disitasi"
        }

    private fun hasAuthoritativeSourceMarker(normalizedEvidence: String): Boolean =
        listOf(
            "[ppk-fktp",
            "[kapita selekta",
            "[iso indonesia",
            "[mims indonesia",
            "[who ",
            "official clinical guideline",
            "national clinical guideline",
            "pharmaceutical_drug_compendium",
            "source_role=authoritative_guidance"
        ).any(normalizedEvidence::contains)

    private companion object {
        val CITATION_PATTERN = Regex("\\[([EW]\\d+)]")
    }
}
