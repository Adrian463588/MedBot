package com.medbot.app.domain.model

import java.util.UUID

data class AbcdEvaluation(
    val asymmetryScore: Float,       // 0.0 to 1.0
    val borderScore: Float,          // 0.0 to 1.0 (irregularity)
    val colorScore: Float,           // 0.0 to 1.0 (variegation)
    val diameterMm: Float,           // Estimated diameter in mm
    val totalRiskScore: Float,       // Calculated cumulative risk 0.0 to 10.0
    val riskClassification: String,  // Low Risk, Moderate Risk, High Risk
    val asymmetryDescription: String,
    val borderDescription: String,
    val colorDescription: String,
    val diameterDescription: String
)

data class SkinRecord(
    val id: String = UUID.randomUUID().toString(),
    val bodyPart: String,            // Wajah, Leher, Lengan Kiri, Punggung, dll.
    val imagePath: String,           // Local file path
    val abcdEvaluation: AbcdEvaluation,
    val differentialDiagnoses: List<String>,
    val urgencyLevel: UrgencyLevel,
    val clinicalSummary: String,
    val homeCareAdvice: List<String>,
    val userNotes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class BodyPartCategory(
    val id: String,
    val displayNameId: String,
    val displayNameEn: String,
    val iconName: String
)
