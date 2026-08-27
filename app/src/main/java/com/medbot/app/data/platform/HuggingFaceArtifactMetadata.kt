package com.medbot.app.data.platform

import org.json.JSONArray

/** Metadata returned by the authenticated Hugging Face repository tree API. */
data class HuggingFaceArtifactMetadata(
    val sizeBytes: Long,
    val sha256: String,
    val sourceRevision: String
)

/** Parses only the exact file and fields needed for byte-level verification. */
object HuggingFaceArtifactMetadataParser {
    private val sha256Pattern = Regex("[0-9a-fA-F]{64}")

    fun parse(
        responseBody: String,
        fileName: String,
        expectedSizeBytes: Long,
        expectedSourceRevision: String
    ): Result<HuggingFaceArtifactMetadata> = runCatching {
        val files = JSONArray(responseBody)
        val file = (0 until files.length())
            .asSequence()
            .map { files.getJSONObject(it) }
            .firstOrNull { it.optString("path") == fileName }
            ?: error("Official artifact metadata is missing")
        val sizeBytes = file.optLong("size", -1L)
        val sha256 = file.optJSONObject("lfs")?.optString("oid").orEmpty().trim()
        val sourceRevision = file.optJSONObject("lastCommit")?.optString("id").orEmpty().trim()
        require(sizeBytes == expectedSizeBytes) { "Official artifact size changed" }
        require(sha256Pattern.matches(sha256)) { "Official LFS SHA-256 is unavailable" }
        if (sourceRevision.isNotBlank()) {
            require(sourceRevision == expectedSourceRevision) { "Official artifact revision changed" }
        }
        HuggingFaceArtifactMetadata(sizeBytes, sha256.lowercase(), expectedSourceRevision)
    }
}
