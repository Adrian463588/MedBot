package com.medbot.app.data.ai

import com.medbot.app.domain.model.ModelFormat
import com.medbot.app.domain.model.ModelManifest

object ModelRegistry {

    val OFFICIAL_MODELS: List<ModelManifest> = listOf(
        ModelManifest(
            id = "gemma-4-e2b-it",
            displayName = "Gemma 4 E2B Instruct (Mobile Standard)",
            version = "2026.08",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/google/gemma-4-E2B-it-LiteRT/resolve/main/gemma-4-E2B-it.litertlm",
            sizeBytes = 2_580_000_000L,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            minimumRamMb = 6000,
            isMultimodal = true,
            recommendedBackend = "GPU",
            description = "Model utama yang sangat optimal untuk ponsel Android 6-8 GB RAM. Mendukung penalaran klinis, triase, dan multimodalitas."
        ),
        ModelManifest(
            id = "gemma-4-e4b-it",
            displayName = "Gemma 4 E4B Instruct (High Precision)",
            version = "2026.08",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/google/gemma-4-E4B-it-LiteRT/resolve/main/gemma-4-E4B-it.litertlm",
            sizeBytes = 3_650_000_000L,
            sha256 = "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2",
            minimumRamMb = 8000,
            isMultimodal = true,
            recommendedBackend = "GPU",
            description = "Model berkapabilitas lebih tinggi untuk perangkat Android 8-12 GB RAM (seperti POCO X7 Pro). Memberikan analisis diagnosis banding lebih mendalam."
        ),
        ModelManifest(
            id = "gemma-2-2b-it-gguf",
            displayName = "Gemma 2 2B Instruct (Q4_K_M GGUF)",
            version = "2.0",
            format = ModelFormat.GGUF,
            downloadUrl = "https://huggingface.co/unsloth/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            sizeBytes = 1_550_000_000L,
            sha256 = "8f3b7d1592c3a50f789e9f783281c7e909a823f6681b95b871c5ef33a1e9c201",
            minimumRamMb = 4000,
            isMultimodal = false,
            recommendedBackend = "CPU",
            description = "Model ringkas dan hemat memori (~1.5 GB), dapat berjalan di perangkat dengan RAM 4-6 GB."
        ),
        ModelManifest(
            id = "litert-vision-medsiglip",
            displayName = "LiteRT Vision Medical Bundle",
            version = "1.0",
            format = ModelFormat.LITERTLM,
            downloadUrl = "https://huggingface.co/google/medsiglip-litert/resolve/main/medsiglip-vision.litertlm",
            sizeBytes = 450_000_000L,
            sha256 = "b7a99823c14a9a4e8d3568a0c24e5b8d234a9b8971f11a4325a76c023d854e12",
            minimumRamMb = 2000,
            isMultimodal = true,
            recommendedBackend = "GPU",
            description = "Bundel visi khusus untuk klasifikasi lesi kulit, citra luka, dan dokumen rontgen/lab."
        )
    )

    fun getManifestById(id: String): ModelManifest? = OFFICIAL_MODELS.firstOrNull { it.id == id }
}
