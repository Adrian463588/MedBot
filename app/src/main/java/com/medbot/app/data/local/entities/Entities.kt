package com.medbot.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val activeAgentId: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val text: String,
    val isUser: Boolean,
    val agentId: String?,
    val imageUri: String?,
    val citationsJson: String,
    val urgencyLevel: String?,
    val createdAt: Long
)

@Entity(tableName = "rag_documents")
data class RagDocumentEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val fileUri: String,
    val mimeType: String,
    val fileSize: Long,
    val pageCount: Int,
    val chunkCount: Int,
    val sha256: String,
    val indexedAt: Long
)

@Entity(
    tableName = "doc_chunks",
    foreignKeys = [
        ForeignKey(
            entity = RagDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["docId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("docId")]
)
data class DocChunkEntity(
    @PrimaryKey val id: String,
    val docId: String,
    val chunkIndex: Int,
    val textContent: String,
    val pageNumber: Int,
    val sectionTitle: String,
    val embeddingCsv: String // Store vector as comma-separated floats
)

@Entity(tableName = "skin_records")
data class SkinRecordEntity(
    @PrimaryKey val id: String,
    val bodyPart: String,
    val imagePath: String,
    val asymmetryScore: Float,
    val borderScore: Float,
    val colorScore: Float,
    val diameterMm: Float,
    val totalRiskScore: Float,
    val riskClassification: String,
    val asymmetryDesc: String,
    val borderDesc: String,
    val colorDesc: String,
    val diameterDesc: String,
    val differentialDxJson: String,
    val urgencyLevel: String,
    val clinicalSummary: String,
    val homeCareAdviceJson: String,
    val userNotes: String,
    val createdAt: Long
)

@Entity(tableName = "drugs_db")
data class DrugEntity(
    @PrimaryKey val name: String,
    val genericName: String,
    val category: String,
    val indication: String,
    val adultDose: String,
    val childDose: String,
    val contraindications: String,
    val sideEffects: String,
    val isOtc: Boolean,
    val alternativesJson: String
)

@Entity(tableName = "drug_interactions_db")
data class DrugInteractionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drugA: String,
    val drugB: String,
    val severity: String,
    val description: String,
    val recommendation: String
)

@Entity(tableName = "lab_tests_db")
data class LabTestEntity(
    @PrimaryKey val testName: String,
    val category: String,
    val unit: String,
    val normalLow: Double,
    val normalHigh: Double,
    val interpretationLow: String,
    val interpretationHigh: String,
    val clinicalSignificance: String
)

@Entity(tableName = "skin_remedies_db")
data class SkinRemedyEntity(
    @PrimaryKey val conditionKeywords: String,
    val naturalRemedy: String,
    val otcCream: String,
    val referralFlag: Boolean
)

@Entity(tableName = "health_metrics")
data class HealthMetricEntity(
    @PrimaryKey val id: String,
    val type: String,
    val valuePrimary: Float,
    val valueSecondary: Float?,
    val unit: String,
    val notes: String,
    val timestamp: Long
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val timeHour: Int,
    val timeMinute: Int,
    val daysOfWeekCsv: String,
    val isEnabled: Boolean,
    val notes: String
)
