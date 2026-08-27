package com.medbot.app.domain.model

import java.util.Locale

/** Non-secret integrity contract resolved from the official model source. */
data class ModelVerificationMetadata(
    val modelId: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val sourceRevision: String
)

/** Small, deterministic SAF sidecar format for reinstall-safe verification. */
object ModelVerificationMetadataCodec {
    private val sha256Pattern = Regex("[0-9a-fA-F]{64}")

    fun encode(metadata: ModelVerificationMetadata): String = listOf(
        "version=1",
        "modelId=${metadata.modelId}",
        "fileName=${metadata.fileName}",
        "sizeBytes=${metadata.sizeBytes}",
        "sha256=${metadata.sha256.lowercase(Locale.ROOT)}",
        "sourceRevision=${metadata.sourceRevision}"
    ).joinToString("\n") + "\n"

    fun decode(value: String): ModelVerificationMetadata? {
        val fields = value.lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 1)
            }
            .toMap()
        val modelId = fields["modelId"]?.trim().orEmpty()
        val fileName = fields["fileName"]?.trim().orEmpty()
        val sizeBytes = fields["sizeBytes"]?.trim()?.toLongOrNull() ?: return null
        val sha256 = fields["sha256"]?.trim().orEmpty()
        val sourceRevision = fields["sourceRevision"]?.trim().orEmpty()
        if (fields["version"] != "1" || modelId.isBlank() || fileName.isBlank() ||
            sizeBytes <= 0L || !sha256Pattern.matches(sha256) || sourceRevision.isBlank()
        ) {
            return null
        }
        return ModelVerificationMetadata(modelId, fileName, sizeBytes, sha256, sourceRevision)
    }
}
