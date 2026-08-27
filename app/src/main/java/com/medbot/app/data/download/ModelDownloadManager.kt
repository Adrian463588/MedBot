package com.medbot.app.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.data.platform.AndroidSafGateway
import com.medbot.app.data.platform.HuggingFaceTokenStore
import com.medbot.app.data.platform.SafHuggingFaceTokenBackup
import com.medbot.app.domain.model.DownloadProgress
import com.medbot.app.domain.model.ModelDownloadStatus
import com.medbot.app.domain.model.ModelFormat
import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.model.ModelManifestValidationResult
import com.medbot.app.domain.model.ModelManifestValidator
import com.medbot.app.domain.model.ModelAccessRequirement
import com.medbot.app.domain.model.ModelVerificationMetadataCodec
import com.medbot.app.domain.repository.ModelStorageGateway
import com.medbot.app.domain.repository.ModelStorageArtifactNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Typed failure returned when a download cannot be scheduled from the UI. */
class ModelDownloadStartException(
    val code: String,
    cause: Throwable? = null
) : IllegalStateException(code, cause)

/**
 * Schedules and observes SAF-backed model downloads.
 *
 * No app-private model path is ever returned as ready. The legacy synchronous
 * path accessor only returns a previously verified SAF document URI cached by
 * this manager; callers that need a fresh status must observe the progress flow.
 */
class ModelDownloadManager(
    context: Context,
    private val storageGateway: ModelStorageGateway = AndroidSafGateway(context)
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val huggingFaceTokenStore = HuggingFaceTokenStore(appContext)
    private val huggingFaceTokenBackup = SafHuggingFaceTokenBackup(huggingFaceTokenStore, storageGateway)
    private val verifiedDocumentUris = ConcurrentHashMap<String, String>()

    /** Uses the persisted destination only when it is a validated SAF tree. */
    fun startDownload(modelId: String): Result<Unit> {
        return startDownload(modelId, preferences.getString(KEY_SAF_MODEL_URI, null))
    }

    /** Enqueues a download with the destination tree URI carried in WorkManager input. */
    fun startDownload(modelId: String, destinationTreeUri: String?): Result<Unit> {
        val manifest = ModelRegistry.getManifestById(modelId)
            ?: return Result.failure(ModelDownloadStartException("MODEL_UNAVAILABLE"))
        if (ModelManifestValidator.validate(manifest) !is ModelManifestValidationResult.Valid) {
            return Result.failure(ModelDownloadStartException("MODEL_MANIFEST_INVALID"))
        }
        val treeUri = destinationTreeUri?.trim()?.takeIf { it.isNotEmpty() }
            ?: return Result.failure(ModelDownloadStartException("MODEL_STORAGE_DESTINATION_REQUIRED"))
        if (storageGateway.takePersistableTreePermission(treeUri).isFailure) {
            return Result.failure(ModelDownloadStartException("STORAGE_PERMISSION_REQUIRED"))
        }
        if (storageGateway.validateDestination(treeUri).isFailure) {
            return Result.failure(ModelDownloadStartException("STORAGE_UNAVAILABLE"))
        }
        if (manifest.accessRequirement != ModelAccessRequirement.NONE) {
            huggingFaceTokenBackup.restoreIfNeeded(treeUri)
            if (!huggingFaceTokenStore.hasToken()) {
                return Result.failure(ModelDownloadStartException("MODEL_ACCESS_TOKEN_REQUIRED"))
            }
            if (!huggingFaceTokenStore.hasTermsAccepted(manifest.sourceRevision)) {
                return Result.failure(ModelDownloadStartException("MODEL_TERMS_NOT_ACCEPTED"))
            }
        }

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_MODEL_ID to modelId,
                    ModelDownloadWorker.KEY_DESTINATION_TREE_URI to treeUri
                )
            )
            .addTag("download_$modelId")
            .build()
        return try {
            workManager.enqueueUniqueWork("download_$modelId", ExistingWorkPolicy.REPLACE, request)
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(ModelDownloadStartException("DOWNLOAD_ENQUEUE_FAILED", error))
        }
    }

    fun hasHuggingFaceToken(): Boolean = huggingFaceTokenStore.hasToken()

    fun saveHuggingFaceToken(token: String): Result<Unit> = runCatching {
        huggingFaceTokenStore.saveToken(token).getOrThrow()
        destinationTreeUri()?.let { treeUri ->
            huggingFaceTokenBackup.persist(treeUri).getOrThrow()
        }
    }

    fun acceptedHuggingFaceTermsRevision(): String? = huggingFaceTokenStore.acceptedTermsRevision()

    fun acceptHuggingFaceTerms(sourceRevision: String): Result<Unit> {
        val normalized = sourceRevision.trim()
        val isKnownGatedRevision = ModelRegistry.getAllModels().any { manifest ->
            manifest.accessRequirement != ModelAccessRequirement.NONE &&
                manifest.sourceRevision == normalized
        }
        if (!isKnownGatedRevision) {
            return Result.failure(IllegalArgumentException("Unknown gated model source revision"))
        }
        return runCatching {
            huggingFaceTokenStore.saveTermsAccepted(normalized).getOrThrow()
            destinationTreeUri()?.let { treeUri ->
                huggingFaceTokenBackup.persist(treeUri).getOrThrow()
            }
        }
    }

    fun restoreHuggingFaceAccessFromSaf(treeUri: String): Result<Unit> =
        huggingFaceTokenBackup.restoreIfNeeded(treeUri)

    fun persistHuggingFaceAccessToSaf(treeUri: String): Result<Unit> =
        huggingFaceTokenBackup.persist(treeUri)

    fun hasHuggingFaceTokenBackup(treeUri: String): Boolean =
        huggingFaceTokenBackup.hasBackup(treeUri).getOrDefault(false)

    fun clearHuggingFaceToken(): Result<Unit> = runCatching {
        // Keep the local credential until the SAF backup is removed. This
        // prevents a failed provider delete from being followed by a later
        // startup restore of a token the user explicitly cleared.
        destinationTreeUri()?.let { treeUri ->
            huggingFaceTokenBackup.clear(treeUri).getOrThrow()
        }
        huggingFaceTokenStore.clearToken()
    }

    /** Cancels active work while preserving a valid SAF `.part` for resume. */
    fun pauseDownload(modelId: String) = workManager.cancelUniqueWork("download_$modelId")

    /** Cancels active work and removes only this model's SAF partial artifact. */
    fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
        val manifest = verifiedManifest(modelId) ?: return
        val treeUri = destinationTreeUri() ?: return
        storageGateway.setResumeValidator(treeUri, manifest.partialFileName, null)
        storageGateway.findArtifact(treeUri, manifest.partialFileName).getOrNull()?.let {
            storageGateway.deleteArtifact(it.documentUri)
        }
    }

    /** Deletes only the verified model artifact and its partial SAF artifact. */
    fun deleteModel(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
        val manifest = verifiedManifest(modelId) ?: return
        val treeUri = destinationTreeUri() ?: return
        storageGateway.setResumeValidator(treeUri, manifest.partialFileName, null)
        listOf(
            manifest.fileName,
            manifest.partialFileName,
            ModelStorageArtifactNames.verificationMetadata(manifest.id)
        ).forEach { name ->
            storageGateway.findArtifact(treeUri, name).getOrNull()?.let {
                storageGateway.deleteArtifact(it.documentUri)
            }
        }
        verifiedDocumentUris.remove(modelId)
    }

    /**
     * Legacy name retained for the existing repository contract. The returned
     * value is a verified `content://` SAF URI, never an app-private file path.
     */
    fun getInstalledModelPath(modelId: String): String? {
        val cached = verifiedDocumentUris[modelId]
        // Only a prior full verifyArtifact call may populate this cache. A
        // size-only filesystem observation is not sufficient for model load.
        return cached
    }

    /**
     * Re-verifies the canonical SAF artifact and returns its effective contract.
     * Gated models use the authenticated metadata sidecar; the registry
     * bootstrap checksum is never passed to LiteRT-LM for those artifacts.
     */
    fun getVerifiedModelManifest(modelId: String): ModelManifest? {
        val manifest = verifiedManifest(modelId) ?: return null
        val treeUri = destinationTreeUri() ?: return null
        val effective = persistedVerificationManifest(treeUri, manifest)
            ?: manifest.takeIf { it.accessRequirement == ModelAccessRequirement.NONE }
            ?: return null
        val artifact = storageGateway.findArtifact(treeUri, effective.fileName).getOrNull() ?: return null
        val verified = storageGateway.verifyArtifact(
            artifact.documentUri,
            effective.sizeBytes,
            effective.sha256
        ).getOrNull() ?: return null
        verifiedDocumentUris[modelId] = verified.documentUri
        return effective
    }

    fun getDownloadProgressFlow(modelId: String): Flow<DownloadProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow("download_$modelId")
            .map { infos ->
                val info = infos.firstOrNull() ?: return@map checkFileStatus(modelId)
                val manifest = ModelRegistry.getManifestById(modelId)
                val progress = info.progress
                when (info.state) {
                    WorkInfo.State.RUNNING -> DownloadProgress(
                        modelId = modelId,
                        bytesDownloaded = progress.getLong(ModelDownloadWorker.KEY_BYTES, 0L),
                        totalBytes = progress.getLong(ModelDownloadWorker.KEY_TOTAL, manifest?.sizeBytes ?: 0L),
                        speedBytesPerSec = progress.getLong(ModelDownloadWorker.KEY_SPEED, 0L),
                        status = when (progress.getString(ModelDownloadWorker.KEY_STATUS)) {
                            "VERIFYING" -> ModelDownloadStatus.VERIFYING
                            "DOWNLOADING" -> ModelDownloadStatus.DOWNLOADING
                            else -> ModelDownloadStatus.PREPARING
                        },
                        errorMessage = progress.getString(ModelDownloadWorker.KEY_ERROR)
                    )
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> DownloadProgress(
                        modelId = modelId,
                        bytesDownloaded = progress.getLong(ModelDownloadWorker.KEY_BYTES, 0L),
                        totalBytes = progress.getLong(ModelDownloadWorker.KEY_TOTAL, manifest?.sizeBytes ?: 0L),
                        speedBytesPerSec = 0L,
                        status = ModelDownloadStatus.PREPARING,
                        errorMessage = progress.getString(ModelDownloadWorker.KEY_ERROR)
                    )

                    WorkInfo.State.SUCCEEDED -> checkFileStatus(modelId)
                    WorkInfo.State.CANCELLED -> checkFileStatus(modelId)
                    WorkInfo.State.FAILED -> {
                        val current = checkFileStatus(modelId)
                        if (current.status == ModelDownloadStatus.READY_TO_LOAD) {
                            // A previous WorkManager failure may outlive a valid
                            // SAF artifact. The verified artifact is the source
                            // of truth; do not surface stale failure state.
                            current
                        } else {
                            current.copy(
                                status = ModelDownloadStatus.ERROR,
                                errorMessage = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                                    ?: current.errorMessage
                                    ?: "DOWNLOAD_FAILED"
                            )
                        }
                    }
                }
            }
            .flowOn(Dispatchers.IO)

    /** Reconciles only artifacts in the persisted SAF tree. */
    fun checkFileStatus(modelId: String): DownloadProgress {
        val manifest = verifiedManifest(modelId)
            ?: return DownloadProgress(modelId, 0L, 0L, 0L, ModelDownloadStatus.ERROR, "MODEL_UNAVAILABLE")
        val treeUri = destinationTreeUri()
            ?: return DownloadProgress(
                modelId,
                0L,
                manifest.sizeBytes,
                0L,
                ModelDownloadStatus.ERROR,
                "MODEL_STORAGE_DESTINATION_REQUIRED"
            )
        val destination = storageGateway.validateDestination(treeUri)
        if (destination.isFailure) {
            return DownloadProgress(
                modelId,
                0L,
                manifest.sizeBytes,
                0L,
                ModelDownloadStatus.ERROR,
                storageError(destination.exceptionOrNull())
            )
        }

        val verificationManifest = persistedVerificationManifest(treeUri, manifest)
        if (manifest.accessRequirement != ModelAccessRequirement.NONE && verificationManifest == null) {
            val finalExists = storageGateway.findArtifact(treeUri, manifest.fileName).getOrNull() != null
            if (finalExists) {
                return DownloadProgress(
                    modelId,
                    0L,
                    manifest.sizeBytes,
                    0L,
                    ModelDownloadStatus.ERROR,
                    "MODEL_MANIFEST_UNAVAILABLE"
                )
            }
        }
        val manifestForVerification = verificationManifest ?: manifest

        val finalArtifact = storageGateway.findArtifact(treeUri, manifest.fileName)
            .getOrElse { error ->
                return DownloadProgress(
                    modelId,
                    0L,
                    manifest.sizeBytes,
                    0L,
                    ModelDownloadStatus.ERROR,
                    storageError(error)
                )
            }
        if (finalArtifact != null) {
            val verified = storageGateway.verifyArtifact(
                finalArtifact.documentUri,
                manifestForVerification.sizeBytes,
                manifestForVerification.sha256
            )
            if (verified.isSuccess) {
                val artifact = verified.getOrThrow()
                verifiedDocumentUris[modelId] = artifact.documentUri
                return DownloadProgress(
                    modelId,
                    manifest.sizeBytes,
                    manifest.sizeBytes,
                    0L,
                    ModelDownloadStatus.READY_TO_LOAD
                )
            }
            return DownloadProgress(
                modelId,
                finalArtifact.sizeBytes.coerceAtLeast(0L),
                manifest.sizeBytes,
                0L,
                ModelDownloadStatus.ERROR,
                storageError(verified.exceptionOrNull())
            )
        }

        val partial = storageGateway.findArtifact(treeUri, manifest.partialFileName)
            .getOrElse { error ->
                return DownloadProgress(
                    modelId,
                    0L,
                    manifest.sizeBytes,
                    0L,
                    ModelDownloadStatus.ERROR,
                    storageError(error)
                )
            }
        if (partial != null) {
            if (partial.sizeBytes > manifest.sizeBytes) {
                return DownloadProgress(
                    modelId,
                    partial.sizeBytes,
                    manifest.sizeBytes,
                    0L,
                    ModelDownloadStatus.ERROR,
                    "PARTIAL_ARTIFACT_OVERSIZED"
                )
            }
            if (partial.sizeBytes > 0L) {
                val validator = storageGateway.getResumeValidator(treeUri, manifest.partialFileName)
                    .getOrElse { error ->
                        return DownloadProgress(
                            modelId,
                            partial.sizeBytes,
                            manifest.sizeBytes,
                            0L,
                            ModelDownloadStatus.ERROR,
                            storageError(error)
                        )
                    }
                if (validator.isNullOrBlank()) {
                    return DownloadProgress(
                        modelId,
                        partial.sizeBytes,
                        manifest.sizeBytes,
                        0L,
                        ModelDownloadStatus.ERROR,
                        "RESUME_VALIDATOR_MISSING"
                    )
                }
            }
            return DownloadProgress(
                modelId,
                partial.sizeBytes.coerceAtLeast(0L),
                manifest.sizeBytes,
                0L,
                ModelDownloadStatus.PAUSED
            )
        }
        return DownloadProgress(
            modelId,
            0L,
            manifest.sizeBytes,
            0L,
            ModelDownloadStatus.NOT_DOWNLOADED
        )
    }

    private fun verifiedManifest(modelId: String): ModelManifest? {
        val manifest = ModelRegistry.getManifestById(modelId) ?: return null
        return manifest.takeIf {
            it.format == ModelFormat.LITERTLM &&
                ModelManifestValidator.validate(it) is ModelManifestValidationResult.Valid
        }
    }

    private fun persistedVerificationManifest(
        treeUri: String,
        manifest: ModelManifest
    ): ModelManifest? {
        val metadataName = ModelStorageArtifactNames.verificationMetadata(manifest.id)
        val artifact = storageGateway.findArtifact(treeUri, metadataName).getOrNull() ?: return null
        val text = storageGateway.openInputStream(artifact.documentUri).getOrNull()?.use(::readBoundedText)
            ?: return null
        val metadata = ModelVerificationMetadataCodec.decode(text) ?: return null
        if (metadata.modelId != manifest.id || metadata.fileName != manifest.fileName ||
            metadata.sourceRevision != manifest.sourceRevision || metadata.sizeBytes != manifest.sizeBytes
        ) {
            return null
        }
        val candidate = manifest.copy(
            sizeBytes = metadata.sizeBytes,
            sha256 = metadata.sha256
        )
        return candidate.takeIf {
            ModelManifestValidator.validate(it) is ModelManifestValidationResult.Valid
        }
    }

    private fun readBoundedText(input: InputStream): String? {
        val bytes = ByteArray(MAX_VERIFICATION_METADATA_BYTES + 1)
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) return String(bytes, 0, offset, Charsets.UTF_8)
            if (count == 0) continue
            offset += count
        }
        return null
    }

    private fun destinationTreeUri(): String? =
        preferences.getString(KEY_SAF_MODEL_URI, null)?.trim()?.takeIf { it.isNotEmpty() }

    private fun storageError(error: Throwable?): String {
        return when (error) {
            is com.medbot.app.domain.repository.ModelStorageException -> error.code.name
            is SecurityException -> "STORAGE_PERMISSION_REQUIRED"
            else -> "STORAGE_UNAVAILABLE"
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "medbot_preferences"
        private const val KEY_SAF_MODEL_URI = "saf_model_folder_uri"
        private const val MAX_VERIFICATION_METADATA_BYTES = 4096
    }
}
