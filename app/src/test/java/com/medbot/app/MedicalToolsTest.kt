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
    fun `ZScoreCalculator correctly identifies normal and stunting states`() = runBlocking {
        val tool = ZScoreCalculatorTool()
        // 24 months, 12kg, 85cm
        val res = tool.execute(mapOf("age_months" to 24, "weight_kg" to 12.0, "height_cm" to 85.0, "gender" to "male"))
        assertTrue(res.isSuccess)
        assertEquals("Gizi Baik (Normal)", res.data["statusNutrition"])
        assertEquals("Tinggi Normal", res.data["statusStunting"])
    }

    @Test
    fun `PaediatricDosing calculates paracetamol syrup volume based on weight`() = runBlocking {
        val tool = PaediatricDosingTool()
        // 12 kg child -> 120 - 180 mg PCT (5.0 - 7.5 mL of 120mg/5mL syrup)
        val res = tool.execute(
            mapOf(
                "drug_name" to "paracetamol",
                "weight_kg" to 12.0,
                "indication" to "demam"
            )
        )
        assertTrue(res.isSuccess)
        assertTrue(res.summary.contains("Paracetamol"))
        assertTrue(res.summary.contains("5.0") || res.summary.contains("7.5"))
    }

    @Test
    fun `SkinAbcdEvaluator calculates high risk for large irregular pigmented lesion`() = runBlocking {
        val tool = SkinAbcdEvaluatorTool()
        val res = tool.execute(mapOf(
            "asymmetry" to true,
            "border_irregular" to true,
            "color_variegated" to true,
            "diameter_mm" to 7.5
        ))
        assertTrue(res.isSuccess)
        val riskScore = res.data["totalRiskScore"] as Double
        assertTrue(riskScore >= 7.5)
        assertTrue(res.data["classification"].toString().contains("Tinggi"))
    }
}
