package com.medbot.app

import com.medbot.app.domain.clinical.ClinicalRetrievalPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalRetrievalPlannerTest {
    private val planner = ClinicalRetrievalPlanner()

    @Test
    fun `keeps the original wording and adds medication evidence sections`() {
        val queries = planner.queries("Saya diare, obat apa?", medicationRequest = true)

        assertEquals("Saya diare, obat apa?", queries.first())
        assertTrue(queries[1].contains("indikasi"))
        assertTrue(queries[1].contains("monograf"))
        assertTrue(queries[1].contains("penatalaksanaan"))
    }

    @Test
    fun `adds triage sections without inventing a medicine`() {
        val query = planner.queries("Demam dan batuk", medicationRequest = false).last()

        assertTrue(query.contains("anamnesis"))
        assertTrue(query.contains("diagnosis banding"))
        assertTrue(query.contains("tanda bahaya"))
        assertTrue(query.contains("penatalaksanaan"))
        assertTrue(query.contains("obat").not())
    }

    @Test
    fun `blank input produces no retrieval work`() {
        assertTrue(planner.queries("   ", medicationRequest = true).isEmpty())
    }
}
