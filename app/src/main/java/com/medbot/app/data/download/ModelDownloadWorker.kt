package com.medbot.app.data.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.data.platform.HuggingFaceArtifactMetadataParser
import com.medbot.app.data.platform.AndroidSafGateway
import com.medbot.app.data.platform.HuggingFaceTokenStore
import com.medbot.app.data.platform.SafHuggingFaceTokenBackup
import com.medbot.app.data.download.ModelDownloadNotificationStage.DOWNLOADING
import com.medbot.app.data.download.ModelDownloadNotificationStage.VERIFYING
import com.medbot.app.domain.model.ModelDownloadProtocol
import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.model.ModelManifestValidationResult
import com.medbot.app.domain.model.ModelManifestValidator
import com.medbot.app.domain.model.ModelAccessRequirement
import com.medbot.app.domain.model.ModelVerificationMetadata
import com.medbot.app.domain.model.ModelVerificationMetadataCodec
import com.medbot.app.domain.repository.ModelStorageException
import com.medbot.app.domain.repository.ModelStorageGateway
import com.medbot.app.domain.repository.ModelStorageArtifactNames
import com.medbot.app.domain.repository.StoredModelArtifact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Resumable, integrity-checked model download worker backed exclusively by SAF. */
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val storageGateway: ModelStorageGateway = AndroidSafGateway(context.applicationContext)
    private val huggingFaceTokenStore = HuggingFaceTokenStore(context.applicationContext)
    private val huggingFaceTokenBackup = SafHuggingFaceTokenBackup(huggingFaceTokenStore, storageGateway)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelId = inputData.getString(KEY_MODEL_ID)?.trim().orEmpty()
        val destinationTreeUri = inputData.getString(KEY_DESTINATION_TREE_URI)?.trim().orEmpty()
        if (modelId.isEmpty()) return@withContext failure("MODEL_UNAVAILABLE")
        if (destinationTreeUri.isEmpty()) return@withContext failure("MODEL_STORAGE_DESTINATION_REQUIRED")

        val registeredManifest = ModelRegistry.getManifestById(modelId)
            ?: return@withContext failure("MODEL_UNAVAILABLE")
        val validation = ModelManifestValidator.validate(registeredManifest)
        if (validation !is ModelManifestValidationResult.Valid) {
            return@withContext failure("MODEL_MANIFEST_INVALID")
        }
        if (registeredManifest.downloadUrl.toHttpUrlOrNull()?.scheme != "https") {
            return@withContext failure("MODEL_URL_INVALID")
        }

        if (storageGateway.takePersistableTreePermission(destinationTreeUri).isFailure) {
            return@withContext failure("STORAGE_PERMISSION_REQUIRED")
        }
        if (storageGateway.validateDestination(destinationTreeUri).isFailure) {
            return@withContext failure("STORAGE_UNAVAILABLE")
        }
        val accessToken = if (registeredManifest.accessRequirement != ModelAccessRequirement.NONE) {
            huggingFaceTokenBackup.restoreIfNeeded(destinationTreeUri)
            val token = huggingFaceTokenStore.readToken()
            if (token.isNullOrBlank()) {
                return@withContext failure("MODEL_ACCESS_TOKEN_REQUIRED")
            }
            if (!huggingFaceTokenStore.hasTermsAccepted(registeredManifest.sourceRevision)) {
                return@withContext failure("MODEL_TERMS_NOT_ACCEPTED")
            }
            token
        } else {
            null
        }

        // Gated repositories can hide their LFS OID until the caller is
        // authenticated. Resolve it from the official tree API before any
        // bytes are accepted; a stale bootstrap value is never trusted.
        val manifest = if (registeredManifest.accessRequirement != ModelAccessRequirement.NONE) {
            resolveAuthenticatedManifest(registeredManifest, accessToken.orEmpty())
                .getOrElse { error ->
                    return@withContext failure(
                        (error as? ManifestResolutionException)?.failureCode
                            ?: "MODEL_MANIFEST_UNAVAILABLE"
                    )
                }
        } else {
            registeredManifest
        }

        persistVerificationMetadata(destinationTreeUri, manifest)?.let { errorCode ->
            return@withContext failure(errorCode)
        }

        val finalArtifact = storageGateway.findArtifact(destinationTreeUri, manifest.fileName)
            .getOrElse { error -> return@withContext failure(storageError(error)) }
        if (finalArtifact != null) {
            val verified = storageGateway.verifyArtifact(
                finalArtifact.documentUri,
                manifest.sizeBytes,
                manifest.sha256
            )
            if (verified.isSuccess) return@withContext success(verified.getOrThrow().documentUri, manifest.sizeBytes)
            // A file with the canonical name failed the immutable manifest. Keep
            // the bytes for forensic recovery, but move them out of the
            // canonical namespace so Retry can create a clean candidate.
            val rejectedName = "${manifest.fileName}.rejected-${manifest.sourceRevision.take(8)}"
            storageGateway.findArtifact(destinationTreeUri, rejectedName)
                .getOrElse { error -> return@withContext failure(storageError(error)) }
                ?.let { rejected ->
                    storageGateway.deleteArtifact(rejected.documentUri)
                        .getOrElse { error -> return@withContext failure(storageError(error)) }
                }
            storageGateway.renameArtifact(finalArtifact.documentUri, rejectedName)
                .getOrElse { error -> return@withContext failure(storageError(error)) }
        }

        var partialArtifact = storageGateway.findArtifact(destinationTreeUri, manifest.partialFileName)
            .getOrElse { error -> return@withContext failure(storageError(error)) }
            ?: storageGateway.createArtifact(destinationTreeUri, manifest.partialFileName)
                .getOrElse { error -> return@withContext failure(storageError(error)) }

        var offset = partialArtifact.sizeBytes
        if (offset < 0L) return@withContext failure("STORAGE_METADATA_UNAVAILABLE")
        if (offset > manifest.sizeBytes) return@withContext failure("PARTIAL_ARTIFACT_OVERSIZED")

        if (offset == manifest.sizeBytes) {
            return@withContext promoteVerifiedPartial(destinationTreeUri, manifest, partialArtifact)
        }

        if (offset == 0L && storageGateway.setResumeValidator(
                destinationTreeUri,
                manifest.partialFileName,
                null
            ).isFailure
        ) {
            return@withContext failure("RESUME_VALIDATOR_STORAGE_FAILED")
        }

        var resumeValidator = if (offset > 0L) {
            storageGateway.getResumeValidator(destinationTreeUri, manifest.partialFileName)
                .getOrElse { error -> return@withContext failure(storageError(error)) }
        } else {
            null
        }
        if (offset > 0L && resumeValidator.isNullOrBlank()) {
            // App/provider/process death can leave bytes without the validator
            // needed to prove that a Range response belongs to the same source.
            // Discard only this unverified candidate and restart from byte zero;
            // never append an unvalidated response to it.
            storageGateway.deleteArtifact(partialArtifact.documentUri)
                .getOrElse { error -> return@withContext failure(storageError(error)) }
            partialArtifact = storageGateway.createArtifact(destinationTreeUri, manifest.partialFileName)
                .getOrElse { error -> return@withContext failure(storageError(error)) }
            offset = 0L
            resumeValidator = null
        }

        try {
            setForeground(
                ModelDownloadNotification.createForegroundInfo(
                    context = applicationContext,
                    modelId = modelId,
                    displayName = manifest.displayName,
                    bytesDownloaded = offset,
                    totalBytes = manifest.sizeBytes,
                    speedBytesPerSecond = 0L,
                    stage = DOWNLOADING
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return@withContext failure("DOWNLOAD_NOTIFICATION_UNAVAILABLE")
        }

        setProgress(
            workDataOf(
                KEY_BYTES to offset,
                KEY_TOTAL to manifest.sizeBytes,
                KEY_SPEED to 0L,
                KEY_STATUS to "DOWNLOADING"
            )
        )

        val url = manifest.downloadUrl.toHttpUrlOrNull()
            ?: return@withContext failure("MODEL_URL_INVALID")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MedBot-Client/1.0 (Android; Mobile)")
            .header("Accept", "*/*")
            .apply {
                if (manifest.accessRequirement != ModelAccessRequirement.NONE) {
                    val token = huggingFaceTokenStore.readToken()
                    if (!token.isNullOrBlank()) {
                        header("Authorization", "Bearer $token")
                    }
                }
                if (offset > 0L) {
                    header("Range", "bytes=$offset-")
                    if (!resumeValidator.isNullOrBlank()) {
                        header("If-Range", resumeValidator)
                    }
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body
                if (
                    manifest.accessRequirement != ModelAccessRequirement.NONE &&
                    response.code in setOf(401, 403)
                ) {
                    return@withContext failure("MODEL_ACCESS_UNAUTHORIZED")
                }
                val protocolFailure = ModelDownloadProtocol.validateResponse(
                    responseCode = response.code,
                    offset = offset,
                    expectedTotal = manifest.sizeBytes,
                    responseLength = body?.contentLength()?.takeIf { it >= 0L },
                    contentRange = response.header("Content-Range"),
                    expectedEtag = resumeValidator,
                    responseEtag = response.header("ETag")
                )
                if (protocolFailure != null) return@withContext failure(protocolFailure.name)
                if (body == null) return@withContext failure("RESPONSE_BODY_EMPTY")

                val responseEtag = response.header("ETag")
                if (!responseEtag.isNullOrBlank() && storageGateway.setResumeValidator(
                        destinationTreeUri,
                        manifest.partialFileName,
                        responseEtag
                    ).isFailure
                ) {
                    return@withContext failure("RESUME_VALIDATOR_STORAGE_FAILED")
                }

                val output = storageGateway.openOutputStream(partialArtifact.documentUri, append = offset > 0L)
                    .getOrElse { error -> return@withContext failure(storageError(error)) }
        var downloaded = offset
        val started = System.nanoTime()
        var lastUpdate = started
        var lastNotificationUpdate = started
        output.use { destination ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            if (isStopped) return@withContext Result.retry()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            if (downloaded + count > manifest.sizeBytes) {
                                return@withContext failure("RESPONSE_OVERSIZED")
                            }
                            destination.write(buffer, 0, count)
                            downloaded += count

                            val now = System.nanoTime()
                            if (now - lastUpdate >= PROGRESS_INTERVAL_NS) {
                                val elapsedSeconds = ((now - started).coerceAtLeast(1L)) / 1_000_000_000.0
                                val transferred = downloaded - offset
                                setProgress(
                                    workDataOf(
                                        KEY_BYTES to downloaded,
                                        KEY_TOTAL to manifest.sizeBytes,
                                        KEY_SPEED to (transferred / elapsedSeconds).toLong(),
                                        KEY_STATUS to "DOWNLOADING"
                                    )
                                )
                                if (now - lastNotificationUpdate >= NOTIFICATION_INTERVAL_NS) {
                                    setForeground(
                                        ModelDownloadNotification.createForegroundInfo(
                                            context = applicationContext,
                                            modelId = modelId,
                                            displayName = manifest.displayName,
                                            bytesDownloaded = downloaded,
                                            totalBytes = manifest.sizeBytes,
                                            speedBytesPerSecond = (transferred / elapsedSeconds).toLong(),
                                            stage = DOWNLOADING
                                        )
                                    )
                                    lastNotificationUpdate = now
                                }
                                lastUpdate = now
                            }
                        }
                    }
                    destination.flush()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            return@withContext failure("DOWNLOAD_IO_FAILED")
        } catch (error: Throwable) {
            return@withContext failure(error.message ?: "DOWNLOAD_FAILED")
        }

        val currentPartial = storageGateway.findArtifact(destinationTreeUri, manifest.partialFileName)
            .getOrElse { error -> return@withContext failure(storageError(error)) }
            ?: return@withContext failure("PARTIAL_ARTIFACT_MISSING")
        val sizeFailure = ModelDownloadProtocol.validateFinalSize(currentPartial.sizeBytes, manifest.sizeBytes)
        if (sizeFailure != null) return@withContext failure(sizeFailure.name)
        return@withContext promoteVerifiedPartial(destinationTreeUri, manifest, currentPartial)
    }

    private suspend fun promoteVerifiedPartial(
        destinationTreeUri: String,
        manifest: ModelManifest,
        partialArtifact: StoredModelArtifact
    ): Result {
        try {
            setForeground(
                ModelDownloadNotification.createForegroundInfo(
                    context = applicationContext,
                    modelId = manifest.id,
                    displayName = manifest.displayName,
                    bytesDownloaded = partialArtifact.sizeBytes,
                    totalBytes = manifest.sizeBytes,
                    speedBytesPerSecond = 0L,
                    stage = VERIFYING
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return failure("DOWNLOAD_NOTIFICATION_UNAVAILABLE")
        }
        setProgress(
            workDataOf(
                KEY_BYTES to partialArtifact.sizeBytes,
                KEY_TOTAL to manifest.sizeBytes,
                KEY_SPEED to 0L,
                KEY_STATUS to "VERIFYING"
            )
        )
        val verified = storageGateway.verifyArtifact(
            partialArtifact.documentUri,
            manifest.sizeBytes,
            manifest.sha256
        ).getOrElse { error ->
            if (ModelDownloadRecovery.shouldDiscardPartial(error)) {
                val cleanup = discardRejectedPartial(destinationTreeUri, manifest, partialArtifact)
                if (cleanup != null) return failure(cleanup)
                // Never retry the same bytes after an integrity failure. The
                // next Retry starts at byte zero with no stale ETag/Range state.
                return failure("SHA256_MISMATCH")
            }
            return failure(storageError(error))
        }
        val promoted = storageGateway.renameArtifact(verified.documentUri, manifest.fileName)
            .getOrElse { error -> return failure(storageError(error)) }
        val final = storageGateway.verifyArtifact(
            promoted.documentUri,
            manifest.sizeBytes,
            manifest.sha256
        ).getOrElse { error ->
            if (ModelDownloadRecovery.shouldDiscardPartial(error)) {
                storageGateway.deleteArtifact(promoted.documentUri)
                    .getOrElse { cleanupError -> return failure(storageError(cleanupError)) }
                return failure("SHA256_MISMATCH")
            }
            return failure(storageError(error))
        }
        storageGateway.setResumeValidator(destinationTreeUri, manifest.partialFileName, null)
            .getOrElse { error -> return failure(storageError(error)) }
        return success(final.documentUri, manifest.sizeBytes)
    }

    /** Removes only the failed partial candidate and its resume proof. */
    private fun discardRejectedPartial(
        destinationTreeUri: String,
        manifest: ModelManifest,
        partialArtifact: StoredModelArtifact
    ): String? {
        storageGateway.setResumeValidator(destinationTreeUri, manifest.partialFileName, null)
            .getOrElse { error -> return storageError(error) }
        storageGateway.deleteArtifact(partialArtifact.documentUri)
            .getOrElse { error -> return storageError(error) }
        return null
    }

    private fun resolveAuthenticatedManifest(
        manifest: ModelManifest,
        accessToken: String
    ): kotlin.Result<ModelManifest> {
        if (manifest.id != MEDGEMMA_MODEL_ID) return kotlin.Result.success(manifest)
        val metadataUrl = "https://huggingface.co/api/models/litert-community/MedGemma-1.5-4B-IT/tree/${manifest.sourceRevision}?recursive=true&expand=true"
            .toHttpUrlOrNull()
            ?: return kotlin.Result.failure(ManifestResolutionException("MODEL_MANIFEST_UNAVAILABLE"))
        val request = Request.Builder()
            .url(metadataUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "MedBot-Client/1.0 (Android; Mobile)")
            .header("Authorization", "Bearer $accessToken")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    return kotlin.Result.failure(ManifestResolutionException("MODEL_ACCESS_UNAUTHORIZED"))
                }
                if (!response.isSuccessful) {
                    return kotlin.Result.failure(ManifestResolutionException("MODEL_MANIFEST_UNAVAILABLE"))
                }
                val body = response.body?.string()
                    ?: return kotlin.Result.failure(ManifestResolutionException("MODEL_MANIFEST_UNAVAILABLE"))
                val metadata = HuggingFaceArtifactMetadataParser.parse(
                    responseBody = body,
                    fileName = manifest.fileName,
                    expectedSizeBytes = manifest.sizeBytes,
                    expectedSourceRevision = manifest.sourceRevision
                ).getOrElse { error ->
                    return kotlin.Result.failure(ManifestResolutionException("MODEL_MANIFEST_UNAVAILABLE", error))
                }
                kotlin.Result.success(
                    manifest.copy(
                        sizeBytes = metadata.sizeBytes,
                        sha256 = metadata.sha256
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            kotlin.Result.failure(ManifestResolutionException("MODEL_MANIFEST_UNAVAILABLE", error))
        }
    }

    private fun persistVerificationMetadata(
        destinationTreeUri: String,
        manifest: ModelManifest
    ): String? {
        val metadataName = ModelStorageArtifactNames.verificationMetadata(manifest.id)
        val artifact = storageGateway.findArtifact(destinationTreeUri, metadataName)
            .getOrElse { error -> return storageError(error) }
            ?: storageGateway.createArtifact(destinationTreeUri, metadataName)
                .getOrElse { error -> return storageError(error) }
        val metadata = ModelVerificationMetadataCodec.encode(
            ModelVerificationMetadata(
                modelId = manifest.id,
                fileName = manifest.fileName,
                sizeBytes = manifest.sizeBytes,
                sha256 = manifest.sha256,
                sourceRevision = manifest.sourceRevision
            )
        )
        storageGateway.openOutputStream(artifact.documentUri, append = false)
            .getOrElse { error -> return storageError(error) }
            .use { output ->
                output.write(metadata.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        return null
    }

    private class ManifestResolutionException(
        val failureCode: String,
        cause: Throwable? = null
    ) : IOException(failureCode, cause)

    private suspend fun success(documentUri: String, totalBytes: Long): Result {
        setProgress(
            workDataOf(
                KEY_BYTES to totalBytes,
                KEY_TOTAL to totalBytes,
                KEY_SPEED to 0L,
                KEY_STATUS to "READY_TO_LOAD"
            )
        )
        return Result.success(workDataOf(KEY_FILE_URI to documentUri))
    }

    private fun failure(message: String): Result {
        Log.w(TAG, "Model download failed: $message")
        return Result.failure(workDataOf(KEY_ERROR to message))
    }

    private fun storageError(error: Throwable?): String =
        when (error) {
            is ModelStorageException -> error.code.name
            is SecurityException -> "STORAGE_PERMISSION_REQUIRED"
            else -> "STORAGE_UNAVAILABLE"
        }

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_DESTINATION_TREE_URI = "destination_tree_uri"
        const val KEY_PROGRESS = "progress"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
        const val KEY_STATUS = "status"
        const val KEY_FILE_URI = "file_uri"
        const val KEY_ERROR = "error"
        private const val TAG = "ModelDownloadWorker"
        private const val MEDGEMMA_MODEL_ID = "medgemma-1-5-4b-it-vision"
        private const val BUFFER_SIZE = 1024 * 1024
        private const val PROGRESS_INTERVAL_NS = 250_000_000L
        private const val NOTIFICATION_INTERVAL_NS = 1_000_000_000L
    }
}
