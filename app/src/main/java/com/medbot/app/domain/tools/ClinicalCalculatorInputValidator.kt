package com.medbot.app.domain.tools

/**
 * Raw user input accepted by the paediatric calculator.
 * The values stay as text until this boundary so empty or malformed fields
 * cannot be replaced with an invented clinical value.
 */
data class PaediatricCalculatorInput(
    val ageMonths: Int,
    val weightKg: Double,
    val heightCm: Double,
    val gender: String,
    val drugName: String,
    val indication: String
)

/** Raw user input accepted by the adult BMI calculator. */
data class BmiCalculatorInput(
    val weightKg: Double,
    val heightCm: Double
)

/** Raw user input accepted by the pregnancy due-date calculator. */
data class DueDateCalculatorInput(
    val day: Int,
    val month: Int,
    val year: Int
)

sealed interface CalculatorInputValidation<out T> {
    data class Valid<T>(val value: T) : CalculatorInputValidation<T>
    data class Invalid(val message: String) : CalculatorInputValidation<Nothing>
}

/**
 * Validates calculator input without applying clinical defaults.
 * This object is pure Kotlin so the same contract can be tested independently
 * of Compose, Android, and the ViewModel lifecycle.
 */
object ClinicalCalculatorInputValidator {
    fun paediatric(
        ageMonths: String,
        weightKg: String,
        heightCm: String,
        gender: String,
        drugName: String,
        indication: String
    ): CalculatorInputValidation<PaediatricCalculatorInput> {
        val age = ageMonths.toIntOrNull()
            ?: return CalculatorInputValidation.Invalid("Usia anak wajib berupa bilangan bulan.")
        if (age !in 0..228) {
            return CalculatorInputValidation.Invalid("Usia anak harus berada pada rentang 0–228 bulan.")
        }
        val weight = weightKg.toDoubleOrNull()
            ?: return CalculatorInputValidation.Invalid("Berat anak wajib berupa angka.")
        if (!weight.isFinite() || weight <= 0.0 || weight > 200.0) {
            return CalculatorInputValidation.Invalid("Berat anak harus lebih besar dari 0 dan paling besar 200 kg.")
        }
        val height = heightCm.toDoubleOrNull()
            ?: return CalculatorInputValidation.Invalid("Tinggi anak wajib berupa angka.")
        if (!height.isFinite() || height <= 0.0 || height > 250.0) {
            return CalculatorInputValidation.Invalid("Tinggi anak harus lebih besar dari 0 dan paling besar 250 cm.")
        }
        val normalizedGender = gender.trim().lowercase()
        if (normalizedGender !in setOf("male", "female")) {
            return CalculatorInputValidation.Invalid("Jenis kelamin wajib dipilih.")
        }
        if (drugName.isBlank()) {
            return CalculatorInputValidation.Invalid("Obat wajib dipilih.")
        }
        if (indication.isBlank()) {
            return CalculatorInputValidation.Invalid("Indikasi atau keluhan wajib diisi.")
        }
        return CalculatorInputValidation.Valid(
            PaediatricCalculatorInput(
                ageMonths = age,
                weightKg = weight,
                heightCm = height,
                gender = normalizedGender,
                drugName = drugName.trim(),
                indication = indication.trim()
            )
        )
    }

    fun bmi(weightKg: String, heightCm: String): CalculatorInputValidation<BmiCalculatorInput> {
        val weight = weightKg.toDoubleOrNull()
            ?: return CalculatorInputValidation.Invalid("Berat wajib berupa angka.")
        if (!weight.isFinite() || weight <= 0.0 || weight > 1000.0) {
            return CalculatorInputValidation.Invalid("Berat harus lebih besar dari 0 dan paling besar 1000 kg.")
        }
        val height = heightCm.toDoubleOrNull()
            ?: return CalculatorInputValidation.Invalid("Tinggi wajib berupa angka.")
        if (!height.isFinite() || height <= 0.0 || height > 300.0) {
            return CalculatorInputValidation.Invalid("Tinggi harus lebih besar dari 0 dan paling besar 300 cm.")
        }
        return CalculatorInputValidation.Valid(BmiCalculatorInput(weight, height))
    }

    fun dueDate(day: String, month: String, year: String): CalculatorInputValidation<DueDateCalculatorInput> {
        val parsedDay = day.toIntOrNull()
            ?: return CalculatorInputValidation.Invalid("Tanggal HPHT wajib diisi sebagai bilangan bulat.")
        val parsedMonth = month.toIntOrNull()
            ?: return CalculatorInputValidation.Invalid("Bulan HPHT wajib diisi sebagai bilangan bulat.")
        val parsedYear = year.toIntOrNull()
            ?: return CalculatorInputValidation.Invalid("Tahun HPHT wajib diisi sebagai bilangan bulat.")
        if (parsedDay !in 1..31 || parsedMonth !in 1..12 || parsedYear !in 1..9999) {
            return CalculatorInputValidation.Invalid("Tanggal HPHT tidak berada pada rentang kalender yang valid.")
        }
        return CalculatorInputValidation.Valid(DueDateCalculatorInput(parsedDay, parsedMonth, parsedYear))
    }
}
