package com.medbot.app

import com.medbot.app.domain.agents.tools.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicalToolsTest {

    @Test
    fun `UrgencyAssessor detects emergency red flags`() = runBlocking {
        val tool = UrgencyAssessorTool()
        val res = tool.execute(mapOf("query" to "Pasien penurunan kesadaran dan kejang berulang"))
        assertTrue(res.isSuccess)
        assertEquals("EMERGENCY", res.data["urgencyLevel"])
    }

    @Test
    fun `ZScoreCalculator is unavailable without an official reference dataset`() = runBlocking {
        val tool = ZScoreCalculatorTool()
        // 24 months, 12kg, 85cm
        val res = tool.execute(mapOf("age_months" to 24, "weight_kg" to 12.0, "height_cm" to 85.0, "gender" to "male"))
        assertEquals(ToolResultStatus.UNAVAILABLE, res.status)
    }

    @Test
    fun `PaediatricDosing is unavailable without a verified monograph`() = runBlocking {
        val tool = PaediatricDosingTool()
        // 12 kg child -> 120 - 180 mg PCT (5.0 - 7.5 mL of 120mg/5mL syrup)
        val res = tool.execute(
            mapOf(
                "drug_name" to "paracetamol",
                "weight_kg" to 12.0,
                "indication" to "demam"
            )
        )
        assertEquals(ToolResultStatus.UNAVAILABLE, res.status)
    }

    @Test
    fun `SkinAbcdEvaluator is unavailable without a validated protocol`() = runBlocking {
        val tool = SkinAbcdEvaluatorTool()
        val res = tool.execute(mapOf(
            "asymmetry" to true,
            "border_irregular" to true,
            "color_variegated" to true,
            "diameter_mm" to 7.5
        ))
        assertEquals(ToolResultStatus.UNAVAILABLE, res.status)
    }
}
