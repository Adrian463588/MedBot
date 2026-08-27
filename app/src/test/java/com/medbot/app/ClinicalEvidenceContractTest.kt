package com.medbot.app

import com.medbot.app.domain.ai.MedicalAnswerDecision
import com.medbot.app.domain.ai.MedicalAnswerGuardrail
import com.medbot.app.domain.model.AppLanguage
import com.medbot.app.domain.model.Citation
import com.medbot.app.domain.model.ClinicalEvidence
import com.medbot.app.domain.model.ClinicalEvidenceKind
import com.medbot.app.domain.model.ClinicalEvidenceSourceRole
import com.medbot.app.domain.model.EvidenceQuery
import com.medbot.app.domain.model.EvidenceResult
import com.medbot.app.data.rag.ClinicalEvidenceQueryPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalEvidenceContractTest {
    private val guardrail = MedicalAnswerGuardrail()

    @Test
    fun `citation must bind to the eligible evidence with the same answer id`() {
        val evidence = clinicalEvidence("E1")
        val citation = Citation(
            citationId = "E1",
            documentTitle = evidence.title,
            snippet = evidence.text
        )

        val allowed = guardrail.reviewWithEvidence(
            query = "Apa tanda bahaya demam?",
            response = "Triase awal perlu pemeriksaan lanjutan [E1].",
            evidence = listOf(evidence),
            citations = listOf(citation),
            language = AppLanguage.INDONESIAN
        )
        assertEquals(MedicalAnswerDecision.ALLOW, allowed.decision)

        val forgedLabel = guardrail.reviewWithEvidence(
            query = "Apa tanda bahaya demam?",
            response = "Triase awal perlu pemeriksaan lanjutan [E2].",
            evidence = listOf(evidence),
            citations = listOf(citation),
            language = AppLanguage.INDONESIAN
        )
        assertEquals(MedicalAnswerDecision.INVALID_OUTPUT, forgedLabel.decision)
    }

    @Test
    fun `product catalogue or unclassified upload cannot satisfy medication evidence`() {
        val productOnly = clinicalEvidence(
            id = "E1",
            role = ClinicalEvidenceSourceRole.USER_PROVIDED,
            kind = ClinicalEvidenceKind.UNKNOWN,
            text = "Nama produk Paracetamol tablet 500 mg"
        )
        assertTrue(!productOnly.medicationEligible)
        assertTrue(!productOnly.generalClinicalEligible)

        val result = guardrail.reviewWithEvidence(
            query = "Obat apa untuk demam?",
            response = "Gunakan obat sesuai kebutuhan [E1].",
            evidence = listOf(productOnly),
            citations = listOf(Citation(citationId = "E1", documentTitle = "Katalog", snippet = productOnly.text)),
            language = AppLanguage.INDONESIAN
        )
        assertEquals(MedicalAnswerDecision.INSUFFICIENT_EVIDENCE, result.decision)
    }

    @Test
    fun `user imported clinical source remains user provided but can ground a typed answer`() {
        val uploadedGuideline = clinicalEvidence(
            id = "E1",
            role = ClinicalEvidenceSourceRole.USER_PROVIDED,
            kind = ClinicalEvidenceKind.GUIDELINE,
            text = "Pedoman demam: triase dan tanda bahaya perlu dinilai."
        )

        assertTrue(uploadedGuideline.generalClinicalEligible)
        assertEquals(ClinicalEvidenceSourceRole.USER_PROVIDED, uploadedGuideline.sourceRole)
    }

    @Test
    fun `textbook reference grounds clinical routing but never medication eligibility`() {
        val textbook = clinicalEvidence(
            id = "E_TEXTBOOK",
            role = ClinicalEvidenceSourceRole.fromWireValue("medical_textbook_reference"),
            kind = ClinicalEvidenceKind.fromWireValue("medical_textbook_reference"),
            text = "Referensi klinis diare: evaluasi hidrasi dan tanda bahaya."
        )

        assertEquals(ClinicalEvidenceSourceRole.CLINICAL_TEXTBOOK_REFERENCE, textbook.sourceRole)
        assertEquals(ClinicalEvidenceKind.CLINICAL_REFERENCE, textbook.evidenceKind)
        assertTrue(textbook.generalClinicalEligible)
        assertTrue(!textbook.medicationEligible)
    }

    @Test
    fun `query planner keeps bilingual clinical synonyms without replacing original query`() {
        val queries = ClinicalEvidenceQueryPlanner().queries("Saya batuk dan demam", medicationRequest = false)

        assertEquals("Saya batuk dan demam", queries.first())
        assertTrue(queries.any { it.contains("cough", ignoreCase = true) })
        assertTrue(queries.any { it.contains("fever", ignoreCase = true) })
    }

    @Test
    fun `typed evidence result is explicit when the embedder is unavailable`() {
        val result: EvidenceResult = EvidenceResult.EmbedderUnavailable
        assertEquals(EvidenceResult.EmbedderUnavailable, result)
        val query = EvidenceQuery("diare", medicationRequest = true)
        assertEquals("diare", query.text)
        assertTrue(query.medicationRequest)
    }

    private fun clinicalEvidence(
        id: String,
        role: ClinicalEvidenceSourceRole = ClinicalEvidenceSourceRole.NATIONAL_CLINICAL_GUIDELINE,
        kind: ClinicalEvidenceKind = ClinicalEvidenceKind.GUIDELINE,
        text: String = "Pedoman demam: tanda bahaya dan penatalaksanaan perlu dinilai."
    ) = ClinicalEvidence(
        evidenceId = id,
        text = text,
        title = "Pedoman klinis",
        sourceRole = role,
        evidenceKind = kind
    )
}
