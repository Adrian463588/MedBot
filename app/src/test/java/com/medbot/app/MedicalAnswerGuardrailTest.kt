package com.medbot.app

import com.medbot.app.domain.ai.MedicalAnswerDecision
import com.medbot.app.domain.ai.MedicalAnswerGuardrail
import com.medbot.app.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalAnswerGuardrailTest {
    private val guardrail = MedicalAnswerGuardrail()

    @Test
    fun `general clinical question without evidence is blocked`() {
        val review = guardrail.review(
            query = "What warning signs require urgent care?",
            response = "Seek urgent care for severe breathing difficulty or loss of consciousness.",
            evidenceText = "",
            citationCount = 0,
            language = AppLanguage.ENGLISH
        )

        assertEquals(MedicalAnswerDecision.INSUFFICIENT_EVIDENCE, review.decision)
    }

    @Test
    fun `catalogue only medication request is blocked`() {
        val review = guardrail.review(
            query = "Buatkan resep obat demam",
            response = "Paracetamol 500 mg diminum tiga kali sehari",
            evidenceText = "Nama produk: Paracetamol; bentuk tablet",
            citationCount = 1,
            language = AppLanguage.INDONESIAN
        )

        assertEquals(MedicalAnswerDecision.INSUFFICIENT_EVIDENCE, review.decision)
    }

    @Test
    fun `medication request without cited evidence is insufficient`() {
        val review = guardrail.review(
            query = "Apa dosis obat ini?",
            response = "INSUFFICIENT_DATA",
            evidenceText = "",
            citationCount = 0,
            language = AppLanguage.INDONESIAN
        )

        assertEquals(MedicalAnswerDecision.INSUFFICIENT_EVIDENCE, review.decision)
        assertTrue(guardrail.blockedMessage(AppLanguage.INDONESIAN).contains("INSUFFICIENT_DATA"))
    }

    @Test
    fun `clinical symptom query requires imported evidence`() {
        assertTrue(guardrail.requiresClinicalEvidence("Apa tanda bahaya demam?"))
        assertTrue(guardrail.requiresClinicalEvidence("What warning signs require urgent care?"))
        assertTrue(guardrail.clinicalEvidenceUnavailableMessage(AppLanguage.INDONESIAN).contains("INSUFFICIENT_DATA"))
    }

    @Test
    fun `grounded quantity must be present in cited evidence`() {
        val review = guardrail.review(
            query = "Jelaskan obat dan dosisnya",
            response = "Monograf mencantumkan 500 mg sebagai kekuatan sediaan.",
            evidenceText = "[ISO Indonesia] Monograf: kekuatan sediaan 500 mg.",
            citationCount = 1,
            language = AppLanguage.INDONESIAN
        )

        assertEquals(MedicalAnswerDecision.ALLOW, review.decision)
    }

    @Test
    fun `unsupported grounded quantity is rejected`() {
        val review = guardrail.review(
            query = "Boleh beri dosis obat ini?",
            response = "Gunakan 750 mg sesuai kebutuhan.",
            evidenceText = "[ISO Indonesia] Monograf hanya mencantumkan kekuatan 500 mg.",
            citationCount = 1,
            language = AppLanguage.INDONESIAN
        )

        assertEquals(MedicalAnswerDecision.INVALID_OUTPUT, review.decision)
    }

    @Test
    fun `explicit compounding protocol can ground a factual answer`() {
        val review = guardrail.review(
            query = "Ringkas formula racikan dari protokol",
            response = "Protokol racikan mencantumkan 500 mg sebagai komponen.",
            evidenceText = "[PPK-FKTP] Protokol racikan terverifikasi: komponen 500 mg.",
            citationCount = 1,
            language = AppLanguage.INDONESIAN
        )

        assertEquals(MedicalAnswerDecision.ALLOW, review.decision)
    }

    @Test
    fun `medication answer must expose the clinical response contract`() {
        assertTrue(
            guardrail.hasRequiredClinicalStructure(
                    "Triase awal: MEDIUM.\n\n" +
                    "Pertanyaan probing: usia dan tanda dehidrasi.\n\n" +
                    "Diagnosis banding: penyebab infeksi atau non-infeksi.\n\n" +
                    "Penatalaksanaan/obat: hanya fakta dari monograf.\n\n" +
                    "Tanda bahaya dan kapan mencari pertolongan."
            )
        )
    }

    @Test
    fun `diarrhoea treatment source is medication evidence even without a monograph heading`() {
        val review = guardrail.review(
            query = "Bagaimana penanganan dan obat diare?",
            response = "Triase awal: perlu menilai dehidrasi.\n\n" +
                "Penatalaksanaan: rehidrasi oralit; antibiotik tidak rutin.\n\n" +
                "Tanda bahaya: darah dalam tinja atau tidak dapat minum.",
            evidenceText = "[KAPITA SELEKTA] Diare — Penatalaksanaan: rehidrasi oralit dan terapi zink; antibiotik tidak rutin.",
            citationCount = 1,
            language = AppLanguage.INDONESIAN
        )

        assertEquals(MedicalAnswerDecision.ALLOW, review.decision)
    }

    @Test
    fun `secondary web education is not prescribing evidence`() {
        val evidence = "[Halodoc — EDUCATION ONLY] secondary_web_education; oralit untuk mencegah dehidrasi."

        assertEquals(false, guardrail.hasClinicalManagementEvidence(evidence))
        val review = guardrail.review(
            query = "Apa obat diare yang harus saya minum?",
            response = "Triase dan pertanyaan probing diperlukan.",
            evidenceText = evidence,
            citationCount = 1,
            language = AppLanguage.INDONESIAN
        )
        assertEquals(MedicalAnswerDecision.INSUFFICIENT_EVIDENCE, review.decision)
    }

    @Test
    fun `competency index is not clinical management evidence`() {
        val evidence = "[SKDI 2012 / 4A — INDEX KOMPETENSI SAJA] Diare; classification_only."

        assertEquals(false, guardrail.hasClinicalManagementEvidence(evidence))
    }
}
