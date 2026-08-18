package com.medbot.app

import com.medbot.app.domain.agents.AgentRegistry
import org.junit.Assert.*
import org.junit.Test

class AgentRegistryTest {

    @Test
    fun `AgentRegistry contains exactly 46 medical specialists plus orchestrator`() {
        // Orchestrator + 45 specialist agents = 46 agents total in ALL_AGENTS
        assertEquals(46, AgentRegistry.ALL_AGENTS.size)
    }

    @Test
    fun `Every agent in registry has non-empty metadata and prompts`() {
        for (agent in AgentRegistry.ALL_AGENTS) {
            assertTrue("Agent id should not be blank", agent.id.isNotBlank())
            assertTrue("Agent displayNameId should not be blank for ${agent.id}", agent.displayNameId.isNotBlank())
            assertTrue("Agent displayNameEn should not be blank for ${agent.id}", agent.displayNameEn.isNotBlank())
            assertTrue("Agent specialtyId should not be blank for ${agent.id}", agent.specialtyId.isNotBlank())
            assertTrue("Agent specialtyEn should not be blank for ${agent.id}", agent.specialtyEn.isNotBlank())
            assertTrue("Agent systemPromptId should not be blank for ${agent.id}", agent.systemPromptId.isNotBlank())
            assertTrue("Agent systemPromptEn should not be blank for ${agent.id}", agent.systemPromptEn.isNotBlank())
            assertTrue("Agent iconName should not be blank for ${agent.id}", agent.iconName.isNotBlank())
        }
    }

    @Test
    fun `getAgentById retrieves correct specialist agent`() {
        val dermatologist = AgentRegistry.getAgentById("dermatology")
        assertEquals("dermatology", dermatologist.id)
        assertEquals("Spesialis Kulit & Kelamin", dermatologist.displayNameId)
        assertTrue(dermatologist.supportsImage)

        val paediatrician = AgentRegistry.getAgentById("paediatrics")
        assertEquals("paediatrics", paediatrician.id)
        assertEquals("Dokter Spesialis Anak", paediatrician.displayNameId)

        val nonExistentFallback = AgentRegistry.getAgentById("unknown_specialist_123")
        assertEquals("orchestrator", nonExistentFallback.id)
    }
}
