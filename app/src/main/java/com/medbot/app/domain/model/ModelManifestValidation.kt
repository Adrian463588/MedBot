package com.medbot.app.domain.model

import java.net.URI

enum class ModelManifestFailure {
    UNSUPPORTED_FORMAT,
    INVALID_ID,
    INVALID_FILE_NAME,
    INVALID_URL,
    INVALID_SIZE,
    INVALID_SHA256,
    INVALID_RAM_REQUIREMENT,
    MISSING_PROVENANCE,
    MISSING_SOURCE_REVISION,
    INVALID_CAPABILITY
}

/** A manifest that passed all integrity and provenance checks. */
data class VerifiedModelManifest(val manifest: ModelManifest)

sealed interface ModelManifestValidationResult {
    data class Valid(val verified: VerifiedModelManifest) : ModelManifestValidationResult
    data class Invalid(val reason: ModelManifestFailure) : ModelManifestValidationResult
}

/** Pure validation boundary shared by the registry, manager, worker, and tests. */
object ModelManifestValidator {
    private val sha256Pattern = Regex("[0-9a-fA-F]{64}")
    private val safeFileNamePattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.litertlm", RegexOption.IGNORE_CASE)

    fun validate(manifest: ModelManifest): ModelManifestValidationResult {
        if (manifest.id.isBlank() || manifest.id.contains('/') || manifest.id.contains('\\')) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.INVALID_ID)
        }
        if (manifest.format != ModelFormat.LITERTLM) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.UNSUPPORTED_FORMAT)
        }
        if (!safeFileNamePattern.matches(manifest.fileName)) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.INVALID_FILE_NAME)
        }
        if (!isHttpsUrl(manifest.downloadUrl)) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.INVALID_URL)
        }
        if (manifest.sizeBytes <= 0L) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.INVALID_SIZE)
        }
        if (!sha256Pattern.matches(manifest.sha256.trim())) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.INVALID_SHA256)
        }
        if (manifest.minimumRamMb <= 0) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.INVALID_RAM_REQUIREMENT)
        }
        if (manifest.provenance.isBlank()) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.MISSING_PROVENANCE)
        }
        if (manifest.sourceRevision.isBlank()) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.MISSING_SOURCE_REVISION)
        }
        val capabilityMatches = manifest.isMultimodal == (manifest.capability == ModelCapability.VISION)
        if (!capabilityMatches) {
            return ModelManifestValidationResult.Invalid(ModelManifestFailure.INVALID_CAPABILITY)
        }
        return ModelManifestValidationResult.Valid(VerifiedModelManifest(manifest))
    }

    fun isVerified(manifest: ModelManifest): Boolean = validate(manifest) is ModelManifestValidationResult.Valid

    private fun isHttpsUrl(value: String): Boolean {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.fragment == null
    }
}
