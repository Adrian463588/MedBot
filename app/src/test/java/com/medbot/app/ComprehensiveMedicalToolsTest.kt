package com.medbot.app

import com.medbot.app.domain.agents.tools.*
import com.medbot.app.domain.model.UrgencyLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ComprehensiveMedicalToolsTest {

    @Test
    fun `UrgencyAssessorTool correctly flags emergency keywords`() = runBlocking {
        val tool = UrgencyAssessorTool()
        val result = tool.execute(mapOf("query" to "Pasien mengalami nyeri dada hebat dan penurunan kesadaran"))
        assertTrue(result.isSuccess)
        assertEquals("EMERGENCY", result.data["urgencyLevel"])
    }

    @Test
    fun `UrgencyAssessorTool correctly flags high urgency`() = runBlocking {
        val tool = UrgencyAssessorTool()
        val result = tool.execute(mapOf("query" to "Demam tinggi 39.5 derajat disertai muntah terus"))
        assertTrue(result.isSuccess)
        assertEquals("HIGH", result.data["urgencyLevel"])
    }

    @Test
    fun `ZScoreCalculatorTool calculates accurate WHO z-scores`() = runBlocking {
        val tool = ZScoreCalculatorTool()
        val result = tool.execute(
            mapOf(
                "age_months" to 24,
                "weight_kg" to 12.0,
                "height_cm" to 85.0,
                "gender" to "male"
            )
        )
        assertTrue(result.isSuccess)
        assertNotNull(result.data["zWeightForAge"])
        assertNotNull(result.data["statusNutrition"])
        assertTrue(result.summary.contains("Status Gizi"))
    }

    @Test
    fun `PaediatricDosingTool calculates correct Paracetamol dosage`() = runBlocking {
        val tool = PaediatricDosingTool()
        val result = tool.execute(
            mapOf(
                "drug_name" to "paracetamol",
                "weight_kg" to 10.0
            )
        )
        assertTrue(result.isSuccess)
        assertTrue(result.summary.contains("100–150 mg"))
        assertTrue(result.summary.contains("Sirup 120mg/5ml"))
    }

    @Test
    fun `PaediatricDosingTool calculates correct Amoxicillin dosage`() = runBlocking {
        val tool = PaediatricDosingTool()
        val result = tool.execute(
            mapOf(
                "drug_name" to "amoxicillin",
                "weight_kg" to 12.0
            )
        )
        assertTrue(result.isSuccess)
        assertTrue(result.summary.contains("Amoxicillin"))
        assertTrue(result.summary.contains("200 mg"))
    }

    @Test
    fun `SkinAbcdEvaluatorTool classifies high risk when multiple criteria met`() = runBlocking {
        val tool = SkinAbcdEvaluatorTool()
        val result = tool.execute(
            mapOf(
                "asymmetry" to true,
                "border_irregular" to true,
                "color_variegated" to true,
                "diameter_mm" to 7.5
            )
        )
        assertTrue(result.isSuccess)
        val score = (result.data["totalRiskScore"] as Number).toDouble()
        assertTrue(score >= 7.5)
        assertTrue(result.summary.contains("Risiko Tinggi"))
    }

    @Test
    fun `BmiCalculatorTool calculates BMI and categories accurately`() = runBlocking {
        val tool = BmiCalculatorTool()
        val result = tool.execute(
            mapOf(
                "weight_kg" to 65.0,
                "height_cm" to 170.0
            )
        )
        assertTrue(result.isSuccess)
        val bmi = (result.data["bmi"] as Number).toDouble()
        assertEquals(22.49, bmi, 0.1)
        assertEquals("Berat Badan Normal (Ideal)", result.data["category"])
    }

    @Test
    fun `DueDateCalculatorTool calculates Naegele HPL date accurately`() = runBlocking {
        val tool = DueDateCalculatorTool()
        val result = tool.execute(
            mapOf(
                "day" to 10,
                "month" to 1,
                "year" to 2026
            )
        )
        assertTrue(result.isSuccess)
        assertEquals(17, result.data["dueDay"])
        assertEquals(10, result.data["dueMonth"])
        assertEquals(2026, result.data["dueYear"])
        assertTrue(result.summary.contains("17 Oktober 2026"))
    }

    @Test
    fun `LabInterpreterTool evaluates critical hemoglobin`() = runBlocking {
        val tool = LabInterpreterTool()
        val result = tool.execute(
            mapOf(
                "test_name" to "hemoglobin",
                "value" to 8.5
            )
        )
        assertTrue(result.isSuccess)
        assertEquals("Rendah (Anemia)", result.data["status"])
    }

    @Test
    fun `LabInterpreterTool evaluates elevated blood glucose`() = runBlocking {
        val tool = LabInterpreterTool()
        val result = tool.execute(
            mapOf(
                "test_name" to "gds",
                "value" to 250.0
            )
        )
        assertTrue(result.isSuccess)
        assertEquals("Tinggi (Hiperglikemia)", result.data["status"])
    }

    @Test
    fun `ToolRegistry executes all registered deterministic tools successfully`() = runBlocking {
        val validInputs = mapOf(
            "assess_urgency" to mapOf("query" to "demam sejak kemarin"),
            "calculate_zscore" to mapOf(
                "age_months" to 24,
                "weight_kg" to 12.0,
                "height_cm" to 85.0,
                "gender" to "male"
            ),
            "get_paediatric_dosing" to mapOf("drug_name" to "paracetamol", "weight_kg" to 12.0),
            "evaluate_skin_abcd" to mapOf(
                "asymmetry" to false,
                "border_irregular" to false,
                "color_variegated" to false,
                "diameter_mm" to 4.0
            ),
            "calculate_bmi" to mapOf("weight_kg" to 65.0, "height_cm" to 170.0),
            "calculate_due_date" to mapOf("day" to 10, "month" to 1, "year" to 2026),
            "interpret_lab_result" to mapOf("test_name" to "hemoglobin", "value" to 14.0)
        )

        for ((name, params) in validInputs) {
            val tool = ToolRegistry.getTool(name)
            assertNotNull("Tool $name must be registered", tool)
            val res = ToolRegistry.executeTool(name, params)
            assertTrue("Execution of $name should succeed", res.isSuccess)
        }
    }
}
