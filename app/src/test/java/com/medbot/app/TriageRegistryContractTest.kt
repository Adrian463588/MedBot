package com.medbot.app

import com.medbot.app.domain.agents.AgentRegistry
import com.medbot.app.domain.agents.TriageOrchestrator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TriageRegistryContractTest {

    @Test
    fun `registry ids are unique and unknown ids fail over to orchestrator`() {
        val ids = AgentRegistry.ALL_AGENTS.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals("orchestrator", AgentRegistry.getAgentById("missing-agent").id)
    }

    @Test
    fun `triage results reference declared agents and bounded confidence`() = runBlocking {
        val queries = listOf(
            "anak demam",
            "ruam kulit gatal",
            "obat dan dosis",
            "batuk berdahak",
            "keluhan umum"
        )
        val orchestrator = TriageOrchestrator()

        queries.forEach { query ->
            val result = orchestrator.triage(query)
            assertEquals(result.primarySpecialist, AgentRegistry.getAgentById(result.primarySpecialist).id)
            assertTrue(result.secondarySpecialists.all { AgentRegistry.getAgentById(it).id == it })
            assertTrue(result.confidence in 0f..1f)
        }
    }

    @Test
    fun `unknown image modality fails over to general practice instead of inventing a specialist`() = runBlocking {
        val result = TriageOrchestrator().triage(
            query = "Keluhan tidak spesifik",
            hasImage = true,
            imageType = "unclassified-image"
        )

        assertEquals("general_practice", result.primarySpecialist)
        assertEquals(0.92f, result.confidence, 0f)
    }
}
