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
    fun `ZScoreCalculatorTool fails closed without an official reference dataset`() = runBlocking {
        val tool = ZScoreCalculatorTool()
        val result = tool.execute(
            mapOf(
                "age_months" to 24,
                "weight_kg" to 12.0,
                "height_cm" to 85.0,
                "gender" to "male"
            )
        )
        assertFalse(result.isSuccess)
        assertEquals(ToolResultStatus.UNAVAILABLE, result.status)
    }

    @Test
    fun `PaediatricDosingTool fails closed without a verified monograph`() = runBlocking {
        val tool = PaediatricDosingTool()
        val result = tool.execute(
            mapOf(
                "drug_name" to "paracetamol",
                "weight_kg" to 10.0,
                "indication" to "demam"
            )
        )
        assertFalse(result.isSuccess)
        assertEquals(ToolResultStatus.UNAVAILABLE, result.status)
    }

    @Test
    fun `PaediatricDosingTool does not invent an antibiotic dose`() = runBlocking {
        val tool = PaediatricDosingTool()
        val result = tool.execute(
            mapOf(
                "drug_name" to "amoxicillin",
                "weight_kg" to 12.0,
                "indication" to "infeksi bakteri"
            )
        )
        assertFalse(result.isSuccess)
        assertEquals(ToolResultStatus.UNAVAILABLE, result.status)
    }

    @Test
    fun `SkinAbcdEvaluatorTool fails closed without a validated clinical protocol`() = runBlocking {
        val tool = SkinAbcdEvaluatorTool()
        val result = tool.execute(
            mapOf(
                "asymmetry" to true,
                "border_irregular" to true,
                "color_variegated" to true,
                "diameter_mm" to 7.5
            )
        )
        assertFalse(result.isSuccess)
        assertEquals(ToolResultStatus.UNAVAILABLE, result.status)
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
                "value" to 8.5,
                "unit" to "g/dL",
                "reference_low" to 12.0,
                "reference_high" to 17.5,
                "reference_source" to "Rentang pada laporan laboratorium"
            )
        )
        assertTrue(result.isSuccess)
        assertEquals("BELOW_REFERENCE", result.data["status"])
    }

    @Test
    fun `LabInterpreterTool evaluates elevated blood glucose`() = runBlocking {
        val tool = LabInterpreterTool()
        val result = tool.execute(
            mapOf(
                "test_name" to "gds",
                "value" to 250.0,
                "unit" to "mg/dL",
                "reference_low" to 70.0,
                "reference_high" to 140.0,
                "reference_source" to "Rentang pada laporan laboratorium"
            )
        )
        assertTrue(result.isSuccess)
        assertEquals("ABOVE_REFERENCE", result.data["status"])
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
            "get_paediatric_dosing" to mapOf(
                "drug_name" to "paracetamol",
                "weight_kg" to 12.0,
                "indication" to "demam"
            ),
            "evaluate_skin_abcd" to mapOf(
                "asymmetry" to false,
                "border_irregular" to false,
                "color_variegated" to false,
                "diameter_mm" to 4.0
            ),
            "calculate_bmi" to mapOf("weight_kg" to 65.0, "height_cm" to 170.0),
            "calculate_due_date" to mapOf("day" to 10, "month" to 1, "year" to 2026),
            "interpret_lab_result" to mapOf(
                "test_name" to "hemoglobin",
                "value" to 14.0,
                "unit" to "g/dL",
                "reference_low" to 12.0,
                "reference_high" to 17.5,
                "reference_source" to "Rentang pada laporan laboratorium"
            )
        )

        for ((name, params) in validInputs) {
            val tool = ToolRegistry.getTool(name)
            assertNotNull("Tool $name must be registered", tool)
            val res = ToolRegistry.executeTool(name, params)
            if (name in setOf("calculate_zscore", "get_paediatric_dosing", "evaluate_skin_abcd")) {
                assertEquals("$name must report unavailable", ToolResultStatus.UNAVAILABLE, res.status)
            } else {
                assertTrue("Execution of $name should succeed", res.isSuccess)
            }
        }
    }
}
