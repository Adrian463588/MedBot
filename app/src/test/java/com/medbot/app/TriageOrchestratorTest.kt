package com.medbot.app

import com.medbot.app.domain.agents.TriageOrchestrator
import com.medbot.app.domain.model.UrgencyLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TriageOrchestratorTest {

    private lateinit var orchestrator: TriageOrchestrator

    @Before
    fun setUp() {
        orchestrator = TriageOrchestrator()
    }

    @Test
    fun `triage routes severe chest pain to emergency medicine with emergency urgency`() = runBlocking {
        val result = orchestrator.triage("Pasien tiba-tiba mengeluh nyeri dada kiri menjalar ke rahang dan sesak napas berat")
        assertEquals("emergency_medicine", result.primarySpecialist)
        assertEquals(UrgencyLevel.EMERGENCY, result.urgency)
    }

    @Test
    fun `triage routes child symptoms to paediatrics`() = runBlocking {
        val result = orchestrator.triage("Anak saya usia 2 tahun demam 38.5 dan batuk sejak kemarin malam")
        assertEquals("paediatrics", result.primarySpecialist)
    }

    @Test
    fun `triage routes skin lesions with image to dermatology`() = runBlocking {
        val result = orchestrator.triage(
            query = "Ada ruam merah gatal di lengan",
            hasImage = true,
            imageType = "skin_lesion"
        )
        assertEquals("dermatology", result.primarySpecialist)
    }

    @Test
    fun `triage routes drug dosage questions to pharmacy`() = runBlocking {
        val result = orchestrator.triage("Berapa dosis paracetamol dan aturan minum obat amoksisilin?")
        assertEquals("pharmacy", result.primarySpecialist)
    }
}
