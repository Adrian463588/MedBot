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

class BmiCalculatorTool : LocalMedicalTool {
    override val name: String = "calculate_bmi"
    override val description: String = "Menghitung Indeks Massa Tubuh (BMI) dan klasifikasi berat badan WHO Asia-Pasifik"

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val weightKg = (params["weight_kg"] as? Number)?.toDouble() ?: 65.0
        val heightCm = (params["height_cm"] as? Number)?.toDouble() ?: 170.0
        val heightM = heightCm / 100.0

        val bmi = if (heightM > 0) weightKg / (heightM * heightM) else 0.0
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
        val day = (params["day"] as? Number)?.toInt() ?: 1
        val month = (params["month"] as? Number)?.toInt() ?: 1
        val year = (params["year"] as? Number)?.toInt() ?: 2026

        // Naegele's rule: +7 days, -3 months, +1 year (or +7 days, +9 months)
        val calendar = java.util.Calendar.getInstance().apply {
            set(year, month - 1, day)
            add(java.util.Calendar.DAY_OF_MONTH, 7)
            add(java.util.Calendar.MONTH, 9)
        }

        val dueDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val dueMonth = calendar.get(java.util.Calendar.MONTH) + 1
        val dueYear = calendar.get(java.util.Calendar.YEAR)

        val monthNames = listOf("", "Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
        val hplFormatted = "$dueDay ${monthNames.getOrElse(dueMonth) { "" }} $dueYear"
        val summary = "Taksiran Persalinan (HPL / Naegele): $hplFormatted (Berdasarkan HPHT $day/${month}/$year). Lakukan kontrol kehamilan rutin (ANC) minimal 6 kali."

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
        val testName = (params["test_name"] as? String)?.lowercase() ?: "hemoglobin"
        val value = (params["value"] as? Number)?.toDouble() ?: 14.0

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

        val summary = "Evaluasi Lab: $testName = $value (Status: $status, Rujukan: $normalRange). $interpretation"

        return ToolResult(
            toolName = name,
            isSuccess = true,
            summary = summary,
            data = mapOf(
                "testName" to testName,
                "value" to value,
                "status" to status,
            )
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


