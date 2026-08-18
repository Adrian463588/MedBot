package com.medbot.app.domain.repository

import com.medbot.app.domain.model.DoctorAgent
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.domain.model.SkinRecord
import kotlinx.coroutines.flow.Flow

/** Domain boundary for an initialized, local-only language model runtime. */
interface LocalLlmGateway {
    fun buildFullSystemPrompt(
        agent: DoctorAgent,
        persona: PersonaConfig,
        ragContext: String? = null
    ): String

    fun streamInference(
        prompt: String,
        systemPrompt: String,
        agent: DoctorAgent
    ): Flow<String>
}

enum class LocalInferenceFailure {
    VISION_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    RAG_UNAVAILABLE
}

/** Explicit local-runtime failure; callers must render an unavailable state. */
class LocalInferenceException(
    val failure: LocalInferenceFailure,
    message: String
) : IllegalStateException(message)

/** Domain boundary for a local vision model; no heuristic implementation is valid. */
interface SkinAnalysisGateway {
    fun analyzeSkinImage(imagePath: String, bodyPart: String, userNotes: String = ""): SkinRecord
}

enum class SkinAnalysisStatus {
    INSUFFICIENT_DATA,
    UNAVAILABLE
}

/** Typed fail-closed skin-analysis state shared across data and presentation boundaries. */
class SkinAnalysisException(
    val status: SkinAnalysisStatus,
    message: String
) : IllegalStateException(message)
