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
import com.medbot.app.domain.model.DownloadProgress
import com.medbot.app.domain.model.ModelDownloadStatus
import com.medbot.app.domain.model.ModelFormat
import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.model.ModelManifestValidationResult
import com.medbot.app.domain.model.ModelManifestValidator
import com.medbot.app.domain.repository.ModelStorageGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

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
    private val verifiedDocumentUris = ConcurrentHashMap<String, String>()

    /** Uses the persisted destination only when it is a validated SAF tree. */
    fun startDownload(modelId: String) {
        startDownload(modelId, preferences.getString(KEY_SAF_MODEL_URI, null))
    }

    /** Enqueues a download with the destination tree URI carried in WorkManager input. */
    fun startDownload(modelId: String, destinationTreeUri: String?) {
        val manifest = ModelRegistry.getManifestById(modelId) ?: return
        if (ModelManifestValidator.validate(manifest) !is ModelManifestValidationResult.Valid) return
        val treeUri = destinationTreeUri?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (storageGateway.takePersistableTreePermission(treeUri).isFailure) return
        if (storageGateway.validateDestination(treeUri).isFailure) return

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
        workManager.enqueueUniqueWork("download_$modelId", ExistingWorkPolicy.REPLACE, request)
    }

    /** Cancels active work while preserving a valid SAF `.part` for resume. */
    fun pauseDownload(modelId: String) = workManager.cancelUniqueWork("download_$modelId")

    /** Cancels active work and removes only this model's SAF partial artifact. */
    fun cancelDownload(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
        val manifest = verifiedManifest(modelId) ?: return
        val treeUri = destinationTreeUri() ?: return
        storageGateway.findArtifact(treeUri, manifest.partialFileName).getOrNull()?.let {
            storageGateway.deleteArtifact(it.documentUri)
        }
    }

    /** Deletes only the verified model artifact and its partial SAF artifact. */
    fun deleteModel(modelId: String) {
        workManager.cancelUniqueWork("download_$modelId")
        val manifest = verifiedManifest(modelId) ?: return
        val treeUri = destinationTreeUri() ?: return
        listOf(manifest.fileName, manifest.partialFileName).forEach { name ->
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
    fun getInstalledModelPath(modelId: String): String? = verifiedDocumentUris[modelId]

    fun getDownloadProgressFlow(modelId: String): Flow<DownloadProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow("download_$modelId")
            .map { infos ->
                val info = infos.firstOrNull() ?: return@map checkFileStatus(modelId)
                val manifest = ModelRegistry.getManifestById(modelId)
                val progress = info.progress
                when (info.state) {
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.BLOCKED -> DownloadProgress(
                        modelId = modelId,
                        bytesDownloaded = progress.getLong(ModelDownloadWorker.KEY_BYTES, 0L),
                        totalBytes = progress.getLong(ModelDownloadWorker.KEY_TOTAL, manifest?.sizeBytes ?: 0L),
                        speedBytesPerSec = progress.getLong(ModelDownloadWorker.KEY_SPEED, 0L),
                        status = ModelDownloadStatus.DOWNLOADING,
                        errorMessage = progress.getString(ModelDownloadWorker.KEY_ERROR)
                    )

                    WorkInfo.State.SUCCEEDED -> checkFileStatus(modelId)
                    WorkInfo.State.CANCELLED -> checkFileStatus(modelId)
                    WorkInfo.State.FAILED -> {
                        val current = checkFileStatus(modelId)
                        current.copy(
                            status = ModelDownloadStatus.ERROR,
                            errorMessage = info.outputData.getString(ModelDownloadWorker.KEY_ERROR)
                                ?: current.errorMessage
                                ?: "DOWNLOAD_FAILED"
                        )
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
                manifest.sizeBytes,
                manifest.sha256
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
    }
}
