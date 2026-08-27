package com.medbot.app.domain.model

import java.util.UUID

data class Drug(
    val name: String,
    val genericName: String,
    val category: String,
    val indication: String = "",
    val adultDose: String = "",
    val childDose: String = "",
    val contraindications: String = "",
    val sideEffects: String = "",
    val isOtc: Boolean = true,
    val affordableAlternatives: List<String> = emptyList(),
    val dosageForm: String = "",
    val strength: String = ""
)

data class DrugInteraction(
    val drugA: String,
    val drugB: String,
    val severity: InteractionSeverity,
    val description: String,
    val recommendation: String
)

/**
 * Describes which medication evidence is actually available locally.
 * A product catalogue is intentionally separate from clinical evidence.
 */
data class MedicationKnowledgeStatus(
    val productCatalogAvailable: Boolean,
    val productCatalogCount: Int,
    val monographAvailable: Boolean,
    val interactionDatasetAvailable: Boolean,
    val compoundingProtocolAvailable: Boolean,
    val provenance: String
)

enum class InteractionSeverity(val label: String, val hexColor: String) {
    MINOR("Minor (Ringan)", "#27AE60"),
    MODERATE("Moderat (Sedang)", "#F39C12"),
    MAJOR("Mayor (Bahaya)", "#E74C3C")
}

data class LabTest(
    val testName: String,
    val category: String,
    val unit: String,
    val normalLow: Double,
    val normalHigh: Double,
    val interpretationLow: String,
    val interpretationHigh: String,
    val clinicalSignificance: String
)

data class SkinRemedy(
    val conditionKeywords: String,
    val naturalRemedy: String,
    val otcCream: String,
    val referralFlag: Boolean
)

data class HealthMetric(
    val id: String = UUID.randomUUID().toString(),
    val type: MetricType,
    val valuePrimary: Float,
    val valueSecondary: Float? = null,
    val unit: String,
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class MetricType(val displayName: String, val defaultUnit: String) {
    BLOOD_PRESSURE("Tekanan Darah", "mmHg"),
    BLOOD_SUGAR("Gula Darah Sewaktu", "mg/dL"),
    HEART_RATE("Denyut Jantung", "bpm"),
    WEIGHT("Berat Badan", "kg"),
    TEMPERATURE("Suhu Tubuh", "°C")
}

enum class ReminderNotificationMode(val label: String, val description: String) {
    SOUND_AND_VIBRATE("Suara & Getar", "Dering suara dan getaran"),
    VIBRATE_ONLY("Hanya Getar", "Getaran tanpa suara dering"),
    SILENT("Tanpa Suara", "Notifikasi visual tanpa dering/getar")
}

data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val type: ReminderType = ReminderType.MEDICATION,
    val title: String,
    val timeHour: Int,
    val timeMinute: Int,
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7), // 1 = Sunday, 2 = Monday, etc.
    val isEnabled: Boolean = true,
    val notificationMode: ReminderNotificationMode = ReminderNotificationMode.SOUND_AND_VIBRATE,
    val notes: String = ""
)

enum class ReminderType(val label: String, val iconName: String) {
    MEDICATION("Minum Obat", "medication"),
    WATER("Minum Air", "water_drop"),
    VITALS_CHECK("Cek Tensi/Gula", "monitor_heart"),
    EXERCISE("Aktivitas Fisik", "fitness_center")
}
