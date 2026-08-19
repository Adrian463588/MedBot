package com.medbot.app.data.ai

import com.medbot.app.domain.model.ModelFormat
import com.medbot.app.domain.model.ModelManifest

/**
 * Official and community-tested LiteRT-LM models from Hugging Face litert-community.
 * Sourced directly from Reference4.md.
 */
object ModelRegistry {
    private val _customModels = mutableMapOf<String, ModelManifest>()

    val OFFICIAL_MODELS: List<ModelManifest> = listOf(
        ModelManifest(
            id = "gemma-4-e2b-it",
            displayName = "Gemma 4 E2B Instruct (Recommended)",
            version = "4.0",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.litertlm",
            sizeBytes = 2_791_728_742L,
            sha256 = "",
            minimumRamMb = 3072,
            isMultimodal = false,
            recommendedBackend = "GPU",
            description = "Best balance of performance and accuracy for on-device medical consultation (8K context, 2B parameters)."
        ),
        ModelManifest(
            id = "qwen3-0-6b-it",
            displayName = "Qwen3 0.6B Instruct (Lightweight)",
            version = "3.0",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/qwen3-0.6b-it.litertlm",
            sizeBytes = 644_245_094L,
            sha256 = "",
            minimumRamMb = 1024,
            isMultimodal = false,
            recommendedBackend = "CPU",
            description = "Ultra-fast, lightweight model ideal for quick triage on devices with limited RAM (4K context)."
        ),
        ModelManifest(
            id = "gemma3-1b-it-int4",
            displayName = "Gemma 3 1B IT INT4",
            version = "3.0",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
            sizeBytes = 524_288_000L,
            sha256 = "",
            minimumRamMb = 1536,
            isMultimodal = false,
            recommendedBackend = "GPU",
            description = "Optimized 4-bit quantized Gemma 3 model for rapid inference and minimal storage footprint."
        ),
        ModelManifest(
            id = "gemma-4-e4b-it",
            displayName = "Gemma 4 E4B Instruct (High Quality)",
            version = "4.0",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it-web.litertlm",
            sizeBytes = 3_221_225_472L,
            sha256 = "",
            minimumRamMb = 4096,
            isMultimodal = false,
            recommendedBackend = "GPU",
            description = "Larger parameter model with superior multi-step clinical reasoning and diagnostic depth."
        ),
        ModelManifest(
            id = "vibethinker-3b",
            displayName = "VibeThinker-3B (Reasoning)",
            version = "1.0",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/VibeThinker-3B/resolve/main/vibethinker-3b.litertlm",
            sizeBytes = 2_040_109_465L,
            sha256 = "",
            minimumRamMb = 3072,
            isMultimodal = false,
            recommendedBackend = "GPU",
            description = "Specialized reasoning model optimized for mathematical dosages and structured clinical deductions."
        ),
        ModelManifest(
            id = "llava-onevision-0-5b",
            displayName = "LLaVA-OneVision 0.5B (Vision)",
            version = "0.5",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/LLaVA-OneVision-0.5B/resolve/main/llava-onevision-0.5b.litertlm",
            sizeBytes = 869_269_504L,
            sha256 = "",
            minimumRamMb = 2048,
            isMultimodal = true,
            recommendedBackend = "GPU",
            description = "Compact vision-language model with SigLIP encoder for skin lesion and medical image analysis."
        ),
        ModelManifest(
            id = "internvl3-5-1b",
            displayName = "InternVL3.5-1B (Vision High)",
            version = "3.5",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/InternVL3_5-1B/resolve/main/internvl3_5-1b.litertlm",
            sizeBytes = 880_803_840L,
            sha256 = "",
            minimumRamMb = 2048,
            isMultimodal = true,
            recommendedBackend = "GPU",
            description = "Advanced vision model with InternViT encoder for detailed visual grounding and dermatological inspection."
        )
    )

    fun registerCustomManifest(manifest: ModelManifest) {
        _customModels[manifest.id] = manifest
    }

    fun getAllModels(): List<ModelManifest> = OFFICIAL_MODELS + _customModels.values.toList()

    fun getManifestById(id: String): ModelManifest? =
        _customModels[id] ?: OFFICIAL_MODELS.firstOrNull { it.id == id }
}

