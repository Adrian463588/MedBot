package com.medbot.app.domain.agents

import com.medbot.app.domain.agents.tools.ToolRegistry
import com.medbot.app.domain.model.OrchestratorResult
import com.medbot.app.domain.model.UrgencyLevel

class TriageOrchestrator {

    suspend fun triage(query: String, hasImage: Boolean = false, imageType: String? = null): OrchestratorResult {
        val q = query.lowercase()

        // Red flags take precedence over modality routing. Image metadata must
        // never downgrade an emergency text query.
        val initialUrgency = ToolRegistry.executeTool("assess_urgency", mapOf("query" to query))
        if (initialUrgency.data["urgencyLevel"] == UrgencyLevel.EMERGENCY.name) {
            return OrchestratorResult(
                primarySpecialist = "emergency_medicine",
                secondarySpecialists = listOf("general_practice", "cardiology"),
                confidence = 0.0f,
                urgency = UrgencyLevel.EMERGENCY,
                reasoning = "RED_FLAG_MATCH: aturan tanda bahaya mendahului perutean citra."
            )
        }

        // 1. Image based direct routing
        if (hasImage || imageType != null) {
            val route = when (imageType) {
                "skin_lesion", "skin", "rash", "mole", "wound" -> "dermatology"
                "xray", "ct_scan", "mri", "ultrasound" -> "radiology"
                "lab_report", "blood_test", "urine_test" -> "clinical_pathology"
                "prescription", "pill", "medicine" -> "pharmacy"
                "eye", "fundoscopy" -> "ophthalmology"
                else -> {
                    if (q.contains("kulit") || q.contains("ruam") || q.contains("gatal") || q.contains("tahi lalat") || q.contains("skin")) {
                        "dermatology"
                    } else if (q.contains("rontgen") || q.contains("xray") || q.contains("tulang")) {
                        "radiology"
                    } else if (q.contains("obat") || q.contains("resep") || q.contains("dosis")) {
                        "pharmacy"
                    } else {
                        "general_practice"
                    }
                }
            }
            return OrchestratorResult(
                primarySpecialist = route,
                secondarySpecialists = if (route == "dermatology") listOf("pharmacy") else listOf("general_practice"),
                confidence = 0.0f,
                urgency = UrgencyLevel.INSUFFICIENT_DATA,
                reasoning = "INSUFFICIENT_DATA: citra hanya dapat dirutekan; model vision lokal belum memberikan hasil."
            )
        }

        // 2. Emergency check
        val urgencyToolResult = ToolRegistry.executeTool("assess_urgency", mapOf("query" to query))
        val detectedUrgency = when (urgencyToolResult.data["urgencyLevel"]) {
            "EMERGENCY" -> UrgencyLevel.EMERGENCY
            "HIGH" -> UrgencyLevel.HIGH
            "MEDIUM" -> UrgencyLevel.MEDIUM
            else -> UrgencyLevel.INSUFFICIENT_DATA
        }

        if (detectedUrgency == UrgencyLevel.EMERGENCY) {
            return OrchestratorResult(
                primarySpecialist = "emergency_medicine",
                secondarySpecialists = listOf("general_practice", "cardiology"),
                confidence = 0.0f,
                urgency = UrgencyLevel.EMERGENCY,
                reasoning = "Tanda bahaya kegawatdaruratan terdeteksi. Diprioritaskan ke Dokter Gawat Darurat."
            )
        }

        // 3. Keyword-based Specialty Triage Rules
        val (primary, secondary) = when {
            // Pediatrics
            q.contains("anak") || q.contains("bayi") || q.contains("balita") || q.contains("imunisasi") || q.contains("stunting") || q.contains("asi") ->
                Pair("paediatrics", listOf("preventive_medicine", "nutrition_dietetics"))

            // Dermatology
            q.contains("kulit") || q.contains("ruam") || q.contains("gatal") || q.contains("panu") || q.contains("kudis") || q.contains("tahi lalat") || q.contains("jerawat") || q.contains("eksim") || q.contains("psoriasis") ->
                Pair("dermatology", listOf("pharmacy", "allergy_immunology"))

            // Cardiology
            q.contains("jantung") || q.contains("dada nyeri") || q.contains("berdebar") || q.contains("palpitasi") || q.contains("koroner") || q.contains("tensi tinggi") ->
                Pair("cardiology", listOf("internal_medicine", "lifestyle_medicine"))

            // Pulmonology
            q.contains("batuk") || q.contains("sesak") || q.contains("asma") || q.contains("paru") || q.contains("tbc") || q.contains("tb") || q.contains("ppok") || q.contains("mengi") ->
                Pair("pulmonology", listOf("internal_medicine", "infectious_disease"))

            // Gastroenterology
            q.contains("lambung") || q.contains("maag") || q.contains("gerd") || q.contains("mual") || q.contains("muntah") || q.contains("diare") || q.contains("perut") || q.contains("hati") || q.contains("hepatitis") ->
                Pair("gastroenterology", listOf("internal_medicine", "nutrition_dietetics"))

            // Nephrology
            q.contains("ginjal") || q.contains("kencing") || q.contains("batu ginjal") || q.contains("cuci darah") || q.contains("kreatinin") || q.contains("ureum") ->
                Pair("nephrology", listOf("urology", "internal_medicine"))

            // Endocrinology
            q.contains("diabetes") || q.contains("gula darah") || q.contains("tiroid") || q.contains("hormon") || q.contains("hba1c") ->
                Pair("endocrinology", listOf("internal_medicine", "nutrition_dietetics"))

            // Neurology
            q.contains("stroke") || q.contains("vertigo") || q.contains("migrain") || q.contains("pusing") || q.contains("kebas") || q.contains("kesemutan") || q.contains("saraf") ->
                Pair("neurology", listOf("internal_medicine", "rehabilitation"))

            // Orthopaedics
            q.contains("tulang") || q.contains("patah") || q.contains("retak") || q.contains("sendi") || q.contains("keseleo") || q.contains("otot") || q.contains("pinggang") ->
                Pair("orthopaedics", listOf("sports_medicine", "rehabilitation"))

            // Ophthalmology
            q.contains("mata") || q.contains("kabur") || q.contains("merah") || q.contains("belekan") || q.contains("glaukoma") || q.contains("katarak") ->
                Pair("ophthalmology", listOf("general_practice"))

            // Pharmacy
            q.contains("obat") || q.contains("dosis") || q.contains("resep") || q.contains("efek samping") || q.contains("interaksi obat") || q.contains("generik") ->
                Pair("pharmacy", listOf("general_practice"))

            // Obgyn & Pregnancy
            q.contains("hamil") || q.contains("haid") || q.contains("menstruasi") || q.contains("kandungan") || q.contains("keputihan") || q.contains("janin") ->
                Pair("obstetrics_gynecology", listOf("fertility", "paediatrics"))

            // Dentistry
            q.contains("gigi") || q.contains("gusi") || q.contains("karies") || q.contains("sariawan") || q.contains("gigi bungsu") ->
                Pair("dentistry", listOf("general_practice"))

            // ENT (THT)
            q.contains("telinga") || q.contains("hidung") || q.contains("tenggorokan") || q.contains("amandel") || q.contains("sinus") ->
                Pair("otorhinolaryngology", listOf("general_practice", "allergy_immunology"))

            // Infectious Disease
            q.contains("demam") || q.contains("dbd") || q.contains("malaria") || q.contains("tifoid") || q.contains("infeksi") ->
                Pair("infectious_disease", listOf("internal_medicine", "general_practice"))

            // Mental Health / Psychiatry
            q.contains("cemas") || q.contains("anxiety") || q.contains("depresi") || q.contains("stres") || q.contains("panik") || q.contains("mental") ->
                Pair("psychiatry", listOf("lifestyle_medicine"))

            // Sleep Medicine
            q.contains("tidur") || q.contains("insomnia") || q.contains("ngorok") || q.contains("dengkur") || q.contains("apnea") ->
                Pair("sleep_medicine", listOf("lifestyle_medicine", "neurology"))

            // Nutrition
            q.contains("diet") || q.contains("makanan") || q.contains("gizi") || q.contains("kalori") || q.contains("berat badan") || q.contains("obesitas") ->
                Pair("nutrition_dietetics", listOf("lifestyle_medicine", "endocrinology"))

            else -> Pair("general_practice", listOf("internal_medicine", "preventive_medicine"))
        }

        return OrchestratorResult(
            primarySpecialist = primary,
            secondarySpecialists = secondary,
            confidence = 0.0f,
            urgency = detectedUrgency,
            reasoning = "Analisis keluhan mencerminkan ranah spesialisasi $primary dengan dukungan spesialis ${secondary.joinToString(", ")}."
        )
    }
}
