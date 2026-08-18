package com.medbot.app.domain.agents.tools

import com.medbot.app.domain.model.UrgencyLevel

private sealed interface InputValue<out T> {
    data class Valid<T>(val value: T) : InputValue<T>
    data class Missing(val key: String) : InputValue<Nothing>
    data class Invalid(val key: String, val expectation: String) : InputValue<Nothing>
}

private fun Map<String, Any>.requiredText(key: String): InputValue<String> {
    val rawValue = this[key] ?: return InputValue.Missing(key)
    val value = rawValue as? String
        ?: return InputValue.Invalid(key, "harus berupa teks")
    return if (value.isBlank()) {
        InputValue.Invalid(key, "tidak boleh kosong")
    } else {
        InputValue.Valid(value.trim())
    }
}

private fun Map<String, Any>.requiredFiniteNumber(
    key: String,
    isValid: (Double) -> Boolean,
    expectation: String
): InputValue<Double> {
    val rawValue = this[key] ?: return InputValue.Missing(key)
    val value = (rawValue as? Number)?.toDouble()
        ?: return InputValue.Invalid(key, "harus berupa angka $expectation")
    return if (value.isFinite() && isValid(value)) {
        InputValue.Valid(value)
    } else {
        InputValue.Invalid(key, "harus berupa angka $expectation")
    }
}

private fun Map<String, Any>.requiredInteger(
    key: String,
    range: IntRange,
    expectation: String
): InputValue<Int> {
    val rawValue = this[key] ?: return InputValue.Missing(key)
    val numericValue = (rawValue as? Number)?.toDouble()
        ?: return InputValue.Invalid(key, "harus berupa bilangan bulat $expectation")
    if (!numericValue.isFinite() || numericValue % 1.0 != 0.0) {
        return InputValue.Invalid(key, "harus berupa bilangan bulat $expectation")
    }
    val value = numericValue.toInt()
    return if (value.toDouble() == numericValue && value in range) {
        InputValue.Valid(value)
    } else {
        InputValue.Invalid(key, "harus berada pada rentang $expectation")
    }
}

private fun Map<String, Any>.requiredBoolean(key: String): InputValue<Boolean> {
    val rawValue = this[key] ?: return InputValue.Missing(key)
    return if (rawValue is Boolean) {
        InputValue.Valid(rawValue)
    } else {
        InputValue.Invalid(key, "harus berupa boolean")
    }
}

private fun InputValue<*>.failure(toolName: String): ToolResult {
    return when (this) {
        is InputValue.Missing -> insufficientData(toolName, "Parameter '${this.key}' wajib diisi.")
        is InputValue.Invalid -> validationError(toolName, "Parameter '${this.key}' ${this.expectation}.")
        is InputValue.Valid<*> -> error("Valid input cannot produce a failure")
    }
}

private fun insufficientData(toolName: String, message: String): ToolResult {
    return ToolResult(
        toolName = toolName,
        isSuccess = false,
        summary = "INSUFFICIENT_DATA: $message",
        data = mapOf("status" to ToolResultStatus.INSUFFICIENT_DATA.name),
        errorMessage = message,
        status = ToolResultStatus.INSUFFICIENT_DATA
    )
}

private fun validationError(toolName: String, message: String): ToolResult {
    return ToolResult(
        toolName = toolName,
        isSuccess = false,
        summary = "VALIDATION_ERROR: $message",
        data = mapOf("status" to ToolResultStatus.VALIDATION_ERROR.name),
        errorMessage = message,
        status = ToolResultStatus.VALIDATION_ERROR
    )
}

private fun unavailable(toolName: String, message: String): ToolResult {
    return ToolResult(
        toolName = toolName,
        isSuccess = false,
        summary = "UNAVAILABLE: $message",
        data = mapOf("status" to ToolResultStatus.UNAVAILABLE.name),
        errorMessage = message,
        status = ToolResultStatus.UNAVAILABLE
    )
}

class UrgencyAssessorTool : LocalMedicalTool {
    override val name: String = "assess_urgency"
    override val description: String = "Menghitung tingkat urgensi klinis dan tanda bahaya (red flags)"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val queryValue = params["query"]?.let {
            (it as? String)?.trim()
                ?: return validationError(name, "Parameter 'query' harus berupa teks.")
        }.orEmpty()

        val symptoms = if (params.containsKey("symptoms")) {
            val rawSymptoms = params["symptoms"] as? List<*>
                ?: return validationError(name, "Parameter 'symptoms' harus berupa daftar teks.")
            val parsedSymptoms = mutableListOf<String>()
            for (rawSymptom in rawSymptoms) {
                val symptom = rawSymptom as? String
                    ?: return validationError(name, "Setiap symptom harus berupa teks.")
                symptom.trim().takeIf { it.isNotEmpty() }?.let(parsedSymptoms::add)
            }
            parsedSymptoms
        } else {
            emptyList()
        }

        val textQuery = (listOf(queryValue) + symptoms).filter { it.isNotBlank() }
            .joinToString(" ")
            .lowercase()
        if (textQuery.isBlank()) {
            return insufficientData(name, "Parameter 'query' atau 'symptoms' wajib berisi keluhan.")
        }

        val emergencyKeywords = listOf(
            "nyeri dada", "chest pain", "sesak napas berat", "tidak bisa bernapas",
            "kejang", "convulsion", "penurunan kesadaran", "tidak sadar", "stroke",
            "bibir miring", "lumpuh separuh", "muntah darah", "perdarahan hebat",
            "anafilaksis", "tersedak", "overdosis", "koma"
        )

        val highKeywords = listOf(
            "demam tinggi", "fever > 39", "muntah terus", "dehidrasi berat",
            "patah tulang", "fraktur", "luka bakar luas", "mata kena bahan kimia",
            "dengue", "dbd", "trombosit turun", "nyeri perut hebat", "appendicitis"
        )

        val mediumKeywords = listOf(
            "demam", "batuk berdahak", "diare", "mual", "pusing berputar",
            "vertigo", "gatal berair", "ruam merah", "sakit telinga", "nyeri kemih"
        )

        val hasEmergency = emergencyKeywords.any { textQuery.contains(it) }
        val hasHigh = highKeywords.any { textQuery.contains(it) }
        val hasMedium = mediumKeywords.any { textQuery.contains(it) }

        if (!hasEmergency && !hasHigh && !hasMedium) {
            return insufficientData(name, "Tidak ada tanda bahaya atau pola gejala yang cukup untuk menentukan urgensi.")
        }

        val level = when {
            hasEmergency -> UrgencyLevel.EMERGENCY
            hasHigh -> UrgencyLevel.HIGH
            hasMedium -> UrgencyLevel.MEDIUM
            else -> error("Insufficient data is returned before urgency is built")
        }

        val advice = when (level) {
            UrgencyLevel.EMERGENCY -> "KONDISI KRITIS: Segera ke IGD rumah sakit terdekat atau hubungi nomor darurat 112 / 119."
            UrgencyLevel.HIGH -> "PERLU PERHATIAN MEDIS SEGERA: Kunjungi puskesmas atau klinik dalam waktu < 24 jam."
            UrgencyLevel.MEDIUM -> "PERLU KONTROL: Lakukan observasi di rumah dan konsultasikan ke dokter jika tidak membaik dalam 2 hari."
            UrgencyLevel.LOW -> "RAWAT MANDIRI: Dapat ditangani dengan perawatan mandiri dan istirahat cukup."
            UrgencyLevel.INSUFFICIENT_DATA -> error("Insufficient data is returned before advice is built")
        }

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = "[URGENSI: ${level.labelId}] $advice",
            data = mapOf(
                "urgencyLevel" to level.name,
                "urgencyColor" to level.hexColor,
                "actionAdvice" to advice
            )
        )
    }
}

class ZScoreCalculatorTool : LocalMedicalTool {
    override val name: String = "calculate_zscore"
    override val description: String = "Menghitung Z-Score WHO untuk status gizi balita/anak"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val ageInput = params.requiredInteger("age_months", 0..228, "0 sampai 228 bulan")
        if (ageInput !is InputValue.Valid) return ageInput.failure(name)
        val ageMonths = ageInput.value

        val weightInput = params.requiredFiniteNumber(
            "weight_kg",
            { it > 0.0 && it <= 200.0 },
            "lebih besar dari 0 dan paling besar 200 kg"
        )
        if (weightInput !is InputValue.Valid) return weightInput.failure(name)
        val weightKg = weightInput.value

        val heightInput = params.requiredFiniteNumber(
            "height_cm",
            { it > 0.0 && it <= 250.0 },
            "lebih besar dari 0 dan paling besar 250 cm"
        )
        if (heightInput !is InputValue.Valid) return heightInput.failure(name)
        val heightCm = heightInput.value

        val genderInput = params.requiredText("gender")
        if (genderInput !is InputValue.Valid) return genderInput.failure(name)
        when (genderInput.value.lowercase()) {
            "f", "female", "perempuan", "wanita",
            "m", "male", "laki-laki", "laki laki", "pria" -> Unit
            else -> return validationError(name, "Parameter 'gender' harus bernilai male/female.")
        }

        return unavailable(
            name,
            "Dataset referensi pertumbuhan anak yang tervalidasi belum tersedia di perangkat; Z-score tidak dihitung dari aproksimasi."
        )
    }
}

class PaediatricDosingTool : LocalMedicalTool {
    override val name: String = "get_paediatric_dosing"
    override val description: String = "Menghitung dosis sirup/obat aman untuk anak per berat badan (kgBB)"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val drugInput = params.requiredText("drug_name")
        if (drugInput !is InputValue.Valid) return drugInput.failure(name)
        val drugName = drugInput.value

        val weightInput = params.requiredFiniteNumber(
            "weight_kg",
            { it > 0.0 && it <= 200.0 },
            "lebih besar dari 0 dan paling besar 200 kg"
        )
        if (weightInput !is InputValue.Valid) return weightInput.failure(name)
        val weightKg = weightInput.value

        val indicationInput = params.requiredText("indication")
        if (indicationInput !is InputValue.Valid) return indicationInput.failure(name)
        return unavailable(
            name,
            "Monograf obat, konsentrasi sediaan, usia, kontraindikasi, alergi, fungsi ginjal, dan rencana klinis tervalidasi wajib tersedia sebelum dosis dapat dihitung."
        )
    }
}

class BmiCalculatorTool : LocalMedicalTool {
    override val name: String = "calculate_bmi"
    override val description: String = "Menghitung Indeks Massa Tubuh (BMI) dan klasifikasi berat badan WHO Asia-Pasifik"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val weightInput = params.requiredFiniteNumber(
            "weight_kg",
            { it > 0.0 && it <= 1000.0 },
            "lebih besar dari 0 dan paling besar 1000 kg"
        )
        if (weightInput !is InputValue.Valid) return weightInput.failure(name)
        val weightKg = weightInput.value

        val heightInput = params.requiredFiniteNumber(
            "height_cm",
            { it > 0.0 && it <= 300.0 },
            "lebih besar dari 0 dan paling besar 300 cm"
        )
        if (heightInput !is InputValue.Valid) return heightInput.failure(name)
        val heightCm = heightInput.value
        val heightM = heightCm / 100.0

        val bmi = weightKg / (heightM * heightM)
        val (category, advice) = when {
            bmi < 18.5 -> "Berat Badan Kurang (Underweight)" to "Tingkatkan asupan kalori bergizi seimbang dan konsultasikan pola makan padat nutrisi."
            bmi in 18.5..22.9 -> "Berat Badan Normal (Ideal)" to "Pertahankan pola makan seimbang dan aktivitas fisik minimal 150 menit per minggu."
            bmi in 23.0..24.9 -> "Kelebihan Berat Badan (Overweight)" to "Kurangi konsumsi gula/lemak jenuh dan tingkatkan olahraga aerobik rutin."
            bmi in 25.0..29.9 -> "Obesitas Tingkat I" to "Batasi kalori harian, targetkan penurunan BB bertahap 0.5-1 kg/minggu, dan periksa profil lipid/gula darah."
            else -> "Obesitas Tingkat II (Berat)" to "Sangat dianjurkan berkonsultasi dengan dokter/ahli gizi untuk program penurunan berat badan terstruktur."
        }

        val idealMinWeight = 18.5 * (heightM * heightM)
        val idealMaxWeight = 22.9 * (heightM * heightM)
        val summary = "BMI: ${"%.1f".format(bmi)} kg/m² ($category). Rentang BB Ideal: ${"%.1f".format(idealMinWeight)} - ${"%.1f".format(idealMaxWeight)} kg. $advice"

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = summary,
            data = mapOf(
                "bmi" to bmi,
                "category" to category,
                "idealMinWeight" to idealMinWeight,
                "idealMaxWeight" to idealMaxWeight,
                "advice" to advice
            )
        )
    }
}

class DueDateCalculatorTool : LocalMedicalTool {
    override val name: String = "calculate_due_date"
    override val description: String = "Menghitung Taksiran Persalinan / Hari Perkiraan Lahir (HPL) dengan Rumus Naegele"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val dayInput = params.requiredInteger("day", 1..31, "1 sampai 31")
        if (dayInput !is InputValue.Valid) return dayInput.failure(name)
        val day = dayInput.value

        val monthInput = params.requiredInteger("month", 1..12, "1 sampai 12")
        if (monthInput !is InputValue.Valid) return monthInput.failure(name)
        val month = monthInput.value

        val yearInput = params.requiredInteger("year", 1..9999, "1 sampai 9999")
        if (yearInput !is InputValue.Valid) return yearInput.failure(name)
        val year = yearInput.value

        val calendar = java.util.Calendar.getInstance().apply {
            clear()
            isLenient = false
            set(year, month - 1, day)
        }
        try {
            calendar.timeInMillis
        } catch (_: IllegalArgumentException) {
            return validationError(name, "Parameter tanggal HPHT bukan tanggal kalender yang valid.")
        }

        // Naegele's rule: +7 days and +9 calendar months.
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)
        calendar.add(java.util.Calendar.MONTH, 9)

        val dueDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val dueMonth = calendar.get(java.util.Calendar.MONTH) + 1
        val dueYear = calendar.get(java.util.Calendar.YEAR)

        val monthNames = listOf("", "Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
        val hplFormatted = "$dueDay ${monthNames[dueMonth]} $dueYear"
        val summary = "Taksiran Persalinan (HPL / Naegele): $hplFormatted (berdasarkan HPHT $day/$month/$year). Hasil ini adalah perhitungan kalender, bukan penilaian kehamilan."

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = summary,
            data = mapOf(
                "hplDate" to hplFormatted,
                "dueDay" to dueDay,
                "dueMonth" to dueMonth,
                "dueYear" to dueYear
            )
        )
    }
}

class LabInterpreterTool : LocalMedicalTool {
    override val name: String = "interpret_lab_result"
    override val description: String = "Mengevaluasi nilai laboratorium darah, gula darah, dan fungsi organ"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val testNameInput = params.requiredText("test_name")
        if (testNameInput !is InputValue.Valid) return testNameInput.failure(name)
        val testName = testNameInput.value.lowercase()

        val valueInput = params.requiredFiniteNumber(
            "value",
            { it >= 0.0 },
            "tidak negatif"
        )
        if (valueInput !is InputValue.Valid) return valueInput.failure(name)
        val value = valueInput.value

        val unitInput = params.requiredText("unit")
        if (unitInput !is InputValue.Valid) return unitInput.failure(name)
        val unit = unitInput.value
        val lowInput = params.requiredFiniteNumber("reference_low", { it >= 0.0 }, "tidak negatif")
        if (lowInput !is InputValue.Valid) return lowInput.failure(name)
        val highInput = params.requiredFiniteNumber("reference_high", { it >= lowInput.value }, "lebih besar atau sama dengan batas bawah")
        if (highInput !is InputValue.Valid) return highInput.failure(name)
        val sourceInput = params.requiredText("reference_source")
        if (sourceInput !is InputValue.Valid) return sourceInput.failure(name)

        val status = when {
            value < lowInput.value -> "BELOW_REFERENCE"
            value > highInput.value -> "ABOVE_REFERENCE"
            else -> "WITHIN_REFERENCE"
        }
        val summary = "Hasil $testName: $value $unit dibandingkan dengan rentang ${lowInput.value}–${highInput.value} $unit dari ${sourceInput.value}. Status: $status. Ini bukan diagnosis."

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = summary,
            data = mapOf(
                "testName" to testName,
                "value" to value,
                "unit" to unit,
                "status" to status,
                "referenceLow" to lowInput.value,
                "referenceHigh" to highInput.value,
                "referenceSource" to sourceInput.value
            )
        )
    }
}

class SkinAbcdEvaluatorTool : LocalMedicalTool {
    override val name: String = "evaluate_skin_abcd"
    override val description: String = "Menilai risiko keganasan lesi kulit berdasarkan kriteria ABCD"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val asymmetryInput = params.requiredBoolean("asymmetry")
        if (asymmetryInput !is InputValue.Valid) return asymmetryInput.failure(name)
        val asymmetry = asymmetryInput.value

        val borderInput = params.requiredBoolean("border_irregular")
        if (borderInput !is InputValue.Valid) return borderInput.failure(name)
        val borderIrregular = borderInput.value

        val colorInput = params.requiredBoolean("color_variegated")
        if (colorInput !is InputValue.Valid) return colorInput.failure(name)
        val colorVariegated = colorInput.value

        val diameterInput = params.requiredFiniteNumber(
            "diameter_mm",
            { it > 0.0 && it <= 1000.0 },
            "lebih besar dari 0 dan paling besar 1000 mm"
        )
        if (diameterInput !is InputValue.Valid) return diameterInput.failure(name)
        val diameterMm = diameterInput.value

        return unavailable(
            name,
            "Protokol penilaian lesi tervalidasi dan pemeriksaan klinis langsung belum tersedia; input ABCD tidak diterjemahkan menjadi skor atau label jinak/kanker."
        )
    }
}
