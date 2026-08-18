package com.medbot.app

import com.medbot.app.domain.tools.CalculatorInputValidation
import com.medbot.app.domain.tools.ClinicalCalculatorInputValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalCalculatorInputValidatorTest {

    @Test
    fun `paediatric calculator rejects empty clinical fields`() {
        val result = ClinicalCalculatorInputValidator.paediatric(
            ageMonths = "",
            weightKg = "",
            heightCm = "",
            gender = "",
            drugName = "",
            indication = ""
        )

        assertTrue(result is CalculatorInputValidation.Invalid)
    }

    @Test
    fun `paediatric calculator preserves explicitly entered values`() {
        val result = ClinicalCalculatorInputValidator.paediatric(
            ageMonths = "24",
            weightKg = "12.5",
            heightCm = "86",
            gender = "female",
            drugName = "paracetamol",
            indication = " demam "
        )

        assertTrue(result is CalculatorInputValidation.Valid)
        val input = (result as CalculatorInputValidation.Valid).value
        assertEquals(24, input.ageMonths)
        assertEquals(12.5, input.weightKg, 0.0)
        assertEquals("demam", input.indication)
    }

    @Test
    fun `due date calculator rejects missing date instead of choosing a date`() {
        val result = ClinicalCalculatorInputValidator.dueDate("", "", "")

        assertTrue(result is CalculatorInputValidation.Invalid)
    }
}
