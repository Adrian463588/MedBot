package com.medbot.app.domain.repository

import com.medbot.app.domain.model.DoctorAgent
import com.medbot.app.domain.model.ChatMessage
import com.medbot.app.domain.model.PersonaConfig
import com.medbot.app.domain.model.SkinRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Domain boundary for an initialized, local-only language model runtime. */
interface LocalLlmGateway {
    /** True only when a local runtime is initialized and ready to generate. */
    fun isReady(): Boolean = true

    /** True only when the initialized runtime accepts image content. */
    fun supportsVision(): Boolean = false

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

    /**
     * Streams a message through a session-scoped conversation. Implementations
     * that support native conversation state reuse their KV cache; the default
     * delegates to the one-shot API so test and unavailable gateways remain
     * fail-closed.
     */
    fun streamInferenceInConversation(
        conversationId: String,
        prompt: String,
        systemPrompt: String,
        agent: DoctorAgent,
        history: List<ChatMessage> = emptyList()
    ): Flow<String> = streamInference(prompt, systemPrompt, agent)

    /**
     * Streams a multimodal request through an initialized local vision runtime.
     * The default implementation fails closed so text-only gateways cannot
     * accidentally claim to process an image.
     */
    fun streamVisionInference(
        imageUri: String,
        prompt: String,
        systemPrompt: String,
        agent: DoctorAgent
    ): Flow<String> = flow {
        throw LocalInferenceException(
            LocalInferenceFailure.VISION_UNAVAILABLE,
            "No initialized local vision-capable runtime accepts image content."
        )
    }

    /** Session-scoped multimodal counterpart to [streamInferenceInConversation]. */
    fun streamVisionInferenceInConversation(
        conversationId: String,
        imageUri: String,
        prompt: String,
        systemPrompt: String,
        agent: DoctorAgent,
        history: List<ChatMessage> = emptyList()
    ): Flow<String> = streamVisionInference(imageUri, prompt, systemPrompt, agent)
}

enum class LocalInferenceFailure {
    VISION_UNAVAILABLE,
    MODEL_UNAVAILABLE,
    RAG_UNAVAILABLE,
    INFERENCE_FAILED,
    INSUFFICIENT_EVIDENCE,
    UNSAFE_OUTPUT
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
