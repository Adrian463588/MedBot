package com.medbot.app.data.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.medbot.app.data.ai.ModelRegistry
import com.medbot.app.data.platform.AndroidSafGateway
import com.medbot.app.domain.model.ModelDownloadProtocol
import com.medbot.app.domain.model.ModelManifest
import com.medbot.app.domain.model.ModelManifestValidationResult
import com.medbot.app.domain.model.ModelManifestValidator
import com.medbot.app.domain.repository.ModelStorageException
import com.medbot.app.domain.repository.ModelStorageGateway
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

        val manifest = ModelRegistry.getManifestById(modelId)
            ?: return@withContext failure("MODEL_UNAVAILABLE")
        val validation = ModelManifestValidator.validate(manifest)
        if (validation !is ModelManifestValidationResult.Valid) {
            return@withContext failure("MODEL_MANIFEST_INVALID")
        }
        if (manifest.downloadUrl.toHttpUrlOrNull()?.scheme != "https") {
            return@withContext failure("MODEL_URL_INVALID")
        }

        if (storageGateway.takePersistableTreePermission(destinationTreeUri).isFailure) {
            return@withContext failure("STORAGE_PERMISSION_REQUIRED")
        }
        if (storageGateway.validateDestination(destinationTreeUri).isFailure) {
            return@withContext failure("STORAGE_UNAVAILABLE")
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
            return@withContext failure("TARGET_EXISTS_INVALID")
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
        val url = manifest.downloadUrl.toHttpUrlOrNull()
            ?: return@withContext failure("MODEL_URL_INVALID")
        val request = Request.Builder()
            .url(url)
            .apply {
                if (offset > 0L) {
                    header("Range", "bytes=$offset-")
                    header("If-Range", resumeValidator.orEmpty())
                }
            }
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body
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
            return failure(if (error is ModelStorageException) "SHA256_MISMATCH" else storageError(error))
        }
        val promoted = storageGateway.renameArtifact(verified.documentUri, manifest.fileName)
            .getOrElse { error -> return failure(storageError(error)) }
        val final = storageGateway.verifyArtifact(
            promoted.documentUri,
            manifest.sizeBytes,
            manifest.sha256
        ).getOrElse { error -> return failure(storageError(error)) }
        storageGateway.setResumeValidator(destinationTreeUri, manifest.partialFileName, null)
        return success(final.documentUri, manifest.sizeBytes)
    }

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
        private const val BUFFER_SIZE = 1024 * 1024
        private const val PROGRESS_INTERVAL_NS = 250_000_000L
    }
}
