package com.medbot.app

import com.medbot.app.domain.clinical.ClinicalEvidenceSelector
import com.medbot.app.domain.clinical.ClinicalResponsePlanner
import com.medbot.app.domain.model.AppLanguage
import com.medbot.app.domain.model.DocChunk
import com.medbot.app.domain.model.OrchestratorResult
import com.medbot.app.domain.model.SearchResult
import com.medbot.app.domain.model.UrgencyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalResponsePlannerTest {
    private val triage = OrchestratorResult(
        primarySpecialist = "gastroenterology",
        urgency = UrgencyLevel.MEDIUM,
        reasoning = "diare requires symptom triage"
    )

    @Test
    fun `diarrhoea plan asks for age hydration and bleeding before medication advice`() {
        val plan = ClinicalResponsePlanner().plan(
            query = "saya sakit diare bagaimana penanganan dan obatnya",
            triage = triage,
            evidenceText = "Penatalaksanaan: rehidrasi; antibiotik tidak rutin.",
            language = AppLanguage.INDONESIAN
        )

        assertEquals("diarrhoea", plan.topic)
        assertTrue(plan.probingQuestions.any { it.contains("usia", ignoreCase = true) })
        assertTrue(plan.probingQuestions.any { it.contains("darah", ignoreCase = true) })
        assertTrue(plan.probingQuestions.any { it.contains("minum", ignoreCase = true) })
        assertTrue(plan.toPromptBlock().contains("Diagnosis banding", ignoreCase = true))
    }

    @Test
    fun `selector prefers an exact clinical title over an unrelated symptom mention`() {
        val exact = result(
            id = "diare",
            title = "Diare",
            text = "**Penatalaksanaan:** rehidrasi dan evaluasi **tanda bahaya**."
        )
        val unrelated = result(
            id = "other",
            title = "Morbili",
            text = "Diare dapat muncul sebagai gejala penyerta; **diagnosis** memerlukan pemeriksaan."
        )

        val selected = ClinicalEvidenceSelector.select(
            query = "obat diare",
            results = listOf(unrelated, exact),
            maxResults = 4
        )

        assertEquals("Diare", selected.first().documentTitle)
        assertTrue(ClinicalEvidenceSelector.hasRelevantEvidence("obat diare", selected))
    }

    @Test
    fun `selector rejects unrelated catalogue text`() {
        val catalogue = result(
            id = "catalogue",
            title = "Produk Farmasi",
            text = "Nama produk tablet, bentuk sediaan, dan kekuatan produk."
        )

        assertFalse(ClinicalEvidenceSelector.hasRelevantEvidence("obat diare", listOf(catalogue)))
    }

    private fun result(id: String, title: String, text: String) = SearchResult(
        chunk = DocChunk(
            id = id,
            docId = "doc",
            chunkIndex = 0,
            textContent = text,
            pageNumber = 0,
            sectionTitle = title,
            embedding = FloatArray(0)
        ),
        similarityScore = 0.8f,
        documentTitle = title
    )
}
