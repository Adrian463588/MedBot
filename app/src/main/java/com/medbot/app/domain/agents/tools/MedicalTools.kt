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

        val level = when {
            hasEmergency -> UrgencyLevel.EMERGENCY
            hasHigh -> UrgencyLevel.HIGH
            hasMedium -> UrgencyLevel.MEDIUM
            else -> UrgencyLevel.LOW
        }

        val advice = when (level) {
            UrgencyLevel.EMERGENCY -> "KONDISI KRITIS: Segera ke IGD rumah sakit terdekat atau hubungi nomor darurat 112 / 119."
            UrgencyLevel.HIGH -> "PERLU PERHATIAN MEDIS SEGERA: Kunjungi puskesmas atau klinik dalam waktu < 24 jam."
            UrgencyLevel.MEDIUM -> "PERLU KONTROL: Lakukan observasi di rumah dan konsultasikan ke dokter jika tidak membaik dalam 2 hari."
            UrgencyLevel.LOW -> "RAWAT MANDIRI: Dapat ditangani dengan perawatan mandiri dan istirahat cukup."
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
        val isFemale = when (genderInput.value.lowercase()) {
            "f", "female", "perempuan", "wanita" -> true
            "m", "male", "laki-laki", "laki laki", "pria" -> false
            else -> return validationError(name, "Parameter 'gender' harus bernilai male/female.")
        }

        // Median WHO standard approximations
        val medianWeight = if (isFemale) {
            3.2 + (ageMonths * 0.4)
        } else {
            3.3 + (ageMonths * 0.43)
        }
        val sdWeight = medianWeight * 0.12
        val zWeightForAge = (weightKg - medianWeight) / sdWeight

        val medianHeight = if (isFemale) {
            49.0 + (ageMonths * 1.5)
        } else {
            50.0 + (ageMonths * 1.55)
        }
        val sdHeight = medianHeight * 0.045
        val zHeightForAge = (heightCm - medianHeight) / sdHeight

        val statusNutrition = when {
            zWeightForAge < -3.0 -> "Gizi Buruk (Severely Underweight)"
            zWeightForAge < -2.0 -> "Gizi Kurang (Underweight)"
            zWeightForAge > 2.0 -> "Risiko Gizi Lebih / Obesitas"
            else -> "Gizi Baik (Normal)"
        }

        val statusStunting = when {
            zHeightForAge < -3.0 -> "Sangat Pendek (Severely Stunted)"
            zHeightForAge < -2.0 -> "Pendek (Stunted)"
            else -> "Tinggi Normal"
        }

        val summary = "Status Gizi: $statusNutrition (Z-BB/U: ${"%.2f".format(zWeightForAge)}), Status Pertumbuhan: $statusStunting (Z-TB/U: ${"%.2f".format(zHeightForAge)})"

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = summary,
            data = mapOf(
                "zWeightForAge" to zWeightForAge,
                "zHeightForAge" to zHeightForAge,
                "statusNutrition" to statusNutrition,
                "statusStunting" to statusStunting
            )
        )
    }
}

class PaediatricDosingTool : LocalMedicalTool {
    override val name: String = "get_paediatric_dosing"
    override val description: String = "Menghitung dosis sirup/obat aman untuk anak per berat badan (kgBB)"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val drugInput = params.requiredText("drug_name")
        if (drugInput !is InputValue.Valid) return drugInput.failure(name)
        val drugName = drugInput.value.lowercase()

        val weightInput = params.requiredFiniteNumber(
            "weight_kg",
            { it > 0.0 && it <= 200.0 },
            "lebih besar dari 0 dan paling besar 200 kg"
        )
        if (weightInput !is InputValue.Valid) return weightInput.failure(name)
        val weightKg = weightInput.value

        val indicationInput = params.requiredText("indication")
        if (indicationInput !is InputValue.Valid) return indicationInput.failure(name)
        val indication = indicationInput.value

        val dosingInfo = when {
            drugName.contains("paracetamol") || drugName.contains("pct") -> {
                val mgMin = weightKg * 10.0
                val mgMax = weightKg * 15.0
                val syrup120Per5mlMin = (mgMin / 120.0) * 5.0
                val syrup120Per5mlMax = (mgMax / 120.0) * 5.0
                "Paracetamol: ${"%.0f".format(mgMin)}–${"%.0f".format(mgMax)} mg/kali minum (diberikan tiap 4-6 jam bila demam >38°C, maks 4x sehari). Sirup 120mg/5ml: ${"%.1f".format(syrup120Per5mlMin)}–${"%.1f".format(syrup120Per5mlMax)} ml/kali."
            }
            drugName.contains("ibuprofen") -> {
                val mgMin = weightKg * 5.0
                val mgMax = weightKg * 10.0
                val syrup100Per5ml = (mgMax / 100.0) * 5.0
                "Ibuprofen: ${"%.0f".format(mgMin)}–${"%.0f".format(mgMax)} mg/kali minum (tiap 6-8 jam sesudah makan). Sirup 100mg/5ml: ${"%.1f".format(syrup100Per5ml)} ml/kali."
            }
            drugName.contains("amoxicillin") || drugName.contains("amoksisilin") -> {
                val dailyMg = weightKg * 50.0 // 50 mg/kgBB/hari dibagi 3 dosis
                val perDoseMg = dailyMg / 3.0
                val syrup125Per5ml = (perDoseMg / 125.0) * 5.0
                "Amoxicillin: ${"%.0f".format(perDoseMg)} mg per kali (3 kali sehari tiap 8 jam). Sirup 125mg/5ml: ${"%.1f".format(syrup125Per5ml)} ml/kali (Wajib habiskan sesuai anjuran dokter)."
            }
            drugName.contains("cetirizine") || drugName.contains("setirizin") -> {
                val dose = if (weightKg < 10.0) "2.5 mg (2.5 ml sirup 5mg/5ml) 1x sehari" else "5 mg (5 ml sirup 5mg/5ml) 1x sehari"
                "Cetirizine Sirup 5mg/5ml: $dose untuk mengatasi alergi/gatal."
            }
            else -> return validationError(name, "Obat '$drugName' belum didukung oleh kalkulator deterministik.")
        }

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = dosingInfo,
            data = mapOf(
                "drug" to drugName,
                "weightKg" to weightKg,
                "indication" to indication,
                "dosingText" to dosingInfo
            )
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

        // Naegele's rule: +7 days, -3 months, +1 year (or +7 days, +9 months)
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 7)
        calendar.add(java.util.Calendar.MONTH, 9)

        val dueDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val dueMonth = calendar.get(java.util.Calendar.MONTH) + 1
        val dueYear = calendar.get(java.util.Calendar.YEAR)

        val monthNames = listOf("", "Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
        val hplFormatted = "$dueDay ${monthNames[dueMonth]} $dueYear"
        val summary = "Taksiran Persalinan (HPL / Naegele): $hplFormatted (Berdasarkan HPHT $day/$month/$year). Lakukan kontrol kehamilan rutin (ANC) minimal 6 kali."

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
        if (!isCompatibleLabUnit(testName, unit)) {
            return validationError(name, "Unit '$unit' tidak sesuai dengan pemeriksaan '$testName'.")
        }

        val (status, interpretation, normalRange) = when {
            testName.contains("hemoglobin") || testName.contains("hb") -> {
                when {
                    value < 12.0 -> Triple("Rendah (Anemia)", "Kadar hemoglobin di bawah batas normal, mengindikasikan anemia (kekurangan zat besi atau kehilangan darah).", "12.0 - 17.5 g/dL")
                    value > 17.5 -> Triple("Tinggi (Polisitemia)", "Kadar hemoglobin tinggi, dapat terkait hemokonsentrasi/dehidrasi atau merokok.", "12.0 - 17.5 g/dL")
                    else -> Triple("Normal", "Kadar hemoglobin dalam batas normal yang sehat.", "12.0 - 17.5 g/dL")
                }
            }
            testName.contains("trombosit") || testName.contains("platelet") -> {
                when {
                    value < 150000.0 -> Triple("Rendah (Trombositopenia)", "Waspadai tanda demam berdarah dengue (DBD) atau gangguan pembekuan darah. Amati tanda perdarahan.", "150.000 - 450.000 /µL")
                    value > 450000.0 -> Triple("Tinggi (Trombositosis)", "Peningkatan sel pembeku darah akibat respon inflamasi atau infeksi.", "150.000 - 450.000 /µL")
                    else -> Triple("Normal", "Jumlah keping darah dalam rentang normal.", "150.000 - 450.000 /µL")
                }
            }
            testName.contains("leukosit") || testName.contains("wbc") -> {
                when {
                    value < 4000.0 -> Triple("Rendah (Leukopenia)", "Kadar sel darah putih rendah; waspadai supresi imun atau infeksi virus fase awal.", "4.000 - 10.000 /µL")
                    value > 10000.0 -> Triple("Tinggi (Leukositosis)", "Tanda umum adanya infeksi bakteri aktif, radang akut, atau stres fisik tubuh.", "4.000 - 10.000 /µL")
                    else -> Triple("Normal", "Jumlah leukosit normal.", "4.000 - 10.000 /µL")
                }
            }
            testName.contains("gds") || testName.contains("gula") || testName.contains("glucose") -> {
                when {
                    value < 70.0 -> Triple("Rendah (Hipoglikemia)", "Gula darah terlalu rendah! Segera konsumsi 1-2 sendok gula atau teh manis hangat.", "70 - 140 mg/dL")
                    value > 200.0 -> Triple("Tinggi (Hiperglikemia)", "Gula darah sewaktu sangat tinggi, mengindikasikan diabetes yang belum terkontrol.", "70 - 140 mg/dL")
                    value in 140.0..199.0 -> Triple("Perhatian (Toleransi Glukosa Terganggu)", "Nilai di atas normal sewaktu, disarankan tes GDP dan HbA1c konfirmasi.", "70 - 140 mg/dL")
                    else -> Triple("Normal", "Kadar gula darah sewaktu normal.", "70 - 140 mg/dL")
                }
            }
            testName.contains("asam urat") || testName.contains("uric") -> {
                when {
                    value > 7.0 -> Triple("Tinggi (Hiperurisemia)", "Asam urat tinggi dapat memicu radang sendi gout akut dan pembentukan kristal ginjal. Batasi jeroan dan emping.", "3.5 - 7.0 mg/dL")
                    else -> Triple("Normal", "Kadar asam urat dalam batas aman.", "3.5 - 7.0 mg/dL")
                }
            }
            else -> Triple("Tercatat", "Hasil tes $testName terukur $value. Konsultasikan rentang rujukan spesifik lab Anda dengan dokter.", "Sesuai rujukan lab")
        }

        val summary = "Evaluasi Lab: $testName = $value $unit (Status: $status, Rujukan: $normalRange). $interpretation"

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = summary,
            data = mapOf(
                "testName" to testName,
                "value" to value,
                "unit" to unit,
                "status" to status
            )
        )
    }

    private fun isCompatibleLabUnit(testName: String, unit: String): Boolean {
        val normalizedUnit = unit.lowercase()
            .replace("μ", "u")
            .replace("µ", "u")
            .replace(" ", "")
        return when {
            testName.contains("hemoglobin") || testName.contains("hb") -> normalizedUnit == "g/dl"
            testName.contains("trombosit") || testName.contains("platelet") ||
                testName.contains("leukosit") || testName.contains("wbc") ->
                normalizedUnit in setOf("/ul", "cells/ul", "cell/ul", "sel/ul")
            testName.contains("gds") || testName.contains("gula") || testName.contains("glucose") ||
                testName.contains("asam urat") || testName.contains("uric") -> normalizedUnit == "mg/dl"
            else -> true
        }
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

        var riskScore = 0.0
        if (asymmetry) riskScore += 2.5
        if (borderIrregular) riskScore += 2.5
        if (colorVariegated) riskScore += 2.5
        if (diameterMm >= 6.0) riskScore += 2.5

        val classification = when {
            riskScore >= 7.5 -> "Risiko Tinggi (Perlu Biopsi/Pemeriksaan Dermatologi Segera)"
            riskScore >= 5.0 -> "Risiko Sedang (Pantau Perubahan Ukuran & Warna)"
            else -> "Risiko Rendah / Lesi Jinak (Tetap Amati Linimasa)"
        }

        val summary = "Skor Risiko ABCD: ${"%.1f".format(riskScore)}/10.0 ($classification). Asimetri: $asymmetry, Batas ireguler: $borderIrregular, Warna bervariasi: $colorVariegated, Diameter: ${diameterMm} mm."

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = summary,
            data = mapOf(
                "totalRiskScore" to riskScore,
                "classification" to classification,
                "diameterMm" to diameterMm
            )
        )
    }
}
