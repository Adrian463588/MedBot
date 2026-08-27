package com.medbot.app.domain.model

import java.util.UUID

enum class AppLanguage(val code: String, val displayName: String) {
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English")
}

enum class PersonaTone(val id: String, val label: String, val promptModifier: String) {
    EMPATHETIC(
        "empathetic",
        "Empatis & Hangat",
        "Gunakan nada bicara yang hangat, ramah, penuh empati, dan menenangkan hati pasien. Gunakan analogi sederhana untuk menjelaskan istilah medis."
    ),
    CLINICAL(
        "clinical",
        "Klinis & Presisi",
        "Gunakan gaya penulisan formal medis, presisi, mencantumkan terminologi klinis, diagnosis banding, dan dasar rasional medis."
    ),
    CONCISE(
        "concise",
        "Ringkas & Aksi",
        "Berikan jawaban yang sangat ringkas, to-the-point, fokus pada langkah aksi dan pertolongan pertama, hindari pengantar panjang."
    ),
    EDUCATIONAL(
        "educational",
        "Edukatif & Rinci",
        "Fokus pada edukasi komprehensif mengenai patofisiologi penyakit, penyebab, dan pencegahan jangka panjang."
    )
}

enum class DetailDepth(val id: String, val label: String, val promptModifier: String) {
    SIMPLE(
        "simple",
        "Bahasa Awam (Sederhana)",
        "Jelaskan dengan bahasa orang awam tanpa istilah medis yang rumit. Maksimal 3 paragraf."
    ),
    STANDARD(
        "standard",
        "Standar (Berimbang)",
        "Berikan penjelasan terstruktur lengkap dengan poin-poin anjuran dan tanda bahaya."
    ),
    DEEP(
        "deep",
        "Mendalam (Tenaga Medis)",
        "Berikan analisis mendalam mencakup diagnosis banding komprehensif dan rujukan protokol klinis."
    )
}

data class PersonaConfig(
    val selectedAgentId: String = "orchestrator",
    val tone: PersonaTone = PersonaTone.EMPATHETIC,
    val depth: DetailDepth = DetailDepth.STANDARD,
    val language: AppLanguage = AppLanguage.INDONESIAN,
    val customInstructions: String = "",
    val patientProfileSummary: String = ""
)

data class DoctorAgent(
    val id: String,
    val displayNameId: String,
    val displayNameEn: String,
    val specialtyId: String,
    val specialtyEn: String,
    val systemPromptId: String,
    val systemPromptEn: String,
    val tools: List<String> = emptyList(),
    val supportsImage: Boolean = false,
    val iconName: String = "local-hospital"
)

data class OrchestratorResult(
    val primarySpecialist: String,
    val secondarySpecialists: List<String> = emptyList(),
    /** Zero means no calibrated probability is available for rule-based routing. */
    val confidence: Float = 0.0f,
    val urgency: UrgencyLevel = UrgencyLevel.INSUFFICIENT_DATA,
    val reasoning: String = ""
)

enum class UrgencyLevel(val labelId: String, val labelEn: String, val hexColor: String) {
    LOW("Rendah", "Low", "#27AE60"),
    MEDIUM("Sedang", "Medium", "#F39C12"),
    HIGH("Tinggi", "High", "#E67E22"),
    EMERGENCY("Gawat Darurat", "Emergency", "#E74C3C"),
    INSUFFICIENT_DATA("Data tidak cukup", "Insufficient data", "#64748B")
}

data class Citation(
    /** Stable identifier used to bind a displayed claim to one evidence item. */
    val citationId: String = "",
    val documentTitle: String,
    val pageNumber: Int = 0,
    val snippet: String,
    val sectionTitle: String = "",
    /** Empty for local files; HTTPS only for allowlisted online sources. */
    val sourceUrl: String = "",
    /** Persisted SAF URI for a user-selected local source, when permission remains valid. */
    val sourceUri: String = "",
    val sourceName: String = "",
    /** Stable source classification, kept as a serialized value at the UI boundary. */
    val sourceRole: String = "",
    val sourceSha256: String = "",
    val recordId: String = "",
    val revision: String = "",
    val retrievedAt: Long? = null
)

enum class ChatGenerationPhase {
    IDLE,
    CHECKING_MODEL,
    INDEXING_KNOWLEDGE,
    PREPARING,
    RETRIEVING_LOCAL_EVIDENCE,
    SEARCHING_LOCAL,
    SEARCHING_WEB,
    INFERENCING,
    GENERATING,
    READY,
    UNAVAILABLE,
    ERROR,
    CANCELLED
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val text: String,
    val isUser: Boolean,
    val agentId: String? = null,
    val imageUri: String? = null,
    val citations: List<Citation> = emptyList(),
    val urgencyLevel: UrgencyLevel? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val activeAgentId: String = "orchestrator",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
