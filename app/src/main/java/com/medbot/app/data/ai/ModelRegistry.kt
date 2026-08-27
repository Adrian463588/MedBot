package com.medbot.app.data.ai

import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.model.ModelCapability
import com.medbot.app.domain.model.ModelFormat
import com.medbot.app.domain.model.ModelAccessRequirement

/**
 * Release-owned LiteRT-LM manifest registry.
 *
 * Entries are pinned to official HTTPS sources with exact byte size, source
 * revision, provenance, and an explicit runtime capability. A download is
 * still unavailable until the received SAF bytes pass the effective verified
 * contract and LiteRT-LM initializes them successfully on the device.
 */
object ModelRegistry {
    /**
     * Release-owned manifests pinned to immutable Hugging Face revisions.
     *
     * Public artifacts carry their verified LFS content OID here. Gated
     * artifacts may replace this bootstrap value with the authenticated
     * repository metadata before transfer; the downloader still hashes every
     * received byte before it promotes a SAF `.part` document to the final
     * model name.
    */
    val OFFICIAL_MODELS: List<ModelManifest> = listOf(
        ModelManifest(
            id = "qwen3-0-6b-nothink",
            displayName = "Qwen3 0.6B Direct (Recommended)",
            version = "main@6aa2daf8",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/6aa2daf8aba4aa456797fb8040b36a3948bcfda7/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            sizeBytes = 347_251_840L,
            sha256 = "2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139",
            minimumRamMb = 1024,
            isMultimodal = false,
            recommendedBackend = "CPU",
            description = "Direct-answer LiteRT-LM model with thinking disabled in the verified artifact for faster on-device chat.",
            fileName = "qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",
            provenance = "Hugging Face litert-community/Qwen3-0.6B-int4 model card; verified no-think LiteRT-LM artifact",
            sourceRevision = "6aa2daf8aba4aa456797fb8040b36a3948bcfda7",
            capability = ModelCapability.TEXT
        ),
        ModelManifest(
            id = "qwen3-0-6b",
            displayName = "Qwen3 0.6B (Text)",
            version = "main@8414150f",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/8414150f2e9dcc82449bcc9c5abc404b399a4d06/Qwen3-0.6B.litertlm",
            sizeBytes = 614_236_160L,
            sha256 = "555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4",
            minimumRamMb = 1024,
            isMultimodal = false,
            recommendedBackend = "CPU",
            description = "Text-only LiteRT-LM model for local conversation on devices with limited memory.",
            fileName = "Qwen3-0.6B.litertlm",
            provenance = "Hugging Face litert-community/Qwen3-0.6B model card; official LiteRT Community LFS artifact",
            sourceRevision = "8414150f2e9dcc82449bcc9c5abc404b399a4d06",
            capability = ModelCapability.TEXT
        ),
        ModelManifest(
            id = "llava-onevision-0-5b",
            displayName = "LLaVA-OneVision 0.5B (Vision)",
            version = "main@f57e8294",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/LLaVA-OneVision-0.5B/resolve/f57e82940debeeba231497e7ddb8cc21e0a122fd/LLaVA-OneVision-0.5B.litertlm",
            sizeBytes = 829_262_144L,
            sha256 = "7311912ada952c1905de6496193678117abd3b88fc82c319a30c167eb47ba97c",
            minimumRamMb = 2048,
            isMultimodal = true,
            recommendedBackend = "GPU",
            description = "Multimodal LiteRT-LM model for local image and text input when the vision backend is available.",
            fileName = "LLaVA-OneVision-0.5B.litertlm",
            provenance = "Hugging Face litert-community/LLaVA-OneVision-0.5B model card; official LiteRT Community LFS artifact",
            sourceRevision = "f57e82940debeeba231497e7ddb8cc21e0a122fd",
            capability = ModelCapability.VISION
        ),
        ModelManifest(
            id = "internvl3-5-1b",
            displayName = "InternVL3.5 1B (Vision)",
            version = "main@52b8757b",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/InternVL3_5-1B/resolve/52b8757b42f46fb5c394dffbda8895a8c17bc1b5/model.litertlm",
            sizeBytes = 818_025_840L,
            sha256 = "5ae4dbc96c8d4919e4776e9b8eee7f5ece8bdd2d1c057f73576c3d5f289d7ca4",
            minimumRamMb = 2048,
            isMultimodal = true,
            recommendedBackend = "GPU",
            description = "Multimodal LiteRT-LM model for detailed local image inspection when the vision backend is available.",
            fileName = "model.litertlm",
            provenance = "Hugging Face litert-community/InternVL3_5-1B model card; official LiteRT Community LFS artifact",
            sourceRevision = "52b8757b42f46fb5c394dffbda8895a8c17bc1b5",
            capability = ModelCapability.VISION
        ),
        ModelManifest(
            id = "gemma-4-e2b-it",
            displayName = "Gemma 4 E2B Instruct (Text)",
            version = "main@6b78abd0",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6b78abd019e61a1ca4cbe3b212d2c9ce8ff38a94/gemma-4-E2B-it.litertlm",
            sizeBytes = 2_588_147_712L,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            minimumRamMb = 3072,
            isMultimodal = false,
            recommendedBackend = "GPU",
            description = "Larger text-only LiteRT-LM model for local conversation on devices with sufficient memory.",
            fileName = "gemma-4-E2B-it.litertlm",
            provenance = "Hugging Face litert-community/gemma-4-E2B-it-litert-lm model card; official LiteRT Community LFS artifact",
            sourceRevision = "6b78abd019e61a1ca4cbe3b212d2c9ce8ff38a94",
            capability = ModelCapability.TEXT
        ),
        ModelManifest(
            id = "medgemma-1-5-4b-it-vision",
            displayName = "MedGemma 1.5 4B IT (Vision)",
            version = "artifact@9bcaf1a2",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/litert-community/MedGemma-1.5-4B-IT/resolve/9bcaf1a255db7a73120b1ff6baa5015512569cd2/medgemma-1.5-4b-it_q4_block32_vision_ekv2048.litertlm",
            sourcePageUrl = "https://huggingface.co/litert-community/MedGemma-1.5-4B-IT",
            sizeBytes = 3_023_069_488L,
            sha256 = "1627e2e433c3799e4ff06ff0895408ca65b255f786dc270fb5cfa325e349233a",
            minimumRamMb = 4096,
            isMultimodal = true,
            recommendedBackend = "GPU",
            description = "Official MedGemma 1.5 4B IT LiteRT-LM vision artifact for local clinical image and text analysis after verified source access.",
            fileName = "medgemma-1.5-4b-it_q4_block32_vision_ekv2048.litertlm",
            provenance = "Official Hugging Face litert-community/MedGemma-1.5-4B-IT model card; HAI-DEF gated source; LFS SHA-256 and byte size verified from the official repository API",
            sourceRevision = "9bcaf1a255db7a73120b1ff6baa5015512569cd2",
            capability = ModelCapability.VISION,
            accessRequirement = ModelAccessRequirement.HAI_DEF_ACCEPTANCE_AND_AUTHENTICATION
        )
    )

    /**
     * Arbitrary custom URLs are not a supported production input. The method
     * remains as a compatibility boundary for older callers, but it never
     * registers an unverified or user-supplied manifest.
     */
    @Deprecated("Custom URL manifests are not supported without release provenance")
    fun registerCustomManifest(@Suppress("UNUSED_PARAMETER") manifest: ModelManifest): Boolean = false

    fun getAllModels(): List<ModelManifest> = OFFICIAL_MODELS

    fun getManifestById(id: String): ModelManifest? =
        OFFICIAL_MODELS.firstOrNull { it.id == id }
}
