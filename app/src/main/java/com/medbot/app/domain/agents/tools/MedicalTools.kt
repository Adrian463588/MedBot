package com.medbot.app.domain.agents.tools

import com.medbot.app.domain.model.UrgencyLevel
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

class UrgencyAssessorTool : LocalMedicalTool {
    override val name: String = "assess_urgency"
    override val description: String = "Menghitung tingkat urgensi klinis dan tanda bahaya (red flags)"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val symptoms = (params["symptoms"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val textQuery = (params["query"] as? String)?.lowercase() ?: symptoms.joinToString(" ").lowercase()

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
        val ageMonths = (params["age_months"] as? Number)?.toInt() ?: 24
        val weightKg = (params["weight_kg"] as? Number)?.toDouble() ?: 12.0
        val heightCm = (params["height_cm"] as? Number)?.toDouble() ?: 85.0
        val gender = (params["gender"] as? String)?.lowercase() ?: "male"

        // Median WHO standard approximations
        val medianWeight = when {
            gender.startsWith("f") -> 3.2 + (ageMonths * 0.4)
            else -> 3.3 + (ageMonths * 0.43)
        }
        val sdWeight = medianWeight * 0.12
        val zWeightForAge = (weightKg - medianWeight) / sdWeight

        val medianHeight = when {
            gender.startsWith("f") -> 49.0 + (ageMonths * 1.5)
            else -> 50.0 + (ageMonths * 1.55)
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
        val drugName = (params["drug_name"] as? String)?.lowercase() ?: "paracetamol"
        val weightKg = (params["weight_kg"] as? Number)?.toDouble() ?: 10.0

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
            else -> {
                "Dosis empiris umum untuk $drugName pada anak BB ${weightKg} kg: 10 mg/kgBB. Konsultasikan sediaan persisnya dengan apoteker."
            }
        }

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = dosingInfo,
            data = mapOf("drug" to drugName, "weightKg" to weightKg, "dosingText" to dosingInfo)
        )
    }
}

class SkinAbcdEvaluatorTool : LocalMedicalTool {
    override val name: String = "evaluate_skin_abcd"
    override val description: String = "Menilai risiko keganasan lesi kulit berdasarkan kriteria ABCD"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val asymmetry = (params["asymmetry"] as? Boolean) ?: false
        val borderIrregular = (params["border_irregular"] as? Boolean) ?: false
        val colorVariegated = (params["color_variegated"] as? Boolean) ?: false
        val diameterMm = (params["diameter_mm"] as? Number)?.toDouble() ?: 4.0

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
