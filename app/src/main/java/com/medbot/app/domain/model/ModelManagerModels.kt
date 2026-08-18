package com.medbot.app.domain.model

enum class ModelFormat {
    LITERTLM,
    GGUF,
    ONNX
}

enum class ModelDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY_TO_LOAD,
    LOADED_IN_RAM,
    ERROR
}

data class ModelManifest(
    val id: String,
    val displayName: String,
    val version: String,
    val format: ModelFormat,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val minimumRamMb: Int,
    val isMultimodal: Boolean,
    val recommendedBackend: String, // "GPU", "CPU", "AUTO"
    val description: String
)

data class DownloadProgress(
    val modelId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val status: ModelDownloadStatus,
    val errorMessage: String? = null
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()) * 100f else 0f
}
