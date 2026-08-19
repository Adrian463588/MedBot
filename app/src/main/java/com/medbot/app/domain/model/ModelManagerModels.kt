package com.medbot.app.domain.model

enum class ModelFormat {
    LITERTLM,
    GGUF,
    ONNX
}

/** Capability declared by a verified local model manifest. */
enum class ModelCapability {
    TEXT,
    VISION
}

/** Runtime capability required by a model load request. */
enum class VisionCapability {
    NOT_REQUIRED,
    REQUIRED
}

/** Availability states used when model prerequisites are not satisfied. */
enum class ModelAvailability {
    AVAILABLE,
    MODEL_UNAVAILABLE,
    STORAGE_PERMISSION_REQUIRED,
    STORAGE_UNAVAILABLE,
    VISION_RUNTIME_UNAVAILABLE
}

/** Durable model transfer states. No state implies verification success. */
enum class ModelDownloadState {
    NO_STORAGE_DESTINATION,
    STORAGE_PERMISSION_REQUIRED,
    MODEL_UNAVAILABLE,
    READY_TO_DOWNLOAD,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY_TO_LOAD,
    LOADED,
    FAILED
}

/** Domain contract for a verified or unavailable model load attempt. */
sealed interface ModelLoadResult {
    data class Loaded(
        val sourceUri: String,
        val backend: String,
        val visionCapability: VisionCapability
    ) : ModelLoadResult

    data class Unavailable(
        val code: String,
        val message: String
    ) : ModelLoadResult
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
    val description: String,
    /** Exact artifact name in the destination SAF tree. Required for verification. */
    val fileName: String = "",
    /** Release-owned source provenance; blank values are never downloadable. */
    val provenance: String = "",
    /** Immutable source revision or release identifier; blank values are never downloadable. */
    val sourceRevision: String = "",
    /** Declared model capability; legacy constructors derive this from isMultimodal. */
    val capability: ModelCapability = if (isMultimodal) ModelCapability.VISION else ModelCapability.TEXT
) {
    val visionCapability: VisionCapability
        get() = if (isMultimodal || capability == ModelCapability.VISION) {
            VisionCapability.REQUIRED
        } else {
            VisionCapability.NOT_REQUIRED
        }

    val requiresVision: Boolean
        get() = visionCapability == VisionCapability.REQUIRED

    val partialFileName: String
        get() = "$fileName.part"
}

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
