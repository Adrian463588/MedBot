package com.medbot.app

import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.domain.model.ModelCapability
import com.medbot.app.domain.model.ModelAccessRequirement
import com.medbot.app.domain.model.ModelFormat
import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.model.ModelManifestFailure
import com.medbot.app.domain.model.ModelManifestValidationResult
import com.medbot.app.domain.model.ModelManifestValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestValidationTest {
    @Test
    fun blankShaIsUnavailable() {
        val result = ModelManifestValidator.validate(manifest(sha256 = ""))

        assertEquals(
            ModelManifestFailure.INVALID_SHA256,
            (result as ModelManifestValidationResult.Invalid).reason
        )
    }

    @Test
    fun missingProvenanceIsUnavailable() {
        val result = ModelManifestValidator.validate(manifest(provenance = ""))

        assertEquals(
            ModelManifestFailure.MISSING_PROVENANCE,
            (result as ModelManifestValidationResult.Invalid).reason
        )
    }

    @Test
    fun missingSourceRevisionIsUnavailable() {
        val result = ModelManifestValidator.validate(manifest(sourceRevision = ""))

        assertEquals(
            ModelManifestFailure.MISSING_SOURCE_REVISION,
            (result as ModelManifestValidationResult.Invalid).reason
        )
    }

    @Test
    fun nonHttpsOrUnsafeFileNameIsUnavailable() {
        val invalidUrl = ModelManifestValidator.validate(manifest(downloadUrl = "http://example.invalid/model.litertlm"))
        val invalidName = ModelManifestValidator.validate(manifest(fileName = "../model.litertlm"))

        assertEquals(
            ModelManifestFailure.INVALID_URL,
            (invalidUrl as ModelManifestValidationResult.Invalid).reason
        )
        assertEquals(
            ModelManifestFailure.INVALID_FILE_NAME,
            (invalidName as ModelManifestValidationResult.Invalid).reason
        )
    }

    @Test
    fun completeManifestIsVerified() {
        val result = ModelManifestValidator.validate(manifest())

        assertTrue(result is ModelManifestValidationResult.Valid)
    }

    @Test
    @Suppress("DEPRECATION")
    fun productionRegistryAndCustomUrlBoundaryAreVerified() {
        assertTrue(ModelRegistry.getAllModels().isNotEmpty())
        assertTrue(ModelRegistry.getAllModels().all { ModelManifestValidator.isVerified(it) })
        assertTrue(ModelRegistry.getManifestById("test-model") == null)
        assertTrue(!ModelRegistry.registerCustomManifest(manifest()))
        assertTrue(ModelRegistry.getAllModels().all { ModelManifestValidator.isVerified(it) })
    }

    @Test
    fun medGemmaVisionManifestUsesOfficialArtifactMetadataAndGatedAccess() {
        val manifest = ModelRegistry.getManifestById("medgemma-1-5-4b-it-vision")

        requireNotNull(manifest)
        assertEquals(
            "https://huggingface.co/litert-community/MedGemma-1.5-4B-IT/resolve/9bcaf1a255db7a73120b1ff6baa5015512569cd2/medgemma-1.5-4b-it_q4_block32_vision_ekv2048.litertlm",
            manifest.downloadUrl
        )
        assertEquals(3_023_069_488L, manifest.sizeBytes)
        assertEquals(
            "1627e2e433c3799e4ff06ff0895408ca65b255f786dc270fb5cfa325e349233a",
            manifest.sha256
        )
        assertEquals(ModelAccessRequirement.HAI_DEF_ACCEPTANCE_AND_AUTHENTICATION, manifest.accessRequirement)
        assertTrue(ModelManifestValidator.isVerified(manifest))
    }

    private fun manifest(
        downloadUrl: String = "https://example.invalid/models/test-model.litertlm",
        fileName: String = "test-model.litertlm",
        sha256: String = "a".repeat(64),
        provenance: String = "test release manifest",
        sourceRevision: String = "test-revision"
    ): ModelManifest = ModelManifest(
        id = "test-model",
        displayName = "Test model",
        version = "1.0.0",
        format = ModelFormat.LITERTLM,
        downloadUrl = downloadUrl,
        sizeBytes = 1024L,
        sha256 = sha256,
        minimumRamMb = 1024,
        isMultimodal = false,
        recommendedBackend = "CPU",
        description = "Test-only manifest fixture",
        fileName = fileName,
        provenance = provenance,
        sourceRevision = sourceRevision,
        capability = ModelCapability.TEXT
    )
}
